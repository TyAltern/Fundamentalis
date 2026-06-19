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
}
