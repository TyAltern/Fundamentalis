package me.tyalternative.fundamentalis.core;


import me.tyalternative.fundamentalis.api.FundamentalisProvider;
import me.tyalternative.fundamentalis.core.api.CoreAPIImpl;
import me.tyalternative.fundamentalis.core.command.StatsCommand;
import me.tyalternative.fundamentalis.core.config.CoreConfig;
import me.tyalternative.fundamentalis.core.database.DatabaseManager;
import me.tyalternative.fundamentalis.core.database.repository.EntityStatsRepository;
import me.tyalternative.fundamentalis.core.database.repository.PlayerStatsRepository;
import me.tyalternative.fundamentalis.core.entity.EntityService;
import me.tyalternative.fundamentalis.core.entity.EntityTracker;
import me.tyalternative.fundamentalis.core.stats.StatTypeRegistryImpl;
import me.tyalternative.fundamentalis.core.stats.StatsManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/**
 * Point d'entrée de {@code fundamentalis-core}.
 *
 * <p>Orchestre l'initialisation de tous les sous-systèmes dans un ordre strict.
 * Chaque étape dépend des précédentes — une erreur dans une étape provoque
 * l'auto-désactivation du plugin pour éviter un état incohérent.
 *
 * <h2>Ordre d'initialisation ({@code onEnable})</h2>
 * <ol>
 *   <li>Chargement de {@link CoreConfig} (lecture du {@code config.yml}).</li>
 *   <li>Connexion MySQL via {@link DatabaseManager} et exécution des migrations DDL.</li>
 *   <li>Création des repositories ({@link PlayerStatsRepository}, {@link EntityStatsRepository}).</li>
 *   <li>Enregistrement des {@link me.tyalternative.fundamentalis.api.stats.StatType StatType}
 *       intégrés dans {@link StatTypeRegistryImpl}.</li>
 *   <li>Création de {@link EntityService}.</li>
 *   <li>Création de {@link StatsManager} et démarrage de l'auto-save.</li>
 *   <li>Création et enregistrement de {@link EntityTracker} comme listener Bukkit.</li>
 *   <li>Enregistrement de l'API via {@link FundamentalisProvider}
 *       — à partir de cette étape, {@code FundamentalisAPI.get()} est disponible.</li>
 * </ol>
 *
 * <h2>Ordre d'arrêt ({@code onDisable})</h2>
 * <ol>
 *   <li>Désenregistrement de l'API (les modules tiers ne peuvent plus l'appeler).</li>
 *   <li>Flush final de toutes les entités via {@link EntityTracker#onServerShutdown()}.</li>
 *   <li>Arrêt de l'auto-save via {@link StatsManager#shutdown()}.</li>
 *   <li>Fermeture du pool MySQL via {@link DatabaseManager#close()}.</li>
 * </ol>
 */
public final class CorePlugin extends JavaPlugin {

    // -------------------------------------------------------------------------
    // Sous-systèmes — accessibles en lecture par les autres classes du Core
    // -------------------------------------------------------------------------

    private CoreConfig              coreConfig;
    private DatabaseManager         databaseManager;
    private PlayerStatsRepository   playerStatsRepository;
    private EntityStatsRepository   entityStatsRepository;
    private StatTypeRegistryImpl    statTypeRegistry;
    private EntityService           entityService;
    private StatsManager            statsManager;
    private EntityTracker           entityTracker;

    // -------------------------------------------------------------------------
    // onEnable
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║   Fundamentalis Core v1 — Démarrage  ║");
        getLogger().info("╚══════════════════════════════════════╝");

        // Étape 1 — Configuration
        if (!initConfig()) return;

        // Étape 2 — Base de données
        if (!initDatabase()) return;

        // Étape 3 — Repositories
        initRepositories();

        // Étape 4 — Registre des StatType
        initStatTypeRegistry();

        // Étape 5 — EntityService
        initEntityService();

        // Étape 6 — StatsManager
        initStatsManager();

        // Étape 7 — EntityTracker (listeners Bukkit)
        initEntityTracker();

        // Étape 8 — Commandes admin
        initCommands();

        // Étape 9 — Enregistrement de l'API publique
        // À faire EN DERNIER : les modules qui dépendent de nous peuvent maintenant
        // appeler FundamentalisAPI.get() dans leurs propres onEnable()
        registerAPI();

