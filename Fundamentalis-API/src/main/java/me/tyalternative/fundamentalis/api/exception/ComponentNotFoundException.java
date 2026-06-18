package me.tyalternative.fundamentalis.api.exception;

/**
 * Lancée quand on tente d'accéder à un composant qui n'est pas
 * attaché à l'entité demandée.
 * <p>
 * Exemple :
 *   holder.getComponent(IStatsComponent.class)
 *   -> ComponentNotFoundException si l'entité n'a pas de stats
 */
public class ComponentNotFoundException extends RuntimeException {

    private final Class<?> componentType;

    public ComponentNotFoundException(Class<?> componentType, String entityName) {
        super("Component '" + componentType.getSimpleName() + "' introuvable sur l'entité : " + entityName);

        this.componentType = componentType;
    }

    public Class<?> getComponentType() {
        return componentType;
    }
}
