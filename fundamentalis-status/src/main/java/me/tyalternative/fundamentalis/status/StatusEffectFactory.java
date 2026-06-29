package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;

/**
 * Fabrique une nouvelle instance de {@link StatusEffect} à chaque application
 * d'un palier - typiquement une référence de constructeur :
 *
 * <pre>{@code
 * statusEffectFactoryRegistry.register(StatusEffectTypes.VAMPIRISM, VampirismEffect::new);
 * }</pre>
 *
 * <p>C'est cette indirection qui permet à chaque effet de porter un état
 * propre : {@link StatusComponent#applyEffect} appelle {@link #create} à
 * chaque palier nouvellement appliqué, garantissant une instance fraîche
 * (donc un état fraîchement initialisé) à chaque fois - contrairement à
 * l'ancien {@code StatusEffectBehavior} qui était une instance unique
 * partagée par toutes les entités et toutes les applications.
 */
@FunctionalInterface
public interface StatusEffectFactory {

    /**
     * Crée une nouvelle instance de {@link StatusEffect} pour ce palier.
     *
     * @param holder         le holder de l'entité affectée
     * @param statsComponent le composant de stats de la même entité, ou {@code null} si absent
     * @param meta           les métadonnées initiales de ce palier (type, niveau, expiration…)
     * @return une nouvelle instance de {@link StatusEffect}, prête pour {@link StatusEffect#onApply()}
     */
    StatusEffect create(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta);
}
