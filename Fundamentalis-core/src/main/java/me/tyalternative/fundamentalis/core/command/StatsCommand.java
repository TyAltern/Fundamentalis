package me.tyalternative.fundamentalis.core.command;

import me.tyalternative.fundamentalis.api.command.TargetResolver;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.event.stats.StatChangeEvent;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.core.stats.StatsComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Commande admin {@code /stats} — consultation et modification des stats
 * de base d'un joueur.
 *
 * <h2>Usages</h2>
 * <ul>
 *   <li>{@code /stats} — affiche ses propres stats.</li>
 *   <li>{@code /stats <joueur>} — affiche les stats d'un autre joueur.</li>
 *   <li>{@code /stats set <joueur> <stat> <valeur>} — remplace la valeur de base
 *       (permission {@code fundamentalis.stats.set}).</li>
 *   <li>{@code /stats add <joueur> <stat> <valeur>} — ajoute/soustrait à la valeur
 *       de base (permission {@code fundamentalis.stats.set}).</li>
 * </ul>
 *
 * <p>{@code <cible>} accepte soit un nom de joueur en ligne, soit l'UUID
 * d'une entité quelconque (mob compris) — voir {@link TargetResolver}. Une
 * entité non encore trackée par Fundamentalis (mob vanilla jamais visé
 * auparavant) est enregistrée à la volée lors de la résolution.
 *
 * <p>Cette commande passe exclusivement par {@link IEntityService} et
 * {@link IStatsComponent} — elle ne dépend d'aucune classe concrète du Core
 * pour la lecture/écriture des stats, afin de rester un exemple d'usage
 * correct de l'API pour les futurs modules.
 *
 * <p>Les stats consultables sont celles enregistrées dans
 * {@link IStatTypeRegistry}, ce qui inclut automatiquement les stats custom
 * ajoutées par d'autres plugins (ex : {@code mana} depuis {@code fundamentalis-spells}).
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final IEntityService    entityService;
    private final IStatTypeRegistry statTypeRegistry;

    /** Distance maximale de ray trace pour résoudre l'entité regardée, en blocs. */
    private static final double LOOK_DISTANCE = 32.0;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService    service d'accès aux ComponentHolder
     * @param statTypeRegistry registre des StatType, pour résoudre les noms de stats
     */
    public StatsCommand(IEntityService entityService, IStatTypeRegistry statTypeRegistry) {
        this.entityService    = entityService;
        this.statTypeRegistry = statTypeRegistry;
    }

    // -------------------------------------------------------------------------
    // Exécution
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /stats - afficher ses propres stats
        if (args.length == 0) {
            if (!(sender instanceof  Player player)) {
                sender.sendMessage("§cCette commande nécessite une joueur. Précisez un cible : /stats <cible>");
                return true;
            }
            displayStats(entityService.getPlayer(player), player.getName(), sender);
            return true;
        }

        // /stats set|add <cible> <stat> <valeur>
        if (args.length == 4 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            return handleModify(sender, args);
        }

        // /stats <cible> — afficher les stats d'une autre cible (joueur ou UUID d'entité)
        if (args.length == 1) {
            Optional<ComponentHolder> target = TargetResolver.resolve(entityService, args[0]);
            if (target.isEmpty()) {
                sender.sendMessage("§cJoueur introuvable : " + args[0]);
                return true;
            }
            displayStats(target.get(), describeTarget(target.get()), sender);
            return true;
        }

        sender.sendMessage("§cUsage : /stats [cible] | /stats set|add <cible> <stat> <valeur>");
        return true;
    }

    // -------------------------------------------------------------------------
    // Modification (set / add)
    // -------------------------------------------------------------------------

    /**
     * Traite {@code /stats set <cible> <stat> <valeur>} et
     * {@code /stats add <cible> <stat> <valeur>}.
     */
    private boolean handleModify(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fundamentalis.stats.set")) {
            sender.sendMessage("§cVous n'avez pas la permission de modifier les stats.");
            return true;
        }

        boolean isSet = args[0].equalsIgnoreCase("set");

        Optional<ComponentHolder> targetHolder = TargetResolver.resolve(entityService, args[1]);
        if (targetHolder.isEmpty()) {
            sender.sendMessage("§cCible introuvable : " + args[1]);
            return true;
        }
        ComponentHolder holder = targetHolder.get();

        Optional<StatType> statType = statTypeRegistry.find(args[2]);
        if (statType.isEmpty()) {
            sender.sendMessage("§cStat inconnue : " + args[2]);
            sender.sendMessage("§7Stats disponibles : " + statTypeRegistry.getAll().stream()
                    .map(StatType::getId).collect(Collectors.joining(", ")));
            return true;
        }

        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cValeur invalide : " + args[3] + " (nombre entier attendu).");
            return true;
        }

        // On passe par le ComponentHolder — jamais d'accès direct à une classe du Core
        IStatsComponent stats  = holder.require(StatsComponent.KEY);

        int applied;
        if (isSet) {
            applied = stats.setBase(statType.get(), value, StatChangeEvent.Cause.COMMAND);
        } else {
            applied = stats.getBase(statType.get()) + value;
            applied = stats.setBase(statType.get(), applied, StatChangeEvent.Cause.COMMAND);
        }

        String targetName = describeTarget(holder);
        String verb = isSet ? "définie à" : "modifiée, nouvelle valeur :";
        sender.sendMessage("§a" + statType.get().getId() + " de " + targetName + " " + verb + " " + applied);

        if (holder.getEntity() instanceof Player targetPlayer && !sender.equals(targetPlayer)) {
            targetPlayer.sendMessage("§eVotre " + statType.get().getId() + " a été " + verb + " " + applied);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Affichage
    // -------------------------------------------------------------------------

    /**
     * Affiche toutes les stats de base de {@code target} à {@code viewer}.
     *
     * <p>Itère sur {@link IStatTypeRegistry#getAll()} plutôt que sur un enum
     * fixe, afin d'afficher automatiquement les stats custom ajoutées par
     * d'autres modules.
     */
    private void displayStats(ComponentHolder holder, String targetName, CommandSender viewer) {
        IStatsComponent stats  = holder.require(StatsComponent.KEY);

        boolean isSelf = holder.getEntity() instanceof Player p && viewer.equals(p);
        String title = isSelf ? "Vos statistiques" : "Statistiques de " + targetName;
        viewer.sendMessage("§6========== " + title + " ==========");

        for (StatType type : statTypeRegistry.getAll()) {
            int baseValue = stats.getBase(type);
            double finalValue = stats.getFinal(type);
            viewer.sendMessage("§7" + type.getId() + " : §f" + baseValue + " §8(" + finalValue + ")");
        }

        viewer.sendMessage("§6" + "=".repeat(20 + title.length()));
    }

    /**
     * Construit un nom lisible pour une cible — nom du joueur, ou type
     * d'entité + UUID tronqué pour un mob.
     */
    private String describeTarget(ComponentHolder holder) {
        var entity = holder.getEntity();
        if (entity instanceof Player player) return player.getName();
        if (entity == null) return "entité inconnue";
        return entity.getType().name() + " (" + holder.getEntityId().substring(0, 8) + "…)";
    }

    // -------------------------------------------------------------------------
    // Tab-complete
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete (CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("set");
            completions.add("add");
            completions.addAll(TargetResolver.completeTargets(sender, LOOK_DISTANCE));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            completions.addAll(TargetResolver.completeTargets(sender, LOOK_DISTANCE));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            statTypeRegistry.getAll().forEach(p -> completions.add(p.getId()));
        }

        String partial = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }
}
