package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.status.StatusEffectCategory;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;

/**
 * Catalogue des {@link StatusEffectType} intégrés à {@code fundamentalis-status}.
 *
 * <p>Reprend les 11 effets identifiés dans la roadmap, répartis sur les 4
 * {@link StatusEffectCategory} :
 * <ul>
 *   <li><strong>DoT</strong> - Poison, Brûlure, Saignement, Électrocution</li>
 *   <li><strong>CC</strong> - Étourdissement, Ralentissement, Gel</li>
 *   <li><strong>StatModifier</strong> - Force, Régénération</li>
 *   <li><strong>Spécial</strong> - Vampirisme, Furtivité ("Furry" dans la roadmap d'origine)</li>
 * </ul>
 *
 * <p>Ces constantes sont enregistrées dans le {@code IStatusEffectRegistry}
 * par {@code StatusPlugin#onEnable}, exactement comme {@code StatType}
 * l'est par le Core. Un plugin tiers peut définir ses propres effets de la
 * même façon, sans toucher à cette classe.
 *
 * <p>Les durées par défaut sont exprimées en ticks (20 ticks = 1 seconde).
 */
public class StatusEffectTypes {
    private StatusEffectTypes() {}

    // -------------------------------------------------------------------------
    // Dégâts sur la durée — niveau influence l'intensité du tick
    // -------------------------------------------------------------------------

    /** Poison — DoT physique léger, 3 niveaux, 10s par défaut. */
    public static final StatusEffectType POISON = StatusEffectType.of("poison",StatusEffectCategory.DAMAGE_OVER_TIME, 4, 200);

    /** Brûlure — DoT feu, 3 niveaux, 8s par défaut. */
    public static final StatusEffectType BURN = StatusEffectType.of("burn", StatusEffectCategory.DAMAGE_OVER_TIME, 4, 160);

    /** Brûlure infernale (ne peut pas s'étendre avant la fin du temps) — DoT feu, 3 niveaux, 8s par défaut. */
    public static final StatusEffectType INFERNAL_BURN = StatusEffectType.of("infernal_burn", StatusEffectCategory.DAMAGE_OVER_TIME, 4, 160);

    /** Saignement — DoT physique, dégâts proportionnels aux PV max de la victime, 3 niveaux, 6s par défaut. */
    public static final StatusEffectType BLEED = StatusEffectType.of("bleed", StatusEffectCategory.DAMAGE_OVER_TIME, 3, 120);

    /** Électrocution — DoT électrique, dégâts élevés mais courte durée, 2 niveaux, 4s par défaut. */
    public static final StatusEffectType ELECTROCUTION = StatusEffectType.of("electrocution", StatusEffectCategory.DAMAGE_OVER_TIME, 2, 80);

    // -------------------------------------------------------------------------
    // Contrôle de foule
    // -------------------------------------------------------------------------

    /** Étourdissement — bloque mouvement, attaque, interaction et saut. Binaire (1 niveau), 10s par défaut. */
    public static final StatusEffectType STUN = StatusEffectType.of("stun", StatusEffectCategory.CROWD_CONTROL, 1, 200);

    /** Ralentissement — réduit la vitesse de déplacement sans bloquer d'action, 3 niveaux, 10s par défaut. */
    public static final StatusEffectType SLOW = StatusEffectType.of("slow", StatusEffectCategory.CROWD_CONTROL, 3, 200);

    /** Gel — gêle la victime, 2 niveaux, 10s par défaut. */
    public static final StatusEffectType FREEZE = StatusEffectType.of("freeze", StatusEffectCategory.CROWD_CONTROL, 1, 200);

    /** Enracinement — empêche de prendre du knockback , 1 niveaux, 10s par défaut. */
    public static final StatusEffectType ROOTING = StatusEffectType.of("rooting", StatusEffectCategory.CROWD_CONTROL, 1, 200);

    /** Impulsion Gravitationnelle — inflige un knockback négatif à ses ciles, les ramenant vers l'attaquant, 3 niveaux, 10s par défaut. */
    public static final StatusEffectType GRAVITY_PULSE = StatusEffectType.of("gravity_pulse", StatusEffectCategory.CROWD_CONTROL, 3, 200);

    // -------------------------------------------------------------------------
    // Modificateurs de stat temporaires
    // -------------------------------------------------------------------------

    /** Force — augmente temporairement la stat FORCE, 3 niveaux, 60s par défaut. */
    public static final StatusEffectType STRENGTH = StatusEffectType.of("strength", StatusEffectCategory.STAT_MODIFIER, 3, 1200);

    /** Régénération — accélère la récupération de vie (consommée par un futur HealthComponent), 3 niveaux, 30s par défaut. */
    public static final StatusEffectType REGENERATION = StatusEffectType.of("regeneration", StatusEffectCategory.STAT_MODIFIER, 3, 600);

    /** Vitesse — accélère la vitesse du joueur, 5 niveaux, 30s par défaut. */
    public static final StatusEffectType SPEED = StatusEffectType.of("speed", StatusEffectCategory.STAT_MODIFIER, 5, 600);

    /** Resistance — augmente la résistance, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType TOUGHNESS = StatusEffectType.of("toughness", StatusEffectCategory.STAT_MODIFIER, 3, 600);

    /** Soin Instantané — soigne instantanément, 10 niveaux, 0.5s par défaut. */
    public static final StatusEffectType HEAL_BURST = StatusEffectType.of("heal_burst", StatusEffectCategory.STAT_MODIFIER, 10, 10);

    /** Absorption — octroi de la vie temporaire supplémentaire, 5 niveaux, 30s par défaut. */
    public static final StatusEffectType ABSORPTION = StatusEffectType.of("absorption", StatusEffectCategory.STAT_MODIFIER, 5, 600);

    /** Adrénaline — Augment la force, mais réduit la défense, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType ADRENALINE = StatusEffectType.of("adrenaline", StatusEffectCategory.STAT_MODIFIER, 3, 600);

    // -------------------------------------------------------------------------
    // Effets spéciaux
    // -------------------------------------------------------------------------

    /** Vampirisme — vole un pourcentage des dégâts infligés sous forme de soin, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType VAMPIRISM = StatusEffectType.of("vampirism", StatusEffectCategory.SPECIAL, 3, 600);

    /** Epine — renvoie un pourcent des dégâts subit à l'attaquant, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType THORNS = StatusEffectType.of("thorns", StatusEffectCategory.SPECIAL, 3, 600);

    /** Dénégation — en magazine tous les dégâts tant que l'effet est actif puis les appliques tous en même temps, 1 niveau, 60s par défaut. */
    public static final StatusEffectType DENIAL = StatusEffectType.of("denial", StatusEffectCategory.SPECIAL, 1, 1200);

    /** Soif de Sang — obtient un bonus de force pour tous les kill réalisés, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType BLOODLUST = StatusEffectType.of("bloodlust", StatusEffectCategory.SPECIAL, 3, 600);

    /** Enchainement — lie plusieurs entités entre elles et partagent leurs dégâts, 5 niveaux, 30s par défaut. */
    public static final StatusEffectType CHAIN = StatusEffectType.of("chain", StatusEffectCategory.SPECIAL, 5, 20);
    public static final StatusEffectType CHAIN_LINK = StatusEffectType.of("chain_link", StatusEffectCategory.SPECIAL, 1, 300);

    /** Furtivité — effet spécial sur mesure (mécanique à définir par un futur comportement custom), 1 niveau, 20s par défaut. */
    public static final StatusEffectType STEALTH = StatusEffectType.of("stealth", StatusEffectCategory.SPECIAL, 1, 400);
}
