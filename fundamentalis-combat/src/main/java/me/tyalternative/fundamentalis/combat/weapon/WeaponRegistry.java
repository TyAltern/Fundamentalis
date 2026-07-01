package me.tyalternative.fundamentalis.combat.weapon;


import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registre central des {@link CustomWeapon} et point d'entrée pour
 * l'identification d'un {@link ItemStack} comme arme custom.
 *
 * <p>Combine les responsabilités de l'ancien {@code WeaponManager} et
 * {@code WeaponRegistry} : enregistrement, résolution par id, résolution
 * depuis un item via PDC, et catalogue par défaut.
 *
 * <p>Le catalogue par défaut enregistré dans {@link #registerDefaultWeapons()}
 * reprend les 5 armes de mêlée de l'ancienne version de Fundamentalis
 * (épée, hache, lance, poings - une par famille).
 */
public class WeaponRegistry {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Plugin plugin;
    private final Logger logger;

    /** Map id → arme. LinkedHashMap implicite via HashMap suffisant ici (pas d'ordre requis). */
    public final Map<String, CustomWeapon> registeredWeapons = new HashMap<>();

    /** Clé PDC partagée pour identifier une arme sur un ItemStack. */
    private final NamespacedKey weaponIdKey;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param plugin instance du plugin Combat, pour construire la {@link NamespacedKey}
     * @param logger logger du plugin pour confirmer les enregistrements
     */
    public WeaponRegistry(Plugin plugin, Logger logger) {
        this.plugin      = plugin;
        this.logger      = logger;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");
    }

    // -------------------------------------------------------------------------
    // Catalogue par défaut
    // -------------------------------------------------------------------------

    /**
     * Enregistre les 5 armes de mêlée de base, une par famille de
     * {@link WeaponType} (hors BOW et MAGIC_STAFF).
     *
     * <p>Sert de point de départ - d'autres armes peuvent être ajoutées en
     * appelant {@link #registerWeapon(CustomWeapon)} depuis un autre plugin
     * ou une extension future de ce module.
     */
    public void registerDefaultWeapons() {
        registerSwords();
        registerAxes();
        registerSpears();
        registerFists();


        // BOW et MAGIC_STAFF volontairement omis : leur pipeline spécifique
        // (projectiles, sorts) n'est pas encore implémenté dans Combat v1.

        logger.info("[WeaponRegistry] " + registeredWeapons.size() + " armes enregistrées par défaut.");
    }


    public void registerSwords() {

        CustomWeapon woodenSword = new CustomWeapon(
                "wooden_sword", WeaponType.SWORD, "Épée en bois",
                4.0, 1.6, 3.0, Material.WOODEN_SWORD
        );
        registerWeapon(woodenSword);

        CustomWeapon ironSword = new CustomWeapon(
                "iron_sword", WeaponType.SWORD, "Épée en fer",
                6.0, 1.6, 3.0, Material.IRON_SWORD
        );
        registerWeapon(ironSword);

        CustomWeapon excalibur = new CustomWeapon(
                "excalibur", WeaponType.SWORD, "Excalibur",
                12.0, 1.4, 2.5, Material.DIAMOND_SWORD
        );
        registerWeapon(excalibur);

        CustomWeapon katana = new CustomWeapon("katana", WeaponType.SWORD, "Katana",
                7.0, 2.0, 3.0, Material.NETHERITE_SWORD
        ) {
            @Override
            public void onHitEffect(DamageInfo info) {
                if (Math.random() < 0.5) {
                    info.getAttacker().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4*20, 1, false, false));
                } else {
                    info.getVictim().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 4*20, 1, false, false));
                }
            }
        };
        registerWeapon(katana);

    }

    public void registerAxes() {

        CustomWeapon lumberjackAxe = new CustomWeapon(
                "lumberjack_axe", WeaponType.AXE, "Hache du bûcheron",
                8.0, 0.9, 3.0, Material.IRON_AXE
        );
        registerWeapon(lumberjackAxe);

        CustomWeapon battleAxe = new CustomWeapon(
                "battle_axe", WeaponType.AXE, "Hache de guerre",
                11.0, 0.8, 3.0, Material.DIAMOND_AXE
        );
        registerWeapon(battleAxe);

        CustomWeapon executionerAxe = new CustomWeapon(
                "executioner_axe", WeaponType.AXE, "Hache du bourreau",
                15.0, 0.6, 2.5, Material.NETHERITE_AXE
        ) {
            @Override
            public void onHitEffect(DamageInfo info) {
                info.getAttacker().damage(info.getFinalDamage()/10);
            }
        };
        registerWeapon(executionerAxe);

        CustomWeapon doubleAxe = new CustomWeapon(
                "double_axe", WeaponType.AXE, "Hache double",
                9.5, 1.0, 3.0, Material.GOLDEN_AXE
        );
        registerWeapon(doubleAxe);

    }

    public void registerSpears() {
        // Lance de base
        CustomWeapon woodenSpear = new CustomWeapon(
                "wooden_spear", WeaponType.SPEAR, "Lance en bois",
                4.5, 1.4, 4.5, Material.STICK
        );
        registerWeapon(woodenSpear);

        // Lance de chevalier
        CustomWeapon knightLance = new CustomWeapon(
                "knight_lance", WeaponType.SPEAR, "Lance de chevalier",
                6.0, 1.3, 5.0, Material.IRON_HOE
        );
        registerWeapon(knightLance);

        // Trident (lance magique)
        CustomWeapon trident = new CustomWeapon(
                "trident", WeaponType.SPEAR, "Trident",
                7.5, 1.5, 4.0, Material.TRIDENT
        );
        registerWeapon(trident);

        // Hallebarde (lance lourde)
        CustomWeapon halberd = new CustomWeapon(
                "halberd", WeaponType.SPEAR, "Hallebarde",
                8.0, 1.1, 4.5, Material.DIAMOND_HOE
        );
        registerWeapon(halberd);

        CustomWeapon chainWhip = new CustomWeapon(
                "chain_whip", WeaponType.SPEAR, "Fouet enchaîné",
                6.0, 1.1, 4.0, Material.CHAIN
        ) {
            @Override
            public void onHitEffect(DamageInfo info) {
                LivingEntity attacker = info.getAttacker();
                if (attacker == null) return;

                StatusEffectType chainType = FundamentalisAPI.get().getStatusEffectRegistry().getOrThrow("chain");
                FundamentalisAPI.get().getEntityService().get(attacker)
                        .flatMap(holder -> holder.get(FundamentalisAPI.get().getStatusComponentKey()))
                        .ifPresent(status -> status.applyEffect(
                                chainType, 2, chainType.getDefaultDurationTicks(), "weapon:" + getId()));
            }
        };
        registerWeapon(chainWhip);
    }

    public void registerFists() {
        // Gants de cuir
        CustomWeapon leatherGloves = new CustomWeapon(
                "leather_gloves", WeaponType.FIST, "Gants de cuir",
                2.5, 2.0, 2.5, Material.LEATHER
        );
        registerWeapon(leatherGloves);

        // Gantelets de fer
        CustomWeapon ironGauntlets = new CustomWeapon(
                "iron_gauntlets", WeaponType.FIST, "Gantelets de fer",
                4.0, 1.8, 2.5, Material.IRON_INGOT
        );
        registerWeapon(ironGauntlets);

        // Griffes (très rapides)
        CustomWeapon claws = new CustomWeapon(
                "claws", WeaponType.FIST, "Griffes",
                3.5, 2.4, 2.0, Material.FLINT
        );
        registerWeapon(claws);

        // Poings du dragon (légendaire)
        CustomWeapon dragonFists = new CustomWeapon(
                "dragon_fists", WeaponType.FIST, "Poings du dragon",
                6.0, 2.2, 2.0, Material.NETHERITE_INGOT
        ){
            @Override
            public void onHitEffect(DamageInfo info) {
                info.getVictim().setFireTicks(80);
                info.getAttacker().setFireTicks(20);
            }
        };
        registerWeapon(dragonFists);
    }

    // -------------------------------------------------------------------------
    // Enregistrement et résolution
    // -------------------------------------------------------------------------

    /**
     * Enregistre une arme dans le catalogue.
     *
     * @param weapon l'arme à enregistrer
     * @throws IllegalStateException si une arme avec le même id est déjà enregistrée
     */
    public void registerWeapon(CustomWeapon weapon) {
        if (registeredWeapons.containsKey(weapon.getId())) {
            throw new IllegalStateException(
                    "Une arme avec l'id '" + weapon.getId() + "' est déjà enregistrée.");
        }
        registeredWeapons.put(weapon.getId(), weapon);
        logger.fine("[WeaponRegistry] Arme enregistrée : " + weapon.getName() + " (" + weapon.getId() + ")");
    }

    /**
     * Résout une arme par son identifiant.
     *
     * @param id identifiant de l'arme (ex : {@code "iron_sword"})
     * @return l'arme correspondante, ou {@code null} si inconnue
     */
    public CustomWeapon getWeapon(String id) {
        return registeredWeapons.get(id);
    }

    /**
     * Résout l'arme représentée par un {@link ItemStack}, via sa clé PDC.
     *
     * @param item l'item à inspecter - il peut être {@code null} ou sans meta
     * @return l'arme correspondante, ou {@code null} si l'item n'est pas une arme custom connue
     */
    public CustomWeapon getWeaponFromItemStack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(weaponIdKey, PersistentDataType.STRING)) {
            return null;
        }

        String weaponId = meta.getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
        return getWeapon(weaponId);
    }

    /**
     * @param item l'item à vérifier
     * @return {@code true} si cet item représente une arme custom enregistrée
     */
    public boolean isCustomWeapon(ItemStack item) {
        return getWeaponFromItemStack(item) != null;
    }

    /**
     * @return une copie immuable du catalogue complet, indexé par id
     */
    public Map<String, CustomWeapon> getAllWeapons() {
        return Collections.unmodifiableMap(new HashMap<>(registeredWeapons));
    }

    // -------------------------------------------------------------------------
    // Distribution d'item
    // -------------------------------------------------------------------------

    /**
     * Crée et donne une copie de l'arme à un joueur.
     *
     * @param weaponId identifiant de l'arme à donner
     * @param player   le joueur qui recevra l'item
     * @return {@code true} si l'arme a été trouvée et donnée, {@code false} si l'id est inconnu
     */
    public boolean giveWeaponItem(String weaponId, Player player) {
        CustomWeapon weapon = getWeapon(weaponId);
        if (weapon == null) {
            player.sendMessage("§cArme introuvable : " + weaponId);
            return false;
        }
        ItemStack item = weapon.createItemStack(plugin);
        player.getInventory().addItem(item);
        player.sendMessage("§aVous avez reçu : §6" + weapon.getName());
        return true;
    }


}
