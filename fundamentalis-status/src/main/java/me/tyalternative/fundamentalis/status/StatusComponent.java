package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.Component;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.event.status.StatusEffectAppliedEvent;
import me.tyalternative.fundamentalis.api.event.status.StatusEffectExpiredEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
import me.tyalternative.fundamentalis.status.engine.EffectTierEngine;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation de {@link IStatusComponent}.
 *
 * <p>Délègue toute la logique de sélection du palier actif à
 * {@link EffectTierEngine} (pur, sans dépendance Bukkit) et ajoute par-dessus :
 * <ul>
 *   <li>La création et le maintien d'une instance {@link StatusEffect} vivante
 *       par palier - créée via {@link StatusEffectFactoryRegistry} dès
 *       l'application, conservée tant que le palier existe (actif ou en
 *       sommeil), pour porter un état propre comme dans l'ancienne version.</li>
 *   <li>Le déclenchement des événements {@link StatusEffectAppliedEvent} /
 *       {@link StatusEffectExpiredEvent}.</li>
 *   <li>L'appel à {@code onApply}/{@code onTick}/{@code onRemove} sur l'instance
 *       {@link StatusEffect} correspondante, à chaque transition active/sommeil.</li>
 *   <li>Le stockage des résistances par {@link DamageType}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Les résistances sont stockées dans une {@link ConcurrentHashMap} pour une
 * lecture thread-safe. Le moteur de paliers, ses instances et leurs
 * transitions sont réservés au thread principal Bukkit, comme {@code StatsComponent}.
 *
 * <h2>Cycle de vie</h2>
 * Aucune persistance - à {@link #onDetach()}, toutes les instances actives
 * sont proprement désactivées ({@code onRemove}) puis abandonnées (pas de
 * flush BDD), conformément au choix de ne pas persister les effets de statut.
 */
public class StatusComponent implements IStatusComponent, Component {

    // -------------------------------------------------------------------------
    // Clé du composant
    // -------------------------------------------------------------------------

    public static final ComponentKey<IStatusComponent> KEY =
            ComponentKey.of("fundamentalis:status", IStatusComponent.class);

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final ComponentHolder holder;
    private final EffectTierEngine engine = new EffectTierEngine();
    private final StatusEffectFactoryRegistry factoryRegistry;

    /**
     * Instances vivantes de {@link StatusEffect}, indexées par l'id du palier
     * ({@link ActiveStatusEffect#id()}) auquel elles correspondent. Une entrée
     * existe pour tout palier connu du moteur, qu'il soit actif ou en sommeil
     * - seule l'instance du palier actif reçoit onTick.
     */
    private final Map<UUID, StatusEffect> liveEffects = new HashMap<>();

    /** Résistances par DamageType, en RAM uniquement. Thread-safe en lecture. */
    private final ConcurrentHashMap<DamageType, Double> resistances = new ConcurrentHashMap<>();

    /**
     * Composant de stats de la même entité, transmis à chaque {@link StatusEffect}
     * créé. Peut être {@code null} si l'entité n'a pas (encore) de stats.
     */
    private final IStatsComponent statsComponent;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param holder          le ComponentHolder auquel ce composant est attaché
     * @param factoryRegistry registre des fabriques d'effets par {@link StatusEffectType}
     * @param statsComponent  le composant de stats de la même entité, ou {@code null} si absent
     */
    public StatusComponent(ComponentHolder holder, StatusEffectFactoryRegistry factoryRegistry, IStatsComponent statsComponent) {
        this.holder = holder;
        this.factoryRegistry = factoryRegistry;
        this.statsComponent = statsComponent;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie (Component)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void onAttach() {
        // Rien à initialiser - tous les effets démarrent vides.
    }

    /** {@inheritDoc} */
    @Override
    public void onDetach() {
        // Pas de persistance : on libère juste l'état en mémoire, mais on
        // appelle quand même onRemove() sur les instances actives pour que
        // les effets ayant posé un état externe (StatModifier, attribut Bukkit…)
        // le retirent proprement avant le démontage du composant.
        for (ActiveStatusEffect active : engine.getAllActive()) {
            StatusEffect liveEffect = liveEffects.get(active.id());
            if (liveEffect != null) liveEffect.onRemove();
        }
        liveEffects.clear();
        engine.clear();
    }


    // -------------------------------------------------------------------------
    // Application et retrait (IStatusComponent)
    // -------------------------------------------------------------------------
    /** {@inheritDoc} */
    @Override
    public ActiveStatusEffect applyEffect(StatusEffectType type, int level, long durationTicks, String sourceId) {
        int clampedLevel = type.clampLevel(level);
        long currentTick = Bukkit.getCurrentTick();
        long expiresAt   = currentTick + Math.max(1, durationTicks);

        ActiveStatusEffect newTier = new ActiveStatusEffect(
                UUID.randomUUID(), holder.getEntity().getUniqueId(), type, clampedLevel, expiresAt, sourceId, false);

        // Création de l'instance vivante AVANT le recalcul de palier actif,
        // pour qu'elle soit disponible si ce palier devient immédiatement actif.
        StatusEffectFactory factory = factoryRegistry.get(type);
        if (factory != null) {
            liveEffects.put(newTier.id(), factory.create(holder, statsComponent, newTier));
        }

        EffectTierEngine.TierChangeResult result = engine.addTier(newTier);

        // L'instance réellement stockée (avec son état actif correct) doit être relue
        ActiveStatusEffect storedTier = engine.getAllTiers(type).stream()
                .filter(t -> t.id().equals(newTier.id())).findFirst().orElse(newTier);

        boolean becomeActive = storedTier.active();

        Bukkit.getPluginManager().callEvent(
                new StatusEffectAppliedEvent(holder, storedTier, becomeActive));

        if (result.changed()) handleTierChange(result, false);

        return storedTier;
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeEffect(StatusEffectType type) {
        List<ActiveStatusEffect> tiers = engine.getAllTiers(type);
        if (tiers.isEmpty()) return false;

        ActiveStatusEffect previousActive = engine.getActive(type).orElse(null);
        StatusEffect previousLiveEffect = previousActive != null ? liveEffects.get(previousActive.id()) : null;

        boolean removed = engine.removeAllTiers(type);

        // On nettoie toutes les instances vivantes de ce type, actives ou en sommeil
        for (ActiveStatusEffect tier : tiers) {
            liveEffects.remove(tier.id());
        }

        if (previousActive != null) {
            if (previousLiveEffect != null) previousLiveEffect.onRemove();

            Bukkit.getPluginManager().callEvent(
                    new StatusEffectExpiredEvent(holder, previousActive, null, true));
        }

        return removed;
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeEffectInstance(UUID effectInstanceId) {
        StatusEffectType type = engine.getAll().stream()
                .filter(t -> t.id().equals(effectInstanceId))
                .map(ActiveStatusEffect::type)
                .findFirst().orElse(null);
        if (type == null) return false;

        long currentTick = Bukkit.getCurrentTick();

        boolean wasActive = engine.getActive(type)
                .map(active -> active.id().equals(effectInstanceId))
                .orElse(false);

        EffectTierEngine.TierChangeResult result = engine.removeTier(type, effectInstanceId, currentTick);
        if (result == null) return false;

        StatusEffect removedLive = liveEffects.remove(effectInstanceId);
        if (wasActive && removedLive != null) {
            removedLive.onRemove();
        }

        if (result.changed()) {
            handleTierChange(result, true);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void clearAllEffects() {
        for (ActiveStatusEffect active : engine.getAllActive()) {
            StatusEffect liveEffect = liveEffects.get(active.id());
            if (liveEffect != null) liveEffect.onRemove();

            Bukkit.getPluginManager().callEvent(
                    new StatusEffectExpiredEvent(holder, active, null, true));
        }
        liveEffects.clear();
        engine.clear();
    }



    // -------------------------------------------------------------------------
    // Lecture (IStatusComponent)
    // -------------------------------------------------------------------------

    @Override
    public boolean hasActiveEffect(StatusEffectType type) {
        return engine.getActive(type).isPresent();
    }

    @Override
    public Optional<ActiveStatusEffect> getActiveEffect(StatusEffectType type) {
        return engine.getActive(type);
    }

    @Override
    public Collection<ActiveStatusEffect> getAllEffects() {
        return Collections.unmodifiableCollection(engine.getAll());
    }

    @Override
    public Collection<ActiveStatusEffect> getActiveEffects() {
        return Collections.unmodifiableCollection(engine.getAllActive());
    }


    // -------------------------------------------------------------------------
    // Accès à l'instance vivante (réservé aux listeners de fundamentalis-status)
    // -------------------------------------------------------------------------

    /**
     * Retourne l'instance {@link StatusEffect} vivante du palier actuellement
     * actif pour ce type, s'il en existe une.
     *
     * <p>Utilisé par {@code CrowdControlListener} pour consulter
     * {@link me.tyalternative.fundamentalis.status.effects.IBlocksActions IBlocksActions}
     * sur le palier actif sans dupliquer la logique de résolution de palier.
     * Cette méthode n'est pas exposée par {@link IStatusComponent} - elle
     * retourne une classe interne à {@code fundamentalis-status}, jamais
     * accessible depuis un autre module.
     *
     * @param type le type d'effet recherché
     * @return l'instance vivante du palier actif, ou {@code null} si aucun palier
     *         actif n'existe pour ce type ou si ce type n'a pas de fabrique associée
     */
    public StatusEffect getLiveEffect(StatusEffectType type) {
        return engine.getActive(type)
                .map(active -> liveEffects.get(active.id()))
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Résistances (IStatusComponent)
    // -------------------------------------------------------------------------

    @Override
    public void setResistance(DamageType type, double resistance) {
        resistances.put(type, Math.max(0.0, Math.min(1.0, resistance)));
    }

    @Override
    public double getResistance(DamageType type) {
        return resistances.getOrDefault(type, 0.0);
    }


    // -------------------------------------------------------------------------
    // Tick - appelé par le ticker central de fundamentalis-status
    // -------------------------------------------------------------------------

    /**
     * Fait avancer ce composant d'un tick : appelle {@link StatusEffect#onTick()}
     * sur l'instance vivante de chaque palier actif, puis purge les paliers
     * expirés et gère les transitions qui en résultent.
     *
     * <p>Appelé par le ticker central - jamais directement par un autre module.
     *
     * @param currentTick le tick serveur courant
     */
    public void tick(long currentTick) {
        // 1. Tick des instances vivantes actives (DoT, etc.)
        for (ActiveStatusEffect active : engine.getAllActive()) {
            StatusEffect liveEffect = liveEffects.get(active.id());
            if (liveEffect != null) {
                liveEffect.updateMeta(active);
                liveEffect.onTick();
            }
        }

        // 2. Purge des paliers expirés + transitions résultantes.
        // On capture les ids existants avant le tick du moteur pour pouvoir
        // déterminer, après coup, lesquels ont été purgés et nettoyer leurs
        // instances vivantes correspondantes sans scan global.
        Set<UUID> idsBeforeTick = new HashSet<>();
        for (ActiveStatusEffect t : engine.getAll()) idsBeforeTick.add(t.id());

        List<EffectTierEngine.TierChangeResult> changes = engine.tick(currentTick);

        Set<UUID> idsAfterTick = new HashSet<>();
        for (ActiveStatusEffect t : engine.getAll()) idsAfterTick.add(t.id());

        idsBeforeTick.removeAll(idsAfterTick); // ne reste que les ids purgés à ce tick
        idsBeforeTick.forEach(liveEffects::remove);

        for (EffectTierEngine.TierChangeResult change : changes) {
            if (change.changed()) {
                handleTierChange(change, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Gestion centralisée d'un changement de palier actif
    // -------------------------------------------------------------------------

    /**
     * Réagit à un changement de palier actif détecté par le moteur :
     * appelle {@code onRemove()} sur l'instance de l'ancien palier actif (s'il
     * y en avait un), {@code onApply()} sur celle du nouveau (s'il y en a un),
     * et fire {@link StatusEffectExpiredEvent} si l'ancien palier a disparu.
     */
    private void handleTierChange(EffectTierEngine.TierChangeResult change, boolean manualRemoval) {
        ActiveStatusEffect previous = change.previousActive();
        ActiveStatusEffect current = change.newActive();

        if (previous != null) {
            StatusEffect liveEffect = liveEffects.get(previous.id());
            if (liveEffect != null) liveEffect.onRemove();

            Bukkit.getPluginManager().callEvent(
                    new StatusEffectExpiredEvent(holder, previous, current, manualRemoval));
        }

        if (current != null) {
            StatusEffect liveEffect = liveEffects.get(current.id());
            if (liveEffect != null) {
                liveEffect.updateMeta(current);
                liveEffect.onApply();
            }
        }
    }
}
