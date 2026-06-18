package me.tyalternative.fundamentalis.api.component;

/**
 * <H2>Interface marqueur de base pour tout composant du système.</H2>
 *
 * <br>
 * Un composant est un fragment de données et de comportement attaché
 * à un {@link ComponentHolder} (joueur, mob, boss...).
 * <p>
 * <H3>Exemples de composants :</H3>
 * <pre>{@code
 *   IStatsComponent    → stats RPG (force, défense...)
 *   IHealthComponent   → santé étendue, boucliers (défini dans Core)
 *   IClassComponent    → classe et spécialisation (défini dans Classes)
 *   ISpellComponent    → sorts actifs (défini dans Spells)
 *   }
 * <br>
 * Chaque composant déclare un TYPE_KEY statique pour s'identifier dans
 * le registre. Convention :
 *
 * <pre>{@code
 *   public interface IStatsComponent extends Component {
 *       ComponentKey<IStatsComponent> KEY =
 *           ComponentKey.of("fundamentalis:stats", IStatsComponent.class);
 *   }
 *   }
 * <br>
 * Les modules tiers peuvent créer leurs propres composants en suivant
 * la même convention, sans modifier Fundamentalis.
 */
public interface Component {

    /**
     * Appelé par le Core quand le composant est attaché à un holder.
     * Initialiser les données, démarrer des tasks si nécessaire.
     */
    void onAttach();

    /**
     * Appelé par le Core quand le composant est détaché.
     * Libérer les ressources, annuler les tasks, sauvegarder si besoin.
     */
    void onDetach();
}
