package me.tyalternative.fundamentalis.api.component;

import me.tyalternative.fundamentalis.api.exception.ComponentNotFoundException;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;

/**
 * <H2>Représente une entité trackée par Fundamentalis, capable de porter
 * des composants (stats, santé, classe, sorts...).</H2>
 *<p>
 * Toute LivingEntity trackée (joueur, mob, boss) est wrappée dans un
 * ComponentHolder par l'EntityService du Core. C'est l'objet central
 * auquel les autres systèmes accèdent pour lire ou écrire des données.
 *
 * <H3>Accès aux composants</H3>
 *<pre>{@code
 *   // Accès sécurisé (recommandé quand le composant est optionnel)
 *   holder.get(IStatsComponent.KEY).ifPresent(stats -> {
 *       int force = stats.getBase(StatType.FORCE);
 *   });
 *
 *   // Accès direct (lance ComponentNotFoundException si absent)
 *   IStatsComponent stats = holder.require(IStatsComponent.KEY);
 * }
 * <H3>Qui crée les ComponentHolder ?</H3>
 * Uniquement le Core (EntityService). Les autres modules ne les
 * instancient jamais directement.
 */
public interface ComponentHolder {

    // =========================================================
    // Accès à l'entité sous-jacente
    // =========================================================

    /**
     * Retourne l'entité Bukkit associée à ce holder.<br>
     * Peut retourner null si l'entité a été déchargée (chunk unload).
     */
    LivingEntity getEntity();

    /**
     * Retourne l'UUID de l'entité sous forme de String.<br>
     * Toujours disponible même si l'entité est déchargée.
     */
    String getEntityId();

    /**
     * Indique si l'entité est actuellement chargée et valide.
     */
    boolean isValid();

    // =========================================================
    // Accès aux composants
    // =========================================================

    /**
     * Retourne le composant associé à cette clé, ou Optional.empty()<br>
     * si ce composant n'est pas attaché à ce holder.
     * <p>
     * Utiliser cette méthode quand le composant est optionnel.
     */
    <C extends Component> Optional<C> get(ComponentKey<C> key);

    /**
     * Retourne le composant associé à cette clé.<br>
     * Lance {@link ComponentNotFoundException} si le composant est absent.
     * <p>
     * Utiliser cette méthode quand l'absence du composant est une erreur <br>
     * (ex : dans le pipeline de dégâts, toute entité doit avoir des stats).
     */
    <C extends Component> C require(ComponentKey<C> key);

    /**
     * Vérifie si ce holder possède un composant pour cette clé.
     */
    <C extends Component> boolean has(ComponentKey<C> key);

    // =========================================================
    // Gestion des composants (réservé au Core)
    // =========================================================

    /**
     * Attache un composant à ce holder.<br>
     * Appelle component.onAttach() après l'ajout.
     * <p>
     * Cette méthode est appelée uniquement par le Core.<br>
     * Les modules externes ne doivent pas l'utiliser directement.
     *
     * @throws IllegalStateException si un composant avec la même clé
     *                               est déjà attaché.
     */
    <C extends Component> void attach(ComponentKey<C> key, C component);

    /**
     * Détache et retourne le composant associé à cette clé. <br>
     * Appelle component.onDetach() avant la suppression.<br>
     * Sans effet si le composant est absent.
     */
    <C extends Component> Optional<C> detach(ComponentKey<C> key);
}
