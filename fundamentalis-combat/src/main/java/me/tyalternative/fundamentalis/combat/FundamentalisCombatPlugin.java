package me.tyalternative.fundamentalis.combat;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.combat.command.WeaponCommand;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.listener.CombatListener;
import me.tyalternative.fundamentalis.combat.listener.WeaponSwitchListener;
import me.tyalternative.fundamentalis.combat.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée de {@code fundamentalis-combat}.
 *
 * <p>Dépend de {@code fundamentalis-api} et {@code fundamentalis-core} (déclarés
 * dans {@code plugin.yml}), via lesquels il accède à {@link IEntityService} et à
 * la {@link ComponentKey} typée du composant de stats — jamais directement aux
 * classes concrètes du Core.
 *
 * <h2>Ordre d'initialisation ({@code onEnable})</h2>
 * <ol>
 *   <li>Récupération des services du Core via {@link FundamentalisAPI#get()}.</li>
 *   <li>Création de {@link WeaponRegistry} et enregistrement du catalogue par défaut.</li>
 *   <li>Création de {@link DamageManager}, le pipeline de dégâts.</li>
 *   <li>Enregistrement de {@link CombatListener} et {@link WeaponSwitchListener}.</li>
 *   <li>Enregistrement des commandes admin ({@code /giveweapon}).</li>
 * </ol>
 */
public final class FundamentalisCombatPlugin extends JavaPlugin {

    // -------------------------------------------------------------------------
    // Sous-systèmes
    // -------------------------------------------------------------------------

    private WeaponRegistry weaponRegistry;
    private DamageManager damageManager;

    // -------------------------------------------------------------------------
    // onEnable
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        getLogger().info("[Combat] Démarrage…");

        // Étape 1 — Récupération des services du Core via le Service Locator.
        // Garanti disponible car fundamentalis-core est en hard depend.
        IEntityService entityService = FundamentalisAPI.get().getEntityService();
        ComponentKey<IStatsComponent> statsKey = FundamentalisAPI.get().getStatsComponentKey();

        // Étape 2 — Catalogue d'armes
        weaponRegistry = new WeaponRegistry(this, getLogger());
        weaponRegistry.registerDefaultWeapons();
        getLogger().info("[1/4] WeaponRegistry initialisé.");

        // Étape 3 — Pipeline de dégâts
        damageManager = new DamageManager(entityService, statsKey);
        getLogger().info("[2/4] DamageManager initialisé.");

        // Étape 4 — Listeners
        boolean debugLog = getConfig().getBoolean("debug-log", false);
        getServer().getPluginManager().registerEvents(
                new CombatListener(damageManager, weaponRegistry, getLogger(), debugLog), this);
        getServer().getPluginManager().registerEvents(
                new WeaponSwitchListener(weaponRegistry), this);
        getLogger().info("[3/4] Listeners enregistrés (CombatListener, WeaponSwitchListener).");

        // Étape 5 — Commandes
        initCommands();
        getLogger().info("[4/4] Commandes enregistrées.");

        getLogger().info("[Combat] Démarré (" + weaponRegistry.getAllWeapons().size() + " armes au catalogue).");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Combat] Arrêt terminé.");
    }

    // -------------------------------------------------------------------------
    // Initialisation des commandes
    // -------------------------------------------------------------------------

    private void initCommands() {
        WeaponCommand weaponCommand = new WeaponCommand(weaponRegistry);
        var command = getCommand("giveweapon");
        if (command == null) {
            getLogger().severe("Commande 'giveweapon' introuvable dans plugin.yml !");
            return;
        }
        command.setExecutor(weaponCommand);
        command.setTabCompleter(weaponCommand);
    }

    // -------------------------------------------------------------------------
    // Accesseurs (pour d'éventuels tests ou futures extensions du module)
    // -------------------------------------------------------------------------

    /**
     * @return le registre des armes custom
     */
    public WeaponRegistry getWeaponRegistry() {
        return weaponRegistry;
    }

    /**
     * @return le pipeline de dégâts central
     */
    public DamageManager getDamageManager() {
        return damageManager;
    }
}