package me.tyalternative.fundamentalis.status.effects.CC;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class GravityPulseEffect extends StatusEffect implements Listener {

    private final Plugin plugin;

    private static final double PULSE_PER_LEVEL  = 0.33;

    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public GravityPulseEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                              Plugin plugin) {
        super(holder, statsComponent, meta);
        this.plugin        = plugin;
    }

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onRemove() {
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;
    }

    @EventHandler
    public void onPreDamage(PreDamageEvent event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(getEntity().getUniqueId())) {
            double previousPulse = event.getKnockbackFactor();
            int sign = previousPulse >= 0? -1: 1;
            event.setKnockbackFactor(PULSE_PER_LEVEL * getLevel() * previousPulse * sign);
        }
    }
}
