package me.tyalternative.fundamentalis.combat.damage;

/**
 * Résultat immuable d'un dégât traité par {@link DamageManager#dealDamage(DamageInfo)},
 * retourné en plus de l'état muté dans le {@link DamageInfo} pour faciliter
 * l'usage côté analytics et logging.
 *
 * @param originalDamage        montant de dégât avant tout calcul du pipeline
 * @param finalDamage           montant réellement appliqué à la victime
 * @param wasCritical           {@code true} si ce coup était critique
 * @param wasBlocked            {@code true} si le coup a été totalement bloqué (invulnérabilité, immunité)
 * @param wasImmune             {@code true} si la victime était immunisée au type de dégât
 * @param wasKill               {@code true} si ce coup a tué la victime
 * @param wasCharged            {@code true} si l'attaque était suffisamment chargée pour compter
 * @param reductionFromDefense  montant de dégât retranché par la défense de la victime
 * @param reductionFromEffects  montant de dégât retranché par des effets (faiblesse, résistance…)
 * @param bonusFromEffects      montant de dégât ajouté par des effets (force, critique…)
 */
public record DamageResult(
        double  originalDamage,
        double  finalDamage,
        boolean wasCritical,
        boolean wasBlocked,
        boolean wasImmune,
        boolean wasKill,
        boolean wasCharged,
        double  reductionFromDefense,
        double  reductionFromEffects,
        double  bonusFromEffects
) {

    /**
     * Différence nette entre le dégât final et le dégât d'origine.
     * Négative si le dégât a été réduit, positive s'il a été amplifié.
     *
     * @return {@code finalDamage - originalDamage}
     */
    public double getTotalReduction() {
        return finalDamage - originalDamage;
    }

    /**
     * Pourcentage de variation par rapport au dégât d'origine.
     *
     * @return le pourcentage de variation, ou {@code 0} si {@code originalDamage} vaut 0
     */
    public double getReductionPercent() {
        if (originalDamage == 0) return 0;
        return (getTotalReduction() / originalDamage) * 100;
    }

    @Override
    public String toString() {
        return String.format(
                "DamageResult[original=%.2f, final=%.2f (%.1f%%), crit=%s, blocked=%s, immune=%s, kill=%s, charged=%s]",
                originalDamage, finalDamage, getReductionPercent(),
                wasCritical, wasBlocked, wasImmune, wasKill, wasCharged
        );
    }
}
