package me.tyalternative.fundamentalis.status.effects.CC;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

public class SlowEffect extends StatusEffect {

    private final NamespacedKey modifierKey;
    private static final double BONUS_PER_LEVEL  = 0.20;

    public SlowEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta) {
        super(holder, statsComponent, meta);
        this.modifierKey = new NamespacedKey("fundamentalis", "status_slow_" + meta.id());
    }

    @Override
    public void onApply() {
        LivingEntity entity = getEntity();
        if (entity == null || !entity.isValid()) return;

        AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        var modifier = new AttributeModifier(modifierKey,  -(BONUS_PER_LEVEL * getLevel()), AttributeModifier.Operation.MULTIPLY_SCALAR_1 );
        if (speedAttr != null) speedAttr.addModifier(modifier);
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onRemove() {
        LivingEntity entity = getEntity();
        if (entity == null || !entity.isValid()) return;

        AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(modifierKey);
    }
}
