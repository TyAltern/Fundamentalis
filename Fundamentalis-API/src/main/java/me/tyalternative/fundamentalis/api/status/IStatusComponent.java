package me.tyalternative.fundamentalis.api.status;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * <h2>Contrat public du composant d'effets de statut.</h2>
 * <p>
 * Tout ce que les autres modules peuvent faire avec les effets de statut
 * d'une entité passe par cette interface. Ils n'ont jamais accès à
 * l'implémentation concrète ({@code StatusComponent} dans {@code fundamentalis-status}).
 *
 * <h3>Application et file de priorité par niveau</h3>
 * <p>
 * {@link #applyEffect} ajoute un nouveau palier pour un {@link StatusEffectType}
 * donné. Si un palier de niveau strictement supérieur est déjà actif pour ce
 * type, le nouveau palier est enregistré mais reste en sommeil tant que le
 * palier supérieur n'a pas expiré - voir {@link ActiveStatusEffect} pour le
 * détail exact de cette mécanique.
 *
 * <h3>Résistances</h3>
 * <p>
 * Une entité peut avoir une résistance par {@link DamageType}, exprimée en
 * pourcentage [0.0, 1.0]. Cette résistance est consultée par
 * {@code fundamentalis-combat} via {@link me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent PreDamageEvent}
 * - {@code fundamentalis-status} écoute cet événement et applique lui-même la
 * réduction, {@code IStatusComponent} ne fait qu'exposer la donnée brute.
 *
 * <h3>Thread safety</h3>
 * L'implémentation garantit que les lectures sont thread-safe.
 * Les écritures ({@code applyEffect}, {@code removeEffect}, {@code setResistance})
 * doivent être faites sur le thread principal Bukkit.
 *
 * @see StatusEffectType
 * @see ActiveStatusEffect
 */
public interface IStatusComponent extends Component {

    // =========================================================
    // Application et retrait
    // =========================================================

    /**
     * Applique un nouveau palier d'effet sur cette entité.
     *
     * <p>Si {@code level} est strictement inférieur au niveau du palier déjà
     * actif pour ce {@link StatusEffectType}, le nouveau palier est créé mais
     * reste en sommeil jusqu'à ce que le palier supérieur expire. Si
     * {@code level} est supérieur ou égal, ce nouveau palier devient
     * immédiatement actif.
     *
     * @param type           le type d'effet à appliquer
     * @param level          le niveau du palier (sera clampé dans [1, {@link StatusEffectType#getMaxLevel()}])
     * @param durationTicks  durée du palier en ticks ; utiliser
     *                       {@link StatusEffectType#getDefaultDurationTicks()} si non spécifié explicitement
     * @param sourceId       identifiant de la source (UUID joueur, {@code "spell:fireball"}…), nullable
     * @return l'instance {@link ActiveStatusEffect} créée pour ce palier
     */
    ActiveStatusEffect applyEffect(StatusEffectType type, int level, long durationTicks, String sourceId);

    /**
     * Retire immédiatement <strong>tous</strong> les paliers (actifs et en
     * sommeil) d'un {@link StatusEffectType} donné.
     *
     * @param type le type d'effet à retirer entièrement
     * @return {@code true} si au moins un palier a été retiré
     */
    boolean removeEffect(StatusEffectType type);

    /**
     * Retire une instance précise de palier (identifiée par son
     * {@link ActiveStatusEffect#id()}), sans toucher aux autres paliers du
     * même type.
     *
     * @param effectInstanceId l'id de l'instance à retirer
     * @return {@code true} si l'instance a été trouvée et retirée
     */
    boolean removeEffectInstance(java.util.UUID effectInstanceId);

    /**
     * Retire tous les effets de statut actifs et en sommeil, toutes
     * catégories confondues.
     *
     * <p>À utiliser à la mort de l'entité ou pour un effet de "purge" complet.
     */
    void clearAllEffects();

    // =========================================================
    // Lecture
    // =========================================================

    /**
     * Indique si un palier de ce {@link StatusEffectType} est actuellement
     * actif (et non simplement en sommeil) sur cette entité.
     *
     * @param type le type d'effet à vérifier
     * @return {@code true} si un palier actif existe pour ce type
     */
    boolean hasActiveEffect(StatusEffectType type);

    /**
     * Retourne le palier actuellement actif pour ce {@link StatusEffectType},
     * s'il en existe un.
     *
     * @param type le type d'effet recherché
     * @return le palier actif, ou {@link Optional#empty()} si aucun palier
     *         de ce type n'est actif (qu'il soit absent ou en sommeil)
     */
    Optional<ActiveStatusEffect> getActiveEffect(StatusEffectType type);

    /**
     * Retourne tous les paliers (actifs et en sommeil) de tous types présents
     * sur cette entité.
     *
     * @return collection immuable de tous les paliers connus
     */
    Collection<ActiveStatusEffect> getAllEffects();

    /**
     * Retourne uniquement les paliers actuellement actifs (visibles), un par
     * {@link StatusEffectType} au maximum.
     *
     * @return collection immuable des paliers actifs
     */
    Collection<ActiveStatusEffect> getActiveEffects();

    // =========================================================
    // Résistances
    // =========================================================

    /**
     * Définit la résistance de cette entité à un {@link DamageType}.
     *
     * @param type       le type de dégât concerné
     * @param resistance ratio de réduction, clampé dans [0.0, 1.0]
     *                   ({@code 1.0} = immunité totale)
     */
    void setResistance(DamageType type, double resistance);

    /**
     * Retourne la résistance actuelle de cette entité à un {@link DamageType}.
     *
     * @param type le type de dégât concerné
     * @return le ratio de résistance, {@code 0.0} si aucune résistance définie
     */
    double getResistance(DamageType type);
}
