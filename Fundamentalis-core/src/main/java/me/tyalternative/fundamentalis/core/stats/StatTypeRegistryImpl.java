package me.tyalternative.fundamentalis.core.stats;

import me.tyalternative.fundamentalis.api.exception.StatTypeNotRegisteredException;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.StatType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implémentation du registre des {@link StatType}.
 *
 * <p>Utilise une {@link LinkedHashMap} pour conserver l'ordre d'enregistrement,
 * ce qui garantit un affichage cohérent dans les menus et les commandes.
 *
 * <p>La map est synchronisée sur {@code this} pour les écritures (enregistrement
 * au démarrage des plugins). Les lectures sont non synchronisées car elles
 * n'ont lieu qu'après la phase d'enregistrement, quand la map est stable.
 *
 * <p>Les six stats intégrées ({@link StatType#FORCE}, {@link StatType#DEFENSE}…)
 * sont enregistrées dans {@link #registerBuiltins()}, appelé par
 * {@link me.tyalternative.fundamentalis.core.CorePlugin CorePlugin.onEnable()}.
 */
public class StatTypeRegistryImpl implements IStatTypeRegistry {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    /** Map ordonnée : id → StatType. Immuable après la phase d'enregistrement. */
    private final Map<String,StatType> registry = new LinkedHashMap<>();

    private final Logger logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param logger logger du plugin pour confirmer les enregistrements
     */
    public StatTypeRegistryImpl(Logger logger) {
        this.logger = logger;
    }
    // -------------------------------------------------------------------------
    // Enregistrement des stats intégrées
    // -------------------------------------------------------------------------

    /**
     * Enregistre les six stats intégrées de Fundamentalis.
     * Appelé par {@code CorePlugin#onEnable()} avant tout autre enregistrement.
     */
    public void registerBuiltins() {
        register(StatType.FORCE);
        register(StatType.DEFENSE);
        register(StatType.VITALITE);
        register(StatType.DEXTERITE);
        register(StatType.INTELLIGENCE);
        register(StatType.ENDURANCE);
        logger.info("[StatTypeRegistry] 6 stats intégrées enregistrées.");
    }

    // -------------------------------------------------------------------------
    // Implémentation de IStatTypeRegistry
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>L'enregistrement est synchronisé pour être thread-safe dans le cas
     * improbable où deux plugins s'enregistrent simultanément.
     */
    @Override
    public synchronized void register(StatType type) {
        if (type == null) {
            throw new IllegalArgumentException("Impossible d'enregistrer un StatType null.");
        }
        if (registry.containsKey(type.getId())) {
            throw new IllegalStateException(
                    "Un StatType avec l'id '" + type.getId() + "' est déjà enregistré. "
                            + "Chaque id doit être unique dans tout l'écosystème Fundamentalis.");
        }
        registry.put(type.getId(), type);
        logger.fine("[StatTypeRegistry] Stat enregistrée : " + type.getId()
                + " [" + type.getMinValue() + "-" + type.getMaxValue()
                + ", défaut=" + type.getDefaultValue() + "]");
    }

    /** {@inheritDoc} */
    @Override
    public Optional<StatType> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toLowerCase().trim()));
    }

    /** {@inheritDoc} */
    @Override
    public StatType getOrThrow(String id) {
        return find(id).orElseThrow(() -> new StatTypeNotRegisteredException(id));
    }

    /** {@inheritDoc} */
    @Override
    public Collection<StatType> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRegistered(String id) {
        return id != null && registry.containsKey(id.toLowerCase().trim());
    }

}
