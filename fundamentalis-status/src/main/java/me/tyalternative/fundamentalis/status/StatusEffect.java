package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.component.Component;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * Classe de base pour un effet de statut concret, instanciée
 * <strong>une fois par application</strong> - comme dans l'ancienne version
 * de Fundamentalis. Chaque sous-classe est un fichier autonome
 * ({@code VampirismEffect}, {@code PoisonEffect}…) qui porte librement son
 * propre état interne (compteurs, cooldowns, données de calcul…), en plus du
 * niveau et de la durée déjà gérés par le moteur de paliers.
 *
 * <h2>Différence avec l'ancien {@code StatusEffectBehavior}</h2>
 * Un {@code StatusEffectBehavior} était <strong>partagé</strong> entre toutes
 * les entités et toutes les applications d'un même type d'effet - il ne
 * pouvait donc porter aucun état propre à une application précise. Une
 * {@code StatusEffect} est créée fraîche à chaque {@code applyEffect}, exactement
 * comme {@code new VampirismEffect(...)} dans l'ancienne version.
 *
 * <h2>Cycle d'appel</h2>
 * <p>Identique dans l'esprit à l'ancienne version ({@code onApply}/{@code onTick}/{@code onRemove})
 * mais adapté à la file de priorité par niveau de la v2 :
 * <ul>
 *   <li>{@link #onApply} - ce palier vient de devenir le palier actif/visible
 *       (à l'application initiale, ou après expiration d'un palier supérieur).</li>
 *   <li>{@link #onTick} - appelé à chaque tick tant que ce palier reste actif.</li>
 *   <li>{@link #onRemove} - ce palier cesse d'être actif (expiration, retrait
 *       manuel, ou supplanté par un palier de niveau supérieur).</li>
 * </ul>
 * Un palier en sommeil (non actif) ne reçoit <strong>aucun</strong> de ces
 * appels tant qu'il n'est pas promu actif - son instance {@code StatusEffect}
 * existe déjà (créée à l'application) mais reste dormante.
 *
 * <h2>Créer un effet custom - exemple minimal</h2>
 * <pre>{@code
 * public class ConfusionEffect extends StatusEffect {
 *     public ConfusionEffect(ComponentHolder holder, IStatsComponent stats, ActiveStatusEffect meta) {
 *         super(holder, stats, meta);
 *     }
 *
 *     @Override public void onApply() { getEntity().sendMessage("Vous êtes confus !"); }
 *     @Override public void onTick()  { /* rien de périodique * / }
 *     @Override public void onRemove() { getEntity().sendMessage("La confusion se dissipe."); }
 * }
 *
 * // Enregistrement dans un plugin tiers, après le chargement de fundamentalis-status :
 * StatusEffectType CONFUSION = StatusEffectType.of("confusion", StatusEffectCategory.SPECIAL, 1, 100);
 * FundamentalisAPI.get().getStatusEffectRegistry().register(CONFUSION);
 * statusEffectFactoryRegistry.register(CONFUSION, ConfusionEffect::new);
 * }</pre>
 *
 * @see StatusEffectFactory
 * @see StatusComponent
 */
public abstract class StatusEffect {

    // -------------------------------------------------------------------------
    // Champs - fournis par le moteur, identiques pour tous les effets
    // -------------------------------------------------------------------------

    private final ComponentHolder holder;
    private final IStatsComponent statsComponent;

    /**
     * Métadonnées immuables de ce palier (type, niveau, expiration…), tenues
     * à jour par {@link StatusComponent} à chaque tick - toujours la version
     * la plus récente au moment de l'appel d'une des 3 méthodes de cycle de vie.
     */
    protected ActiveStatusEffect meta;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param holder         le holder de l'entité affectée
     * @param statsComponent le composant de stats de la même entité, ou {@code null} si absent
     * @param meta           les métadonnées initiales de ce palier
     */
    protected StatusEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta) {
        this.holder         = holder;
        this.statsComponent = statsComponent;
        this.meta           = meta;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie - à implémenter par chaque effet concret
    // -------------------------------------------------------------------------

    /** Appelé quand ce palier devient le palier actif/visible pour son type. */
    public abstract void onApply();

    /** Appelé à chaque tick tant que ce palier reste le palier actif. */
    public abstract void onTick();

    /**
     * Appelé quand ce palier cesse d'être actif (expiration, retrait manuel,
     * ou supplanté par un palier supérieur). Doit annuler proprement tout ce
     * que {@link #onApply} avait mis en place.
     */
    public abstract void onRemove();

    // -------------------------------------------------------------------------
    // Accesseurs utilitaires pour les sous-classes
    // -------------------------------------------------------------------------

    /** @return l'entité Bukkit affectée par cet effet */
    protected LivingEntity getEntity() { return holder.getEntity(); }

    /** @return le holder de l'entité affectée */
    protected ComponentHolder getHolder() { return holder; }

    /** @return le composant de stats de l'entité, ou {@code null} si absent */
    protected IStatsComponent getStatsComponent() { return statsComponent; }

    /** @return le niveau courant de ce palier */
    protected int getLevel() { return meta.level(); }

    /** @return le temps restant de l'effet */
    protected long getDuration() { return meta.remainingTicks(Bukkit.getCurrentTick()); }
    /** @return le temps restant de l'effet */
    protected int getDurationInt() { return Math.toIntExact(meta.remainingTicks(Bukkit.getCurrentTick())); }

    /** @return le type d'effet de ce palier */
    protected StatusEffectType getType() { return meta.type(); }

    /** @return l'id unique de cette instance de palier (stable pour toute sa durée de vie) */
    protected UUID getInstanceId() { return meta.id(); }

    /** @return l'identifiant de la source ayant appliqué ce palier, ou {@code null} */
    protected String getSourceId() { return meta.sourceId(); }

    /**
     * Met à jour les métadonnées tenues par cette instance.
     * Appelé uniquement par {@link StatusComponent} - jamais par l'effet lui-même.
     */
    final void updateMeta(ActiveStatusEffect newMeta) {
        this.meta = newMeta;
    }
}
