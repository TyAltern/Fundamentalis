package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.status.StatusEffect;

public class HealBurstEffect extends StatusEffect {

    private static final double HEAL_PER_LEVEL = 2;

    private final DamageManager damageManager;

    public HealBurstEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                              DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

    @Override
    public void onApply() {
        damageManager.healEntity(getEntity(), HEAL_PER_LEVEL * getLevel());

    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRemove() {
        // Rien à nettoyer.
    }
}
