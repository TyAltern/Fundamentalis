package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageCalculationEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusComponent;
import me.tyalternative.fundamentalis.status.StatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class AbsorptionEffect extends StatusEffect implements Listener {

    private static final double ABSORPTION_PER_LEVEL = 4.0; // 2 cœurs par niveau
    private double remainingAbsorption;

    private final Plugin plugin;

    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public AbsorptionEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                              Plugin plugin) {
        super(holder, statsComponent, meta);
        this.plugin        = plugin;
    }

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;

        remainingAbsorption = ABSORPTION_PER_LEVEL * getLevel();
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostDamageCalculation(PostDamageCalculationEvent event) {
        LivingEntity entity = event.getVictim();
        if (!entity.getUniqueId().equals(getEntity().getUniqueId())) return;
        if (remainingAbsorption <= 0) return;

        remainingAbsorption -= event.getFinalDamage();
        if (remainingAbsorption > 0) event.setFinalDamage(0);
        else {
            event.setFinalDamage(-remainingAbsorption);
            getHolder().require(StatusComponent.KEY).removeEffectInstance(getInstanceId());
        }

    }
}
