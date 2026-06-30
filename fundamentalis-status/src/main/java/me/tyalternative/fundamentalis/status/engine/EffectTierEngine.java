package me.tyalternative.fundamentalis.status.engine;

import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;

import java.util.*;

/**
 * Moteur pur de sélection du palier actif parmi plusieurs paliers d'un même
 * {@link StatusEffectType} appliqués à une entité.
 *
 * <p>Implémente la règle validée : chaque palier porte un timestamp
 * d'expiration <strong>absolu</strong>, fixé une fois pour toutes à son
 * application et jamais mis en pause. Le palier <em>actif</em> (visible) à un
 * instant donné est, parmi tous les paliers non expirés d'un même type, celui
 * du niveau le plus élevé. Les autres continuent de décompter en arrière-plan.
 *
 * <p>Classe volontairement sans dépendance à Bukkit ni à {@code ComponentHolder} -
 * elle ne manipule que des {@link ActiveStatusEffect}, ce qui la rend
 * testable unitairement de façon triviale et réutilisable pour n'importe
 * quelle entité sans setup Bukkit.
 *
 * <h2>Utilisation typique</h2>
 * <p>{@link me.tyalternative.fundamentalis.status.StatusComponent StatusComponent}
 * détient une instance d'{@code EffectTierEngine} par entité et lui délègue
 * tout le travail de maintien de la cohérence active/sommeil :
 * <pre>{@code
 * EffectTierEngine engine = new EffectTierEngine();
 * TierChangeResult result = engine.addTier(newEffect);
 * // result.newlyActive()  → palier qui vient de prendre le dessus, si différent de l'ancien
 * // result.newlyDormant() → ancien palier actif désormais relégué en sommeil, si applicable
 * }</pre>
 */
public class EffectTierEngine {

    // -------------------------------------------------------------------------
    // État interne - tous les paliers connus, actifs et en sommeil, groupés par type
    // -------------------------------------------------------------------------

    /** Map type d'effet → liste de tous les paliers connus pour ce type (tous niveaux confondus). */
    private final Map<StatusEffectType, List<ActiveStatusEffect>> tiersByType = new HashMap<>();

    // -------------------------------------------------------------------------
    // Ajout d'un nouveau palier
    // -------------------------------------------------------------------------

    /**
     * Enregistre un nouveau palier et recalcule lequel est actif pour son
     * {@link StatusEffectType}.
     *
     * @param newTier le palier à ajouter - son champ {@code active} est ignoré
     *                en entrée, il est recalculé par cette méthode
     * @return le résultat du recalcul : quel palier est désormais actif, et
     *         lequel (le cas échéant) vient d'être relégué en sommeil
     */
    public TierChangeResult addTier(ActiveStatusEffect newTier) {
        List<ActiveStatusEffect> tiers = tiersByType.computeIfAbsent(newTier.type(), k -> new ArrayList<>());

        // Le nouveau palier est ajouté avec active=false par défaut ;
        // recomputeActive() déterminera s'il doit être promu actif.
        tiers.add(newTier.asActive(false));
        return recomputeActive(newTier.type(), 0);
    }

    // -------------------------------------------------------------------------
    // Retrait
    // -------------------------------------------------------------------------

    /**
     * Retire un palier précis par son id et recalcule le palier actif pour
     * son type.
     *
     * @param type             le type d'effet concerné
     * @param effectInstanceId l'id du palier à retirer
     * @param currentTick      le tick serveur courant, pour le recalcul
     * @return le résultat du recalcul, ou {@code null} si aucun palier
     *         portant cet id n'a été trouvé
     */
    public TierChangeResult removeTier(StatusEffectType type, UUID effectInstanceId, long currentTick) {
        List<ActiveStatusEffect> tiers = tiersByType.get(type);
        if (tiers == null) return null;

        boolean removed = tiers.removeIf(t -> t.id().equals(effectInstanceId));
        if (!removed) return null;

        if (tiers.isEmpty()) tiersByType.remove(type);
        return recomputeActive(type, currentTick);
    }

    /**
     * Retire <strong>tous</strong> les paliers d'un type donné, sans
     * recalcul nécessaire puisqu'il n'en reste aucun.
     *
     * @param type le type d'effet à effacer entièrement
     * @return {@code true} si au moins un palier a été retiré
     */
    public boolean removeAllTiers(StatusEffectType type) {
        return tiersByType.remove(type) != null;
    }

    /** Retire tous les paliers, tous types confondus. */
    public void clear() {
        tiersByType.clear();
    }

    // -------------------------------------------------------------------------
    // Tick - purge des paliers expirés et recalcul
    // -------------------------------------------------------------------------

