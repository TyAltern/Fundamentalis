package me.tyalternative.fundamentalis.combat.weapon;


import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
     * <p>Sert de point de départ — d'autres armes peuvent être ajoutées en
     * appelant {@link #registerWeapon(CustomWeapon)} depuis un autre plugin
     * ou une extension future de ce module.
     */
    public void registerDefaultWeapons() {
        registerWeapon(new CustomWeapon(
                "iron_sword", WeaponType.SWORD, "Épée en fer",
                6.0, 1.6, 3.0, Material.IRON_SWORD, 600
        ) {
            @Override
            public void onHitEffect(DamageInfo info) {
                info.getAttacker().sendMessage("Attack!");
            }
        });
        registerWeapon(new CustomWeapon(
                "iron_axe", WeaponType.AXE, "Hache en fer",
                9.0, 0.9, 3.0, Material.IRON_AXE, 1100
        ));
        registerWeapon(new CustomWeapon(
                "iron_spear", WeaponType.SPEAR, "Lance en fer",
                5.0, 1.4, 4.5, Material.IRON_HOE, 700
        ));
        registerWeapon(new CustomWeapon(
                "fists", WeaponType.FIST, "Poings",
                3.0, 2.0, 2.5, Material.LEATHER, 500
        ));
        // BOW et MAGIC_STAFF volontairement omis : leur pipeline spécifique
        // (projectiles, sorts) n'est pas encore implémenté dans Combat v1.

        logger.info("[WeaponRegistry] " + registeredWeapons.size() + " armes enregistrées par défaut.");
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
     * @param item l'item à inspecter — peut être {@code null} ou sans meta
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
