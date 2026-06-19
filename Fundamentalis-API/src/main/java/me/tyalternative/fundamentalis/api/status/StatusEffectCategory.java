package me.tyalternative.fundamentalis.api.status;

/**
 * Famille de comportement d'un effet de statut.
 *
 * <p>Purement informative au niveau de l'API - c'est l'implémentation du
 * comportement (côté {@code fundamentalis-status}) qui détermine ce qui se
 * passe réellement à chaque tick. Cette catégorie sert surtout à l'affichage
 * (icônes, tri dans les commandes) et à des filtres rapides pour les modules
 * qui ne s'intéressent qu'à une famille (ex : un module de protection contre
 * les contrôles de foule n'a besoin de filtrer que sur {@link #CROWD_CONTROL}).
 */
public enum StatusEffectCategory {

    /**
     * Dégâts sur la durée (Poison, Brûlure, Saignement, Électrocution…).
     * Inflige des dégâts à intervalle régulier via le pipeline de Combat.
     */
    DAMAGE_OVER_TIME,

    /**
     * Contrôle de foule (Étourdissement, Ralentissement, Gel…).
     * Restreint tout ou partie des actions du joueur affecté.
     */
    CROWD_CONTROL,

    /**
     * Modificateur de statistique temporaire (Force, Faiblesse, Régénération…).
     * S'appuie sur {@link me.tyalternative.fundamentalis.api.stats.StatModifier StatModifier}.
     */
    STAT_MODIFIER,

    /**
     * Effet à comportement unique ne rentrant dans aucune autre catégorie
     * (Vampirisme, Invulnérabilité, Marqué…).
     */
    SPECIAL
}
