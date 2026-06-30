package me.tyalternative.fundamentalis.api.event.combat;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PostDamageCalculationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final LivingEntity attacker;
    private final LivingEntity victim;
    private final DamageType   damageType;
    private double             finalDamage;
    private final boolean      wasCritical;
    private final boolean      willKill;
    private boolean            cancelled;

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
     * @param willKill     {@code true} si ce coup a tué la victime
     */
    public PostDamageCalculationEvent(@Nullable LivingEntity attacker, @NotNull LivingEntity victim,
                           @NotNull DamageType damageType, double finalDamage,
                           boolean wasCritical, boolean willKill) {
        super(false);
        this.attacker    = attacker;
        this.victim      = victim;
        this.damageType  = damageType;
        this.finalDamage = finalDamage;
        this.wasCritical = wasCritical;
        this.willKill     = willKill;
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

    /** @param finalDamage la nouvelle valeur de finalDamage */
    public void setFinalDamage(double finalDamage) { this.finalDamage = finalDamage; }

    /** @return {@code true} si ce coup était un coup critique */
    public boolean wasCritical() { return wasCritical; }

    /** @return {@code true} si ce coup a tué la victime */
    public boolean willKill() { return willKill; }

    // -------------------------------------------------------------------------
    // Cancellable
    // -------------------------------------------------------------------------

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    // -------------------------------------------------------------------------
    // Bukkit boilerplate
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
