package me.tyalternative.fundamentalis.api.combat;


/**
 * Catégorie de dégâts, utilisée pour les résistances et immunités.
 *
 * <p>Déclarée dans l'API (et non dans {@code fundamentalis-combat}) car de
 * futurs modules comme {@code fundamentalis-status} (résistances par type) ou
 * {@code fundamentalis-spells} (sorts élémentaires) ont besoin de la référencer
 * sans dépendre du module Combat lui-même.
 *
 * @see me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent
 */
public enum DamageType {
}
