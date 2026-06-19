package me.tyalternative.fundamentalis.combat.damage;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.combat.weapon.CustomWeapon;
import org.bukkit.entity.LivingEntity;

/**
 * Contexte complet d'un dégât en cours de traitement par
 * {@link DamageManager}.
 *
 * <p>Construit via {@link Builder} et passé à {@link DamageManager#dealDamage(DamageInfo)},
 * qui le fait transiter à travers tout le pipeline en mutant ses champs de
 * résultat (finalDamage, wasCrit, wasKill…) au fur et à mesure des étapes.
 *
 * <p>Les setters de résultat sont package-private : seul {@link DamageManager}
 * peut les modifier. Le code appelant (listeners, autres modules) ne lit
 * que les résultats une fois {@code dealDamage} terminé.
 */
public class DamageInfo {

    // -------------------------------------------------------------------------
    // Entités impliquées
    // -------------------------------------------------------------------------

    private final LivingEntity attacker;
    private final LivingEntity victim;

    // -------------------------------------------------------------------------
    // Informations de base (immuables après construction)
    // -------------------------------------------------------------------------

    private final double       baseDamage;
    private final DamageSource source;
    private final DamageType   type;
    private final boolean      canCrit;
    private final CustomWeapon weapon;
    private final AttackType   attackType;

    private boolean forcedCrit;
    private boolean canKnockback;

    // -------------------------------------------------------------------------
    // Résultats — mutés uniquement par DamageManager pendant le pipeline
    // -------------------------------------------------------------------------

    private double  finalDamage;
    private boolean wasCrit;
    private boolean wasBlocked;
    private boolean wasImmune;
    private boolean wasKill;
    private boolean wasCharged;

    // -------------------------------------------------------------------------
    // Constructeur (via Builder uniquement)
    // -------------------------------------------------------------------------

    private DamageInfo(Builder builder) {
        this.attacker     = builder.attacker;
        this.victim       = builder.victim;
        this.baseDamage   = builder.baseDamage;
        this.source       = builder.source;
        this.type         = builder.type;
        this.canCrit      = builder.canCrit;
        this.forcedCrit   = builder.forcedCrit;
        this.canKnockback = builder.canKnockback;
        this.weapon       = builder.weapon;
        this.attackType   = builder.attackType;
    }

    // -------------------------------------------------------------------------
    // Getters — informations de base
    // -------------------------------------------------------------------------

    /** @return l'entité à l'origine du dégât, ou {@code null} (DoT, environnement) */
    public LivingEntity getAttacker() { return attacker; }

    /** @return l'entité qui subit le dégât */
    public LivingEntity getVictim() { return victim; }

    /** @return le montant de dégât avant tout calcul du pipeline */
    public double getBaseDamage() { return baseDamage; }

    /** @return l'origine technique du dégât */
    public DamageSource getSource() { return source; }

    /** @return la catégorie de dégât (physique, feu, magie…) */
    public DamageType getType() { return type; }

    /** @return {@code true} si ce dégât peut produire un coup critique */
    public boolean canCrit() { return canCrit; }

    /** @return {@code true} si le critique est forcé, indépendamment du jet aléatoire */
    public boolean isCritForced() { return forcedCrit; }

    /** @return {@code true} si ce dégât doit appliquer un recul à la victime */
    public boolean canKnockback() { return canKnockback; }

    /** @return l'arme utilisée, ou {@code null} si le dégât n'est pas une attaque d'arme */
    public CustomWeapon getWeapon() { return weapon; }

    /** @return le type d'attaque (mêlée, distance, magie…) */
    public AttackType getAttackType() { return attackType; }

    // -------------------------------------------------------------------------
    // Getters — résultats (valides uniquement après dealDamage())
    // -------------------------------------------------------------------------

    /** @return le montant de dégât réellement appliqué après tout le pipeline */
    public double getFinalDamage() { return finalDamage; }

