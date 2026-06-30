package me.tyalternative.fundamentalis.status.effects.StatModifier;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.LivingEntity;

/**
 * Régénération — soigne un montant proportionnel au niveau à intervalle
 * régulier, tant que ce palier est actif.
 *
 * <p>Contrairement à {@code StrengthEffect}, la régénération applique un soin
 * <strong>direct</strong> via {@link DamageManager#healEntity} plutôt qu'un
 * {@link me.tyalternative.fundamentalis.api.stats.StatModifier StatModifier} —
 * il n'existe pas de stat "régénération passive" dans le système actuel,
 * donc l'effet agit directement sur les PV courants, comme un Poison inversé.
 */
public class RegenerationEffect extends StatusEffect {

    private static final long   TICK_INTERVAL   = 20L; // un tick de soin par seconde
    private static final long   TICK_REDUCTION_PER_LEVEL  = 5L;
    private static final double HEAL_PER_TICK  = 1.0;

    private final DamageManager damageManager;
    private long ticksSinceLastHeal = 0;

    public RegenerationEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                              DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

    @Override
    public void onApply() {
        ticksSinceLastHeal = 0;
    }

    @Override
    public void onTick() {
        ticksSinceLastHeal++;
        if (ticksSinceLastHeal < (TICK_INTERVAL - TICK_REDUCTION_PER_LEVEL * (getLevel() - 1))) return;
        ticksSinceLastHeal = 0;

        LivingEntity entity = getEntity();
        if (entity == null || !entity.isValid()) return;

        damageManager.healEntity(entity, HEAL_PER_TICK);
    }

    @Override
    public void onRemove() {
        // Rien à nettoyer.
    }
}
