package me.tyalternative.fundamentalis.combat.damage;

/**
 * Catégorie d'attaque utilisée pour déterminer le comportement du pipeline
 * de dégâts (gestion du critique notamment).
 *
 * <p>Contrairement à {@link me.tyalternative.fundamentalis.api.combat.DamageType DamageType}
 * (élément du dégât : physique, feu, magie…), {@code AttackType} décrit la
 * <em>méthode</em> d'attaque (mêlée, distance, sort…). Reste interne à
 * {@code fundamentalis-combat} — les futurs modules n'ont besoin que de
 * {@code DamageType}, défini dans l'API.
 */
public enum AttackType {

    /** Attaque corps à corps (épée, hache, lance, poings…). */
    MELEE("Corps à corps", true),

    /** Attaque à distance (arc, projectile…). */
    RANGED("Distance", true),

    /** Sort ou attaque magique (bâton magique, futur système ANIMA). */
    MAGIC("Magique", true),

    /** Catégorie technique utilisée en interne pour forcer un critique. */
    CRITICAL("Critique", false),

    /** Attaque spéciale (capacités de classe, futurs effets de combo…). */
    SPECIAL("Spécial", true);

    private final String  displayName;
    private final boolean canCrit;

    AttackType(String displayName, boolean canCrit) {
        this.displayName = displayName;
        this.canCrit      = canCrit;
    }

    /** @return le nom affiché dans les messages au joueur */
    public String getDisplayName() { return displayName; }

    /** @return {@code true} si ce type d'attaque peut produire un coup critique */
    public boolean canCrit() { return canCrit; }
}
