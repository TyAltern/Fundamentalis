package me.tyalternative.fundamentalis.status.effects.special;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageCalculationEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class DenialEffect extends StatusEffect implements Listener {

    private final Plugin plugin;
    private final DamageManager damageManager;

    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    private double storedDamage;

    // -------------------------------------------------------------------------
    // Constructeur — signature attendue par StatusEffectFactory (référence de constructeur)
    // -------------------------------------------------------------------------

    public DenialEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
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

        storedDamage = 0;
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRemove() {
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;

        DamageInfo info = damageManager.createStatusDamage(
                getEntity(), DamageSource.STATUS_DENIAL, storedDamage, DamageType.PHYSICAL);
        damageManager.dealDamage(info);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPostDamageCalculation(PostDamageCalculationEvent event) {
        if (event.getVictim().getUniqueId().equals(getEntity().getUniqueId())) {
            storedDamage += event.getFinalDamage();
            event.setFinalDamage(0);
        }
    }
}
