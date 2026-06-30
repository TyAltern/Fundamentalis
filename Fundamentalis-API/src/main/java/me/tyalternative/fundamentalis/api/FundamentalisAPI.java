package me.tyalternative.fundamentalis.api;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry;

/**
 * Central Service Locator for the Fundamentalis API.
 *
 * <p>This class is the <strong>single entry point</strong> for any module or
 * third-party plugin that needs to interact with Fundamentalis. It provides
 * access to every service exposed by the API without coupling the caller to
 * any concrete implementation class.
 *
 * <h2>Access pattern</h2>
 * <pre>{@code
 * // Récupérer un service — à faire dans un listener ou une méthode, jamais dans un champ statique
 * IEntityService entities = FundamentalisAPI.get().getEntityService();
 * IStatTypeRegistry stats = FundamentalisAPI.get().getStatTypeRegistry();
 * }</pre>
 *
 * <h2>Availability</h2>
 * The API is available after {@code fundamentalis-core} has completed its
 * {@code onEnable}. Calling {@link #get()} before that point will throw an
 * {@link IllegalStateException}. Always declare {@code fundamentalis-core} as
 * a hard dependency in your {@code plugin.yml}:
 * <pre>{@code
 * depend: [fundamentalis-core]
 * }</pre>
 *
 * <h2>For implementors</h2>
 * The Core registers its implementation via {@link FundamentalisProvider}.
 * No other plugin should call {@link FundamentalisProvider#register}.
 *
 * @see FundamentalisProvider
 * @see IEntityService
 * @see IStatTypeRegistry
 */
public abstract class FundamentalisAPI {

    // Instance unique — enregistrée par FundamentalisProvider au démarrage du Core
    private static FundamentalisAPI instance;

    // -------------------------------------------------------------------------
    // Service Locator
    // -------------------------------------------------------------------------

