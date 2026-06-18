package me.tyalternative.fundamentalis.api.stats;


/**
 * <H2>Définition immuable d'une statistique.<H2>
 * <p>
 * Un StatType n'est pas un enum pour rester extensible : n'importe
 * quel module (ou plugin tiers) peut créer ses propres stats et les
 * enregistrer dans le StatTypeRegistry du Core.
 * <p>
 * Deux StatType sont égaux si et seulement si leur id est identique.
 * Cela permet de les utiliser comme clés de Map sans se soucier de
 * l'instance exacte.
 * <p>
 * <H3> Stats intégrées </H3> <br>
 * Les constantes ci-dessous sont les stats de base. Elles sont définies
 * ici (dans l'API) pour que tous les modules puissent y référer sans
 * dépendre du Core. Elles sont enregistrées dans le StatTypeRegistry
 * par le Core à son démarrage.
 * <p>
 * <H3> Créer une stat custom </H3>
 *   <pre>{@code
 *   StatType MANA = StatType.of("mana", 100, 0, 9999);
 *
 *   // Puis dans la classe principale de votre plugin :
 *   Fundamentalis.get().getStatTypeRegistry().register(MANA);
 *   }
 */
public class StatType {

    // =========================================================
    // Stats intégrées — référençables depuis n'importe quel module
    // =========================================================

    public static final StatType FORCE        = of("force",        5, 1, 9999);
    public static final StatType DEFENSE      = of("defense",      5, 1, 9999);
    public static final StatType VITALITE     = of("vitalite",     5, 1, 9999);
    public static final StatType DEXTERITE    = of("dexterite",    5, 1, 9999);
    public static final StatType INTELLIGENCE = of("intelligence", 5, 1, 9999);
    public static final StatType ENDURANCE    = of("endurance",    5, 1, 9999);

    // =========================================================
    // Champs
    // =========================================================

    /** Identifiant unique, lowercase, stable dans le temps (clé de BDD). */
    private final String id;

    /** Valeur utilisée à la création d'une nouvelle entité. */
    private final int defaultValue;

    private final int minValue;
    private final int maxValue;

    // =========================================================
    // Constructeur & factory
    // =========================================================


    private StatType(String id, int defaultValue, int minValue, int maxValue) {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("L'id d'un StatType ne peut pas être vide");
        if (minValue > maxValue)
            throw new IllegalArgumentException("min > max pour StatType '" + id + "'");
        if (defaultValue < minValue || defaultValue > maxValue)
            throw new IllegalArgumentException(
                    "defaultValue hors bornes [" + minValue + ", " + maxValue + "] pour StatType '" + id + "'");
        this.id           = id.toLowerCase().trim();
        this.defaultValue = defaultValue;
        this.minValue     = minValue;
        this.maxValue     = maxValue;
    }

    /**
     * Crée un nouveau StatType.
     *
     * @param id           Identifiant unique (ex : "mana"). Ne pas changer après publication.
     * @param defaultValue Valeur initiale pour toute nouvelle entité.
     * @param minValue     Valeur plancher (inclusive).
     * @param maxValue     Valeur plafond (inclusive).
     */
    public static StatType of(String id, int defaultValue, int minValue, int maxValue) {
        return new StatType(id, defaultValue, minValue, maxValue);
    }

    // =========================================================
    // Utilitaires
    // =========================================================

    /**
     * Ramène une valeur dans les bornes de ce StatType.
     * Utilisé par l'implémentation avant tout setStat().
     */
    public int clamp(int value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    // =========================================================
    // Getters
    // =========================================================

    public String getId()           { return id; }
    public int    getDefaultValue() { return defaultValue; }
    public int    getMinValue()     { return minValue; }
    public int    getMaxValue()     { return maxValue; }

    // =========================================================
    // Equals / hashCode / toString
    // L'égalité est basée sur l'id uniquement — pas sur l'instance.
    // =========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatType other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "StatType(" + id + ")"; }
}
