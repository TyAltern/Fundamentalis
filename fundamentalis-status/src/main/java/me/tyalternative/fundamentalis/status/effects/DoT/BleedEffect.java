package me.tyalternative.fundamentalis.status.effects.DoT;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockType;
import org.bukkit.entity.LivingEntity;

/**
 * Saignement — dégâts sur la durée proportionnels aux PV <strong>maximum</strong>
 * de la victime (et non une valeur fixe), pour rester pertinent contre des
 * cibles à haute vitalité. Illustre un effet dont le calcul de dégâts dépend
 * d'un attribut Bukkit de la victime au moment du tick, pas seulement du niveau.
 */
public class BleedEffect extends StatusEffect{


    private static final long   TICK_INTERVAL                = 20L;
    private static final double MAX_HEALTH_PERCENT_PER_LEVEL = 0.02; // 2% des PV max par niveau, par tick

    private final DamageManager damageManager;
    private long ticksSinceLastDamage = 0;

    public BleedEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
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

        var maxHealthAttr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

        double damage = maxHealth * MAX_HEALTH_PERCENT_PER_LEVEL * getLevel();
        DamageInfo info = damageManager.createStatusDamage(
                victim, DamageSource.STATUS_BLEED, damage, DamageType.PHYSICAL);
        damageManager.dealDamage(info);

        Particle.BLOCK.builder()
                .count(20)
                .data(BlockType.REDSTONE_BLOCK.createBlockData())
                .location(victim.getLocation().clone().add(0,1,0))
                .receivers(32, true)
                .spawn();
    }

    @Override
    public void onRemove() {
        // Rien à nettoyer.
    }
}
