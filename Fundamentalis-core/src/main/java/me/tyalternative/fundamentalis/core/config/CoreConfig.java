package me.tyalternative.fundamentalis.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lit et valide le fichier {@code config.yml} au démarrage du plugin.
 *
 * <p>Toutes les valeurs sont chargées une seule fois dans {@link #load()} puis
 * exposées via des getters. Les autres classes ne lisent jamais directement
 * la {@link FileConfiguration} de Bukkit — elles passent par cette classe.
 * Cela centralise la validation et facilite les tests unitaires.
 *
 * <p>En cas de valeur manquante ou invalide, un avertissement est loggué et la
 * valeur par défaut définie dans {@code config.yml} est appliquée automatiquement
 * par Bukkit via {@code saveDefaultConfig()}.
 */
public class CoreConfig {

    // -------------------------------------------------------------------------
    // Valeurs par défaut (miroir de config.yml, utilisées comme garde-fous)
    // -------------------------------------------------------------------------

    private static final String  DEFAULT_HOST               = "localhost";
    private static final int     DEFAULT_PORT               = 3306;
    private static final String  DEFAULT_DB_NAME            = "fundamentalis";
    private static final String  DEFAULT_USERNAME           = "racine";
    private static final String  DEFAULT_PASSWORD           = "Ou99pi22&*";
    private static final int     DEFAULT_POOL_SIZE          = 10;
    private static final int     DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final boolean DEFAULT_DEBUG_LOG          = false;
    private static final int     DEFAULT_AUTO_SAVE_INTERVAL = 6000;

    // -------------------------------------------------------------------------
    // Champs chargés depuis config.yml
    // -------------------------------------------------------------------------

    private final JavaPlugin plugin;

    private String  dbHost;
    private int     dbPort;
    private String  dbName;
    private String  dbUsername;
    private String  dbPassword;
    private int     poolSize;
    private int     connectionTimeout;
    private boolean debugLog;
    private int     autoSaveInterval;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param plugin l'instance du plugin, utilisée pour accéder à la config Bukkit
     */
    public CoreConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Chargement
    // -------------------------------------------------------------------------

    /**
     * Copie {@code config.yml} depuis le jar si absent, puis charge toutes les
     * valeurs en mémoire.
     *
     * <p>Doit être appelé une seule fois dans {@code FundamentalisCorePlugin#onEnable()},
     * avant toute autre initialisation.
     */
    public void load() {
        // Copie le config.yml embarqué dans le jar si le fichier n'existe pas encore
        plugin.saveDefaultConfig();
        FileConfiguration cfg = plugin.getConfig();

        dbHost             = cfg.getString("database.host",               DEFAULT_HOST);
        dbPort             = cfg.getInt("database.port",                  DEFAULT_PORT);
        dbName             = cfg.getString("database.name",               DEFAULT_DB_NAME);
        dbUsername         = cfg.getString("database.username",           DEFAULT_USERNAME);
        dbPassword         = cfg.getString("database.password",           DEFAULT_PASSWORD);
        poolSize           = cfg.getInt("database.pool-size",             DEFAULT_POOL_SIZE);
        connectionTimeout  = cfg.getInt("database.connection-timeout",    DEFAULT_CONNECTION_TIMEOUT);
        debugLog           = cfg.getBoolean("stats.debug-log",            DEFAULT_DEBUG_LOG);
        autoSaveInterval   = cfg.getInt("stats.auto-save-interval",       DEFAULT_AUTO_SAVE_INTERVAL);

        validate();
    }

    /**
     * Valide les valeurs chargées et applique les garde-fous nécessaires.
     * Logge un avertissement pour chaque valeur corrigée.
     */
    private void validate() {
        if (dbHost == null || dbHost.isBlank()) {
            plugin.getLogger().warning("[Config] database.host invalide : utilisation de la valeur par défaut : " + DEFAULT_HOST);
            dbHost = DEFAULT_HOST;
        }
        if (dbPort < 1 || dbPort > 65535) {
            plugin.getLogger().warning("[Config] database.port invalide (" + dbPort + ") : utilisation de la valeur par défaut : " + DEFAULT_PORT);
            dbPort = DEFAULT_PORT;
        }
        if (poolSize < 1) {
            plugin.getLogger().warning("[Config] database.pool-size invalide (" + poolSize + ") : utilisation de la valeur par défaut : " + DEFAULT_POOL_SIZE);
            poolSize = DEFAULT_POOL_SIZE;
        }
        if (connectionTimeout < 1000) {
            plugin.getLogger().warning("[Config] database.connection-timeout trop faible (" + connectionTimeout + "ms) : minimum 1000ms appliqué.");
            connectionTimeout = 1000;
        }
        if (autoSaveInterval < 200) {
            plugin.getLogger().warning("[Config] stats.auto-save-interval trop faible (" + autoSaveInterval + " ticks) : minimum 200 ticks appliqué.");
            autoSaveInterval = 200;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return hôte du serveur MySQL (ex : {@code "localhost"}) */
    public String  getDbHost()            { return dbHost; }

    /** @return port MySQL (défaut : {@code 3306}) */
    public int     getDbPort()            { return dbPort; }

    /** @return nom de la base de données */
    public String  getDbName()            { return dbName; }

    /** @return nom d'utilisateur MySQL */
    public String  getDbUsername()        { return dbUsername; }

    /** @return mot de passe MySQL */
    public String  getDbPassword()        { return dbPassword; }

    /** @return taille du pool HikariCP */
    public int     getPoolSize()          { return poolSize; }

    /** @return délai max en ms pour obtenir une connexion */
    public int     getConnectionTimeout() { return connectionTimeout; }

    /** @return {@code true} si le mode debug stats est activé */
    public boolean isDebugLog()           { return debugLog; }

    /**
     * @return intervalle en ticks entre chaque flush automatique des stats en BDD
     *         (6000 ticks = 5 minutes)
     */
    public int     getAutoSaveInterval()  { return autoSaveInterval; }

    /**
     * Construit l'URL JDBC complète à partir des paramètres de connexion.
     *
     * @return URL JDBC au format {@code jdbc:mysql://host:port/dbName?...}
     */
    public String buildJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8"
                + "&autoReconnect=true";
    }
}
