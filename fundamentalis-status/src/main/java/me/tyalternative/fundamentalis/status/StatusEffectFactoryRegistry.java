package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.status.StatusEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registre interne (propre à {@code fundamentalis-status}) associant chaque
 * {@link StatusEffectType} à sa {@link StatusEffectFactory}.
 *
 * <p>Remplace l'ancien {@code StatusEffectBehaviorRegistry} : au lieu d'un
 * comportement <em>partagé</em>, chaque type d'effet est associé à une
 * <em>fabrique</em> qui crée une instance fraîche de {@link StatusEffect} à
 * chaque application, permettant un état propre par instance - exactement le
 * modèle de l'ancienne version (une classe Java par effet).
 *
 * <p>Comme l'ancien registre de comportements, celui-ci reste interne à
 * {@code fundamentalis-status} (contrairement à {@code IStatusEffectRegistry},
 * qui vit dans l'API et ne décrit que les <em>définitions</em> d'effets). Un
 * plugin tiers déclare son propre effet en 3 étapes :
 *
 * <pre>{@code
 * // 1. Définir le type (généralement dans l'API du plugin tiers ou en local)
 * StatusEffectType CONFUSION = StatusEffectType.of(
 *         "confusion", StatusEffectCategory.SPECIAL, 1, 100);
 *
 * // 2. L'enregistrer dans le registre PUBLIC de l'API (visible par les autres modules)
 * FundamentalisAPI.get().getStatusEffectRegistry().register(CONFUSION);
 *
 * // 3. Associer sa fabrique dans le registre de fundamentalis-status
 * //    (nécessite un accès à StatusEffectFactoryRegistry, exposé par StatusPlugin)
 * statusEffectFactoryRegistry.register(CONFUSION, ConfusionEffect::new);
 * }</pre>
 *
 * <p>Si un {@link StatusEffectType} est enregistré dans
 * {@code IStatusEffectRegistry} mais n'a aucune fabrique associée ici,
 * {@link StatusComponent} le traite comme un effet "neutre" (aucune instance
 * {@link StatusEffect} créée, juste les métadonnées de palier) - utile pour
 * des effets purement déclaratifs consultés par d'autres modules.
 */
public class StatusEffectFactoryRegistry {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Map<StatusEffectType, StatusEffectFactory> factories = new HashMap<>();
    private final Logger logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param logger logger du plugin Status, pour confirmer les enregistrements
     */
    public StatusEffectFactoryRegistry(Logger logger) {
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Enregistrement
    // -------------------------------------------------------------------------

    /**
     * Associe une fabrique à un type d'effet.
     *
     * <p>Si une fabrique est déjà enregistrée pour ce type, elle est
     * <strong>remplacée</strong> - un plugin tiers peut ainsi personnaliser
     * la logique d'un effet intégré sans modifier {@code fundamentalis-status}.
     *
     * @param type    le type d'effet concerné
     * @param factory la fabrique à associer, typiquement une référence de constructeur
     */
    public void register (StatusEffectType type, StatusEffectFactory factory) {
        if (factories.containsKey(type)) {
            logger.warning("[StatusEffectFactoryRegistry] Fabrique remplacée pour : " + type.getId());
        }
        factories.put(type,factory);
        logger.fine("[StatusEffectFactoryRegistry] Fabrique enregistrée pour : " + type.getId());
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /**
     * @param type le type d'effet recherché
     * @return la fabrique associée, ou {@code null} si aucune n'est enregistrée
     *         (l'effet est alors traité comme neutre par {@code StatusComponent})
     */
    public StatusEffectFactory get(StatusEffectType type) {
        return factories.get(type);
    }

    /**
     * @param type le type d'effet à vérifier
     * @return {@code true} si une fabrique est enregistrée pour ce type
     */
    public boolean has(StatusEffectType type) {
        return factories.containsKey(type);
    }
}
