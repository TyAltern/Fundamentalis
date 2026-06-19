package me.tyalternative.fundamentalis.api.status;

import me.tyalternative.fundamentalis.api.exception.StatusEffectTypeNotRegisteredException;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry that holds every known {@link StatusEffectType} in the system.
 *
 * <p>This registry is the single source of truth for status effect definitions.
 * {@code fundamentalis-status} registers its built-in effects (Poison, Burn,
 * Stun…) at startup. Any third-party module that defines custom effects must
 * call {@link #register(StatusEffectType)} in its {@code onEnable},
 * <strong>before</strong> {@code fundamentalis-status} finishes loading
 * (declare it in {@code load-before} or as a {@code depend} of your plugin).
 *
 * <p>Accessible via the Service Locator:
 * <pre>{@code
 * IStatusEffectRegistry registry = FundamentalisAPI.get().getStatusEffectRegistry();
 * }</pre>
 *
 * @see StatusEffectType
 */
public interface IStatusEffectRegistry {

    /**
     * Registers a {@link StatusEffectType} so the system can apply, persist
     * and tick it.
     *
     * <p>Registration is permanent for the lifetime of the server.
     * Call this once in your plugin's {@code onEnable}.
     *
     * @param type the effect definition to register — must not be {@code null}
     * @throws IllegalStateException    if an effect with the same id is already registered
     * @throws IllegalArgumentException if {@code type} is {@code null}
     */
    void register(StatusEffectType type);

    /**
     * Looks up a {@link StatusEffectType} by its id (case-insensitive).
     *
     * @param id the effect identifier (e.g. {@code "poison"})
     * @return an {@link Optional} containing the effect, or empty if unknown
     */
    Optional<StatusEffectType> find(String id);

    /**
     * Looks up a {@link StatusEffectType} by its id, throwing if not found.
     *
     * <p>Prefer this over {@link #find(String)} in code paths where an
     * unknown id is a programming error (e.g. reading from a hard-coded config key).
     *
     * @param id the effect identifier
     * @return the matching {@link StatusEffectType}
     * @throws StatusEffectTypeNotRegisteredException if no effect with this id is registered
     */
    StatusEffectType getOrThrow(String id);

    /**
     * Returns every registered {@link StatusEffectType} in registration order.
     *
     * <p>The returned collection is an immutable snapshot.
     *
     * @return all registered status effect types
     */
    Collection<StatusEffectType> getAll();

    /**
     * Returns {@code true} if an effect with the given id is registered.
     *
     * @param id the effect identifier to check
     * @return {@code true} if registered, {@code false} otherwise
     */
    boolean isRegistered(String id);
}
