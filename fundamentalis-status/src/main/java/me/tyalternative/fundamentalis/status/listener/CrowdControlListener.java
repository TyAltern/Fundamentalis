package me.tyalternative.fundamentalis.status.listener;

import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.status.StatusComponent;
import me.tyalternative.fundamentalis.status.StatusEffect;
import me.tyalternative.fundamentalis.status.effects.CC.IBlocksActions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
//import org.bukkit.event.player.PlayerJumpEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Annule les actions Bukkit correspondant aux {@link IBlocksActions.ActionType}
 * bloqués par les instances {@link StatusEffect} actuellement actives sur un joueur.
 *
 * <p>Pour chaque event intercepté, consulte tous les paliers actifs du joueur
 * concerné (via {@link IStatusComponent#getActiveEffects()}), récupère
 * l'instance {@link StatusEffect} vivante correspondante via
 * {@link StatusComponent#getLiveEffect}, et annule l'event si au moins une
 * instance implémente {@link IBlocksActions} et bloque l'action en cours.
 *
 * <h2>Pourquoi un listener séparé plutôt que dans CombatListener ?</h2>
 * Le blocage de mouvement/attaque/interaction relève d'events Bukkit
 * complètement différents de ceux gérés par {@code fundamentalis-combat}
 * ({@code PlayerMoveEvent}, {@code PlayerInteractEvent}…) - les séparer garde
 * chaque listener focalisé sur une seule responsabilité.
 *
 * <h2>Limites de cette première itération</h2>
 * Seuls les joueurs sont concernés (pas les mobs custom) - bloquer l'IA d'un
 * mob nécessiterait une intégration avec son moteur de comportement (futur
 * {@code fundamentalis-mobs}), hors-scope ici.
 */
public class CrowdControlListener implements Listener {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final IEntityService                  entityService;
    private final ComponentKey<IStatusComponent>   statusKey;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService service d'accès aux ComponentHolder
     * @param statusKey     clé typée du composant de statut
     */
    public CrowdControlListener(IEntityService entityService, ComponentKey<IStatusComponent> statusKey) {
        this.entityService = entityService;
        this.statusKey      = statusKey;
    }

    // -------------------------------------------------------------------------
    // Mouvement
    // -------------------------------------------------------------------------

    /**
     * Annule tout déplacement horizontal si {@link IBlocksActions.ActionType#MOVEMENT}
     * est bloqué. La rotation de la caméra (regarder autour de soi) reste
     * autorisée - seule la translation est annulée, en repositionnant le
     * joueur à ses coordonnées précédentes avec sa nouvelle orientation.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isActionBlocked(event.getPlayer(), IBlocksActions.ActionType.MOVEMENT)) return;
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return; // pure rotation de caméra, pas de translation - on laisse passer
        }

        var frozen = event.getFrom().clone();
        frozen.setYaw(event.getTo().getYaw());
        frozen.setPitch(event.getTo().getPitch());
        event.setTo(frozen);
    }

//    /**
//     * Annule le saut si {@link IBlocksActions.ActionType#JUMP} est bloqué.
//     */
//    @EventHandler(ignoreCancelled = true)
//    public void onPlayerJump(PlayerJumpEvent event) {
//        if (isActionBlocked(event.getPlayer(), IBlocksActions.ActionType.JUMP)) {
//            event.setCancelled(true);
//        }
//    }

    // -------------------------------------------------------------------------
    // Attaque
    // -------------------------------------------------------------------------

    /**
     * Annule toute attaque émise par un joueur si
     * {@link IBlocksActions.ActionType#ATTACK} est bloqué pour lui.
     *
     * <p>Priorité {@code LOWEST} : on agit avant
     * {@code fundamentalis-combat}'s {@code CombatListener} (priorité HIGHEST)
     * pour ne pas laisser le pipeline de dégâts s'exécuter inutilement si
     * l'attaque doit de toute façon être bloquée.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (isActionBlocked(attacker, IBlocksActions.ActionType.ATTACK)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Interaction
    // -------------------------------------------------------------------------

    /**
     * Annule toute interaction (clic droit) si
     * {@link IBlocksActions.ActionType#INTERACT} est bloqué.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return; // ex : plaque de pression, pas une interaction volontaire
        if (isActionBlocked(event.getPlayer(), IBlocksActions.ActionType.INTERACT)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    /**
     * Vérifie si au moins une instance d'effet actuellement active sur ce
     * joueur bloque le type d'action donné.
     */
    private boolean isActionBlocked(Player player, IBlocksActions.ActionType action) {
        return entityService.get(player)
                .flatMap(holder -> holder.get(statusKey))
                .filter(status -> status instanceof StatusComponent)
                .map(status -> (StatusComponent) status)
                .map(status -> hasBlockingTier(status, action))
                .orElse(false);
    }

    private boolean hasBlockingTier(StatusComponent status, IBlocksActions.ActionType action) {
        for (ActiveStatusEffect active : status.getActiveEffects()) {
            StatusEffect liveEffect = status.getLiveEffect(active.type());
            if (liveEffect instanceof IBlocksActions blocker
                    && blocker.getBlockedActions().contains(action)) {
                return true;
            }
        }
        return false;
    }
}
