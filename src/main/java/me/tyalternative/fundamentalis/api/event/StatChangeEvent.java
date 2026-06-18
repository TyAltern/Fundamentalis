package me.tyalternative.fundamentalis.api.event;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.StatType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * <H2>Événement Bukkit déclenché APRÈS qu'une valeur de stat de base ait changé.</H2>
 *
 * Cet événement est purement informatif (non cancellable). Il sert à
 * notifier les autres systèmes qu'une stat a été modifiée pour qu'ils
 * puissent réagir en conséquence.
 *
 * <H3>Qui écoute cet événement ?</H3>
 *
 *<pre>{@code
 *   fundamentalis-core  → synchronise la HP max en BDD si VITALITE change
 *   fundamentalis-combat → invalide le cache de dégâts si FORCE change
 *   fundamentalis-classes → peut déclencher une évolution si un seuil est atteint
 *}
 * <H3>Exemple de listener</H3>
 *
 *<pre>{@code
 *   @EventHandler
 *   public void onStatChange(StatChangeEvent event) {
 *       if (event.getStatType() != StatType.VITALITE) return;
 *       updateHealthBar(event.getHolder(), event.getNewValue());
 *   }
 *}
 * <H3>Cause</H3>
 * Le champ {@link Cause} permet aux listeners de savoir d'où vient
 * le changement et d'ignorer certaines sources si nécessaire.
 */
public class StatChangeEvent extends Event {


    private static final HandlerList HANDLERS = new HandlerList();

    // =========================================================
    // Cause du changement
    // =========================================================

    public enum Cause {
        /** Modification manuelle via commande admin (/stats set). */
        COMMAND,
        /** Modification par un item équipé / déséquipé. */
        ITEM,
        /** Modification par une montée de niveau ou de classe. */
        LEVEL_UP,
        /** Modification par un sort ou un effet de statut. */
        SPELL_OR_EFFECT,
        /** Chargement initial depuis la base de données. */
        DATABASE_LOAD,
        /** Remise à zéro (reset de personnage, wipe). */
        RESET,
        /** Cause inconnue ou non spécifiée. */
        OTHER
    }

    // =========================================================
    // Champs
    // =========================================================

    private final ComponentHolder holder;
    private final StatType        statType;
    private final int             oldValue;
    private final int             newValue;
    private final Cause           cause;

    // =========================================================
    // Constructeur
    // =========================================================


    public StatChangeEvent(ComponentHolder holder, StatType statType,
                           int oldValue, int newValue, Cause cause) {
        super(false); // false = synchrone (sur le thread principal Bukkit)
        this.holder = holder;
        this.statType = statType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.cause = cause;
    }

    // =========================================================
    // Getters
    // =========================================================

    /** Le holder dont la stat a changé (joueur ou mob). */
    public ComponentHolder getHolder()   { return holder; }

    /** La stat qui a été modifiée. */
    public StatType        getStatType() { return statType; }

    /** Valeur avant la modification. */
    public int             getOldValue() { return oldValue; }

    /** Valeur après la modification. */
    public int             getNewValue() { return newValue; }

    /** Différence nette (newValue - oldValue). Négative si diminution. */
    public int             getDelta()    { return newValue - oldValue; }

    /** Raison du changement. */
    public Cause           getCause()    { return cause; }

    // =========================================================
    // Bukkit boilerplate
    // =========================================================

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
