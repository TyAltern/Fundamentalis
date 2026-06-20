package me.tyalternative.fundamentalis.api.event.status;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * <H2>Événement Bukkit déclenché APRÈS qu'un palier d'effet de statut ait expiré ou ait été retiré.</H2>
 *
 * Fire à la fois pour une expiration naturelle (durée écoulée) et pour un
 * retrait manuel via {@code IStatusComponent#removeEffect}/{@code removeEffectInstance}
 * — voir {@link #wasManualRemoval()} pour distinguer les deux cas.
 *
 * <p>Si un palier de niveau inférieur était en sommeil derrière celui qui
 * vient d'expirer, ce dernier devient actif <strong>avant</strong> que cet
 * événement ne soit fire — {@link #getNewActiveEffect()} permet de le
 * consulter directement sans requête supplémentaire.
 *
 * <H3>Qui écoute cet événement ?</H3>
 * <pre>{@code
 *   fundamentalis-classes → retire un bonus de classe lié à un effet à son expiration
 * }</pre>
 *
 * <H3>Exemple de listener</H3>
 * <pre>{@code
 *   @EventHandler
 *   public void onEffectExpired(StatusEffectExpiredEvent event) {
 *       if (event.wasManualRemoval()) return; // ignorer les purges manuelles
 *       notifyPlayer(event.getHolder(), event.getExpiredEffect());
 *   }
 * }</pre>
 */
public class StatusEffectExpiredEvent extends Event{

    private static final HandlerList HANDLERS = new HandlerList();

    // =========================================================
    // Champs
    // =========================================================

    private final ComponentHolder    holder;
    private final ActiveStatusEffect expiredEffect;
    private final ActiveStatusEffect newActiveEffect; // nullable
    private final boolean            manualRemoval;

    // =========================================================
    // Constructeur
    // =========================================================

    /**
     * @param holder          le holder de l'entité affectée
     * @param expiredEffect   le palier qui vient d'expirer ou d'être retiré
     * @param newActiveEffect le palier de niveau inférieur devenu actif suite à cette expiration,
     *                        ou {@code null} si aucun palier de ce type n'est plus actif
     * @param manualRemoval   {@code true} si retiré manuellement (commande, purge),
     *                        {@code false} si expiré naturellement (durée écoulée)
     */
    public StatusEffectExpiredEvent(ComponentHolder holder, ActiveStatusEffect expiredEffect,
                                    ActiveStatusEffect newActiveEffect, boolean manualRemoval) {
        super(false);
        this.holder          = holder;
        this.expiredEffect   = expiredEffect;
        this.newActiveEffect = newActiveEffect;
        this.manualRemoval   = manualRemoval;
    }

    // =========================================================
    // Getters
    // =========================================================

    /** Le holder de l'entité affectée. */
    public ComponentHolder getHolder() { return holder; }

    /** Le palier qui vient d'expirer ou d'être retiré. */
    public ActiveStatusEffect getExpiredEffect() { return expiredEffect; }

    /**
     * Le palier de niveau inférieur devenu actif suite à cette expiration,
     * ou {@code null} si plus aucun palier de ce type n'est actif sur l'entité.
     */
    public ActiveStatusEffect getNewActiveEffect() { return newActiveEffect; }

    /**
     * {@code true} si ce palier a été retiré manuellement
     * (commande admin, purge), {@code false} s'il a expiré naturellement.
     */
    public boolean wasManualRemoval() { return manualRemoval; }

    // =========================================================
    // Bukkit boilerplate
    // =========================================================

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
