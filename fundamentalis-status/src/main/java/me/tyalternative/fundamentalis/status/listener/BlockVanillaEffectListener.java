package me.tyalternative.fundamentalis.status.listener;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class BlockVanillaEffectListener implements Listener {

    @EventHandler
    public void onTickingDamage(EntityDamageEvent event) {

        if (event.getEntity() instanceof LivingEntity entity && event.getCause() == EntityDamageEvent.DamageCause.POISON) {
            // TODO: FAIRE EN SORTE QUE CELA NE SOIT QUE LORSQU'IL EST SOUS L'EFFET D'UN EFFET DE STATUS DE POISON
            event.setCancelled(true);
        }

        if (event.getEntity() instanceof LivingEntity entity && event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            // TODO: FAIRE EN SORTE QUE CELA NE SOIT QUE LORSQU'IL EST SOUS L'EFFET D'UN EFFET DE STATUS DE GELE
            event.setCancelled(true);
        }

    }
}
