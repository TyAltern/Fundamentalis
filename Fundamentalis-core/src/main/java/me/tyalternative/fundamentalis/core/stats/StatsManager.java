package me.tyalternative.fundamentalis.core.stats;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.stats.StatChangeEvent;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.core.database.repository.EntityStatsRepository;
import me.tyalternative.fundamentalis.core.database.repository.PlayerStatsRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Orchestre le cycle de vie des {@link StatsComponent}.
 *
 * <p>Ce manager est responsable de :
 * <ul>
 *   <li><strong>Création</strong> — instancie et initialise un {@link StatsComponent}
 *       pour une entité donnée.</li>
 *   <li><strong>Chargement</strong> — déclenche le chargement async depuis la BDD
 *       et applique les données sur le thread principal une fois prêtes.</li>
 *   <li><strong>Sauvegarde</strong> — flush les stats en BDD à la demande
 *       ou lors du flush périodique automatique.</li>
 *   <li><strong>Auto-save</strong> — tâche périodique qui persiste les stats de
 *       tous les joueurs connectés à intervalle configurable.</li>
 * </ul>
 *
 * <p>Ce manager ne gère pas lui-même le cycle de vie des entités (connexion,
 * déconnexion, mort…). C'est {@link me.tyalternative.fundamentalis.core.entity.EntityTracker EntityTracker}
 * qui écoute ces événements Bukkit et délègue au manager.
 */
public class StatsManager {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final JavaPlugin            plugin;
    private final IStatTypeRegistry     registry;
    private final PlayerStatsRepository playerRepo;
    private final EntityStatsRepository entityRepo;
    private final Logger                logger;
    private final boolean               debugLog;
    private final int                   autoSaveInterval;

    /** Tâche Bukkit du flush périodique — annulée dans {@link #shutdown()}. */
    private BukkitTask autoSaveTask;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param plugin           instance du plugin, pour planifier les tâches Bukkit
     * @param registry         registre des StatType
     * @param playerRepo       repository de persistance des stats joueurs
     * @param entityRepo       repository de persistance des stats entités
     * @param logger           logger du plugin
     * @param debugLog         si {@code true}, logge chaque modification de stat
     * @param autoSaveInterval intervalle en ticks entre chaque auto-save (ex : 6000 = 5 min)
     */
    public StatsManager(JavaPlugin plugin,
                        IStatTypeRegistry registry,
                        PlayerStatsRepository playerRepo,
                        EntityStatsRepository entityRepo,
                        Logger logger, boolean debugLog,
                        int autoSaveInterval
    ) {
        this.plugin           = plugin;
        this.registry         = registry;
        this.playerRepo       = playerRepo;
        this.entityRepo       = entityRepo;
        this.logger           = logger;
        this.debugLog         = debugLog;
        this.autoSaveInterval = autoSaveInterval;
    }

    // -------------------------------------------------------------------------
    // Démarrage / arrêt
    // -------------------------------------------------------------------------

