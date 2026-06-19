package me.tyalternative.fundamentalis.combat.command;

import me.tyalternative.fundamentalis.combat.weapon.CustomWeapon;
import me.tyalternative.fundamentalis.combat.weapon.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande admin {@code /giveweapon} - distribue une arme custom du catalogue
 * à un joueur.
 *
 * <h2>Usages</h2>
 * <ul>
 *   <li>{@code /giveweapon <id>} - se donne l'arme à soi-même.</li>
 *   <li>{@code /giveweapon <id> <joueur>} - donne l'arme à un autre joueur.</li>
 *   <li>{@code /giveweapon list} - liste les armes disponibles dans le catalogue.</li>
 * </ul>
 *
 * <p>Permission requise : {@code fundamentalis.combat.giveweapon} (défaut : op).
 */
public class WeaponCommand implements CommandExecutor, TabCompleter {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final WeaponRegistry weaponRegistry;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param weaponRegistry registre des armes, source du catalogue
     */
    public WeaponCommand(WeaponRegistry weaponRegistry) {
        this.weaponRegistry = weaponRegistry;
    }

    // -------------------------------------------------------------------------
    // Exécution
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fundamentalis.combat.giveweapon")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage : /giveweapon <id> [joueur] | /giveweapon list");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listWeapons(sender);
            return true;
        }

        String weaponId = args[0];
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
            sender.sendMessage("§cPrécisez un joueur : /giveweapon <id> <joueur>");
            return true;
        }

        boolean success = weaponRegistry.giveWeaponItem(weaponId, target);
        if (success && !sender.equals(target)) {
            sender.sendMessage("§a" + weaponId + " donnée à " + target.getName() + ".");
        }

        return true;
    }


    // -------------------------------------------------------------------------
    // Listing
    // -------------------------------------------------------------------------

    private void listWeapons(CommandSender sender) {
        sender.sendMessage("§6========== Armes disponibles ==========");
        for (CustomWeapon weapon : weaponRegistry.getAllWeapons().values()) {
            sender.sendMessage("§7- §f" + weapon.getId() + " §7(" + weapon.getName()
                    + ", " + weapon.getWeaponType().getDisplayName() + ")");
        }
        sender.sendMessage("§6========================================");
    }

    // -------------------------------------------------------------------------
    // Tab-complete
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.addAll(weaponRegistry.getAllWeapons().keySet());
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        String partial = args.length> 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }

}
