package me.tyalternative.fundamentalis.core.entity;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.core.component.ComponentHolderImpl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Implémentation de {@link IEntityService}.
 *
 * <p>Maintient un registre en mémoire de tous les {@link ComponentHolder} actifs,
 * indexés par UUID. C'est l'annuaire central que tous les modules consultent pour
 * accéder aux composants d'une entité.
 *
 * <p>Le registre est une {@link ConcurrentHashMap} pour permettre des lectures
 * thread-safe depuis des tâches async (ex : vérification de stats dans un timer).
 * Les écritures ({@link #register}, {@link #unregister}) restent réservées au
 * thread principal Bukkit, orchestrées par {@link EntityTracker}.
 *
 * <p><strong>Ce service ne crée jamais de holders lui-même.</strong>
 * C'est {@link EntityTracker} qui écoute les événements Bukkit et délègue
 * la création à ce service via {@link #register}.
 */
public class EntityService implements IEntityService {

    // -------------------------------------------------------------------------
    // Registre
    // -------------------------------------------------------------------------

    /**
     * Map UUID → ComponentHolder des entités actuellement trackées.
     * ConcurrentHashMap pour les lectures thread-safe.
     */
    private final ConcurrentHashMap<UUID, ComponentHolder> holders = new ConcurrentHashMap<>();

    private final Logger logger;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param logger logger du plugin pour les messages de debug
     */
    public EntityService(Logger logger) {
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Implémentation de IEntityService
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public Optional<ComponentHolder> get(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        return Optional.ofNullable(holders.get(entity.getUniqueId()));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ComponentHolder> get(UUID entityId) {
        if (entityId == null) return Optional.empty();
        return Optional.ofNullable(holders.get(entityId));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException si le joueur n'est pas tracké
     *         (ne devrait jamais arriver après {@code PlayerJoinEvent})
     */
    @Override
    public ComponentHolder getPlayer(Player player) {
        ComponentHolder holder = holders.get(player.getUniqueId());
        if (holder == null) {
            throw new IllegalStateException(
                    "Le joueur '" + player.getName() + "' (" + player.getUniqueId()
                            + ") n'est pas tracké par Fundamentalis. "
                            + "Vérifiez que EntityTracker est bien enregistré et que "
                            + "PlayerJoinEvent a été traité avant cet appel.");
        }
        return holder;
    }

    /** {@inheritDoc} */
    @Override
    public Collection<ComponentHolder> getAll() {
        return Collections.unmodifiableCollection(holders.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTracked(LivingEntity entity) {
        return entity != null && holders.containsKey(entity.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Gestion interne du registre (réservé à EntityTracker)
    // -------------------------------------------------------------------------

    /**
     * Crée un nouveau {@link ComponentHolderImpl} pour l'entité donnée et
     * l'enregistre dans le registre.
     *
     * <p>Doit être appelé sur le thread principal Bukkit, depuis {@link EntityTracker}.
     *
     * @param entity l'entité à enregistrer — ne doit pas être {@code null}
     * @return le holder nouvellement créé
     * @throws IllegalStateException si cette entité est déjà enregistrée
     */
    public ComponentHolder register(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if (holders.containsKey(uuid)) {
            throw new IllegalStateException(
                    "L'entité " + uuid + " est déjà enregistrée dans l'EntityService. "
                            + "Appelez unregister() avant de la ré-enregistrer.");
        }
        ComponentHolderImpl holder = new ComponentHolderImpl(entity);
        holders.put(uuid, holder);
        return holder;
    }

    /**
     * Retire le holder de l'entité du registre et détache tous ses composants.
     *
     * <p>Doit être appelé <strong>après</strong> que
     * {@link me.tyalternative.fundamentalis.api.event.entity.EntityUnregisteredEvent EntityUnregisteredEvent}
     * a été traité par tous les listeners (pour leur laisser le temps de sauvegarder).
     *
     * @param entityUUID l'UUID de l'entité à désenregistrer
     * @return le holder retiré, ou {@link Optional#empty()} si l'entité n'était pas trackée
     */
    public Optional<ComponentHolder> unregister(UUID entityUUID) {
        ComponentHolder holder = holders.remove(entityUUID);
        if (holder == null) return Optional.empty();

        // Détachement de tous les composants dans l'ordre inverse
        ((ComponentHolderImpl) holder).detachAll();
        return Optional.of(holder);
    }

    /**
     * Retourne le nombre d'entités actuellement trackées.
     * Utile pour le monitoring et les commandes de debug.
     *
     * @return nombre de holders actifs
     */
    public int getTrackedCount() {
        return holders.size();
    }

}
