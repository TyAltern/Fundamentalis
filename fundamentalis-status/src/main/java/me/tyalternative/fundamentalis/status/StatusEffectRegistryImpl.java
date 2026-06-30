package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.exception.StatusEffectTypeNotRegisteredException;
import me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;

import java.util.*;
import java.util.logging.Logger;

/**
 * Implémentation du registre des {@link StatusEffectType}.
 *
 * <p>Utilise une {@link LinkedHashMap} pour conserver l'ordre d'enregistrement.
 * Instanciée par {@code FundamentalisStatusPlugin#onEnable()} et immédiatement
 * enregistrée dans {@code FundamentalisAPI} via
 * {@code FundamentalisAPI.registerStatusServices(...)}, pour que
 * {@code FundamentalisAPI.get().getStatusEffectRegistry()} soit disponible.
 */
public class StatusEffectRegistryImpl implements IStatusEffectRegistry {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Map<String, StatusEffectType> registry = new LinkedHashMap<>();
    private final Logger logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param logger logger du plugin Status, pour confirmer les enregistrements
     */
    public StatusEffectRegistryImpl(Logger logger) {
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Implémentation de IStatusEffectRegistry
    // -------------------------------------------------------------------------


    @Override
    public synchronized void register(StatusEffectType type) {
        if (type == null) {
            throw new IllegalArgumentException("Impossible d'enregistrer un StatusEffectType null.");
        }
        if (registry.containsKey(type.getId())) {
            throw new IllegalStateException(
                    "Un StatusEffectType avec l'id '" + type.getId() + "' est déjà enregistré.");
        }
        registry.put(type.getId(), type);
        logger.fine("[StatusEffectRegistry] Effet enregistré : " + type.getId());
    }

    @Override
    public Optional<StatusEffectType> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toLowerCase().trim()));
    }

    @Override
    public StatusEffectType getOrThrow(String id) {
        return find(id).orElseThrow(() -> new StatusEffectTypeNotRegisteredException(id));
    }

    @Override
    public Collection<StatusEffectType> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Override
    public boolean isRegistered(String id) {
        return id != null && registry.containsKey(id.toLowerCase().trim());
    }
}
