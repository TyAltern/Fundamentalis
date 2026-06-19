package me.tyalternative.fundamentalis.combat.listener;

import me.tyalternative.fundamentalis.combat.damage.AttackType;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageResult;
import me.tyalternative.fundamentalis.combat.weapon.CustomWeapon;
import me.tyalternative.fundamentalis.combat.weapon.WeaponRegistry;
import org.bukkit.damage.DamageScaling;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Intercepte les attaques de mêlée entre entités vivantes et les redirige
 * intégralement vers {@link DamageManager}, en annulant l'event Bukkit natif.
 *
 * <p>Reprend la logique de l'ancienne version : dès qu'un {@link LivingEntity}
 * frappe un autre {@link LivingEntity}, l'event vanilla est annulé et
 * remplacé par le pipeline custom - c'est le seul moyen de faire intervenir
 * les stats ({@link me.tyalternative.fundamentalis.api.stats.IStatsComponent IStatsComponent})
 * dans le calcul des dégâts.
 *
 * <p>Priorité {@link EventPriority#HIGHEST} : on laisse les autres plugins
 * (protection de zone, anti-PvP…) annuler l'event en amont si nécessaire -
 * {@code ignoreCancelled = true} fait que Combat n'agit pas si l'event est
 * déjà annulé par un plugin de priorité inférieure.
 *
 * <h2>Hors-scope de cette itération</h2>
 * Les attaques à distance (projectiles) ne sont pas interceptées ici - le
 * catalogue d'armes actuel ne contient que des armes de mêlée
 * ({@link WeaponRegistry#registerDefaultWeapons()}). Le support des
 * projectiles sera ajouté avec l'arc, dans une itération ultérieure.
 */
public class CombatListener implements Listener {

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final DamageManager   damageManager;
    private final WeaponRegistry  weaponRegistry;
    private final Logger          logger;
    private final boolean         debugLog;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param damageManager  pipeline de dégâts central
     * @param weaponRegistry registre des armes, pour identifier l'arme tenue par l'attaquant
     * @param logger         logger du plugin Combat
     * @param debugLog       si {@code true}, logge le détail de chaque {@link DamageResult}
     */
    public CombatListener(DamageManager damageManager, WeaponRegistry weaponRegistry,
                          Logger logger, boolean debugLog) {
        this.damageManager  = damageManager;
        this.weaponRegistry = weaponRegistry;
        this.logger         = logger;
        this.debugLog       = debugLog;
    }

    // -------------------------------------------------------------------------
    // Interception
    // -------------------------------------------------------------------------

    /**
     * Intercepte toute attaque de mêlée entre deux {@link LivingEntity} et la
     * redirige vers {@link DamageManager#dealDamage(DamageInfo)}.
     *
     * <p>L'event vanilla est systématiquement annulé dès lors que les deux
     * parties sont des {@link LivingEntity} - c'est le pipeline custom qui
     * applique les dégâts via {@link LivingEntity#damage(double)} à l'intérieur
     * de {@link DamageManager}.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim))    return;

        CustomWeapon weapon = resolveHeldWeapon(attacker);

        DamageInfo   info   = damageManager.createWeaponDamage(attacker, victim, weapon, AttackType.MELEE);
        DamageResult result = damageManager.dealDamage(info);

        if (debugLog) {
            logger.log(Level.INFO, "[Combat] " + result);
        }

        // On prend le contrôle total des dégâts — Bukkit ne doit plus rien appliquer.
        event.setCancelled(true);

    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------

    /**
     * Résout l'arme custom tenue en main principale par l'attaquant, ou
     * {@code null} s'il n'a pas d'équipement ou tient un item non reconnu
     * (attaque à mains nues ou item vanilla).
     */
    private CustomWeapon resolveHeldWeapon(LivingEntity attacker) {
        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) return null;
        return weaponRegistry.getWeaponFromItemStack(equipment.getItemInMainHand());
    }
}
