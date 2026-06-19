package me.tyalternative.fundamentalis.api.entity;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * <H2>Point d'entrée pour récupérer le ComponentHolder d'une entité.</H2>
 *
 * C'est le service que les modules externes utilisent pour accéder
 * aux composants d'une entité. Il est disponible via le Service Locator:
 *<pre>{@code
 *   FundamentalisAPI.get().getEntityService()
 *}
 * <H3>Pourquoi un service séparé ? </H3>
 * Le ComponentHolder d'une entité est géré par le Core (cycle de vie,
 * persistance). Les autres modules ne doivent pas instancier eux-mêmes
 * des holders, ils les demandent à ce service.
 *
 * <H3> Exemple d'usage dans un listener de combat </H3>
 *
 *<pre>{@code
 *   IEntityService entities = FundamentalisAPI.get().getEntityService();
 *
 *   entities.get(attacker).ifPresent(holder -> {
 *       IStatsComponent stats = holder.require(IStatsComponent.KEY);
 *       double force = stats.getFinal(StatType.FORCE);
 *   });
 *}
 */
public interface IEntityService {
    /**
     * Retourne le ComponentHolder d'une entité vivante, ou empty()
     * si cette entité n'est pas trackée par Fundamentalis.
     *<p>
     * Une entité non trackée est une entité vanilla qui n'a pas encore
     * été enregistrée (ex : spawner vanilla sans config custom).
     */
    Optional<ComponentHolder> get(LivingEntity entity);

    /**
     * Retourne le ComponentHolder par UUID.
     * Utile quand l'entité peut être déchargée (scheduled tasks).
     */
    Optional<ComponentHolder> get(UUID entityId);

    /**
     * Version raccourcie pour les joueurs — toujours trackés après login.
     * Équivalent à get(player) mais retourne directement le holder
     * sans Optional, car l'absence d'un joueur trackée est une erreur.
     *
     * @throws IllegalStateException si le joueur n'est pas chargé.
     */
    ComponentHolder getPlayer(Player player);

    /**
     * Retourne tous les ComponentHolder actuellement chargés en mémoire.
     * La collection est immuable et représente un snapshot au moment de l'appel.
     */
    Collection<ComponentHolder> getAll();

    /**
     * Indique si cette entité est actuellement trackée.
     */
    boolean isTracked(LivingEntity entity);
}
