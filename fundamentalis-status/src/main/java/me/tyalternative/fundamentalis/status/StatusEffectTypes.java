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
    public static final StatusEffectType POISON = StatusEffectType.of("poison",StatusEffectCategory.DAMAGE_OVER_TIME, 3, 200);

    /** Brûlure — DoT feu, 3 niveaux, 8s par défaut. */
    public static final StatusEffectType BURN = StatusEffectType.of("burn", StatusEffectCategory.DAMAGE_OVER_TIME, 3, 160);

    /** Saignement — DoT physique, dégâts proportionnels aux PV max de la victime, 3 niveaux, 6s par défaut. */
    public static final StatusEffectType BLEED = StatusEffectType.of("bleed", StatusEffectCategory.DAMAGE_OVER_TIME, 3, 120);

    /** Électrocution — DoT électrique, dégâts élevés mais courte durée, 2 niveaux, 4s par défaut. */
    public static final StatusEffectType ELECTROCUTION = StatusEffectType.of("electrocution", StatusEffectCategory.DAMAGE_OVER_TIME, 2, 80);

    // -------------------------------------------------------------------------
    // Contrôle de foule
    // -------------------------------------------------------------------------

    /** Étourdissement — bloque mouvement, attaque, interaction et saut. Binaire (1 niveau), 2s par défaut. */
    public static final StatusEffectType STUN = StatusEffectType.of("stun", StatusEffectCategory.CROWD_CONTROL, 1, 40);

    /** Ralentissement — réduit la vitesse de déplacement sans bloquer d'action, 3 niveaux, 5s par défaut. */
    public static final StatusEffectType SLOW = StatusEffectType.of("slow", StatusEffectCategory.CROWD_CONTROL, 3, 100);

    /** Gel — ralentissement sévère + bloque le saut, 2 niveaux, 4s par défaut. */
    public static final StatusEffectType FREEZE = StatusEffectType.of("freeze", StatusEffectCategory.CROWD_CONTROL, 2, 80);

    // -------------------------------------------------------------------------
    // Modificateurs de stat temporaires
    // -------------------------------------------------------------------------

    /** Force — augmente temporairement la stat FORCE, 3 niveaux, 60s par défaut. */
    public static final StatusEffectType STRENGTH = StatusEffectType.of("strength", StatusEffectCategory.STAT_MODIFIER, 3, 1200);

    /** Régénération — accélère la récupération de vie (consommée par un futur HealthComponent), 3 niveaux, 30s par défaut. */
    public static final StatusEffectType REGENERATION = StatusEffectType.of("regeneration", StatusEffectCategory.STAT_MODIFIER, 3, 600);

    // -------------------------------------------------------------------------
    // Effets spéciaux
    // -------------------------------------------------------------------------

    /** Vampirisme — vole un pourcentage des dégâts infligés sous forme de soin, 3 niveaux, 30s par défaut. */
    public static final StatusEffectType VAMPIRISM = StatusEffectType.of("vampirism", StatusEffectCategory.SPECIAL, 3, 600);

    /** Furtivité — effet spécial sur mesure (mécanique à définir par un futur comportement custom), 1 niveau, 20s par défaut. */
    public static final StatusEffectType STEALTH = StatusEffectType.of("stealth", StatusEffectCategory.SPECIAL, 1, 400);
}
