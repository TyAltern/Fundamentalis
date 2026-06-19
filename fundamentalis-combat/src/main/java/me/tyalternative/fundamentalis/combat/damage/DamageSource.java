package me.tyalternative.fundamentalis.combat.damage;

/**
 * Origine technique d'un dégât, utilisée par le pipeline pour décider quelles
 * étapes appliquer (ex : la défense de la victime ne s'applique qu'aux dégâts
 * d'arme, pas aux DoT).
 *
 * <p>Les valeurs {@code STATUS_*} sont prévues pour {@code fundamentalis-status}
 * (effets de poison, brûlure…) mais restent déclarées ici plutôt que dans
 * l'API : c'est le module Combat qui orchestre le pipeline et décide comment
 * traiter chaque source, le futur module status n'a besoin que de créer un
 * {@link DamageInfo} avec la bonne source via {@code DamageManager.createStatusDamage}.
 */
public enum DamageSource {

        /** Dégât d'arme corps à corps. */
    WEAPON,

    /** Effet de statut : poison (réservé à fundamentalis-status). */
    STATUS_POISON,

    /** Effet de statut : brûlure (réservé à fundamentalis-status). */
    STATUS_BURN,

    /** Effet de statut : saignement (réservé à fundamentalis-status). */
    STATUS_BLEED,

    /** Effet de statut : électrocution (réservé à fundamentalis-status). */
    STATUS_ELECTROCUTION,

    /** Effet de statut : gel (réservé à fundamentalis-status). */
    STATUS_FREEZE,

    /** Sort actif (réservé à fundamentalis-spells). */
    SPELL_ACTIVE,

    /** Sort passif (réservé à fundamentalis-spells). */
    SPELL_PASSIVE,

    /** Projectile (flèche, futur projectile magique…). */
    PROJECTILE,

    /** Dégâts environnementaux (chute, feu naturel, noyade…). */
    ENVIRONMENT,

    /** Source non catégorisée. */
    UNKNOWN;

/** @return {@code true} si cette source est un effet de statut (préfixe {@code STATUS_}) */
public boolean isFromStatus() {
    return name().startsWith("STATUS_");
}

/** @return {@code true} si cette source est un sort (préfixe {@code SPELL_}) */
public boolean isFromSpell() {
    return name().startsWith("SPELL_");
}

/** @return {@code true} si cette source provient d'une arme (mêlée ou projectile) */
public boolean isFromWeapon() {
    return this == WEAPON || this == PROJECTILE;
}
}
