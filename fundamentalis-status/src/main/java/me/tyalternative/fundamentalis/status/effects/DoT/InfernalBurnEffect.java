package me.tyalternative.fundamentalis.status.effects.DoT;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.*;

/**
 * Brûlure infernale — dégâts de feu sur la durée, plus intense que le Poison
 * (1.5 dégât/tick par niveau) mais avec la même cadence.
 * À la différence de la brûlure par défaut, celle-ci ne peut pas
 * être éteinte.
 */
public class InfernalBurnEffect extends StatusEffect {

    private static final long   TICK_INTERVAL    = 20L;
    private static final double DAMAGE_PER_LEVEL = 1;

    private final DamageManager damageManager;
    private long ticksSinceLastDamage = 0;

    public InfernalBurnEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                      DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

    @Override
    public void onApply() {
        ticksSinceLastDamage = 0;
        getEntity().setVisualFire(true);

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
                victim, DamageSource.STATUS_BURN, damage, DamageType.FIRE);
        damageManager.dealDamage(info);
    }

    @Override
    public void onRemove() {
        getEntity().setVisualFire(false);
    }
}
