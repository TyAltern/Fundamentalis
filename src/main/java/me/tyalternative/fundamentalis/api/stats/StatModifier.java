package me.tyalternative.fundamentalis.api.stats;

/**
 * <H2>Représente une modification temporaire ou permanente d'une stat.</H2>
 * <p>
 * Les modificateurs sont empilables et s'appliquent dans un ordre précis :
 * <p>
 *   <pre>{@code
 *   1. BASE                  valeur brute stockée
 *   2. + somme des FLAT      ex : +10 force depuis un item
 *   3. × (1 + somme PERCENT) ex : +20% depuis un sort
 *   4. × produit MULTIPLY    ex : ×1.5 depuis un buff de classe
 *   = valeur finale
 *   }
 * <p>
 * Chaque modificateur porte une source (id unique en string) pour pouvoir
 * être retiré précisément plus tard sans effet de bord.
 * <p>
 * <H3>Exemple d'usage :</H3>
 *   <pre>{@code
 *   StatModifier m = StatModifier.flat("sword_of_power", StatType.FORCE, 15);
 *   statsComponent.addModifier(m);
 *   // ... plus tard :
 *   statsComponent.removeModifier("sword_of_power");
 *   }
 */
public record StatModifier(
        String   source,    // Identifiant unique du modificateur (ex: "item_excalibur", "spell_rage")
        StatType statType,  // Quelle stat est modifiée
        Type     type,      // Comment la valeur est appliquée
        double   value      // Valeur du modificateur (peut être négative)

) {

    // =========================================================
    // Type de modification
    // =========================================================

    public enum Type {
        /**
         * Ajoute une valeur absolue.
         * Exemple : FLAT +10 FORCE → ajoute 10 points de force.
         */
        FLAT,

        /**
         * Ajoute un pourcentage de la valeur de base.
         * Exemple : PERCENT +0.20 FORCE → ajoute 20% de la base de force.
         * Note : la valeur est un ratio (0.20 = 20%, pas 20).
         */
        PERCENT,

        /**
         * Multiplie la valeur courante (après FLAT et PERCENT).
         * Exemple : MULTIPLY 1.5 FORCE → multiplie le résultat par 1.5.
         */
        MULTIPLY
    }

    // =========================================================
    // Validation du record
    // =========================================================

    public StatModifier {
        if (source == null || source.isBlank())
            throw new IllegalArgumentException("La source d'un StatModifier ne peut pas être vide");
        if (statType == null)
            throw new IllegalArgumentException("Le StatType d'un StatModifier ne peut pas être null");
        if (type == null)
            throw new IllegalArgumentException("Le Type d'un StatModifier ne peut pas être null");
        if (type == Type.MULTIPLY && value <= 0)
            throw new IllegalArgumentException("Un modificateur MULTIPLY doit avoir une valeur > 0");
    }

    // =========================================================
    // Factories — API fluide
    // =========================================================

    /** Crée un modificateur absolu. Ex : flat("ring", FORCE, 5) → +5 force. */
    public static StatModifier flat(String source, StatType statType, double value) {
        return new StatModifier(source, statType, Type.FLAT, value);
    }

    /**
     * Crée un modificateur en pourcentage.
     * Ex : percent("potion", FORCE, 0.25) → +25% de la force de base.
     */
    public static StatModifier percent(String source, StatType statType, double value) {
        return new StatModifier(source, statType, Type.PERCENT, value);
    }

    /**
     * Crée un multiplicateur.
     * Ex : multiply("berserker_class", FORCE, 1.3) → ×1.3 sur la force totale.
     */
    public static StatModifier multiply(String source, StatType statType, double value) {
        return new StatModifier(source, statType, Type.MULTIPLY, value);
    }
}