    /** @return {@code true} si ce coup était critique */
    public boolean wasCrit() { return wasCrit; }

    /** @return {@code true} si ce coup a été totalement bloqué (immunité, invulnérabilité) */
    public boolean wasBlocked() { return wasBlocked; }

    /** @return {@code true} si la victime était immunisée à ce type de dégât */
    public boolean wasImmune() { return wasImmune; }

    /** @return {@code true} si ce coup a tué la victime */
    public boolean isWasKill() { return wasKill; }

    /** @return {@code true} si l'attaque était suffisamment chargée pour compter (cooldown Minecraft) */
    public boolean wasCharged() { return wasCharged; }

    // -------------------------------------------------------------------------
    // Setters publics (configuration avant traitement, pas un résultat)
    // -------------------------------------------------------------------------

    /** Active ou désactive le recul pour ce dégât. */
    public void setCanKnockback(boolean canKnockback) { this.canKnockback = canKnockback; }

    /** Force ce coup à être critique, indépendamment du jet aléatoire. */
    public void setForcedCrit(boolean forcedCrit) { this.forcedCrit = forcedCrit; }

    // -------------------------------------------------------------------------
    // Setters package-private — réservés à DamageManager
    // -------------------------------------------------------------------------

    void setFinalDamage(double finalDamage) { this.finalDamage = finalDamage; }
    void setWasCrit(boolean wasCrit)         { this.wasCrit = wasCrit; }
    void setWasBlocked(boolean wasBlocked)   { this.wasBlocked = wasBlocked; }
    void setWasImmune(boolean wasImmune)     { this.wasImmune = wasImmune; }
    void setWasKill(boolean wasKill)         { this.wasKill = wasKill; }
    void setWasCharged(boolean wasCharged)   { this.wasCharged = wasCharged; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Construit un {@link DamageInfo}. La {@code victim} est obligatoire,
     * tout le reste a des valeurs par défaut sûres.
     */
    public static class Builder {
        private LivingEntity  attacker;
        private LivingEntity  victim;
        private double        baseDamage   = 1;
        private DamageSource  source       = DamageSource.UNKNOWN;
        private DamageType    type         = DamageType.PHYSICAL;
        private boolean       canCrit      = false;
        private boolean       forcedCrit   = false;
        private boolean       canKnockback = true;
        private CustomWeapon  weapon;
        private AttackType    attackType;

        public Builder attacker(LivingEntity attacker)       { this.attacker = attacker; return this; }
        public Builder victim(LivingEntity victim)           { this.victim = victim; return this; }
        public Builder baseDamage(double baseDamage)         { this.baseDamage = baseDamage; return this; }
        public Builder source(DamageSource source)           { this.source = source; return this; }
        public Builder type(DamageType type)                 { this.type = type; return this; }
        public Builder canCrit(boolean canCrit)               { this.canCrit = canCrit; return this; }
        public Builder forcedCrit(boolean forcedCrit)         { this.forcedCrit = forcedCrit; return this; }
        public Builder canKnockback(boolean canKnockback)    { this.canKnockback = canKnockback; return this; }
        public Builder weapon(CustomWeapon weapon)            { this.weapon = weapon; return this; }
        public Builder attackType(AttackType attackType)     { this.attackType = attackType; return this; }

        /**
         * Construit l'instance finale.
         *
         * @return un {@link DamageInfo} immuable, prêt pour {@link DamageManager#dealDamage(DamageInfo)}
         * @throws IllegalStateException si {@code victim} est {@code null} ou {@code baseDamage} négatif
         */
        public DamageInfo build() {
            if (victim == null) {
                throw new IllegalStateException("La victime (victim) est obligatoire pour construire un DamageInfo.");
            }
            if (baseDamage < 0) {
                throw new IllegalStateException("baseDamage ne peut pas être négatif : " + baseDamage);
            }
            return new DamageInfo(this);
        }
    }





}
