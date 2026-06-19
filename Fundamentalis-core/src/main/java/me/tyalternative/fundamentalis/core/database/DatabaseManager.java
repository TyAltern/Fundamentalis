package me.tyalternative.fundamentalis.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.tyalternative.fundamentalis.core.config.CoreConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Gère le pool de connexions MySQL via HikariCP et les migrations du schéma.
 *
 * <p>Cycle de vie :
 * <ol>
 *   <li>{@link #connect()} — ouvre le pool et exécute les migrations</li>
 *   <li>{@link #getConnection()} — utilisé par les repositories pour chaque requête</li>
 *   <li>{@link #close()} — ferme proprement le pool à l'arrêt du plugin</li>
 * </ol>
 *
 * <p>Les connexions récupérées via {@link #getConnection()} doivent impérativement
 * être fermées dans un bloc {@code try-with-resources} pour être retournées au pool :
 * <pre>{@code
 * try (Connection conn = databaseManager.getConnection()) {
 *     // utiliser conn...
 * }
 * }</pre>
 *
 * <p>Toutes les opérations SQL coûteuses (INSERT, UPDATE) sont exécutées de manière
 * asynchrone par les repositories. Cette classe ne fait que fournir les connexions.
 */
public class DatabaseManager {

    // -------------------------------------------------------------------------
    // Schéma SQL — migrations exécutées au démarrage si les tables sont absentes
    // -------------------------------------------------------------------------

    /**
     * Table des stats joueurs.
     * Clé primaire composite : (player_uuid, stat_id) — un joueur peut avoir
     * plusieurs stats, chacune stockée dans une ligne distincte.
     */
    private static final String SQL_CREATE_PLAYER_STATS = """
            CREATE TABLE IF NOT EXISTS player_stats (
                player_uuid  CHAR(36)    NOT NULL,
                stat_id      VARCHAR(64) NOT NULL,
                value        INT         NOT NULL,
                updated_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, stat_id),
                INDEX idx_player (player_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

    /**
     * Table des stats entités custom (mobs, boss...).
     * Données volatiles : elles sont supprimées quand l'entité est désenregistrée.
     * L'index sur entity_uuid accélère le chargement de toutes les stats d'un mob.
     */
    private static final String SQL_CREATE_ENTITY_STATS = """
            CREATE TABLE IF NOT EXISTS entity_stats (
                entity_uuid  CHAR(36)    NOT NULL,
                stat_id      VARCHAR(64) NOT NULL,
                value        INT         NOT NULL,
                PRIMARY KEY (entity_uuid, stat_id),
                INDEX idx_entity (entity_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final CoreConfig       config;
    private final Logger           logger;
    private       HikariDataSource dataSource;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param config configuration du plugin (credentials MySQL, taille du pool…)
     * @param logger logger du plugin pour les messages de démarrage
     */
    public DatabaseManager(CoreConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    /**
     * Ouvre le pool de connexions HikariCP et exécute les migrations SQL.
     *
     * <p>Lance une {@link SQLException} si la connexion échoue — le plugin
     * doit alors s'auto-désactiver pour éviter un état incohérent.
     *
     * @throws SQLException si la connexion à MySQL est impossible ou si une
     *                      migration échoue
     */
    public void connect() throws SQLException {
        logger.info("[Database] Connexion à MySQL — " + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName());

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.buildJdbcUrl());
        hikariConfig.setUsername(config.getDbUsername());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setPoolName("Fundamentalis-Pool");

        // Optimisations recommandées pour MySQL avec HikariCP
        hikariConfig.addDataSourceProperty("cachePrepStmts",          "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize",       "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit",   "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts",      "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState",     "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata",  "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits",     "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats",       "false");

        dataSource = new HikariDataSource(hikariConfig);

        // Test de connexion + migrations
        try (Connection conn = dataSource.getConnection()) {
            logger.info("[Database] Connexion établie. Exécution des migrations…");
            runMigrations(conn);
            logger.info("[Database] Migrations terminées.");
        }

    }

    /**
     * Exécute toutes les migrations DDL dans une seule connexion.
     * Utilise {@code CREATE TABLE IF NOT EXISTS} pour que les migrations soient
     * idempotentes — elles peuvent être relancées sans danger.
     *
     * @param conn connexion active sur laquelle exécuter les DDL
     * @throws SQLException si une instruction DDL échoue
     */
    private void runMigrations(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_PLAYER_STATS);
            stmt.execute(SQL_CREATE_ENTITY_STATS);
        }
    }

    /**
     * Retourne une connexion depuis le pool.
     *
     * <p><strong>Important :</strong> fermer la connexion dans un
     * {@code try-with-resources} pour la retourner au pool immédiatement.
     *
     * @return une connexion active
     * @throws SQLException            si le pool est épuisé ou si la connexion est perdue
     * @throws IllegalStateException   si {@link #connect()} n'a pas encore été appelé
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager non initialisé — appelez connect() en premier.");
        }
        return dataSource.getConnection();
    }

    /**
     * Ferme proprement le pool de connexions.
     * Doit être appelé dans {@code CorePlugin#onDisable()}.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[Database] Pool de connexions fermé.");
        }
    }

    /**
     * @return {@code true} si le pool est ouvert et opérationnel
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
