package me.tyalternative.fundamentalis.api.stats;

import me.tyalternative.fundamentalis.api.component.Component;
import me.tyalternative.fundamentalis.api.event.stats.StatChangeEvent;

import java.util.Collection;
import java.util.Map;

/**
 * <h2>Contrat public du composant de stats.</h2>
 * <p>
 * Tout ce que les autres modules peuvent faire avec les stats d'une
 * entité passe par cette interface. Ils n'ont jamais accès à
 * l'implémentation concrète (StatsComponent dans le Core).
 * <p>
 * <h3>Valeur "base" vs valeur "finale"</h3>
 * <pre>{@code
 *   getBase(type)  → valeur brute stockée en BDD, sans modificateur.
 *                    À utiliser pour l affichage dans les menus de stats.
 *
 *   getFinal(type) → valeur réelle utilisée pour les calculs de gameplay
 *                    (dégâts, défense, HP max...). Tient compte de tous
 *                    les StatModifier actifs et fire un StatComputeEvent
 *                    pour que les autres modules puissent contribuer.
 *}
 * <h3>Thread safety</h3>
 * L'implémentation garantit que les lectures sont thread-safe.
 * Les écritures (setBase, addModifier) doivent être faites sur le
 * thread principal Bukkit.
 */
public interface IStatsComponent extends Component {

    // =========================================================
    // Lecture
    // =========================================================

    /**
     * Retourne la valeur de base d'une stat (sans modificateurs).
     *
     * @throws me.tyalternative.fundamentalis.api.exception.StatTypeNotRegisteredException
     *         si le StatType n'est pas enregistré.
     */
    int getBase(StatType type);

    /**
     * Retourne la valeur finale d'une stat après application de tous
     * les modificateurs et fire du StatComputeEvent.
     * <p>
     * C'est cette valeur qui doit être utilisée dans tous les calculs
     * de gameplay (pipeline de dégâts, formules de santé, etc.).
     */
    double getFinal(StatType type);

    /**
     * Retourne toutes les valeurs de base sous forme de Map immuable.
     * Pratique pour sérialiser ou afficher un panneau de stats complet.
     */
    Map<StatType, Integer> getAllBase();

    // =========================================================
    // Setter (valeur de base)
    // =========================================================

    /**
     * Remplace la valeur de base d'une stat. <br>
     * La valeur est automatiquement clampée dans [min, max] du StatType.<br>
     * Fire un {@link StatChangeEvent} après la modification.
     *
     * @param type   La stat à modifier.
     * @param value  Nouvelle valeur (sera clampée).
     * @return       La valeur effectivement appliquée après clamp.
     */
    int setBase(StatType type, int value);

    /**
     * Remplace la valeur de base d'une stat. <br>
     * La valeur est automatiquement clampée dans [min, max] du StatType.<br>
     * Fire un {@link StatChangeEvent} après la modification.
     *
     * @param type   La stat à modifier.
     * @param value  Nouvelle valeur (sera clampée).
     * @param cause  Cause du changement.
     * @return       La valeur effectivement appliquée après clamp.
     */
    int setBase(StatType type, int value, StatChangeEvent.Cause cause);

    /**
     * Ajoute une valeur à la stat de base (peut être négative).
     * Équivalent à {@code setBase(type, getBase(type) + amount)}.
     *
     * @return La valeur effectivement appliquée après clamp.
     */
    int addBase(StatType type, int amount);

    // =========================================================
    // Modificateurs temporaires
    // =========================================================

    /**
     * Ajoute un modificateur. <br>
     * Si un modificateur avec la même source existe déjà pour ce {@link StatType},
     * il est remplacé (pas dupliqué).
     */
    void addModifier(StatModifier modifier);

    /**
     * Retire tous les modificateurs portant cette source. <br>
     * Sans effet si la source est inconnue.
     */
    void removeModifier(String source);

    /**
     * Retire tous les modificateurs actifs (ex : à la mort, au respawn).
     */
    void clearModifiers();

    /**
     * Retourne les modificateurs actifs pour un {@link StatType} donné. <br>
     * La collection retournée est immuable.
     */
    Collection<StatModifier> getModifiers(StatType type);

    // =========================================================
    // Utilitaires
    // =========================================================

    /**
     * Vérifie si ce composant possède une valeur de base pour ce StatType.
     * Retourne false si la stat n'est pas initialisée (entité fraîchement
     * créée avant le premier chargement depuis la BDD).
     */
    boolean hasStat(StatType type);

    /**
     * Réinitialise toutes les stats à leurs valeurs par défaut.
     * Utile pour un reset de personnage ou un wipe de serveur.
     */
    void resetToDefaults();
}
