package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatModifier;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;

public class ToughnessEffect extends StatusEffect {

    private static final double PERCENT_PER_LEVEL = 0.10; // +10% de DEFENSE par niveau

    public ToughnessEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta) {
        super(holder, statsComponent, meta);
    }

    @Override
    public void onApply() {
        IStatsComponent stats = getStatsComponent();
        if (stats == null) return;

        double value = PERCENT_PER_LEVEL * getLevel();
        stats.addModifier(StatModifier.percent(sourceId(), StatType.DEFENSE, value));
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
