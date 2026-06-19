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

public final class FundamentalisCombatPlugin extends JavaPlugin {

    private IEntityService entityService;
    private ComponentKey<IStatsComponent> statsKey;
    private WeaponRegistry weaponRegistry;
    private DamageManager  damageManager;

    @Override
    public void onEnable() {
        entityService = FundamentalisAPI.get().getEntityService();
        statsKey = FundamentalisAPI.get().getStatsComponentKey();

        weaponRegistry = new WeaponRegistry(this, getLogger());
        damageManager = new DamageManager(entityService, statsKey);

        weaponRegistry.registerDefaultWeapons();

        initCommands();

        initListener();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void initListener() {
        CombatListener combatListener = new CombatListener(damageManager, weaponRegistry, getLogger(), true);
        getServer().getPluginManager().registerEvents(combatListener, this);

        WeaponSwitchListener weaponSwitchListener = new WeaponSwitchListener(weaponRegistry);
        getServer().getPluginManager().registerEvents(weaponSwitchListener, this);

    }

    /**
     * Enregistre les commandes admin de Fundamentalis-Combat.
     *
     * <p>La commande {@code giveweapon} doit être déclarée dans le {@code plugin.yml}
     * pour que {@code getCommand("giveweapon")} ne retourne pas {@code null}.
     */
    private void initCommands() {
        WeaponCommand weaponCommand = new WeaponCommand(weaponRegistry);
        var command = getCommand("giveweapon");
        if (command == null) {
            getLogger().severe("Commande 'giveweapon' introuvable dans plugin.yml ! "
                    + "La commande /giveweapon ne fonctionnera pas.");
            return;
        }
        command.setExecutor(weaponCommand);
        command.setTabCompleter(weaponCommand);
        getLogger().info("Commande /giveweapon enregistrée.");
    }
}
