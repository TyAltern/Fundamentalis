package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatModifier;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;

/**
 * Force — augmente temporairement la stat {@link StatType#FORCE} de la cible
 * via un {@link StatModifier} de type {@code PERCENT} (+10% par niveau).
 *
 * <p>S'appuie directement sur {@link IStatsComponent#addModifier}/{@code removeModifier} —
 * la source du modificateur est dérivée de l'id de l'instance de palier
 * ({@code "status:<uuid>"}) pour garantir un retrait précis sans affecter
 * d'autres modificateurs posés par ailleurs (items, sorts…).
 *
 * <p>Si l'entité n'a pas de {@link IStatsComponent} (ex : mob sans stats
 * RPG), {@link #onApply}/{@link #onRemove} sont silencieusement no-op.
 */
public class StrengthEffect extends StatusEffect {

    private static final double PERCENT_PER_LEVEL = 0.10; // +10% de FORCE par niveau

    public StrengthEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta) {
        super(holder, statsComponent, meta);
    }

    @Override
    public void onApply() {
        IStatsComponent stats = getStatsComponent();
        if (stats == null) return;

        double value = PERCENT_PER_LEVEL * getLevel();
        stats.addModifier(StatModifier.percent(sourceId(), StatType.FORCE, value));
    }

    @Override
    public void onTick() {
        // Rien de périodique — le modificateur reste actif tant qu'il est posé.
    }

    @Override
    public void onRemove() {
        IStatsComponent stats = getStatsComponent();
        if (stats == null) return;
        stats.removeModifier(sourceId());
    }

    /** Source unique du StatModifier, dérivée de l'id de cette instance de palier. */
    private String sourceId() {
        return "status:" + getInstanceId();
    }
}
