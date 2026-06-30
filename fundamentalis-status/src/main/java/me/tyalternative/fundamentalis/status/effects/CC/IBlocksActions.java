package me.tyalternative.fundamentalis.status.effects.CC;

import java.util.Set;

/**
 * Implémentée par les {@link me.tyalternative.fundamentalis.status.StatusEffect StatusEffect}
 * de contrôle de foule qui bloquent certaines actions joueur tant qu'ils sont actifs.
 *
 * <p>{@code CrowdControlListener} consulte cette interface (via
 * {@code instanceof}) sur l'instance {@link me.tyalternative.fundamentalis.status.StatusEffect StatusEffect}
 * du palier actif d'une entité pour décider d'annuler ou non un event Bukkit
 * (mouvement, attaque, interaction, saut).
 *
 * <p>Le niveau de blocage est défini <strong>effet par effet</strong> via
 * {@link #getBlockedActions()}, comme convenu : un Stun bloque tout, un Slow
 * ne bloque rien (et ne devrait donc pas implémenter cette interface du tout).
 */
public interface IBlocksActions {

    /** Catégorie d'action joueur potentiellement bloquée par un effet de CC. */
    enum ActionType {
        /** Bloque tout déplacement (le joueur est figé sur place). */
        MOVEMENT,
        /** Bloque les attaques (mêlée et distance). */
        ATTACK,
        /** Bloque l'interaction avec le monde (clic droit, ouverture de coffres…). */
        INTERACT,
        /** Bloque le saut. */
        JUMP
    }

    /**
     * @return l'ensemble des actions bloquées tant que ce palier reste actif
     */
    Set<ActionType> getBlockedActions();
}
