package me.tyalternative.fundamentalis.combat.weapon;

import me.tyalternative.fundamentalis.combat.damage.AttackType;

/**
 * Famille d'arme - définit les valeurs par défaut (dégâts, vitesse, portée)
 * réutilisées par chaque {@link CustomWeapon} de cette famille, sauf si
 * l'arme spécifie ses propres valeurs.
 */
public enum WeaponType {

    SWORD("Épée", 6.0, 1.6, 3.0, AttackType.MELEE, "Arme équilibrée", true),
    AXE("Hache", 9.0, 0.9, 3.0, AttackType.MELEE, "Lente mais puissante, brise l'armure", true),
    SPEAR("Lance", 5.0, 1.4, 4.5, AttackType.MELEE, "Grande portée, applique du recul", false),
    FIST("Poings", 3.0, 2.0, 2.5, AttackType.MELEE, "Très rapide, permet des combos", false),
    BOW("Arc", 7.0, 1.0, 20.0, AttackType.RANGED, "Attaque à distance", false),
    MAGIC_STAFF("Bâton magique", 8.0, 1.2, 5.0, AttackType.MAGIC, "Canalise la magie", false);

    private final String      displayName;
    private final double      baseDamage;
    private final double      attackSpeed;
    private final double      reach;
    private final AttackType  attackType;
    private final String      specialAbility;
    private final boolean     canBlock;

    WeaponType(String displayName, double baseDamage, double attackSpeed, double reach,
               AttackType attackType, String specialAbility, boolean canBlock) {
        this.displayName    = displayName;
        this.baseDamage     = baseDamage;
        this.attackSpeed    = attackSpeed;
        this.reach          = reach;
        this.attackType     = attackType;
        this.specialAbility = specialAbility;
        this.canBlock       = canBlock;
    }

    /** @return le nom affiché dans le lore de l'item et les messages */
    public String getDisplayName() { return displayName; }

    /** @return les dégâts de base par défaut pour cette famille d'arme */
    public double getBaseDamage() { return baseDamage; }

    /** @return la vitesse d'attaque par défaut (attribut Minecraft ATTACK_SPEED) */
    public double getAttackSpeed() { return attackSpeed; }

    /** @return la portée par défaut, en blocs */
    public double getReach() { return reach; }

    /** @return le type d'attaque associé (mêlée, distance, magie) */
    public AttackType getAttackType() { return attackType; }

    /** @return une description courte de la spécificité de la famille, affichée dans le lore */
    public String getSpecialAbility() { return specialAbility; }

    /** @return {@code true} si cette famille d'arme peut bloquer (futur usage, non câblé) */
    public boolean canBlock() { return canBlock; }
}
