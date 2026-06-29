package me.tyalternative.fundamentalis.core.entity;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.entity.EntityRegisteredEvent;
import me.tyalternative.fundamentalis.api.event.entity.EntityUnregisteredEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.core.FundamentalisCorePlugin;
import me.tyalternative.fundamentalis.core.stats.StatsComponent;
import me.tyalternative.fundamentalis.core.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Écoute les événements Bukkit liés au cycle de vie des entités et orchestre
 * l'enregistrement / désenregistrement dans l'{@link EntityService}.
 *
 * <p>C'est le point d'entrée unique pour tout ce qui concerne l'apparition
 * et la disparition des entités trackées. Il coordonne :
 * <ol>
 *   <li>L'enregistrement dans {@link EntityService} (création du holder).</li>
 *   <li>La création et le chargement du {@link StatsComponent} via {@link StatsManager}.</li>
 *   <li>Le fire de {@link EntityRegisteredEvent} pour que les autres modules
 *       puissent attacher leurs propres composants.</li>
 *   <li>Le fire de {@link EntityUnregisteredEvent} avant le démontage.</li>
 *   <li>La persistance des stats avant la déconnexion ou la mort.</li>
 * </ol>
 *
 * <h2>Priorité des listeners</h2>
 * Les listeners utilisent {@link EventPriority#MONITOR} en lecture (join/quit)
 * et {@link EventPriority#NORMAL} pour les actions. Cela garantit que Fundamentalis
 * s'exécute après les autres plugins qui pourraient modifier l'événement.
 */
public class EntityTracker implements Listener {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final JavaPlugin    plugin;
    private final EntityService entityService;
    private final StatsManager  statsManager;
    private final Logger        logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param plugin        instance du plugin, pour planifier les tâches Bukkit
     * @param entityService service d'enregistrement des holders
     * @param statsManager  manager du cycle de vie des StatsComponent
     * @param logger        logger du plugin
     */
    public EntityTracker(JavaPlugin plugin,
                         EntityService entityService,
                         StatsManager statsManager,
                         Logger logger) {
        this.plugin        = plugin;
        this.entityService = entityService;
        this.statsManager  = statsManager;
        this.logger        = logger;
    }

    // -------------------------------------------------------------------------
    // Connexion d'un joueur
    // -------------------------------------------------------------------------

    /**
     * Enregistre le joueur dans l'EntityService, charge ses stats depuis la BDD
     * et fire {@link EntityRegisteredEvent} pour que les autres modules
     * attachent leurs composants.
     *
     * <p>Priorité {@link EventPriority#LOWEST} : Fundamentalis s'enregistre en
     * premier pour que les autres modules trouvent le holder déjà disponible
     * dans leurs propres listeners de priorité normale ou haute.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Cas de sécurité : si le joueur était déjà tracké (reconnexion rapide)
        // on nettoie l'ancien holder avant d'en créer un nouveau
        if (entityService.isTracked(player)) {
            logger.warning("[EntityTracker] Joueur " + player.getName()
                    + " déjà tracké à la connexion — nettoyage préventif.");
            handleUnregister(uuid, EntityUnregisteredEvent.Cause.API_CALL);
        }

        ComponentHolder holder = entityService.register(player);
        statsManager.createAndLoad(holder, uuid, true);

        // On fire l'event APRÈS le chargement async — voir onPlayerJoin complète
        // Note : le chargement async met quelques ms. Pour les modules qui
        // attachent leurs composants sur EntityRegisteredEvent, les stats peuvent
        // encore être aux valeurs par défaut. C'est intentionnel et documenté.
        Bukkit.getPluginManager().callEvent(
                new EntityRegisteredEvent(holder, EntityRegisteredEvent.Cause.PLAYER_JOIN));

        logger.fine("[EntityTracker] Joueur enregistré : " + player.getName() + " (" + uuid + ")");
    }

    // -------------------------------------------------------------------------
    // Déconnexion d'un joueur
    // -------------------------------------------------------------------------

    /**
     * Persiste les stats du joueur, fire {@link EntityUnregisteredEvent}
     * pour que les modules sauvegardent leurs données, puis démonte le holder.
     *
     * <p>Priorité {@link EventPriority#MONITOR} : on s'exécute en dernier,
     * après que tous les autres plugins ont traité la déconnexion.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Flush immédiat des stats avant de démonter le holder
        entityService.get(player).ifPresent(holder -> {
            holder.get(StatsComponent.KEY).ifPresent(stats -> {
                statsManager.flushPlayer(uuid, (StatsComponent) stats);
            });
        });

        handleUnregister(uuid, EntityUnregisteredEvent.Cause.PLAYER_QUIT);
        logger.fine("[EntityTracker] Joueur désenregistré : " + player.getName() + " (" + uuid + ")");
    }

    // -------------------------------------------------------------------------
    // Mort d'une entité
    // -------------------------------------------------------------------------

    /**
     * Gère la mort d'une entité trackée (mob custom ou joueur).
     *
     * <p>Pour les joueurs : on flush les stats mais on <em>ne désenregistre pas</em>
     * le holder — le joueur va respawn et réutiliser le même holder.
     *
     * <p>Pour les mobs custom : on flush les stats, on nettoie la BDD
     * (les stats d'un mob mort sont inutiles) et on désenregistre le holder.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID         uuid   = entity.getUniqueId();

        // Seules les entités trackées nous intéressent
        if (!entityService.isTracked(entity)) return;

        if (entity instanceof Player) {
            // Le joueur est mort mais reste connecté — on garde le holder
            // Le StatsComponent est conservé, les modificateurs temporaires sont vidés
            entityService.get(entity).ifPresent(holder ->
                    holder.get(StatsComponent.KEY).ifPresent(IStatsComponent::clearModifiers));
        } else {
            // Mob custom : flush + nettoyage BDD + désenregistrement
            entityService.get(entity).ifPresent(holder ->
                    holder.get(StatsComponent.KEY).ifPresent(stats ->
                            statsManager.flushEntity(uuid, (StatsComponent) stats)
                    )
            );
            statsManager.deleteEntityStats(uuid);
            handleUnregister(uuid, EntityUnregisteredEvent.Cause.DEATH);

        }
    }



    // -------------------------------------------------------------------------
    // Arrêt du serveur
    // -------------------------------------------------------------------------

    /**
     * Désenregistre toutes les entités encore trackées lors de l'arrêt du serveur.
     *
     * <p>Appelé directement par {@link FundamentalisCorePlugin FundamentalisCorePlugin#onDisable()}
     * (pas via un listener Bukkit, pour garantir l'ordre d'exécution).
     *
     * <p>Le flush des stats joueurs est effectué de manière <em>bloquante</em>
     * ici car le serveur est en train de s'arrêter et on ne peut pas attendre
     * des futures async.
     */
    public void onServerShutdown() {
        logger.info("[EntityTracker] Arrêt — désenregistrement de "
                + entityService.getTrackedCount() + " entités…");

        // Copie de la collection pour éviter ConcurrentModificationException
        for (ComponentHolder holder : entityService.getAll().stream().toList()) {
            UUID uuid = UUID.fromString(holder.getEntityId());

            // Flush synchrone des stats — on est sur le thread principal à l'arrêt
            holder.get(StatsComponent.KEY).ifPresent(stats -> {
                if (holder.getEntity() instanceof Player) {
                    statsManager.flushPlayerSync(uuid, (StatsComponent) stats);
                } else {
                    statsManager.flushEntity(uuid, (StatsComponent) stats);
                }
            });

            handleUnregister(uuid, EntityUnregisteredEvent.Cause.SERVER_SHUTDOWN);
        }

        logger.info("[EntityTracker] Toutes les entités ont été désenregistrées.");
    }



    // -------------------------------------------------------------------------
    // Utilitaire interne
    // -------------------------------------------------------------------------

    /**
     * Séquence de désenregistrement réutilisée par tous les chemins de sortie :
     * <ol>
     *   <li>Fire {@link EntityUnregisteredEvent} (les modules sauvegardent).</li>
     *   <li>Appelle {@link EntityService#unregister} (démontage du holder).</li>
     * </ol>
     *
     * @param entityUUID UUID de l'entité à désenregistrer
     * @param cause      raison du désenregistrement
     */
    private void handleUnregister(UUID entityUUID, EntityUnregisteredEvent.Cause cause) {
        entityService.get(entityUUID).ifPresent(holder -> {
            // 1. Notifier les autres modules AVANT de démonter le holder
            Bukkit.getPluginManager().callEvent(
                    new EntityUnregisteredEvent(holder, cause)
            );

            // 2. Démonter le holder (onDetach() sur chaque composant)
            entityService.unregister(entityUUID);
        });
    }
}
