package me.tyalternative.fundamentalis.status.effects.DoT;

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
 * Électrocution — DoT électrique à cadence rapide (un tick toutes les
 * 10 ticks, soit 2 par seconde) mais courte durée et dégâts élevés par tick.
 */
public class ElectrocutionEffect extends StatusEffect {

    private static final long   TICK_INTERVAL    = 10L;
    private static final double DAMAGE_PER_LEVEL = 2.0;

    private final DamageManager damageManager;
    private long ticksSinceLastDamage = 0;

    public ElectrocutionEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                               DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

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
                victim, DamageSource.STATUS_ELECTROCUTION, damage, DamageType.LIGHTNING);
        damageManager.dealDamage(info);
    }

    @Override
    public void onRemove() {
        // Rien à nettoyer.
    }
}
