package me.tyalternative.fundamentalis.core.api;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.core.FundamentalisCorePlugin;
import me.tyalternative.fundamentalis.core.entity.EntityService;
import me.tyalternative.fundamentalis.core.stats.StatsComponent;
import me.tyalternative.fundamentalis.core.stats.StatTypeRegistryImpl;

/**
 * Implémentation de {@link FundamentalisAPI} fournie par le Core.
 *
 * <p>Cette classe est le pont entre le Service Locator public ({@link FundamentalisAPI})
 * et les implémentations concrètes du Core. Elle est instanciée une seule fois
 * dans {@link FundamentalisCorePlugin FundamentalisCorePlugin#onEnable()}
 * et enregistrée via {@link me.tyalternative.fundamentalis.api.FundamentalisProvider FundamentalisProvider}.
 *
 * <p>N'expose que les interfaces publiques — aucune classe concrète du Core
 * ne fuite à travers cette classe. Les modules externes obtiennent des
 * {@link IEntityService} et {@link IStatTypeRegistry}, jamais des
 * {@link EntityService} ou {@link StatTypeRegistryImpl}.
 */
public class CoreAPIImpl extends FundamentalisAPI {
    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    /** Version de l'API — synchronisée avec le pom.xml du parent. */
    private static final String API_VERSION = "1.0";

    private final IEntityService    entityService;
    private final IStatTypeRegistry statTypeRegistry;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService    l'implémentation de l'entity service
     * @param statTypeRegistry l'implémentation du registre des stat types
     */
    public CoreAPIImpl(IEntityService entityService, IStatTypeRegistry statTypeRegistry) {
        this.entityService    = entityService;
        this.statTypeRegistry = statTypeRegistry;
    }

    // -------------------------------------------------------------------------
    // Implémentation de FundamentalisAPI
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Retourne l'instance de {@link EntityService} du Core, exposée via
     * l'interface {@link IEntityService} pour ne pas coupler les modules à
     * l'implémentation.
     */
    @Override
    public IEntityService getEntityService() {
        return entityService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retourne l'instance de {@link StatTypeRegistryImpl} du Core, exposée
     * via l'interface {@link IStatTypeRegistry}.
     */
    @Override
    public IStatTypeRegistry getStatTypeRegistry() {
        return statTypeRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retourne {@link StatsComponent#KEY}, seule et unique référence à une
     * classe concrète du Core dans tout {@code CoreAPIImpl}. La clé elle-même
     * est typée sur l'interface {@link IStatsComponent} — c'est ce type-là,
     * et non {@code StatsComponent}, qui circule auprès des autres modules.
     */
    @Override
    public ComponentKey<IStatsComponent> getStatsComponentKey() {
        return StatsComponent.KEY;
    }

    /**
     * {@inheritDoc}
     *
     * @return la version courante de l'API, ex : {@code "2.0.0"}
     */
    @Override
    public String getVersion() {
        return API_VERSION;
    }
}
