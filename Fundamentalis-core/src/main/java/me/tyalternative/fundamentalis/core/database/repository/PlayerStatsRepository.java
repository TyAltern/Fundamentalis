package me.tyalternative.fundamentalis.core.database.repository;

import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.core.database.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Accès aux données de stats des joueurs dans la table {@code player_stats}.
 *
 * <p>Toutes les méthodes qui lisent ou écrivent en base retournent un
 * {@link CompletableFuture} pour ne jamais bloquer le thread principal Bukkit.
 * Les appelants doivent traiter le résultat via {@code .thenAccept()} ou
 * {@code .thenRun()} et, si une action Bukkit est nécessaire, la réexpédier
 * sur le thread principal avec {@code Bukkit.getScheduler().runTask()}.
 *
 * <p>Exemple de chargement depuis un listener :
 * <pre>{@code
 * repository.load(player.getUniqueId()).thenAccept(stats -> {
 *     // On est sur un thread async ici
 *     Bukkit.getScheduler().runTask(plugin, () -> {
 *         // Retour sur le thread principal pour manipuler l'entité
 *         component.applyLoadedStats(stats);
 *     });
 * });
 * }</pre>
 */
public class PlayerStatsRepository {

    // -------------------------------------------------------------------------
    // Requêtes SQL préparées
    // -------------------------------------------------------------------------

    /** Charge toutes les stats d'un joueur en une seule requête. */
    private static final String SQL_LOAD =
            "SELECT stat_id, value FROM player_stats WHERE player_uuid = ?";

    /**
     * INSERT ou UPDATE atomique.
     * Si la ligne (player_uuid, stat_id) existe déjà, met à jour la valeur.
     * Idempotent — peut être appelé plusieurs fois sans risque de doublon.
     */
    private static final String SQL_UPSERT =
            "INSERT INTO player_stats (player_uuid, stat_id, value) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE value = VALUES(value)";

    /** Supprime toutes les stats d'un joueur (wipe, reset de personnage). */
    private static final String SQL_DELETE_ALL =
            "DELETE FROM player_stats WHERE player_uuid = ?";

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final DatabaseManager db;
    private final Logger          logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param db     gestionnaire de connexions, doit être connecté avant usage
     * @param logger logger du plugin pour les erreurs SQL
     */
    public PlayerStatsRepository(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /**
     * Charge toutes les stats d'un joueur depuis la base de données.
     *
     * <p>Retourne une Map vide si le joueur n'a aucune ligne en base (première
     * connexion). Le {@link StatsComponent StatsComponent}
     * appliquera alors les valeurs par défaut de chaque {@link StatType}.
     *
     * @param playerUUID UUID du joueur
     * @return future résolu avec une Map {@code statId → valeur}, jamais null
     */
    public CompletableFuture<Map<String, Integer>> load(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> stats = new HashMap<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_LOAD)) {

                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        stats.put(rs.getString("stat_id"), rs.getInt("value"));
                    }
                }
            } catch (SQLException e) {
                logger.severe("[PlayerStatsRepository] Erreur lors du chargement des stats de "
                        + playerUUID + " : " + e.getMessage());
            }
            return stats;
        });
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    /**
     * Persiste toutes les stats d'un joueur en une seule transaction batch.
     *
     * <p>Utilise un {@code executeBatch()} pour envoyer toutes les lignes en un
     * seul aller-retour réseau, ce qui est bien plus efficace qu'un upsert par stat.
     *
     * @param playerUUID UUID du joueur
     * @param stats      Map {@code statId → valeur} à persister
     * @return future résolu à {@code null} quand la sauvegarde est terminée
     */
    public CompletableFuture<Void> saveAll(UUID playerUUID, Map<String, Integer> stats) {
        if (stats.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String uuidStr = playerUUID.toString();
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_UPSERT)) {

                conn.setAutoCommit(false); // Transaction unique pour tout le batch

                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    stmt.setString(1, uuidStr);
                    stmt.setString(2, entry.getKey());
                    stmt.setInt(3, entry.getValue());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                logger.severe("[PlayerStatsRepository] Erreur lors de la sauvegarde des stats de "
                        + playerUUID + " : " + e.getMessage());
            }
        });
    }

    /**
     * Persiste une seule stat d'un joueur.
     *
     * <p>À utiliser pour les modifications ponctuelles (commande admin, gain de niveau).
     * Pour les sauvegardes régulières, préférer {@link #saveAll(UUID, Map)}.
     *
     * @param playerUUID UUID du joueur
     * @param statId     identifiant de la stat (ex : {@code "force"})
     * @param value      nouvelle valeur
     * @return future résolu quand l'écriture est terminée
     */
    public CompletableFuture<Void> saveSingle(UUID playerUUID, String statId, int value) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_UPSERT)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, statId);
                stmt.setInt(3, value);
                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.severe("[PlayerStatsRepository] Erreur lors de la sauvegarde de la stat '"
                        + statId + "' pour " + playerUUID + " : " + e.getMessage());
            }
        });
    }

    /**
     * Supprime toutes les stats d'un joueur (reset complet de personnage).
     *
     * <p>Après cet appel, le prochain chargement retournera une Map vide et
     * les valeurs par défaut seront appliquées.
     *
     * @param playerUUID UUID du joueur à réinitialiser
     * @return future résolu quand la suppression est terminée
     */
    public CompletableFuture<Void> deleteAll(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_ALL)) {

                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.severe("[PlayerStatsRepository] Erreur lors du reset des stats de "
                        + playerUUID + " : " + e.getMessage());
            }
        });
    }
}
