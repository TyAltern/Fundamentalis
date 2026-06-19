package me.tyalternative.fundamentalis.core.command;


import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.event.stats.StatChangeEvent;
import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.core.component.ComponentHolderImpl;
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
                sender.sendMessage("§cCette commande nécessite un joueur. Précisez un nom : /stats <joueur>");
                return true;
            }
            displayStats(player, sender);
            return true;
        }

        // /stats set|add <joueur> <stat> <valeur>
        if (args.length == 4 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            return handleModify(sender, args);
        }

        // /stats <joueur> — afficher les stats d'un autre joueur
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable : " + args[0]);
                return true;
            }
            displayStats(target, sender);
            return true;
        }

        sender.sendMessage("§cUsage : /stats [joueur] | /stats set|add <joueur> <stat> <valeur>");
        return true;
    }

    // -------------------------------------------------------------------------
    // Modification (set / add)
    // -------------------------------------------------------------------------

    /**
     * Traite {@code /stats set <joueur> <stat> <valeur>} et
     * {@code /stats add <joueur> <stat> <valeur>}.
     */
    private boolean handleModify(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fundamentalis.stats.set")) {
            sender.sendMessage("§cVous n'avez pas la permission de modifier les stats.");
            return true;
        }

        boolean isSet = args[0].equalsIgnoreCase("set");

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable : " + args[1]);
            return true;
        }

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
        ComponentHolder holder = entityService.getPlayer(target);
        IStatsComponent stats  = holder.require(StatsComponent.KEY);

        int applied;
        if (isSet) {
            applied = stats.setBase(statType.get(), value, StatChangeEvent.Cause.COMMAND);
        } else {
            applied = stats.getBase(statType.get()) + value;
            applied = stats.setBase(statType.get(), applied, StatChangeEvent.Cause.COMMAND);
        }

        String verb = isSet ? "définie à" : "modifiée, nouvelle valeur :";
        sender.sendMessage("§a" + statType.get().getId() + " de " + target.getName() + " " + verb + " " + applied);
        if (!sender.equals(target)) {
            target.sendMessage("§eVotre " + statType.get().getId() + " a été " + verb + " " + applied);
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
    private void displayStats(Player target, CommandSender viewer) {
        ComponentHolder holder = entityService.getPlayer(target);
        IStatsComponent stats  = holder.require(StatsComponent.KEY);

        String title = viewer.equals(target) ? "Vos statistiques" : "Statistiques de " + target.getName();
        viewer.sendMessage("§6========== " + title + " ==========");

        for (StatType type : statTypeRegistry.getAll()) {
            int baseValue = stats.getBase(type);
            double finalValue = stats.getFinal(type);
            viewer.sendMessage("§7" + type.getId() + " : §f" + baseValue + " §8(" + finalValue + ")");
        }

        viewer.sendMessage("§6" + "=".repeat(20 + title.length()));

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
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            statTypeRegistry.getAll().forEach(p -> completions.add(p.getId()));
        }

        String partial = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }


}