    /**
     * Purge les paliers expirés au tick donné et recalcule l'actif pour
     * chaque type affecté.
     *
     * <p>À appeler à chaque tick (ou à intervalle régulier) par le ticker de
     * {@code fundamentalis-status} pour détecter les expirations naturelles.
     *
     * @param currentTick le tick serveur courant
     * @return la liste des changements de palier actif survenus à ce tick
     *         (un par {@link StatusEffectType} affecté), liste vide si rien n'a changé
     */
    public List<TierChangeResult> tick(long currentTick) {
        List<TierChangeResult> changes = new ArrayList<>();

        for (StatusEffectType type : new ArrayList<>(tiersByType.keySet())) {
            List<ActiveStatusEffect> tiers = tiersByType.get(type);
            boolean hasExpired = tiers.stream().anyMatch(t -> t.isExpired(currentTick));
            if(!hasExpired) continue;

            ActiveStatusEffect previousActiveBeforePurge =
                    tiers.stream().filter(ActiveStatusEffect::active).findFirst().orElse(null);

            tiers.removeIf(t -> t.isExpired(currentTick));
            if (tiers.isEmpty()) tiersByType.remove(type);

            changes.add(recomputeActive(type, currentTick, previousActiveBeforePurge));
        }

        return changes;
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /**
     * @param type le type d'effet recherché
     * @return le palier actuellement actif pour ce type, ou {@code empty()} si aucun
     */
    public Optional<ActiveStatusEffect> getActive(StatusEffectType type) {
        List<ActiveStatusEffect> tiers = tiersByType.get(type);
        if (tiers == null) return Optional.empty();
        return tiers.stream().filter(ActiveStatusEffect::active).findFirst();
    }

    /**
     * @param type le type d'effet recherché
     * @return tous les paliers connus (actifs et en sommeil) pour ce type
     */
    public List<ActiveStatusEffect> getAllTiers(StatusEffectType type) {
        return new ArrayList<>(tiersByType.getOrDefault(type, List.of()));
    }

    /** @return tous les paliers actifs, un par type au maximum */
    public List<ActiveStatusEffect> getAllActive() {
        List<ActiveStatusEffect> result = new ArrayList<>();
        for (List<ActiveStatusEffect> tiers : tiersByType.values()) {
            tiers.stream().filter(ActiveStatusEffect::active).findFirst().ifPresent(result::add);
        }
        return result;
    }

    /** @return tous les paliers connus, actifs et en sommeil, tous types confondus */
    public List<ActiveStatusEffect> getAll() {
        List<ActiveStatusEffect> result = new ArrayList<>();
        tiersByType.values().forEach(result::addAll);
        return result;
    }

    // -------------------------------------------------------------------------
    // Cœur du moteur - recalcul du palier actif pour un type
    // -------------------------------------------------------------------------

    /**
     * Détermine, parmi tous les paliers non expirés d'un type, celui de
     * niveau le plus élevé, le marque actif, et marque tous les autres en
     * sommeil. En cas d'égalité de niveau entre plusieurs paliers, celui dont
     * l'expiration est la plus tardive est choisi (le plus "résistant").
     *
     * <p>Calcule {@code previousActive} en lisant la liste courante — à
     * utiliser uniquement quand la liste n'a <strong>pas encore</strong> été
     * mutée (cas {@link #addTier} / {@link #removeTier}, qui appellent ce
     * recalcul avant toute purge). Pour le cas {@link #tick}, où la liste a
     * déjà été amputée des paliers expirés avant l'appel, utiliser la
     * surcharge {@link #recomputeActive(StatusEffectType, long, ActiveStatusEffect)}
     * qui accepte un {@code previousActive} pré-capturé.
     */
    private TierChangeResult recomputeActive(StatusEffectType type, long currentTick) {
        List<ActiveStatusEffect> tiers = tiersByType.get(type);
        ActiveStatusEffect previousActive = tiers == null ? null :
                tiers.stream().filter(ActiveStatusEffect::active).findFirst().orElse(null);
        return recomputeActive(type, currentTick, previousActive);
    }

    /**
     * Détermine, parmi tous les paliers non expirés d'un type, celui de
     * niveau le plus élevé, le marque actif, et marque tous les autres en
     * sommeil. En cas d'égalité de niveau entre plusieurs paliers, celui dont
     * l'expiration est la plus tardive est choisi (le plus "résistant").
     */
    private TierChangeResult recomputeActive(StatusEffectType type, long currentTick, ActiveStatusEffect previousActive) {
        List<ActiveStatusEffect> tiers = tiersByType.get(type);

        ActiveStatusEffect newActive = null;

        if (tiers != null) {
            newActive = tiers.stream()
                    .filter(t -> !t.isExpired(currentTick))
                    .max(Comparator.comparingInt(ActiveStatusEffect::level)
                            .thenComparingLong(ActiveStatusEffect::expiresAtTick))
                    .orElse(null);

            // On reconstruit la liste avec le bon état actif/sommeil sur chaque entrée
            for (int i = 0; i < tiers.size(); i++) {
                ActiveStatusEffect t = tiers.get(i);
                boolean shouldBeActive = newActive != null && t.id().equals(newActive.id());
                if (t.active() != shouldBeActive) {
                    tiers.set(i, t.asActive(shouldBeActive));
                }
            }

            // On relit newActive après mise à jour pour renvoyer l'instance à jour (active=true)
            if (newActive != null) {
                ActiveStatusEffect finalNewActive = newActive;
                newActive = tiers.stream().filter(t -> t.id().equals(finalNewActive.id())).findFirst().orElse(null);
            }
        }

        boolean changed = !Objects.equals(
                previousActive != null ? previousActive.id() : null,
                newActive != null ? newActive.id() : null
        );

        return new TierChangeResult(type, previousActive, newActive, changed);
    }


    // -------------------------------------------------------------------------
    // Résultat de recalcul
    // -------------------------------------------------------------------------

    /**
     * Résultat d'un recalcul de palier actif pour un {@link StatusEffectType}.
     *
     * @param type            le type d'effet concerné
     * @param previousActive  le palier qui était actif avant ce recalcul, {@code null} si aucun
     * @param newActive       le palier actif après ce recalcul, {@code null} si aucun palier
     *                        non expiré ne subsiste pour ce type
     * @param changed         {@code true} si l'identité du palier actif a changé
     *                        (nouveau palier, expiration, ou plus aucun palier actif)
     */
    public record TierChangeResult(
            StatusEffectType   type,
            ActiveStatusEffect previousActive,
            ActiveStatusEffect newActive,
            boolean            changed
    ) {}
}
