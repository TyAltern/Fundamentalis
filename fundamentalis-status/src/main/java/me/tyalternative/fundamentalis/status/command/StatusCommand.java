package me.tyalternative.fundamentalis.status.command;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
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
 * Commande admin {@code /status} — application et retrait manuel d'effets de
 * statut, utile pour les tests.
 *
 * <h2>Usages</h2>
 * <ul>
 *   <li>{@code /status give <joueur> <effet> <niveau> [durée_ticks]} —
 *       applique un palier (permission {@code fundamentalis.status.give}).</li>
 *   <li>{@code /status clear <joueur> [effet]} — retire un effet précis, ou
 *       tous les effets si aucun type n'est précisé (permission {@code fundamentalis.status.clear}).</li>
 *   <li>{@code /status list <joueur>} — liste les effets actifs et en sommeil d'un joueur.</li>
 * </ul>
 *
 * <p>Comme {@code StatsCommand} dans le Core, cette commande passe
 * exclusivement par les interfaces de l'API ({@link IEntityService},
 * {@link IStatusComponent}, {@link IStatusEffectRegistry}) — jamais par les
 * classes concrètes de {@code fundamentalis-status}.
 */
public class StatusCommand implements CommandExecutor, TabCompleter {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final IEntityService                  entityService;
    private final IStatusEffectRegistry           effectRegistry;
    private final ComponentKey<IStatusComponent>  statusKey;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService  service d'accès aux ComponentHolder
     * @param effectRegistry registre des StatusEffectType, pour résoudre les noms d'effets
     * @param statusKey      clé typée du composant de statut
     */
    public StatusCommand(IEntityService entityService, IStatusEffectRegistry effectRegistry,
                         ComponentKey<IStatusComponent> statusKey) {
        this.entityService  = entityService;
        this.effectRegistry  = effectRegistry;
        this.statusKey       = statusKey;
    }

    // -------------------------------------------------------------------------
    // Exécution
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage : /status give|clear|list <joueur> ...");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "give"  -> handleGive(sender, args);
            case "clear" -> handleClear(sender, args);
            case "list"  -> handleList(sender, args);
            default -> {
                sender.sendMessage("§cSous-commande inconnue : " + args[0]);
                yield true;
            }
        };
    }

    // -------------------------------------------------------------------------
    // /status give <joueur> <effet> <niveau> [durée_ticks]
    // -------------------------------------------------------------------------

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fundamentalis.status.give")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUsage : /status give <joueur> <effet> <niveau> [durée_ticks]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable : " + args[1]);
            return true;
        }

        Optional<StatusEffectType> type = effectRegistry.find(args[2]);
        if (type.isEmpty()) {
            sender.sendMessage("§cEffet inconnu : " + args[2]);
            sender.sendMessage("§7Effets disponibles : " + effectRegistry.getAll().stream()
                    .map(StatusEffectType::getId).collect(Collectors.joining(", ")));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNiveau invalide : " + args[3]);
            return true;
        }

        long duration = type.get().getDefaultDurationTicks();
        if (args.length >= 5) {
            try {
                duration = Long.parseLong(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cDurée invalide : " + args[4] + " (ticks attendus)");
                return true;
            }
        }

        ComponentHolder holder = entityService.getPlayer(target);
        IStatusComponent status = holder.require(statusKey);

        ActiveStatusEffect applied = status.applyEffect(
                type.get(), level, duration, "command:" + sender.getName());

        sender.sendMessage("§a" + type.get().getId() + " niveau " + applied.level()
                + " appliqué à " + target.getName() + " pour " + duration + " ticks.");
        if (!sender.equals(target)) {
            target.sendMessage("§eVous subissez " + type.get().getId() + " niveau " + applied.level() + ".");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /status clear <joueur> [effet]
    // -------------------------------------------------------------------------

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fundamentalis.status.clear")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage : /status clear <joueur> [effet]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable : " + args[1]);
            return true;
        }

        ComponentHolder holder = entityService.getPlayer(target);
        IStatusComponent status = holder.require(statusKey);

        if (args.length >= 3) {
            Optional<StatusEffectType> type = effectRegistry.find(args[2]);
            if (type.isEmpty()) {
                sender.sendMessage("§cEffet inconnu : " + args[2]);
                return true;
            }
            boolean removed = status.removeEffect(type.get());
            sender.sendMessage(removed
                    ? "§a" + type.get().getId() + " retiré de " + target.getName() + "."
                    : "§7" + target.getName() + " n'avait pas cet effet actif.");
        } else {
            status.clearAllEffects();
            sender.sendMessage("§aTous les effets de " + target.getName() + " ont été retirés.");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /status list <joueur>
    // -------------------------------------------------------------------------

    private boolean handleList(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable : " + args[1]);
                return true;
            }
        } else if (sender instanceof Player senderPlayer) {
            target = senderPlayer;
        } else {
            sender.sendMessage("§cPrécisez un joueur : /status list <joueur>");
            return true;
        }

        ComponentHolder holder = entityService.getPlayer(target);
        IStatusComponent status = holder.require(statusKey);

        sender.sendMessage("§6========== Effets de " + target.getName() + " ==========");
        if (status.getAllEffects().isEmpty()) {
            sender.sendMessage("§7Aucun effet actif.");
        }
        for (ActiveStatusEffect effect : status.getAllEffects()) {
            String state = effect.active() ? "§a[actif]" : "§7[en sommeil]";
            sender.sendMessage("§7- §f" + effect.type().getId() + " §7niveau " + effect.level() + " " + state);
        }
        sender.sendMessage("§6==========================================");

        return true;
    }


    // -------------------------------------------------------------------------
    // Tab-complete
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
            completions.add("clear");
            completions.add("list");
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("list")) {
            effectRegistry.getAll().forEach(type -> completions.add(type.getId()));
        }

        String partial = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }
}
