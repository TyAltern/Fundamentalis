package me.tyalternative.fundamentalis.status.effects;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.LivingEntity;

/**
 * Poison — dégâts sur la durée, un tick toutes les secondes, intensité
 * proportionnelle au niveau (1 dégât/tick par niveau).
 *
 * <p>Illustre le cas le plus simple d'effet à état : un compteur de ticks
 * interne ({@link #ticksSinceLastDamage}) pour espacer les applications de
 * dégâts sans dépendre d'une cadence calculée à partir du palier (comme le
 * faisait l'ancien {@code DamageOverTimeBehavior} générique) — chaque
 * instance de {@code PoisonEffect} gère sa propre horloge interne, exactement
 * comme dans l'ancienne version de Fundamentalis.
 */
public class PoisonEffect extends StatusEffect {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final long   TICK_INTERVAL  = 20L; // un tick de dégâts par seconde
    private static final double DAMAGE_PER_LEVEL = 1.0;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final DamageManager damageManager;

    /** État propre à cette instance : décompte avant le prochain tick de dégâts. */
    private long ticksSinceLastDamage = 0;

    // -------------------------------------------------------------------------
    // Constructeur — signature attendue par StatusEffectFactory (référence de constructeur)
    // -------------------------------------------------------------------------

    public PoisonEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                        DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    @Override
    public void onApply() {
        ticksSinceLastDamage = 0;

    }

    @Override
    public void onTick() {
        ticksSinceLastDamage++;
        if (ticksSinceLastDamage < TICK_INTERVAL) return;
        ticksSinceLastDamage = 0;

        LivingEntity victim = getEntity();
        if (victim == null || !victim.isValid()) return;

        double damage = getLevel() * DAMAGE_PER_LEVEL;
        DamageInfo info = damageManager.createStatusDamage(
                victim, DamageSource.STATUS_POISON, damage, DamageType.PHYSICAL);
        damageManager.dealDamage(info);
    }

    @Override
    public void onRemove() {
        // Rien à nettoyer — le poison n'a pas d'effet persistant en dehors de ses ticks.
    }
}
