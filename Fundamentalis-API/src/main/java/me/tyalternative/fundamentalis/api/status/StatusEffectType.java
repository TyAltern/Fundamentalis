package me.tyalternative.fundamentalis.api.status;

/**
 * Définition immuable d'un effet de statut (Poison, Stun, Force…).
 *
 * <p>Suit exactement le même principe d'extensibilité que
 * {@link me.tyalternative.fundamentalis.api.stats.StatType StatType} :
 * ce n'est pas un enum, afin que n'importe quel module (ou plugin tiers)
 * puisse créer ses propres effets et les enregistrer dans le
 * {@link IStatusEffectRegistry} sans modifier {@code fundamentalis-status}.
 *
 * <p>Deux {@code StatusEffectType} sont égaux si et seulement si leur
 * {@link #getId() id} est identique - utilisable comme clé de {@link java.util.Map}.
 *
 * <h2>Niveaux et file de priorité</h2>
 * Un effet possède un {@link #getMaxLevel() niveau maximum}. Quand deux
 * instances du même {@code StatusEffectType} sont actives sur la même entité
 * avec des niveaux différents, seul le niveau le plus élevé est actif ; les
 * niveaux inférieurs sont mis en file d'attente et reprennent leur durée
 * restante quand le niveau supérieur expire. Ce comportement est générique,
 * géré par {@code fundamentalis-status} pour tous les effets sans exception.
 *
 * <p>L'<strong>effet</strong> concret d'un changement de niveau (intensité du
 * DoT, force du ralentissement…) n'est <strong>pas</strong> défini ici - il
 * appartient à l'implémentation du comportement enregistrée côté
 * {@code fundamentalis-status} (ex : {@code PoisonBehavior}). Un même
 * {@code StatusEffectType} peut très bien ignorer totalement le niveau
 * (ex : Stun, binaire par nature).
 *
 * <h2>Créer un effet custom</h2>
 * <pre>{@code
 * // Dans votre plugin :
 * public static final StatusEffectType CONFUSION =
 *     StatusEffectType.of("confusion", StatusEffectCategory.CROWD_CONTROL, 3, 100);
 *
 * // Dans onEnable, AVANT que fundamentalis-status ne charge les effets actifs :
 * FundamentalisAPI.get().getStatusEffectRegistry().register(CONFUSION);
 * }</pre>
 *
 * @see IStatusEffectRegistry
 * @see StatusEffectCategory
 */
public final class StatusEffectType {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    /** Identifiant unique, lowercase, stable dans le temps (clé de BDD). */
    private final String id;

    /** Famille de comportement - informative, voir {@link StatusEffectCategory}. */
    private final StatusEffectCategory category;

    /** Niveau maximum atteignable par cet effet (inclusif, minimum 1). */
    private final int maxLevel;

    /** Durée par défaut en ticks, utilisée si aucune durée explicite n'est fournie à l'application. */
    private final long defaultDurationTicks;

    /**
     * Si {@code true}, ce type d'effet n'est PAS retiré automatiquement à la
     * mort de l'entité affectée. {@code false} par défaut : la grande
     * majorité des effets (DoT, CC, buffs de combat) n'a pas de sens à
     * survivre à une mort/respawn.
     */
    private final boolean survivesDeath;

    // -------------------------------------------------------------------------
    // Constructeur & factory
    // -------------------------------------------------------------------------

    private StatusEffectType(String id, StatusEffectCategory category,
                             int maxLevel, long defaultDurationTicks, boolean survivesDeath) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("L'id d'un StatusEffectType ne peut pas être vide");
        if (category == null)
            throw new IllegalArgumentException("La catégorie d'un StatusEffectType ne peut pas être nulle");
        if (maxLevel < 1)
            throw new IllegalArgumentException("maxLevel doit être ≥ 1 pour StatusEffectType '" + id + "'");
        if (defaultDurationTicks < 1)
            throw new IllegalArgumentException("defaultDurationTicks doit être ≥ 1 pour StatusEffectType '" + id + "'");

        this.id                   = id.toLowerCase().trim();
        this.category             = category;
        this.maxLevel             = maxLevel;
        this.defaultDurationTicks = defaultDurationTicks;
        this.survivesDeath        = survivesDeath;
    }

    /**
     * Crée un nouveau {@code StatusEffectType}.
     *
     * @param id                   identifiant unique (ex : {@code "poison"}). Ne pas changer après publication
     * @param category             famille de comportement de l'effet
     * @param maxLevel             niveau maximum atteignable (≥ 1) ; utiliser {@code 1} pour un effet binaire sans palier
     * @param defaultDurationTicks durée par défaut en ticks (20 ticks = 1 seconde) si aucune durée explicite n'est donnée
     * @return une instance immuable de {@code StatusEffectType}
     */
    public static StatusEffectType of(String id, StatusEffectCategory category,
                                      int maxLevel, long defaultDurationTicks) {
        return new StatusEffectType(id, category, maxLevel, defaultDurationTicks, false);
    }

    /**
     * Crée un nouveau {@code StatusEffectType} en choisissant explicitement
     * s'il doit survivre à la mort de l'entité affectée.
     *
     * <p>Réserver {@code survivesDeath = true} aux effets pour lesquels ça a
     * un sens gameplay explicite (ex : une malédiction longue durée, un buff
     * de classe persistant) — ce n'est pas le comportement par défaut.
     *
     * @param id                   identifiant unique (ex : {@code "curse"})
     * @param category             famille de comportement de l'effet
     * @param maxLevel             niveau maximum atteignable (≥ 1)
     * @param defaultDurationTicks durée par défaut en ticks si aucune durée explicite n'est donnée
     * @param survivesDeath        {@code true} si cet effet ne doit PAS être retiré à la mort
     * @return une instance immuable de {@code StatusEffectType}
     */
    public static StatusEffectType of(String id, StatusEffectCategory category,
                                      int maxLevel, long defaultDurationTicks, boolean survivesDeath) {
        return new StatusEffectType(id, category, maxLevel, defaultDurationTicks, survivesDeath);
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    /**
     * Ramène un niveau dans les bornes [{@code 1}, {@link #getMaxLevel()}].
     *
     * @param level le niveau brut à valider
     * @return le niveau clampé
     */
    public int clampLevel(int level) {
        return Math.max(1, Math.min(maxLevel, level));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return l'identifiant unique, lowercase (ex : {@code "poison"}) */
    public String getId() { return id; }

    /** @return la famille de comportement de cet effet */
    public StatusEffectCategory getCategory() { return category; }

    /** @return le niveau maximum atteignable par cet effet */
    public int getMaxLevel() { return maxLevel; }

    /** @return la durée par défaut en ticks si aucune durée explicite n'est fournie */
    public long getDefaultDurationTicks() { return defaultDurationTicks; }

    /**
     * @return {@code true} si cet effet n'est PAS retiré automatiquement à la
     *         mort de l'entité affectée ({@code false} par défaut)
     */
    public boolean survivesDeath() { return survivesDeath; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString - identité basée sur l'id uniquement
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatusEffectType other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "StatusEffectType(" + id + ")"; }
}
