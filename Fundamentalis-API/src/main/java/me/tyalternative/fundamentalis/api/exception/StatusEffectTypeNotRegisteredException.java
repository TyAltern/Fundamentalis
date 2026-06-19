package me.tyalternative.fundamentalis.api.exception;

/**
 * Thrown when a status effect identifier is used that has not been registered
 * in the {@link me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry IStatusEffectRegistry}.
 *
 * <p>Common causes:
 * <ul>
 *   <li>A typo in a configuration file or command referencing an effect by id.</li>
 *   <li>A plugin that defines a custom status effect but forgot to call
 *       {@code IStatusEffectRegistry#register()} in its {@code onEnable}.</li>
 *   <li>A plugin load order issue: the effect-owning plugin loaded after
 *       {@code fundamentalis-status}.</li>
 * </ul>
 *
 * @see me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry
 */
public class StatusEffectTypeNotRegisteredException extends RuntimeException {

    private final String effectId;

    /**
     * @param effectId the unrecognized status effect identifier
     */
    public StatusEffectTypeNotRegisteredException(String effectId) {
        super("Unknown StatusEffectType: '" + effectId + "'. "
                + "Ensure the plugin that owns this effect is loaded before fundamentalis-status "
                + "and calls IStatusEffectRegistry#register() in its onEnable.");
        this.effectId = effectId;
    }

    /** @return the status effect identifier that was not found in the registry */
    public String getEffectId() { return effectId; }
}
