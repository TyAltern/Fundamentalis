package me.tyalternative.fundamentalis.api.event.stats;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.StatModifier;
import me.tyalternative.fundamentalis.api.stats.StatType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <H2>Événement Bukkit déclenché PENDANT le calcul de la valeur finale d'une stat.</H2>
 *
 * C'est le mécanisme central d'extensibilité du système de stats.
 * Quand getFinal(type) est appelé sur un IStatsComponent, cet événement
 * est fire AVANT le calcul final, permettant à n'importe quel module
 * d'injecter des StatModifier supplémentaires sans jamais modifier
 * le composant de stats lui-même.
 *
 * <H3> Qui l'écoute ?</H3>
 *
 *<pre>{@code
 *   fundamentalis-combat  → injecte un bonus FORCE si l'arme tenue le prévoit
 *   fundamentalis-classes → injecte les bonus passifs de la classe active
 *   fundamentalis-effect  → injecte les buffs des effets en cours
 *   fundamentalis-spells  → injecte les buffs des sorts en cours
 *}
 * Aucun de ces modules n'a besoin de connaître les autres. Ils écoutent
 * tous le même event et injectent leurs modificateurs indépendamment.
 *
 * <H3> Exemple de listener dans fundamentalis-combat</H3>
 *
 *<pre>{@code
 *   @EventHandler
 *   public void onStatCompute(StatComputeEvent event) {
 *       if (event.getStatType() != StatType.FORCE) return;
 *
 *       CustomWeapon weapon = weaponManager.getHeldWeapon(event.getHolder());
 *       if (weapon == null) return;
 *
 *       event.addModifier(StatModifier.flat("held_weapon", StatType.FORCE, weapon.getForceBonus()));
 *   }
 *}
 * <H3> Performance</H3>
 * Cet event est fire à chaque appel de getFinal(). Les listeners doivent
 * être rapides. Éviter les accès BDD dans les handlers de cet event.
 * Le Core peut mettre en cache le résultat et invalider le cache via
 * StatChangeEvent si nécessaire.
 */
public class StatComputeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    // =========================================================
    // Champs
    // =========================================================

    private final ComponentHolder    holder;
    private final StatType           statType;
    private final int                baseValue;
    private final List<StatModifier> injectedModifiers;

    // =========================================================
    // Constructeur
    // =========================================================

    public StatComputeEvent(ComponentHolder holder, StatType statType, int baseValue) {
        super(false);
        this.holder            = holder;
        this.statType          = statType;
        this.baseValue         = baseValue;
        this.injectedModifiers = new ArrayList<>();
    }

    // =========================================================
    // API pour les listeners
    // =========================================================

    /**
     * Injecte un modificateur supplémentaire dans ce calcul.
     *<p>
     * Le modificateur doit cibler le même StatType que l'événement.<br>
     * Un modificateur ciblant un autre StatType est ignoré silencieusement.
     */
    public void addModifier(StatModifier modifier) {
        if (modifier.statType().equals(statType)) {
            injectedModifiers.add(modifier);
        }
    }

    // =========================================================
    // Getters
    // =========================================================

    /** Le holder dont on calcule la stat. */
    public ComponentHolder getHolder()   { return holder; }

    /** La stat en cours de calcul. */
    public StatType        getStatType() { return statType; }

    /**
     * La valeur de base stockée en BDD, avant tout modificateur. <br>
     * Utile pour les listeners qui veulent calculer un bonus relatif à la base.
     */
    public int             getBaseValue() { return baseValue; }

    /**
     * Liste immuable des modificateurs injectés par les listeners jusqu'ici. <br>
     * Le Core lira cette liste après que tous les listeners aient répondu.
     */
    public List<StatModifier> getInjectedModifiers() {
        return Collections.unmodifiableList(injectedModifiers);
    }

    // =========================================================
    // Bukkit boilerplate
    // =========================================================

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

}