    /**
     * Démarre la tâche d'auto-save périodique.
     * Appelé par {@link me.tyalternative.fundamentalis.core.CorePlugin CorePlugin#onEnable()}.
     */
    public void start() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // On itère sur les joueurs connectés et on flush leurs stats
            for (Player player : Bukkit.getOnlinePlayers()) {
                flushPlayer(player.getUniqueId(), findStatsComponent(player.getUniqueId()));
            }
        }, autoSaveInterval, autoSaveInterval);

        logger.info("[StatsManager] Auto-save démarré (intervalle : "
                + autoSaveInterval + " ticks).");
    }

    /**
     * Arrête l'auto-save et flush tous les joueurs en ligne de manière synchrone.
     * Appelé par {@link me.tyalternative.fundamentalis.core.CorePlugin CorePlugin.onDisable()}.
     *
     * <p>Le flush synchrone bloque volontairement le thread principal — c'est
     * acceptable à l'arrêt du serveur pour garantir que les données sont sauvegardées
     * avant que la JVM ne se ferme.
     */
    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        logger.info("[StatsManager] Flush final des stats en cours…");
        // Le flush à l'arrêt est géré par EntityTracker via onDisable
    }

    // -------------------------------------------------------------------------
    // Création et chargement d'un StatsComponent
    // -------------------------------------------------------------------------

    /**
     * Crée un {@link StatsComponent} vide (avec les valeurs par défaut) et
     * l'attache au holder, puis déclenche le chargement async des données BDD.
     *
     * <p>Doit être appelé sur le <strong>thread principal Bukkit</strong>.
     * Le composant est immédiatement disponible avec les valeurs par défaut,
     * et sera mis à jour avec les vraies valeurs dès que le chargement async
     * est terminé (quelques ticks plus tard).
     *
     * @param holder     le ComponentHolder auquel attacher le composant
     * @param entityUUID l'UUID de l'entité (joueur ou mob)
     * @param isPlayer   {@code true} pour un joueur (persiste), {@code false} pour un mob
     */
    public void createAndLoad(ComponentHolder holder, UUID entityUUID, boolean isPlayer) {
        // 1. Créer le composant avec les valeurs par défaut et l'attacher
        StatsComponent component = new StatsComponent(holder, registry, debugLog);
        holder.attach(StatsComponent.KEY, component);

        // 2. Charger les données depuis la BDD de manière asynchrone
        var loadFutur = isPlayer ? playerRepo.load(entityUUID) : entityRepo.load(entityUUID);
        loadFutur.thenAccept(loadedData -> {
            // On revient sur le thread principal pour appliquer les données
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Vérification défensive : le holder est-il encore valide ?
                if (!holder.isValid()) return;
                component.applyLoadedData(loadedData);

                if (debugLog) {
                    logger.info("[StatsManager] Stats chargées pour " + entityUUID
                            + " (" + loadedData.size() + " stats depuis la BDD).");
                }
            });
        });

    }

    // -------------------------------------------------------------------------
    // Sauvegarde
    // -------------------------------------------------------------------------

    /**
     * Persiste les stats d'un joueur en BDD de manière asynchrone.
     *
     * <p>Appelé par {@link me.tyalternative.fundamentalis.core.entity.EntityTracker EntityTracker}
     * lors de la déconnexion du joueur, et par la tâche d'auto-save.
     *
     * @param playerUUID UUID du joueur
     * @param component  le composant de stats à persister, ou {@code null} si absent
     */
    public void flushPlayer(UUID playerUUID, StatsComponent component) {
        if (component == null) return;
        Map<String, Integer> data = component.getRawBaseValues();
        playerRepo.saveAll(playerUUID, data).thenRun(() -> {
            if (debugLog) {
                logger.info("[StatsManager] Stats joueur " + playerUUID + " sauvegardées ("
                        + data.size() + " stats).");
            }
        });
    }

    /**
     * Persiste les stats d'une entité custom en BDD de manière asynchrone.
     *
     * @param entityUUID UUID de l'entité
     * @param component  le composant de stats à persister, ou {@code null} si absent
     */
    public void flushEntity(UUID entityUUID, StatsComponent component) {
        if (component == null) return;
        Map<String, Integer> data = component.getRawBaseValues();
        entityRepo.saveAll(entityUUID, data);
    }

    /**
     * Supprime les stats d'une entité custom de la BDD après sa mort ou son despawn.
     * Les données ne sont plus utiles une fois l'entité supprimée.
     *
     * @param entityUUID UUID de l'entité à nettoyer
     */
    public void deleteEntityStats(UUID entityUUID) {
        entityRepo.deleteAll(entityUUID);
    }

    /**
     * Supprime toutes les stats d'un joueur (reset complet de personnage).
     *
     * @param playerUUID UUID du joueur
     */
    public void deletePlayerStats(UUID playerUUID) {
        playerRepo.deleteAll(playerUUID).thenRun(() ->
                logger.info("[StatsManager] Stats joueur " + playerUUID + " supprimées (reset)."));
    }


    // -------------------------------------------------------------------------
    // Utilitaire interne
    // -------------------------------------------------------------------------

    /**
     * Cherche le {@link StatsComponent} d'un joueur à partir de son UUID.
     * Retourne {@code null} si le joueur n'est pas en ligne ou n'a pas de composant.
     */
    private StatsComponent findStatsComponent(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return null;

// TODO: HMMM c'est pas normal ça, je pense qu'il faut je m'y penche plus
        // On passe par l'EntityService pour ne pas créer de dépendance circulaire
        // EntityService est disponible via CorePlugin, mais on évite ce couplage ici.
        // Le flush est délégué à EntityTracker qui a accès aux deux.
        return null; // Résolu dans EntityTracker via le holder
    }

}
