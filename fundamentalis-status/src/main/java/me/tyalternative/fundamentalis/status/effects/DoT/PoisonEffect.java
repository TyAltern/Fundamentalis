package me.tyalternative.fundamentalis.status.effects.DoT;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Poison — dégâts sur la durée, un tick toutes les x ticks, fréquence
 * proportionnelle au niveau.
 *
 * <p>Illustre le cas le plus simple d'effet à état : un compteur de ticks
 * interne ({@link #ticksSinceLastDamage}) pour espacer les applications de
 * dégâts sans dépendre d'une cadence calculée à partir du palier (comme le
 * faisait l'ancien {@code DamageOverTimeBehavior} générique) — chaque
 * instance de {@code PoisonEffect} gère sa propre horloge interne, exactement
 * comme dans l'ancienne version de Fundamentalis.
 */
public class PoisonEffect extends StatusEffect {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final long   TICK_INTERVAL  = 20L; // un tick de dégâts par seconde
    private static final long   TICK_REDUCTION_PER_LEVEL = 5L;
    private static final double DAMAGE_TICK = 1.0;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final DamageManager damageManager;

    /** État propre à cette instance : décompte avant le prochain tick de dégâts. */
    private long ticksSinceLastDamage = 0;

    // -------------------------------------------------------------------------
    // Constructeur — signature attendue par StatusEffectFactory (référence de constructeur)
    // -------------------------------------------------------------------------

    public PoisonEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                        DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.damageManager = damageManager;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    @Override
    public void onApply() {
        ticksSinceLastDamage = 0;

        getEntity().addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) Math.ceil((double) getDuration()),0, false, false, false));
    }

    @Override
    public void onTick() {
        ticksSinceLastDamage++;
        if (ticksSinceLastDamage < Math.max(5L, TICK_INTERVAL - ( TICK_REDUCTION_PER_LEVEL * (getLevel()-1) ))) return;
        ticksSinceLastDamage = 0;

        if (getEntity().getHealth() == 1) return;

        LivingEntity victim = getEntity();
        if (victim == null || !victim.isValid()) return;

        DamageInfo info = damageManager.createStatusDamage(
                victim, DamageSource.STATUS_POISON, DAMAGE_TICK, DamageType.PHYSICAL);
        damageManager.dealDamage(info);
    }

    @Override
    public void onRemove() {
    }
}
