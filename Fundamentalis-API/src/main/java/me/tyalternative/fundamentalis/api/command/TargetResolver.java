package me.tyalternative.fundamentalis.api.command;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Résout l'argument cible d'une commande admin ({@code /stats}, {@code /status}…)
 * vers un {@link ComponentHolder}, qu'il s'agisse d'un nom de joueur ou d'un
 * UUID d'entité quelconque (joueur ou mob).
 *
 * <p>Centralise la logique partagée par toutes les commandes admin de
 * l'écosystème pour éviter de la dupliquer dans chaque module.
 *
 * <h2>Formats acceptés</h2>
 * <ul>
 *   <li>Un nom de joueur en ligne (ex : {@code "Steve"}).</li>
 *   <li>Un UUID complet (ex : {@code "550e8400-e29b-41d4-a716-446655440000"}),
 *       résolu via {@link IEntityService#get(UUID)} si l'entité est déjà
 *       trackée, ou enregistré à la volée via {@link IEntityService#getOrRegister}
 *       si l'UUID correspond à une entité chargée dans le monde mais jamais trackée.</li>
 * </ul>
 *
 * <h2>Utilisation typique</h2>
 * <pre>{@code
 * Optional<ComponentHolder> target = TargetResolver.resolve(entityService, args[1]);
 * if (target.isEmpty()) {
 *     sender.sendMessage("§cCible introuvable : " + args[1]);
 *     return true;
 * }
 * IStatsComponent stats = target.get().require(statsKey);
 * }</pre>
 */
public final class TargetResolver {

    private TargetResolver() {
    }

    // -------------------------------------------------------------------------
    // Détection de format
    // -------------------------------------------------------------------------

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    // -------------------------------------------------------------------------
    // Résolution
    // -------------------------------------------------------------------------

    /**
     * Résout l'argument en {@link ComponentHolder}.
     *
     * <p>Si l'argument est un UUID valide et qu'une entité chargée dans un
     * monde du serveur lui correspond mais n'est pas encore trackée, elle est
     * enregistrée à la volée via {@link IEntityService#getOrRegister} — voir
     * la documentation de cette méthode pour le détail du fire de
     * {@code EntityRegisteredEvent}.
     *
     * @param entityService service d'accès aux ComponentHolder
     * @param argument      l'argument brut fourni par l'utilisateur (nom ou UUID)
     * @return le holder résolu, ou {@link Optional#empty()} si rien ne correspond
     */
    public static Optional<ComponentHolder> resolve(IEntityService entityService, String argument) {
        if (argument == null || argument.isBlank()) return Optional.empty();

        if (UUID_PATTERN.matcher(argument).matches()) {
            resolveByUuid(entityService, argument);
        }

        return resolveByPlayerName(entityService, argument);
    }

    private static Optional<ComponentHolder> resolveByPlayerName(IEntityService entityService, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) player = Bukkit.getPlayer(name);
        if (player == null) return Optional.empty();
        return Optional.of(entityService.getPlayer(player));
    }

    private static Optional<ComponentHolder> resolveByUuid(IEntityService entityService, String rawUuid) {
        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // Déjà trackée — chemin le plus courant (joueur connecté ou mob déjà ciblé une fois)
        Optional<ComponentHolder> existing = entityService.get(uuid);
        if (existing.isPresent()) return existing;

        // Pas encore trackée : on cherche l'entité vivante correspondante dans
        // tous les mondes chargés, et on l'enregistre à la volée si trouvée.
        LivingEntity found = findLivingEntityByUuid(uuid);
        if (found == null) return Optional.empty();

        return Optional.of(entityService.getOrRegister(found));
    }

    /**
     * Parcourt les entités de tous les mondes chargés à la recherche d'une
     * {@link LivingEntity} correspondant à cet UUID.
     *
     * <p>Coût proportionnel au nombre d'entités chargées sur le serveur —
     * acceptable pour un usage ponctuel en commande admin, à ne pas appeler
     * dans une boucle chaude (ticker, pipeline de dégâts…).
     */
    private static LivingEntity findLivingEntityByUuid(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Auto-complétion
    // -------------------------------------------------------------------------

    /**
     * Construit la liste de complétion pour un argument de cible : les noms
     * des joueurs en ligne, plus l'UUID de l'entité actuellement regardée par
     * le joueur qui tape la commande (mob ou autre joueur en visée directe).
     *
     * <p>Le ray trace est limité à {@code maxDistance} blocs et ignore les
     * entités non-{@link LivingEntity} (item droppés, projectiles…).
     *
     * @param sender      l'expéditeur de la commande, pour le ray trace si c'est un joueur
     * @param maxDistance distance maximale de visée prise en compte, en blocs
     * @return liste de complétions : noms de joueurs en ligne + éventuel UUID regardé
     */
    public static List<String> completeTargets(CommandSender sender, double maxDistance) {
        List<String> completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());

        if (sender instanceof Player viewer) {
            LivingEntity looked = getLookedAtEntity(viewer, maxDistance);
            if (looked != null) {
                completions.add(looked.getUniqueId().toString());
            }
        }

        return completions;
    }

    /**
     * Résout l'entité actuellement regardée par un joueur via un ray trace,
     * en excluant le joueur lui-même.
     *
     * @param viewer      le joueur dont on suit la ligne de visée
     * @param maxDistance distance maximale de la recherche, en blocs
     * @return l'entité regardée, ou {@code null} si rien n'est trouvé dans la distance donnée
     */
    public static LivingEntity getLookedAtEntity(Player viewer, double maxDistance) {
        RayTraceResult result = viewer.getWorld().rayTraceEntities(
                viewer.getLocation(),
                viewer.getEyeLocation().getDirection(),
                maxDistance,
                entity -> entity instanceof LivingEntity && !entity.equals(viewer)
        );

        if (result == null || result.getHitEntity() == null) return null;
        return result.getHitEntity() instanceof LivingEntity living? living : null;
    }
}
