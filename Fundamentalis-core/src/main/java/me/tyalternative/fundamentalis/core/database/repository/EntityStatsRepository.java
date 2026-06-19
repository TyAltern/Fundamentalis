package me.tyalternative.fundamentalis.core.database.repository;

import me.tyalternative.fundamentalis.core.database.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Accès aux données de stats des entités custom dans la table {@code entity_stats}.
 *
 * <p>Contrairement aux stats joueurs, les stats d'entités sont <strong>volatiles</strong> :
 * elles sont supprimées dès que l'entité est désenregistrée (mort, despawn).
 * Cette table sert uniquement à persister les stats des mobs pendant leur durée de
 * vie, afin de les retrouver après un redémarrage partiel (ex : crash suivi d'un reload).
 *
 * <p>Si un mob n'a pas de ligne en base, ses stats sont générées dynamiquement par
 * la configuration MythicMobs et n'ont pas besoin d'être chargées depuis MySQL.
 *
 * <p>Toutes les méthodes retournent un {@link CompletableFuture} pour ne jamais
 * bloquer le thread principal Bukkit.
 *
 * @see PlayerStatsRepository pour le pattern de chargement async
 */
public class EntityStatsRepository {

    // -------------------------------------------------------------------------
    // Requêtes SQL préparées
    // -------------------------------------------------------------------------

    private static final String SQL_LOAD =
            "SELECT stat_id, value FROM entity_stats WHERE entity_uuid = ?";

    private static final String SQL_UPSERT =
            "INSERT INTO entity_stats (entity_uuid, stat_id, value) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE value = VALUES(value)";

    /** Nettoyage complet à la mort ou au despawn de l'entité. */
    private static final String SQL_DELETE_ALL =
            "DELETE FROM entity_stats WHERE entity_uuid = ?";

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final DatabaseManager db;
    private final Logger          logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param db     gestionnaire de connexions
     * @param logger logger du plugin pour les erreurs SQL
     */
    public EntityStatsRepository(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /**
     * Charge les stats d'une entité depuis la base de données.
     *
     * <p>Retourne une Map vide si l'entité n'a aucune ligne sauvegardée.
     * Dans ce cas, le {@link StatsComponent StatsComponent}
     * utilisera les valeurs par défaut définies par la configuration du mob.
     *
     * @param entityUUID UUID de l'entité Bukkit
     * @return future résolu avec une Map {@code statId → valeur}, jamais null
     */
    public CompletableFuture<Map<String, Integer>> load(UUID entityUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> stats = new HashMap<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_LOAD)) {

                stmt.setString(1, entityUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        stats.put(rs.getString("stat_id"), rs.getInt("value"));
                    }
                }
            } catch (SQLException e) {
                logger.severe("[EntityStatsRepository] Erreur lors du chargement des stats de l'entité "
                        + entityUUID + " : " + e.getMessage());
            }
            return stats;
        });
    }

    // -------------------------------------------------------------------------
    // Écriture
    // -------------------------------------------------------------------------

    /**
     * Persiste toutes les stats d'une entité en une seule transaction batch.
     *
     * @param entityUUID UUID de l'entité Bukkit
     * @param stats      Map {@code statId → valeur} à persister
     * @return future résolu quand la sauvegarde est terminée
     */
    public CompletableFuture<Void> saveAll(UUID entityUUID, Map<String, Integer> stats) {
        if (stats.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String uuidStr = entityUUID.toString();
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_UPSERT)) {

                conn.setAutoCommit(false);

                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    stmt.setString(1, uuidStr);
                    stmt.setString(2, entry.getKey());
                    stmt.setInt(3, entry.getValue());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                logger.severe("[EntityStatsRepository] Erreur lors de la sauvegarde des stats de l'entité "
                        + entityUUID + " : " + e.getMessage());
            }
        });
    }

    /**
     * Supprime toutes les stats d'une entité.
     *
     * <p>Doit être appelé lors de la mort ou du despawn d'une entité pour
     * éviter l'accumulation de lignes orphelines dans la table.
     *
     * @param entityUUID UUID de l'entité à supprimer
     * @return future résolu quand la suppression est terminée
     */
    public CompletableFuture<Void> deleteAll(UUID entityUUID) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_ALL)) {

                stmt.setString(1, entityUUID.toString());
                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.severe("[EntityStatsRepository] Erreur lors de la suppression des stats de l'entité "
                        + entityUUID + " : " + e.getMessage());
            }
        });
    }
}
