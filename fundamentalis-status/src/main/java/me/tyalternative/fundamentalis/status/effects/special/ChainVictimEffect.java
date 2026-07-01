package me.tyalternative.fundamentalis.status.effects.special;


import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;

import java.util.UUID;

/**
 * Effet "victime" du système Chain — appliqué individuellement à chaque
 * entité enchaînée (la cible initiale ET les entités proches embarquées
 * avec elle). Ne porte aucune logique propre : son cycle de vie
 * ({@link #onApply()}/{@link #onRemove()}) sert uniquement à rejoindre /
 * quitter son groupe dans {@link ChainGroupRegistry}, qui centralise le lien
 * visuel et le partage de dégâts pour TOUT le groupe en un seul endroit.
 *
 * <p>Sa durée (voir {@link StatusEffectTypes#CHAIN_LINK}) est volontairement
 * plus longue que celle du palier {@code CHAIN} qui l'a déclenché — c'est
 * elle, et non celle du lanceur, qui détermine combien de temps la chaîne
 * dure réellement.
 *
 * <p>L'id du groupe à rejoindre est transmis via {@link ActiveStatusEffect#sourceId()},
 * positionné par {@link ChainGroupRegistry} au moment de l'application
 * (voir {@code ChainGroupRegistry#createGroup}).
 *
 * <p>Comme {@code CHAIN_LINK} n'est pas marqué {@code survivesDeath}, la mort
 * de l'entité déclenche automatiquement {@link #onRemove()} via
 * {@code DeathCleanupListener} (qui appelle {@code removeEffect} à
 * {@code EntityDeathEvent}) — pas besoin de gérer la mort séparément ici.
 */
public class ChainVictimEffect extends StatusEffect {

    private final ChainGroupRegistry registry;

    public ChainVictimEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                             ChainGroupRegistry registry) {
        super(holder, statsComponent, meta);
        this.registry = registry;
    }

    @Override
    public void onApply() {
        String sourceId = getSourceId();
        if (sourceId == null) return; // ne devrait jamais arriver : toujours posé par ChainGroupRegistry

        registry.join(UUID.fromString(sourceId), getEntity());
    }

    @Override
    public void onTick() {
        // Rien de périodique : le partage de dégâts est géré par ChainGroupRegistry
        // directement sur PostDamageEvent, pas via un sondage à chaque tick.
    }

    @Override
    public void onRemove() {
        // Couvre les 3 cas de fin de vie : expiration naturelle, retrait manuel,
        // et mort de l'entité (voir la Javadoc de la classe).
        registry.leave(getEntity().getUniqueId());
    }
}
