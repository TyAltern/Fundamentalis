package me.tyalternative.fundamentalis.api;

/**
 * Registration gateway for the {@link FundamentalisAPI} implementation.
 *
 * <p>This class exists to keep the registration mechanism separate from the
 * public API surface. Only {@code fundamentalis-core} should ever call
 * {@link #register} and {@link #unregister} — doing so from any other plugin
 * is a programming error and will throw an {@link IllegalStateException}.
 *
 * <h2>Usage — inside fundamentalis-core only</h2>
 * <pre>{@code
 * // Dans CorePlugin#onEnable(), après l'initialisation complète :
 * FundamentalisProvider.register(new CoreAPIImpl());
 *
 * // Dans CorePlugin#onDisable() :
 * FundamentalisProvider.unregister();
 * }</pre>
 *
 * @see FundamentalisAPI
 */
public final class FundamentalisProvider {

    // Classe utilitaire — pas d'instanciation
    private FundamentalisProvider() {}

    /**
     * Registers the {@link FundamentalisAPI} implementation.
     *
     * <p>Must be called exactly once, at the end of {@code fundamentalis-core}'s
     * {@code onEnable}, after all services are fully initialized.
     *
     * @param api the implementation to register — must not be {@code null}
     * @throws IllegalArgumentException if {@code api} is {@code null}
     * @throws IllegalStateException    if an implementation is already registered
     */
    public static void register(FundamentalisAPI api) {
        if (api == null) {
            throw new IllegalArgumentException("FundamentalisAPI implementation must not be null");
        }
        // La vérification de doublon est faite dans FundamentalisAPI#setInstance
        FundamentalisAPI.setInstance(api);
    }

    /**
     * Unregisters the current {@link FundamentalisAPI} implementation.
     *
     * <p>Must be called in {@code fundamentalis-core}'s {@code onDisable}.
     * After this call, {@link FundamentalisAPI#get()} will throw until
     * a new implementation is registered.
     */
    public static void unregister() {
        FundamentalisAPI.setInstance(null);
    }
}
