package me.tyalternative.fundamentalis.combat.listener;

import me.tyalternative.fundamentalis.combat.weapon.CustomWeapon;
import me.tyalternative.fundamentalis.combat.weapon.WeaponRegistry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.math.MathContext;

/**
 * Synchronise les attributs Bukkit natifs {@code ATTACK_SPEED} et
 * {@code ENTITY_INTERACTION_RANGE} (portée d'interaction/d'attaque) sur
 * les statistiques de l'arme actuellement tenue en main principale.
 *
 * <p>Reproduit la mécanique {@code updateAttackSpeed}/{@code updateReach} de
 * l'ancienne version : chaque {@link CustomWeapon} masque les attributs
 * d'attaque natifs de son {@link org.bukkit.inventory.meta.ItemMeta} (voir
 * {@link CustomWeapon#applyMetadata}).
 *
 * <p>Se déclenche à trois moments : connexion du joueur, changement de slot
 * de hotbar, et échange main principale/secondaire. Si la main principale ne
 * contient pas d'arme custom, les valeurs vanilla par défaut sont restaurées.
 */
public class WeaponSwitchListener implements Listener{

    // -------------------------------------------------------------------------
    // Constantes — valeurs par défaut quand aucune arme custom n'est tenue
    // -------------------------------------------------------------------------

    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final double DEFAULT_REACH        = 3.0;

    private static final double MIN_ATTACK_SPEED = 0.1;
    private static final double MAX_ATTACK_SPEED = 1024.0;
    private static final double MIN_REACH        = 0.5;
    private static final double MAX_REACH        = 16.0;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final WeaponRegistry weaponRegistry;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param weaponRegistry registre des armes, pour résoudre l'item tenu en main
     */
    public WeaponSwitchListener(WeaponRegistry weaponRegistry) {
        this.weaponRegistry = weaponRegistry;
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------

    /**
     * Recalcule les attributs à la connexion — nécessaire car les attributs
     * appliqués lors de la session précédente ne sont pas persistés par Bukkit
     * de façon fiable d'une connexion à l'autre.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateAttributes(event.getPlayer());
    }

    /**
     * Recalcule les attributs à chaque changement de slot de la hotbar.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        // L'item en main n'a pas encore changé au moment de l'event,
        // donc on lit le nouveau slot directement depuis l'inventaire.
        Player player = event.getPlayer();
        var item = player.getInventory().getItem(event.getNewSlot());
        applyForItem(player, item);

    }




    // -------------------------------------------------------------------------
    // Logique de mise à jour
    // -------------------------------------------------------------------------

    /**
     * Recalcule les attributs en lisant directement l'item actuellement en
     * main principale du joueur.
     *
     * @param player le joueur dont les attributs doivent être recalculés
     */
    public void updateAttributes(Player player) {
        applyForItem(player, player.getInventory().getItemInMainHand());
    }

    /**
     * Applique les attributs {@code ATTACK_SPEED} et {@code ENTITY_INTERACTION_RANGE}
     * correspondant à l'item donné (ou les valeurs par défaut s'il ne s'agit pas
     * d'une arme custom).
     */
    private void applyForItem(Player player, ItemStack item) {
        CustomWeapon weapon = weaponRegistry.getWeaponFromItemStack(item);

        double attackSpeed = weapon != null ? weapon.getAttackSpeed() : DEFAULT_ATTACK_SPEED;
        double reach       = weapon != null ? weapon.getReach()       : DEFAULT_REACH;

        attackSpeed = clamp(attackSpeed, MIN_ATTACK_SPEED, MAX_ATTACK_SPEED);
        reach       = clamp(reach, MIN_REACH, MAX_REACH);

        var attackSpeedAttr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(attackSpeed);
        }

        var reachAttr = player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE);
        if (reachAttr != null) {
            reachAttr.setBaseValue(reach);
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
