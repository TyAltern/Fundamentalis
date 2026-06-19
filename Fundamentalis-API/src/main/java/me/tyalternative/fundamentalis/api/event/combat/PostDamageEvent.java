package me.tyalternative.fundamentalis.api.event.combat;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Événement Bukkit déclenché par {@code fundamentalis-combat} <strong>après</strong>
 * que des dégâts ont été appliqués à une entité.
 *
 * <p>Purement informatif (non cancellable) — les dégâts sont déjà infligés au
 * moment où cet événement est lancé. C'est le point d'extension pour tous les
 * effets qui réagissent à un coup porté, sans pouvoir modifier son montant.
 *
 * <h2>Qui l'écoute ?</h2>
 * <pre>{@code
 *   fundamentalis-status  → vampirisme (vol de vie), effets "on hit" (poison, bleed…)
 *   fundamentalis-classes → procs de classe (riposte, charge de rage…)
 * }</pre>
 *
 * <h2>Exemple de listener</h2>
 * <pre>{@code
 * @EventHandler
 * public void onPostDamage(PostDamageEvent event) {
 *     if (event.getAttacker() == null) return;
 *     if (!hasVampirism(event.getAttacker())) return;
 *
 *     double healAmount = event.getFinalDamage() * 0.10;
 *     heal(event.getAttacker(), healAmount);
 * }
 * }</pre>
 *
 * @see PreDamageEvent
 */
public class PostDamageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final LivingEntity attacker;
    private final LivingEntity victim;
    private final DamageType   damageType;
    private final double       finalDamage;
    private final boolean      wasCritical;
    private final boolean      wasKill;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param attacker    l'entité à l'origine des dégâts, ou {@code null} si la source
     *                    n'est pas une entité
     * @param victim      l'entité qui a reçu les dégâts
     * @param damageType  la catégorie de dégâts
     * @param finalDamage le montant réellement appliqué, après tout le pipeline
     * @param wasCritical {@code true} si le coup était critique
     * @param wasKill     {@code true} si ce coup a tué la victime
     */
    public PostDamageEvent(@Nullable LivingEntity attacker, @NotNull LivingEntity victim,
                           @NotNull DamageType damageType, double finalDamage,
                           boolean wasCritical, boolean wasKill) {
        super(false);
        this.attacker    = attacker;
        this.victim      = victim;
        this.damageType  = damageType;
        this.finalDamage = finalDamage;
        this.wasCritical = wasCritical;
        this.wasKill     = wasKill;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return l'entité à l'origine des dégâts, ou {@code null} si la source n'est pas une entité */
    @Nullable
    public LivingEntity getAttacker() { return attacker; }

    /** @return l'entité qui a reçu les dégâts */
    @NotNull
    public LivingEntity getVictim() { return victim; }

    /** @return la catégorie de dégâts */
    @NotNull
    public DamageType getDamageType() { return damageType; }

    /** @return le montant réellement infligé à la victime */
    public double getFinalDamage() { return finalDamage; }

    /** @return {@code true} si ce coup était un coup critique */
    public boolean wasCritical() { return wasCritical; }

    /** @return {@code true} si ce coup a tué la victime */
    public boolean wasKill() { return wasKill; }

    // -------------------------------------------------------------------------
    // Bukkit boilerplate
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
