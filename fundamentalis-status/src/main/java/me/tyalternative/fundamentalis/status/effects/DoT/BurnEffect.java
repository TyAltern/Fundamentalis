package me.tyalternative.fundamentalis.status.effects.DoT;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusComponent;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Brûlure — dégâts de feu sur la durée, plus intense que le Poison
 * (1.5 dégât/tick par niveau) mais avec la même cadence.
 */
public class BurnEffect extends StatusEffect implements Listener {

    private static final long   TICK_INTERVAL    = 20L;
    private static final double DAMAGE_PER_LEVEL = 1;

    private final Plugin plugin;
    private final DamageManager damageManager;
    private long ticksSinceLastDamage = 0;
    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    public BurnEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                      Plugin plugin, DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.plugin        = plugin;
        this.damageManager = damageManager;
    }

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;

        ticksSinceLastDamage = 0;
        getEntity().setVisualFire(true);

    }

    @Override
    public void onTick() {

        if (getEntity().isInWaterOrRain()) {
            getHolder().require(StatusComponent.KEY).removeEffectInstance(getInstanceId());
        }

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
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;

        getEntity().setVisualFire(false);
    }

    // -------------------------------------------------------------------------
    // Listener Bukkit — actif uniquement entre onApply() et onRemove()
    // -------------------------------------------------------------------------

    @EventHandler
    void onProjectileHitEvent(ProjectileHitEvent event) {
        if (event.getEntity() instanceof ThrownPotion potion) {
            if(!potion.getEffects().isEmpty()) return;
            if (!potion.getNearbyEntities(2.0,2.0,2.0).contains(getEntity())) return;
            getHolder().require(StatusComponent.KEY).removeEffectInstance(getInstanceId());

        }
    }

}