    /**
     * Returns the active {@link FundamentalisAPI} implementation.
     *
     * @return the API instance
     * @throws IllegalStateException if {@code fundamentalis-core} has not finished loading yet
     */
    public static FundamentalisAPI get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "FundamentalisAPI is not available yet. "
                            + "Ensure 'fundamentalis-core' is listed as a hard dependency "
                            + "in your plugin.yml and has finished its onEnable.");
        }
        return instance;
    }

    /**
     * Returns {@code true} if the API has been registered and is ready to use.
     *
     * <p>Prefer declaring a hard dependency over polling this method.
     * Use it only in optional-dependency scenarios.
     *
     * @return {@code true} if {@link #get()} can be called safely
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    // -------------------------------------------------------------------------
    // Package-private setter — utilisé uniquement par FundamentalisProvider
    // -------------------------------------------------------------------------

    static void setInstance(FundamentalisAPI api) {
        // On interdit le remplacement accidentel d'une instance déjà enregistrée
        if (instance != null && api != null) {
            throw new IllegalStateException(
                    "FundamentalisAPI is already registered. "
                            + "Only fundamentalis-core should call FundamentalisProvider#register.");
        }
        instance = api;
    }

    // -------------------------------------------------------------------------
    // Registrations tardives — remplies par les modules après le Core
    // -------------------------------------------------------------------------

    /**
     * Clé du composant de statut, enregistrée par {@code fundamentalis-status}
     * dans son {@code onEnable()} via {@link #registerStatusComponentKey}.
     * Null tant que le module Status n'est pas chargé.
     */
    public static ComponentKey<IStatusComponent> statusComponentKey;

    /**
     * Registre des types d'effets de statut, enregistré par {@code fundamentalis-status}.
     * Null tant que le module Status n'est pas chargé.
     */
    public static IStatusEffectRegistry statusEffectRegistry;

    /**
     * Enregistre la clé du composant de statut et le registre des effets.
     * Appelé par {@code StatusPlugin#onEnable()}.
     *
     * @param key      la clé typée du composant {@code IStatusComponent}
     * @param registry le registre des {@code StatusEffectType}
     */
    public static void registerStatusServices(ComponentKey<IStatusComponent> key, IStatusEffectRegistry registry) {
        statusComponentKey = key;
        statusEffectRegistry = registry;
    }

    /**
     * @return la clé du composant de statut, ou {@code null} si
     *         {@code fundamentalis-status} n'est pas chargé
     */
    public static ComponentKey<IStatusComponent> getStoredStatusComponentKey() {
        return statusComponentKey;
    }

    /**
     * @return le registre des effets de statut, ou {@code null} si
     *         {@code fundamentalis-status} n'est pas chargé
     */
    public static IStatusEffectRegistry getStoredStatusEffectRegistry() {
        return statusEffectRegistry;
    }

    // -------------------------------------------------------------------------
    // Services — à implémenter par le Core
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link IEntityService}, used to retrieve the
     * {@link ComponentHolder ComponentHolder}
     * of any tracked living entity.
     *
     * <pre>{@code
     * // Exemple dans un listener de combat :
     * IEntityService entities = FundamentalisAPI.get().getEntityService();
     * entities.get(attacker).ifPresent(holder -> {
     *     double force = holder.require(IStatsComponent.KEY).getFinal(StatType.FORCE);
     * });
     * }</pre>
     *
     * @return the entity service
     */
    public abstract IEntityService getEntityService();

    /**
     * Returns the {@link IStatTypeRegistry}, used to register custom stats
     * or look up existing ones by id.
     *
     * <pre>{@code
     * // Enregistrer une stat custom depuis votre plugin :
     * StatType MANA = StatType.of("mana", 100, 0, 9999);
     * FundamentalisAPI.get().getStatTypeRegistry().register(MANA);
     * }</pre>
     *
     * @return the stat type registry
     */
    public abstract IStatTypeRegistry getStatTypeRegistry();

    /**
     * Returns the typed {@link ComponentKey} used to access a
     * {@link IStatsComponent} on any {@link ComponentHolder}.
     *
     * <p>Modules must never import the Core's concrete {@code StatsComponent}
     * class to read this key — doing so would create a hard dependency on
     * {@code fundamentalis-core}'s implementation package. This method is the
     * only sanctioned way to obtain the key from outside the Core.
     *
     * <pre>{@code
     * // Dans fundamentalis-combat, pour lire les stats d'une entité :
     * ComponentKey<IStatsComponent> key = FundamentalisAPI.get().getStatsComponentKey();
     * Optional<IStatsComponent> stats = holder.get(key);
     * }</pre>
     *
     * @return the typed key for the stats component
     */
    public abstract ComponentKey<IStatsComponent> getStatsComponentKey();

    /**
     * Returns the {@link IStatusEffectRegistry}, used to register custom
     * status effects or look up existing ones by id.
     *
     * <pre>{@code
     * // Enregistrer un effet custom depuis votre plugin :
     * StatusEffectType CONFUSION = StatusEffectType.of(
     *         "confusion", StatusEffectCategory.CROWD_CONTROL, 3, 100);
     * FundamentalisAPI.get().getStatusEffectRegistry().register(CONFUSION);
     * }</pre>
     *
     * @return the status effect type registry
     */
    public abstract IStatusEffectRegistry getStatusEffectRegistry();

    /**
     * Returns the typed {@link ComponentKey} used to access an
     * {@link IStatusComponent} on any {@link ComponentHolder}.
     *
     * <p>Modules must never import {@code fundamentalis-status}'s concrete
     * {@code StatusComponent} class to read this key — this method is the
     * only sanctioned way to obtain it from outside that module.
     *
     * <pre>{@code
     * ComponentKey<IStatusComponent> key = FundamentalisAPI.get().getStatusComponentKey();
     * Optional<IStatusComponent> status = holder.get(key);
     * }</pre>
     *
     * @return the typed key for the status component
     */
    public abstract ComponentKey<IStatusComponent> getStatusComponentKey();

    /**
     * Returns the API version string (e.g. {@code "2.0.0"}).
     *
     * <p>Modules can use this to assert compatibility at startup:
     * <pre>{@code
     * String version = FundamentalisAPI.get().getVersion();
     * if (!version.startsWith("2.")) {
     *     getLogger().severe("Incompatible Fundamentalis version: " + version);
     *     getServer().getPluginManager().disablePlugin(this);
     * }
     * }</pre>
     *
     * @return the current API version
     */
    public abstract String getVersion();
}
