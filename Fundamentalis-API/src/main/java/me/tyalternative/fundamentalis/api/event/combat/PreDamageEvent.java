package me.tyalternative.fundamentalis.api.event.combat;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Événement Bukkit déclenché par {@code fundamentalis-combat} <strong>avant</strong>
 * qu'un montant de dégâts ne soit appliqué à une entité.
 *
 * <p>C'est le point d'extension principal du pipeline de dégâts. Les modules
 * qui gèrent des résistances, des boucliers, des immunités temporaires ou des
 * effets de statut écoutent cet événement pour modifier le montant final
 * <strong>avant</strong> qu'il ne soit appliqué à la cible.
 *
 * <h2>Qui l'écoute ?</h2>
 * <pre>{@code
 *   fundamentalis-status  → applique les résistances par DamageType, les buffs
 *                           Force/Faiblesse, Résistance, Marqué...
 *   fundamentalis-spells  → applique les boucliers magiques
 * }</pre>
 *
 * <h2>Exemple de listener</h2>
 * <pre>{@code
 * @EventHandler
 * public void onPreDamage(PreDamageEvent event) {
 *     if (event.getDamageType() == DamageType.FIRE) {
 *         double resistance = getFireResistance(event.getVictim());
 *         event.setDamage(event.getDamage() * (1.0 - resistance));
 *     }
 * }
 * }</pre>
 *
 * <p>Annuler cet événement ({@link #setCancelled(boolean)}) empêche
 * totalement l'application des dégâts (équivalent à une immunité complète).
 *
 * @see PostDamageEvent
 * @see DamageType
 */
public class PreDamageEvent extends Event implements Cancellable {

    public static final HandlerList HANDLERS = new HandlerList();

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final LivingEntity attacker; // nullable - peut être un DoT, un trap, etc
    private final LivingEntity victim;
    private final DamageType   damageType;

    private double  damage;
    private boolean forceCrit;
    private boolean canKnockBack;
    private double  knockbackFactor;
    private boolean cancelled;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param attacker   l'entité à l'origine des dégâts, ou {@code null} si la source
     *                   n'est pas une entité (DoT, environnement…)
     * @param victim     l'entité qui va recevoir les dégâts
     * @param damageType la catégorie de dégâts
     * @param damage     le montant de dégâts avant modification par les listeners
     */
    public PreDamageEvent(@Nullable LivingEntity attacker, @NotNull LivingEntity victim,
                          @NotNull DamageType damageType, double damage,
                          boolean forceCrit, boolean canKnockBack, double knockbackFactor) {
        super(false);
        this.attacker        = attacker;
        this.victim          = victim;
        this.damageType      = damageType;
        this.damage          = damage;
        this.forceCrit       = forceCrit;
        this.canKnockBack    = canKnockBack;
        this.knockbackFactor = knockbackFactor;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    /** @return l'entité à l'origine des dégâts, ou {@code null} si la source n'est pas une entité */
    @Nullable
    public LivingEntity getAttacker() { return attacker; }

    /** @return l'entité qui va recevoir les dégâts */
    @NotNull
    public LivingEntity getVictim() { return victim; }

    /** @return la catégorie de dégâts */
    @NotNull
    public DamageType getDamageType() { return damageType; }

    /** @return le montant de dégâts actuel, modifiable par les listeners suivants */
    public double getDamage() { return damage; }

    /**
     * Modifie le montant de dégâts qui sera appliqué.
     *
     * @param damage le nouveau montant — sera clampé à 0 minimum par le pipeline
     */
    public void setDamage(double damage) { this.damage = damage; }

    public boolean isCritForced() { return this.forceCrit; }

    public void setForceCrit(boolean forceCrit) { this.forceCrit = forceCrit; }

    public boolean canKnockBack() { return this.canKnockBack; }

    public void setCanKnockBack(boolean canKnockBack) { this.canKnockBack = canKnockBack; }

    public double getKnockbackFactor() { return this.knockbackFactor; }

    public void setKnockbackFactor(double knockbackFactor) { this.knockbackFactor = knockbackFactor; }

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
