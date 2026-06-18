package me.tyalternative.fundamentalis.api.event.entity;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired <strong>before</strong> a {@link ComponentHolder} is dismantled and
 * removed from the entity tracker.
 *
 * <p>This is the correct hook for persisting data, releasing resources, or
 * cleaning up any state that a module attached to this entity.
 *
 * <p>At the time this event fires:
 * <ul>
 *   <li>The holder is still valid and all its components are still accessible.</li>
 *   <li>After all listeners return, the Core calls {@code onDetach()} on every
 *       component and removes the holder from the registry.</li>
 *   <li>Subsequent calls to {@link IEntityService#get(org.bukkit.entity.LivingEntity) IEntityService#get()}
 *       for this entity will return {@code Optional.empty()}.</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * // Dans fundamentalis-classes, pour sauvegarder la classe du joueur :
 * @EventHandler
 * public void onEntityUnregistered(EntityUnregisteredEvent event) {
 *     event.getHolder()
 *          .get(IClassComponent.KEY)
 *          .ifPresent(classRepo::save);
 * }
 * }</pre>
 *
 * @see EntityRegisteredEvent
 * @see ComponentHolder
 */
public class EntityUnregisteredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    // -------------------------------------------------------------------------
    // Source of unregistration
    // -------------------------------------------------------------------------

    /**
     * Describes why the entity is being unregistered.
     */
    public enum Cause {
        /** A player disconnected from the server. */
        PLAYER_QUIT,
        /** The entity died. */
        DEATH,
        /** The entity's chunk was unloaded and the entity despawned. */
        DESPAWN,
        /** The server is shutting down — all remaining entities are unregistered. */
        SERVER_SHUTDOWN,
        /** Unregistration triggered by an API call from a third-party plugin. */
        API_CALL
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final ComponentHolder holder;
    private final Cause           cause;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param holder the holder that is about to be dismantled — still fully valid
     * @param cause  the reason this entity is being unregistered
     */
    public EntityUnregisteredEvent(ComponentHolder holder, Cause cause) {
        super(false);
        this.holder = holder;
        this.cause  = cause;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the holder that is about to be dismantled.
     * All components are still accessible at this point.
     *
     * @return the entity's {@link ComponentHolder}
     */
    public ComponentHolder getHolder() { return holder; }

    /**
     * @return the reason this entity is being unregistered
     */
    public Cause getCause() { return cause; }

    // -------------------------------------------------------------------------
    // Bukkit boilerplate
    // -------------------------------------------------------------------------

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
