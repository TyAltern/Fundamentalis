package me.tyalternative.fundamentalis.status.effects.special;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class BloodLustEffect extends StatusEffect implements Listener {

        // -------------------------------------------------------------------------
        // Constantes
        // -------------------------------------------------------------------------

    private static final double FORCE_INCREMENT_PER_KILL = 0.01;
    private static final long TICK_ADDED_INBETWEEN_KILL = 60;

        // -------------------------------------------------------------------------
        // Champs
        // -------------------------------------------------------------------------

        private final Plugin plugin;
        private final DamageManager damageManager;

        /** État propre à cette instance : évite un double-enregistrement Bukkit. */
        private boolean listening = false;

        // -------------------------------------------------------------------------
        // Constructeur
        // -------------------------------------------------------------------------

        public BloodLustEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                               Plugin plugin, DamageManager damageManager) {
            super(holder, statsComponent, meta);
            this.plugin        = plugin;
            this.damageManager = damageManager;
        }

        // -------------------------------------------------------------------------
        // Cycle de vie
        // -------------------------------------------------------------------------

        @Override
        public void onApply() {
            if (listening) return;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listening = true;
        }

        @Override
        public void onTick() {
            // Rien de périodique — le vol de vie est déclenché par onPostDamage().
        }

        @Override
        public void onRemove() {
            if (!listening) return;
            HandlerList.unregisterAll(this);
            listening = false;
        }
}
