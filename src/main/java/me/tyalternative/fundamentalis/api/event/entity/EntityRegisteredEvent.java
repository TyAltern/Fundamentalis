package me.tyalternative.fundamentalis.api.event.entity;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a {@link ComponentHolder} has been created and fully initialized
 * for a living entity.
 *
 * <p>This is the correct hook for attaching additional components to an entity.
 * For example, {@code fundamentalis-classes} listens to this event to attach
 * an {@code IClassComponent} to every player that joins.
 *
 * <p>At the time this event fires, the Core guarantees that:
 * <ul>
 *   <li>The holder is fully initialized and returned by {@link IEntityService#get(org.bukkit.entity.LivingEntity) IEntityService#get()}.</li>
 *   <li>All built-in Core components (stats, health) are already attached.</li>
 *   <li>Stat data has been loaded from the database (or defaults applied).</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * // Dans fundamentalis-classes :
 * @EventHandler
 * public void onEntityRegistered(EntityRegisteredEvent event) {
 *     if (!(event.getHolder().getEntity() instanceof Player player)) return;
 *
 *     ClassComponent classComponent = new ClassComponent(player);
 *     event.getHolder().attach(IClassComponent.KEY, classComponent);
 * }
 * }</pre>
 *
 * @see EntityUnregisteredEvent
 * @see ComponentHolder
 */
public class EntityRegisteredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    // -------------------------------------------------------------------------
    // Source of registration — indique pourquoi l'entité a été enregistrée
    // -------------------------------------------------------------------------

    /**
     * Describes why the entity was registered.
     * Listeners can use this to skip irrelevant registrations
     * (e.g. attaching a class only to players, not to mobs).
     */
    public enum Cause {
        /** A player connected to the server. */
        PLAYER_JOIN,
        /** A custom mob was spawned by a module (e.g. MythicMobs spawn). */
        MOB_SPAWN,
        /** A boss entity was created by {@code fundamentalis-dungeons}. */
        BOSS_SPAWN,
        /** Registration triggered by an API call from a third-party plugin. */
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
     * @param holder the fully initialized holder for the entity
     * @param cause  the reason this entity was registered
     */
    public EntityRegisteredEvent(ComponentHolder holder, Cause cause) {
        super(false); // synchrone — toujours sur le thread principal Bukkit
        this.holder = holder;
        this.cause  = cause;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * @return the fully initialized {@link ComponentHolder} for this entity
     */
    public ComponentHolder getHolder() { return holder; }

    /**
     * @return the reason this entity was registered
     */
    public Cause getCause() { return cause; }

    // -------------------------------------------------------------------------
    // Bukkit boilerplate
    // -------------------------------------------------------------------------

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

}