        long elapsed = System.currentTimeMillis() - startTime;
        getLogger().info("Fundamentalis Core démarré en " + elapsed + "ms.");
        getLogger().info("API version : " + new CoreAPIImpl(entityService, statTypeRegistry).getVersion());
    }

    // -------------------------------------------------------------------------
    // onDisable
    // -------------------------------------------------------------------------

    @Override
    public void onDisable() {
        getLogger().info("[Core] Arrêt en cours…");

        // Étape 1 — Désenregistrement de l'API
        // Les modules tiers ne peuvent plus appeler FundamentalisAPI.get()
        try {
            FundamentalisProvider.unregister();
        } catch (Exception e) {
            getLogger().warning("[Core] Erreur lors du désenregistrement de l'API : " + e.getMessage());
        }

        // Étape 2 — Flush final de toutes les entités
        if (entityTracker != null) {
            entityTracker.onServerShutdown();
        }

        // Étape 3 — Arrêt de l'auto-save
        if (statsManager != null) {
            statsManager.shutdown();
        }

        // Étape 4 — Fermeture du pool MySQL (en dernier, après tous les flushes)
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("[Core] Arrêt terminé.");
    }

    // -------------------------------------------------------------------------
    // Étapes d'initialisation
    // -------------------------------------------------------------------------

    /**
     * Charge le {@code config.yml} et valide les valeurs.
     *
     * @return {@code true} si le chargement s'est bien passé
     */
    private boolean initConfig() {
        try {
            coreConfig = new CoreConfig(this);
            coreConfig.load();
            getLogger().info("[1/9] Configuration chargée.");
            return true;
        } catch (Exception e) {
            getLogger().severe("[1/9] Erreur lors du chargement de la configuration : " + e.getMessage());
            disable();
            return false;
        }
    }

    /**
     * Ouvre le pool HikariCP et exécute les migrations DDL.
     *
     * <p>En cas d'échec, le plugin se désactive — il ne peut pas fonctionner
     * sans base de données.
     *
     * @return {@code true} si la connexion est établie
     */
    private boolean initDatabase() {
        try {
            databaseManager = new DatabaseManager(coreConfig, getLogger());
            databaseManager.connect();
            getLogger().info("[2/9] Base de données connectée.");
            return true;
        } catch (SQLException e) {
            getLogger().severe("[2/9] Impossible de se connecter à MySQL : " + e.getMessage());
            getLogger().severe("Vérifiez les credentials dans config.yml et que le serveur MySQL est accessible.");
            disable();
            return false;
        }
    }

    /**
     * Instancie les repositories de persistance.
     */
    private void initRepositories() {
        playerStatsRepository = new PlayerStatsRepository(databaseManager, getLogger());
        entityStatsRepository = new EntityStatsRepository(databaseManager, getLogger());
        getLogger().info("[3/9] Repositories initialisés.");
    }

    /**
     * Crée le registre des StatType et enregistre les six stats intégrées.
     */
    private void initStatTypeRegistry() {
        statTypeRegistry = new StatTypeRegistryImpl(getLogger());
        statTypeRegistry.registerBuiltins();
        getLogger().info("[4/9] StatTypeRegistry initialisé ("
                + statTypeRegistry.getAll().size() + " stats enregistrées).");
    }

    /**
     * Crée l'EntityService (registre en mémoire des holders).
     */
    private void initEntityService() {
        entityService = new EntityService(getLogger());
        getLogger().info("[5/9] EntityService initialisé.");
    }

    /**
     * Crée le StatsManager et démarre l'auto-save périodique.
     */
    private void initStatsManager() {
        statsManager = new StatsManager(
                this,
                statTypeRegistry,
                playerStatsRepository,
                entityStatsRepository,
                getLogger(),
                coreConfig.isDebugLog(),
                coreConfig.getAutoSaveInterval()
        );
        statsManager.start();
        getLogger().info("[6/9] StatsManager démarré (auto-save : "
                + coreConfig.getAutoSaveInterval() + " ticks).");
    }

    /**
     * Crée l'EntityTracker et l'enregistre comme listener Bukkit.
     */
    private void initEntityTracker() {
        entityTracker = new EntityTracker(this, entityService, statsManager, getLogger());
        getServer().getPluginManager().registerEvents(entityTracker, this);
        getLogger().info("[7/9] EntityTracker enregistré.");
    }

    /**
     * Enregistre les commandes admin du Core.
     *
     * <p>La commande {@code stats} doit être déclarée dans le {@code plugin.yml}
     * pour que {@code getCommand("stats")} ne retourne pas {@code null}.
     */
    private void initCommands() {
        StatsCommand statsCommand = new StatsCommand(entityService, statTypeRegistry);
        var command = getCommand("stats");
        if (command == null) {
            getLogger().severe("[8/9] Commande 'stats' introuvable dans plugin.yml ! "
                    + "La commande /stats ne fonctionnera pas.");
            return;
        }
        command.setExecutor(statsCommand);
        command.setTabCompleter(statsCommand);
        getLogger().info("[8/9] Commande /stats enregistrée.");
    }

    /**
     * Expose l'API publique via le Service Locator.
     *
     * <p>Doit être appelé en dernier pour que tous les services soient
     * pleinement initialisés avant que les modules tiers puissent y accéder.
     */
    private void registerAPI() {
        CoreAPIImpl api = new CoreAPIImpl(entityService, statTypeRegistry);
        FundamentalisProvider.register(api);
        getLogger().info("[8/9] API publique enregistrée — FundamentalisAPI.get() disponible.");
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    /**
     * Désactive le plugin proprement en loguant un message clair.
     * Appelé uniquement en cas d'erreur critique au démarrage.
     */
    private void disable() {
        getLogger().severe("Fundamentalis Core ne peut pas démarrer. Désactivation du plugin.");
        getServer().getPluginManager().disablePlugin(this);
    }

    // -------------------------------------------------------------------------
    // Accesseurs internes (pour les tests ou les sous-modules du Core)
    // -------------------------------------------------------------------------

    /** @return la configuration chargée du plugin */
    public CoreConfig getCoreConfig()                       { return coreConfig; }

    /** @return le gestionnaire de connexions MySQL */
    public DatabaseManager getDatabaseManager()             { return databaseManager; }

    /** @return le registre des StatType */
    public StatTypeRegistryImpl getStatTypeRegistry()       { return statTypeRegistry; }

    /** @return le service d'accès aux ComponentHolder */
    public EntityService getEntityService()                 { return entityService; }

    /** @return le manager du cycle de vie des StatsComponent */
    public StatsManager getStatsManager()                   { return statsManager; }
}
