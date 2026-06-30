package me.tyalternative.fundamentalis.status.listener;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.event.entity.EntityRegisteredEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.status.StatusComponent;
import me.tyalternative.fundamentalis.status.StatusEffectFactoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Attache un {@link StatusComponent} à chaque entité enregistrée par le Core,
 * en suivant le pattern documenté pour {@link EntityRegisteredEvent}.
 *
 * <p>Priorité {@link EventPriority#NORMAL} (par défaut) : le Core attache
 * d'abord son {@code StatsComponent} à priorité {@code LOWEST}
 * (voir {@code EntityTracker}), donc au moment où ce listener s'exécute,
 * {@link IStatsComponent} est déjà disponible sur le holder et peut être
 * transmis au {@link StatusComponent} nouvellement créé.
 */
public class StatusAttachListener implements Listener {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final StatusEffectFactoryRegistry factoryRegistry;
    private final ComponentKey<IStatsComponent> statsKey;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param factoryRegistry registre des fabriques d'effets, transmis à chaque nouveau StatusComponent
     */
    public StatusAttachListener(StatusEffectFactoryRegistry factoryRegistry, ComponentKey<IStatsComponent> statsKey) {
        this.factoryRegistry = factoryRegistry;
        this.statsKey        = statsKey;
    }

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    /**
     * Attache un nouveau {@link StatusComponent} au holder fraîchement enregistré.
     */
    @EventHandler
    public void onEntityRegistered(EntityRegisteredEvent event) {
        var holder = event.getHolder();

        // Le composant de stats n'est pas garanti présent (entité trackée sans
        // stats RPG) - on transmet null dans ce cas, les effets STAT_MODIFIER
        // deviennent alors silencieusement no-op (voir StrengthEffect par exemple).
        IStatsComponent stats = holder.get(statsKey).orElse(null);

        StatusComponent statusComponent = new StatusComponent(holder, factoryRegistry, stats);
        holder.attach(StatusComponent.KEY, statusComponent);
    }
}
