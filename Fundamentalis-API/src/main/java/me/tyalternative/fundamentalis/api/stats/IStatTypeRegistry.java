package me.tyalternative.fundamentalis.api.stats;

import me.tyalternative.fundamentalis.api.exception.StatTypeNotRegisteredException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry that holds every known {@link StatType} in the system.
 *
 * <p>This registry is the single source of truth for stat definitions.
 * The Core registers the six built-in stats at startup. Any third-party
 * module that defines custom stats must call {@link #register(StatType)}
 * in its {@code onEnable}, <strong>before</strong> the Core finishes loading
 * (use {@code load-before} or {@code dependencies} in {@code plugin.yml}).
 *
 * <p>Accessible via the Service Locator:
 * <pre>{@code
 * IStatTypeRegistry registry = FundamentalisAPI.get().getStatTypeRegistry();
 * }</pre>
 *
 * @see StatType
 */
public interface IStatTypeRegistry {

    /**
     * Registers a {@link StatType} so the system can persist and compute it.
     *
     * <p>Registration is permanent for the lifetime of the server.
     * Call this once in your plugin's {@code onEnable}.
     *
     * @param type the stat definition to register — must not be {@code null}
     * @throws IllegalStateException    if a stat with the same id is already registered
     * @throws IllegalArgumentException if {@code type} is {@code null}
     */
    void register( StatType type);

    /**
     * Looks up a {@link StatType} by its id (case-insensitive).
     *
     * @param id the stat identifier (e.g. {@code "force"})
     * @return an {@link Optional} containing the stat, or empty if unknown
     */
    Optional<StatType> find(String id);

    /**
     * Looks up a {@link StatType} by its id, throwing if not found.
     *
     * <p>Prefer this over {@link #find(String)} in code paths where an
     * unknown id is a programming error (e.g. reading from a hard-coded config key).
     *
     * @param id the stat identifier
     * @return the matching {@link StatType}
     * @throws StatTypeNotRegisteredException if no stat with this id is registered
     */
    StatType getOrThrow(String id);

    /**
     * Returns every registered {@link StatType} in registration order.
     *
     * <p>The returned collection is an immutable snapshot.
     *
     * @return all registered stat types
     */
    Collection<StatType> getAll();

    /**
     * Returns {@code true} if a stat with the given id is registered.
     *
     * @param id the stat identifier to check
     * @return {@code true} if registered, {@code false} otherwise
     */
    boolean isRegistered(String id);
}
