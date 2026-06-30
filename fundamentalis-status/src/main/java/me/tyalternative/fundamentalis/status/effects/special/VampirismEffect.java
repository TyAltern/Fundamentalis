package me.tyalternative.fundamentalis.status.effects.special;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Vampirisme — vole un pourcentage des dégâts infligés par l'entité affectée
 */
public class VampirismEffect extends StatusEffect implements Listener {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final double HEAL_RATIO_PER_LEVEL = 0.033; // +3.3% de vol de vie par niveau

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Plugin        plugin;
    private final DamageManager damageManager;

    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public VampirismEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
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

    // -------------------------------------------------------------------------
    // Listener Bukkit — actif uniquement entre onApply() et onRemove()
    // -------------------------------------------------------------------------

    /**
     * Soigne l'entité affectée par ce palier proportionnellement aux dégâts
     * qu'elle vient d'infliger.
     */
    @EventHandler
    public void onPostDamage(PostDamageEvent event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) return;
        if (!attacker.getUniqueId().equals(getEntity().getUniqueId())) return;

        double healAmount = event.getFinalDamage() * (HEAL_RATIO_PER_LEVEL * getLevel());
        if (healAmount <= 0) return;

        damageManager.healEntity(attacker, healAmount);
    }
}
