package me.tyalternative.fundamentalis.api.event.status;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * <H2>Événement Bukkit déclenché APRÈS qu'un nouveau palier d'effet de statut ait été appliqué.</H2>
 *
 * Fire à chaque appel de {@code IStatusComponent#applyEffect}, que le nouveau
 * palier devienne immédiatement actif ou qu'il reste en sommeil derrière un
 * palier de niveau supérieur déjà en cours (voir {@link #wasImmediatelyActive()}).
 *
 * <p>Purement informatif (non cancellable) — pour empêcher l'application d'un
 * effet, utiliser {@link me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent PreDamageEvent}
 * côté Combat ou un filtre en amont côté module appelant.
 *
 * <H3>Qui écoute cet événement ?</H3>
 * <pre>{@code
 *   fundamentalis-classes → déclenche un proc de classe à l'application d'un effet
 *   fundamentalis-spells  → synchronise l'UI de sorts actifs
 * }</pre>
 *
 * <H3>Exemple de listener</H3>
 * <pre>{@code
 *   @EventHandler
 *   public void onEffectApplied(StatusEffectAppliedEvent event) {
 *       if (!event.getEffect().type().equals(StatusEffectTypes.POISON)) return;
 *       event.getHolder().getEntity().getWorld().spawnParticle(...);
 *   }
 * }</pre>
 */
public class StatusEffectAppliedEvent extends Event{

    private static final HandlerList HANDLERS = new HandlerList();

    // =========================================================
    // Champs
    // =========================================================

    private final ComponentHolder    holder;
    private final ActiveStatusEffect effect;
    private final boolean            wasImmediatelyActive;

    // =========================================================
    // Constructeur
    // =========================================================

    /**
     * @param holder               le holder de l'entité affectée
     * @param effect               le palier nouvellement créé
     * @param wasImmediatelyActive {@code true} si ce palier est devenu actif immédiatement,
     *                             {@code false} s'il reste en sommeil derrière un palier supérieur
     */
    public StatusEffectAppliedEvent(ComponentHolder holder, ActiveStatusEffect effect,
                                    boolean wasImmediatelyActive) {
        super(false); // synchrone — toujours sur le thread principal Bukkit
        this.holder               = holder;
        this.effect               = effect;
        this.wasImmediatelyActive = wasImmediatelyActive;
    }

    // =========================================================
    // Getters
    // =========================================================

    /** Le holder de l'entité affectée. */
    public ComponentHolder    getHolder() { return holder; }

    /** Le palier nouvellement appliqué. */
    public ActiveStatusEffect getEffect() { return effect; }

    /**
     * {@code true} si ce palier est devenu visible/actif dès son application,
     * {@code false} s'il est resté en sommeil derrière un palier de niveau
     * supérieur déjà en cours.
     */
    public boolean wasImmediatelyActive() { return wasImmediatelyActive; }

    // =========================================================
    // Bukkit boilerplate
    // =========================================================

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

}
