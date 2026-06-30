package me.tyalternative.fundamentalis.status.listener;

import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Retire tous les effets de statut d'une entité à sa mort, à l'exception de
 * ceux dont le {@link StatusEffectType} est marqué
 * {@link StatusEffectType#survivesDeath()}.
 *
 * <p>Sans ce listener, les paliers actifs et en sommeil restent attachés au
 * {@code StatusComponent} indéfiniment après la mort — pour un joueur qui
 * respawn, ses anciens buffs/debuffs réapparaîtraient à la réapparition, ce
 * qui n'est cohérent pour aucun des effets intégrés (Poison, Stun, Force…).
 *
 * <p>Fonctionne aussi bien pour les joueurs que pour les mobs custom, tant
 * qu'ils sont trackés par {@link IEntityService} — pas de distinction
 * nécessaire car {@code EntityDeathEvent} est commun aux deux.
 *
 * <p>Priorité {@link EventPriority#MONITOR} : on agit en tout dernier, après
 * que les autres plugins (drops, XP, etc.) ont eu l'occasion de consulter
 * les effets actifs de l'entité si besoin (ex : un module qui calcule un
 * bonus de loot basé sur un buff actif au moment de la mort).
 */
public class DeathCleanupListener implements Listener {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final IEntityService                 entityService;
    private final ComponentKey<IStatusComponent>  statusKey;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService service d'accès aux ComponentHolder
     * @param statusKey     clé typée du composant de statut
     */
    public DeathCleanupListener(IEntityService entityService, ComponentKey<IStatusComponent> statusKey) {
        this.entityService = entityService;
        this.statusKey      = statusKey;
    }

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        entityService.get(entity).ifPresent(holder ->
                holder.get(statusKey).ifPresent(this::clearNonPersistentEffects)
        );
    }

    // -------------------------------------------------------------------------
    // Logique de purge sélective
    // -------------------------------------------------------------------------

    /**
     * Retire tous les types d'effets présents (actifs ou en sommeil) sauf
     * ceux marqués {@link StatusEffectType#survivesDeath()}.
     *
     * <p>On collecte d'abord l'ensemble des {@link StatusEffectType} concernés
     * avant d'appeler {@code removeEffect} pour éviter de muter la collection
     * pendant qu'on l'itère (les implémentations de {@code IStatusComponent}
     * ne garantissent pas qu'itérer sur {@code getAllEffects()} reste valide
     * après un retrait en cours d'itération).
     */
    private void clearNonPersistentEffects(IStatusComponent status) {
        Set<StatusEffectType> typesToRemove = new HashSet<>();

        for (ActiveStatusEffect effect : status.getAllEffects()) {
            if (!effect.type().survivesDeath()) {
                typesToRemove.add(effect.type());
            }
        }

        for (StatusEffectType type : typesToRemove) {
            status.removeEffect(type);
        }
    }
}
