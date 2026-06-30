package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatModifier;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;

public class AdrenalineEffect extends StatusEffect {

    private static final double FORCE_PERCENT_PER_LEVEL = 0.20; // +10% de FORCE par niveau
    private static final double DEFENSE_PERCENT_PER_LEVEL = 0.25; // -10% de DEFENSE par niveau

    public AdrenalineEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta) {
        super(holder, statsComponent, meta);
    }

    @Override
    public void onApply() {
        IStatsComponent stats = getStatsComponent();
        if (stats == null) return;

        double valueForce = FORCE_PERCENT_PER_LEVEL * getLevel();
        double valueDefense = DEFENSE_PERCENT_PER_LEVEL * getLevel();
        stats.addModifier(StatModifier.percent(forceSourceId(), StatType.FORCE, +valueForce));
        stats.addModifier(StatModifier.percent(defenseSourceId(), StatType.DEFENSE, -valueDefense));
    }

    @Override
    public void onTick() {
        // Rien de périodique — le modificateur reste actif tant qu'il est posé.
    }

    @Override
    public void onRemove() {
        IStatsComponent stats = getStatsComponent();
        if (stats == null) return;
        stats.removeModifier(forceSourceId());
        stats.removeModifier(defenseSourceId());
    }

    /** Source unique du StatModifier, dérivée de l'id de cette instance de palier. */
    private String forceSourceId() {
        return "status:force-" + getInstanceId();
    }
    private String defenseSourceId() {
        return "status:defense-" + getInstanceId();
    }
}
