package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Boucle centrale qui fait avancer tous les {@link StatusComponent} actifs
 * d'un tick, à chaque tick serveur.
 *
 * <p>Plutôt qu'une {@link org.bukkit.scheduler.BukkitTask BukkitTask} par
 * effet actif (approche de l'ancienne version), un unique ticker itère sur
 * toutes les entités trackées par {@link IEntityService} et appelle
 * {@link StatusComponent#tick(long)} sur celles qui ont un composant de
 * statut — une seule tâche planifiée pour tout le serveur, quel que soit le
 * nombre d'effets actifs. C'est l'approche la plus performante demandée :
 * pas de coût de création/destruction de tâche à chaque application/expiration
 * d'effet, et une itération unique amortie sur toutes les entités.
 *
 * <p>Les entités sans {@link StatusComponent} (pas encore de palier appliqué)
 * sont ignorées sans coût significatif — {@link ComponentHolder#get} est en
 * O(1) sur une {@link java.util.concurrent.ConcurrentHashMap ConcurrentHashMap}.
 */
public class StatusTicker {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Plugin                         plugin;
    private final IEntityService                 entityService;
    private final ComponentKey<IStatusComponent> statusKey;

    private BukkitTask task;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param plugin        instance du plugin Status, pour planifier la tâche Bukkit
     * @param entityService service d'accès à toutes les entités trackées
     * @param statusKey     clé typée du composant de statut
     */
    public StatusTicker(Plugin plugin, IEntityService entityService, ComponentKey<IStatusComponent> statusKey) {
        this.plugin        = plugin;
        this.entityService = entityService;
        this.statusKey     = statusKey;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    /**
     * Démarre le ticker — une tâke répétée toutes les 1 tick.
     *
     * <p>Doit être appelé une seule fois dans {@code StatusPlugin#onEnable}.
     */
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    /**
     * Arrête le ticker. Doit être appelé dans {@code StatusPlugin#onDisable}.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // -------------------------------------------------------------------------
    // Boucle principale
    // -------------------------------------------------------------------------

    /**
     * Fait avancer tous les {@link StatusComponent} d'un tick.
     *
     * <p>Itère sur un snapshot de {@link IEntityService#getAll()} — chaque
     * appel est indépendant et ne mute pas la collection sous-jacente pendant
     * l'itération, ce qui reste sûr même si un comportement détache/rattache
     * des entités pendant le tick (ex : mort d'un mob suite à un DoT).
     */
    private void tickAll() {
        long currentTick = Bukkit.getCurrentTick();

        for (ComponentHolder holder : entityService.getAll()) {
            if (!holder.isValid()) continue;

            holder.get(statusKey).ifPresent(status -> {
                if (status instanceof StatusComponent component) {
                    component.tick(currentTick);
                }
            });
        }
    }
}
