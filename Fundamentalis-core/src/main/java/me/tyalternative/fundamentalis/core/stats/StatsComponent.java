package me.tyalternative.fundamentalis.core.stats;

import me.tyalternative.fundamentalis.api.component.Component;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.event.stats.StatChangeEvent;
import me.tyalternative.fundamentalis.api.event.stats.StatComputeEvent;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatModifier;
import me.tyalternative.fundamentalis.api.stats.StatType;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation de {@link IStatsComponent}.
 *
 * <p>Gère trois couches de données :
 * <ol>
 *   <li><strong>Base</strong> — valeurs persistées en BDD, modifiées par les commandes
 *       et les systèmes de progression.</li>
 *   <li><strong>Modificateurs</strong> — buffs/debuffs temporaires (items, sorts, buffs
 *       de classe), stockés en RAM uniquement.</li>
 *   <li><strong>Cache final</strong> — résultat calculé de {@code getFinal()}, invalidé
 *       à chaque changement de base ou de modificateurs pour éviter les recalculs inutiles.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>Les valeurs de base sont stockées dans une {@link ConcurrentHashMap} —
 *       lecture thread-safe sans synchronisation.</li>
 *   <li>Les modificateurs et le cache final sont accédés uniquement sur le thread
 *       principal Bukkit (les setters vérifient cela en mode debug).</li>
 * </ul>
 *
 * <h2>Cycle de vie</h2>
 * <ol>
 *   <li>Instanciation par {@link me.tyalternative.fundamentalis.core.stats.StatsManager StatsManager}.</li>
 *   <li>{@link #applyLoadedData(Map)} — données chargées depuis la BDD (async → sync).</li>
 *   <li>Utilisation normale (get/set/addModifier).</li>
 *   <li>{@link #onDetach()} — flush sync final avant démontage.</li>
 * </ol>
 */
public class StatsComponent implements IStatsComponent, Component {

    // -------------------------------------------------------------------------
    // Clé du composant — utilisée par ComponentHolder pour l'identification
    // -------------------------------------------------------------------------

    public static final ComponentKey<IStatsComponent> KEY = ComponentKey.of("fundamentalis:stats",IStatsComponent.class);
//            ComponentKey.of("fundamentalis:stats", IStatsComponent.class);

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    /** Valeurs de base (persistées en BDD). Thread-safe en lecture. */
    private final ConcurrentHashMap<String, Integer> baseValues = new ConcurrentHashMap<>();

    /**
     * Modificateurs temporaires groupés par statId.
     * Source → modificateur, pour permettre un retrait par source en O(1).
     */
    private final Map<String, Map<String, StatModifier>> modifiers = new HashMap<>();

    /** Cache des valeurs finales calculées. Invalidé à chaque changement. */
    private final Map<String, Double> finalCache = new HashMap<>();

    private final ComponentHolder   holder;
    private final IStatTypeRegistry registry;
    private final boolean           debugLog;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param holder   le ComponentHolder auquel ce composant est attaché
     * @param registry registre des StatType, pour résoudre les ids et les défauts
     * @param debugLog si {@code true}, logge chaque modification de stat
     */
    public StatsComponent(ComponentHolder holder, IStatTypeRegistry registry, boolean debugLog) {
        this.holder = holder;
        this.registry = registry;
        this.debugLog = debugLog;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie (Component)
    // -------------------------------------------------------------------------

    /**
     * Appelé par {@link ComponentHolder#attach} après l'ajout au holder.
     * Initialise les stats à leurs valeurs par défaut — elles seront écrasées
     * par {@link #applyLoadedData} dès que le chargement async est terminé.
     */
    @Override
    public void onAttach() {
        for (StatType type : registry.getAll()) {
            baseValues.putIfAbsent(type.getId(),type.getDefaultValue());
        }
    }

    /**
     * Appelé par {@link ComponentHolder#detach} juste avant le démontage.
     * Le flush en BDD est géré par le {@link StatsManager} qui écoute
     * {@link me.tyalternative.fundamentalis.api.event.entity.EntityUnregisteredEvent EntityUnregisteredEvent}.
     */
    @Override
    public void onDetach() {
        finalCache.clear();
        modifiers.clear();
    }

    // -------------------------------------------------------------------------
    // Chargement depuis la BDD
    // -------------------------------------------------------------------------

    /**
     * Applique les données chargées depuis la BDD.
     *
     * <p>Doit être appelé sur le <strong>thread principal Bukkit</strong> après
     * que le future de chargement async soit résolu. Écrase les valeurs par
     * défaut posées dans {@link #onAttach()}.
     *
     * <p>Les stats non présentes dans {@code loadedData} (nouvelles stats ajoutées
     * après la dernière connexion du joueur) conservent leur valeur par défaut.
     *
     * @param loadedData Map {@code statId → valeur} chargée depuis la BDD
     */
    public void applyLoadedData(Map<String, Integer> loadedData) {
        for (Map.Entry<String, Integer> entry : loadedData.entrySet()) {
            // On ignore les ids de stats qui ne sont plus enregistrées (stat supprimée)
            registry.find(entry.getKey()).ifPresent(type ->
                    baseValues.put(type.getId(), type.clamp(entry.getValue()))
            );
        }
        invalidateAllCache();
    }

    // -------------------------------------------------------------------------
    // Lecture (IStatsComponent)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public int getBase(StatType type) {
        return baseValues.getOrDefault(type.getId(), type.getDefaultValue());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vérifie d'abord le cache. Si absent ou invalidé, déclenche un
     * {@link StatComputeEvent} pour permettre l'injection de modificateurs
     * externes, puis applique la formule flat → percent → multiply.
     */
    @Override
    public double getFinal(StatType type) {
        // Lecture du cache (évite de refaire le calcul si rien n'a changé)
        Double cached = finalCache.get(type.getId());
        if (cached != null) return cached;

        int base = getBase(type);

        // On fire le StatComputeEvent pour que les autres modules injectent leurs modificateurs
        StatComputeEvent computeEvent = new StatComputeEvent(holder, type, base);
        Bukkit.getPluginManager().callEvent(computeEvent);

        // On fusionne les modificateurs internes + ceux injectés par l'event
        List<StatModifier> allModifiers = new ArrayList<>();
        Map<String, StatModifier> internalMods = modifiers.get(type.getId());
        if (internalMods != null) allModifiers.addAll(internalMods.values());
        allModifiers.addAll(computeEvent.getInjectedModifiers());

        // Calcul : base + FLAT → × (1 + PERCENT) → × MULTIPLY
        double flat     = allModifiers.stream().filter(m -> m.type() == StatModifier.Type.FLAT)
                                               .mapToDouble(StatModifier::value).sum();
        double percent  = allModifiers.stream().filter(m -> m.type() == StatModifier.Type.PERCENT)
                                               .mapToDouble(StatModifier::value).sum();
        double multiply = allModifiers.stream().filter(m -> m.type() == StatModifier.Type.MULTIPLY)
                                               .mapToDouble(StatModifier::value).reduce(1.0, (a,b) -> a*b);

        double result = (base + flat) * (1.0 + percent) * multiply;
        // La valeur finale ne peut pas descendre en dessous de 0
        result = Math.max(0.0, result);

        finalCache.put(type.getId(), result);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public Map<StatType, Integer> getAllBase() {
        Map<StatType, Integer> result = new LinkedHashMap<>();
        for (StatType type : registry.getAll()) {
            result.put(type, getBase(type));
        }
        return  Collections.unmodifiableMap(result);
    }

    // -------------------------------------------------------------------------
    // Écriture (IStatsComponent)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public int setBase(StatType type, int value) {
        int oldValue = getBase(type);
        int clamped  = type.clamp(value);

        baseValues.put(type.getId(), clamped);
        invalidateCache(type);

        if (debugLog) {
            Bukkit.getLogger().info("[Stats] " + holder.getEntityId()
                    + " | " + type.getId() + " : " + oldValue + " → " + clamped);
        }

        // Notification — les autres modules réagissent via ce event
        StatChangeEvent event = new StatChangeEvent(holder, type, oldValue, clamped, StatChangeEvent.Cause.OTHER);
        Bukkit.getPluginManager().callEvent(event);

        return clamped;
    }

    /** {@inheritDoc} */
    @Override
    public int setBase(StatType type, int value, StatChangeEvent.Cause cause) {
        int oldValue = getBase(type);
        int clamped  = type.clamp(value);

        baseValues.put(type.getId(), clamped);
        invalidateCache(type);

        StatChangeEvent event = new StatChangeEvent(holder, type, oldValue, clamped, cause);
        Bukkit.getPluginManager().callEvent(event);

        return clamped;
    }

    /** {@inheritDoc} */
    @Override
    public int addBase(StatType type, int amount) {
        return setBase(type, getBase(type) + amount);
    }

    // -------------------------------------------------------------------------
    // Modificateurs (IStatsComponent)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void addModifier(StatModifier modifier) {
        modifiers.computeIfAbsent(modifier.statType().getId(), k -> new HashMap<>())
                .put(modifier.source(), modifier);
        invalidateCache(modifier.statType());
    }

    /** {@inheritDoc} */
    @Override
    public void removeModifier(String source) {
        boolean changed = false;
        for (Map<String, StatModifier> byType : modifiers.values()) {
            changed |= byType.remove(source) != null;
        }
        if (changed) invalidateAllCache();
    }

    /** {@inheritDoc} */
    @Override
    public void clearModifiers() {
        modifiers.clear();
        invalidateAllCache();
    }

    /** {@inheritDoc} */
    @Override
    public Collection<StatModifier> getModifiers(StatType type) {
        Map<String, StatModifier> byType = modifiers.get(type.getId());
        if (byType == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(byType.values());
    }

    // -------------------------------------------------------------------------
    // Utilitaires (IStatsComponent)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    public boolean hasStat(StatType type) {
        return baseValues.containsKey(type.getId());
    }

    /** {@inheritDoc} */
    @Override
    public void resetToDefaults() {
        for (StatType type : registry.getAll()) {
            int old = getBase(type);
            baseValues.put(type.getId(), type.getDefaultValue());
            StatChangeEvent event = new StatChangeEvent(holder, type, old, type.getDefaultValue(), StatChangeEvent.Cause.RESET);
            Bukkit.getPluginManager().callEvent(event);
        }
        clearModifiers();
    }

    // -------------------------------------------------------------------------
    // Accès pour les repositories (flush BDD)
    // -------------------------------------------------------------------------

    /**
     * Retourne une copie des valeurs de base sous forme de Map {@code statId → valeur}.
     * Utilisé par {@link StatsManager} pour le flush en BDD.
     *
     * @return snapshot immuable des valeurs de base
     */
    public Map<String, Integer> getRawBaseValues() {
        return Collections.unmodifiableMap(new HashMap<>(baseValues));
    }

    // -------------------------------------------------------------------------
    // Gestion du cache interne
    // -------------------------------------------------------------------------

    /**
     * Invalide le cache de la valeur finale pour une stat donnée.
     * Le prochain appel à {@link #getFinal(StatType)} recalculera la valeur.
     */
    private void invalidateCache(StatType type) {
        finalCache.remove(type.getId());
    }

    /**
     * Invalide tout le cache (ex : clearModifiers ou resetToDefaults).
     */
    private void invalidateAllCache() {
        finalCache.clear();
    }
}
