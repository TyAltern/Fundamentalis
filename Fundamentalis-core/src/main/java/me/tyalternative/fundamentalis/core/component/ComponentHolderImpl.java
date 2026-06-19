package me.tyalternative.fundamentalis.core.component;

import me.tyalternative.fundamentalis.api.component.Component;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.exception.ComponentNotFoundException;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation de {@link ComponentHolder}.
 *
 * <p>Utilise une {@link ConcurrentHashMap} pour stocker les composants, ce qui
 * permet des lectures thread-safe sans synchronisation explicite. Les écritures
 * ({@link #attach}, {@link #detach}) sont réservées au thread principal Bukkit.
 *
 * <p>L'entité Bukkit est stockée comme référence directe. Si l'entité est
 * déchargée (chunk unload), {@link #getEntity()} peut retourner une entité
 * invalide — vérifier {@link #isValid()} avant toute opération Bukkit.
 *
 * <p>Ce holder est créé et géré exclusivement par
 * {@link me.tyalternative.fundamentalis.core.entity.EntityService EntityService}.
 * Aucun autre module ne doit l'instancier directement.
 */
public class ComponentHolderImpl implements ComponentHolder {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    /** Référence à l'entité Bukkit. Peut devenir invalide si l'entité despawn. */
    private final LivingEntity entity;

    /** UUID sous forme de String — toujours disponible, même si l'entité est invalide. */
    private final String entityId;

    /**
     * Map des composants : clé namespaced → composant.
     * ConcurrentHashMap pour des lectures thread-safe.
     */
    private final Map<String, Component> components = new ConcurrentHashMap<>();


    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entity l'entité Bukkit à wrapper — ne doit pas être {@code null}
     */
    public ComponentHolderImpl(LivingEntity entity) {
        if (entity == null) throw new IllegalArgumentException("L'entité d'un ComponentHolder ne peut pas être null.");
        this.entity = entity;
        this.entityId = entity.getUniqueId().toString();
    }

    // -------------------------------------------------------------------------
    // Entité sous-jacente (ComponentHolder)
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public LivingEntity getEntity() { return entity; }

    /** {@inheritDoc} */
    @Override
    public String getEntityId() { return entityId; }

    /**
     * {@inheritDoc}
     *
     * <p>Délègue à {@link LivingEntity#isValid()} de Bukkit, qui retourne
     * {@code false} si l'entité est morte ou si son chunk est déchargé.
     */
    @Override
    public boolean isValid() { return entity.isValid(); }

    // -------------------------------------------------------------------------
    // Accès aux composants (ComponentHolder)
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>La clé est recherchée par son id namespaced. Le cast est sécurisé car
     * {@link #attach} garantit la cohérence entre la clé et le type du composant.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <C extends Component> Optional<C> get(ComponentKey<C> key) {
        Component component = components.get(key.id());
        if (component == null) return Optional.empty();
        return Optional.of((C) component);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ComponentNotFoundException si le composant est absent
     */
    @Override
    public <C extends Component> C require(ComponentKey<C> key) {
        return get(key).orElseThrow(() -> new ComponentNotFoundException(key.type(), entityId));
    }

    /** {@inheritDoc} */
    @Override
    public <C extends Component> boolean has(ComponentKey<C> key) {
        return components.containsKey(key.id());
    }

    // -------------------------------------------------------------------------
    // Gestion des composants (ComponentHolder)
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException si un composant avec la même clé est déjà attaché
     */
    @Override
    public <C extends Component> void attach(ComponentKey<C> key, C component) {
        if (components.containsKey(key.id())) {
            throw new IllegalStateException(
                    "Un composant avec la clé '" + key.id() + "' est déjà attaché à l'entité "
                            + entityId + ". Détachez l'ancien avant d'en attacher un nouveau.");
        }
        components.put(key.id(), component);
        component.onAttach();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Appelle {@link Component#onDetach()} avant de supprimer le composant,
     * même si l'appel lève une exception (logged, mais non propagée pour ne pas
     * bloquer le démontage des autres composants).
     */
    @Override
    @SuppressWarnings("unchecked")
    public <C extends Component> Optional<C> detach(ComponentKey<C> key) {
        Component component = components.remove(key.id());
        if (component == null) return Optional.empty();

        try {
            component.onDetach();
        } catch (Exception e) {
            // On logge l'erreur mais on ne bloque pas le démontage des autres composants
            System.err.println("[Fundamentalis] Erreur lors de onDetach() du composant '"
                    + key.id() + "' sur l'entité " + entityId + " : " + e.getMessage());
        }

        return Optional.of((C) component);
    }

    // -------------------------------------------------------------------------
    // Démontage complet
    // -------------------------------------------------------------------------

    /**
     * Détache tous les composants dans l'ordre inverse d'ajout et appelle
     * {@link Component#onDetach()} sur chacun.
     *
     * <p>Appelé par {@link me.tyalternative.fundamentalis.core.entity.EntityService EntityService}
     * lors de la désenregistration de l'entité, après que
     * {@link me.tyalternative.fundamentalis.api.event.entity.EntityUnregisteredEvent EntityUnregisteredEvent}
     * a été traité par tous les listeners.
     */
    public void detachAll() {
        // On itère sur une copie des clés pour éviter une ConcurrentModificationException
        for (String keyId : components.keySet().stream().toList()) {
            Component component = components.remove(keyId);
            if (component != null) {
                try {
                    component.onDetach();
                } catch (Exception e) {
                    System.err.println("[Fundamentalis] Erreur lors de onDetach() du composant '"
                            + keyId + "' sur l'entité " + entityId + " : " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "ComponentHolder(entity=" + entityId + ", components=" + components.keySet() + ")";
    }
}
