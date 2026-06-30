package me.tyalternative.fundamentalis.combat.weapon;

import com.google.common.collect.HashMultimap;
import me.tyalternative.fundamentalis.combat.damage.AttackType;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Définit une arme custom : ses statistiques de combat et l'{@link ItemStack}
 * qui la représente en jeu.
 *
 * <p>Une instance de {@code CustomWeapon} est un objet de configuration
 * immuable, partagé par toutes les copies de l'item en jeu. L'identification
 * d'un {@link ItemStack} comme étant telle ou telle arme se fait via une clé
 * de Persistent Data Container ({@code "weapon_id"}).
 */
public class CustomWeapon {

    // -------------------------------------------------------------------------
    // Champs — caractéristiques de combat
    // -------------------------------------------------------------------------

    private final String     id;
    private final WeaponType weaponType;
    private final String     name;
    private final double     baseDamage;
    private final double     attackSpeed;
    private final double     reach;
    private final Material   material;

    /**
     * Cooldown entre deux attaques avec cette arme, en millisecondes.
     * Distinct de l'attribut Minecraft {@code ATTACK_SPEED} : ce cooldown est
     * géré manuellement par {@link me.tyalternative.fundamentalis.combat.damage.DamageManager DamageManager}
     * pour empêcher le spam de clics qui contournerait le système de charge
     * d'attaque natif de Minecraft.
     */
    private final long cooldownMillis;

    // -------------------------------------------------------------------------
    // Constructeurs
    // -------------------------------------------------------------------------

    /**
     * Crée une arme avec des statistiques entièrement personnalisées.
     *
     * @param id             identifiant unique, stocké dans le PDC de l'item (ex : {@code "iron_sword"})
     * @param weaponType     famille d'arme (détermine le {@link AttackType} associé)
     * @param name           nom affiché (sans code couleur, appliqué automatiquement)
     * @param baseDamage     dégâts de base, avant tout calcul de stats
     * @param attackSpeed    vitesse d'attaque (attribut Minecraft ATTACK_SPEED)
     * @param reach          portée en blocs
     * @param material       matériau Bukkit de l'item représentant l'arme
     * @param cooldownMillis délai minimum entre deux attaques, en millisecondes
     */
    public CustomWeapon(String id, WeaponType weaponType, String name, double baseDamage,
                        double attackSpeed, double reach, Material material, long cooldownMillis) {
        this.id             = id;
        this.weaponType     = weaponType;
        this.name           = name;
        this.baseDamage     = baseDamage;
        this.attackSpeed    = attackSpeed;
        this.reach          = reach;
        this.material       = material;
        this.cooldownMillis = cooldownMillis;
    }

    public CustomWeapon(String id, WeaponType weaponType, String name, double baseDamage,
                        double attackSpeed, double reach, Material material) {
        this.id             = id;
        this.weaponType     = weaponType;
        this.name           = name;
        this.baseDamage     = baseDamage;
        this.attackSpeed    = attackSpeed;
        this.reach          = reach;
        this.material       = material;
        this.cooldownMillis = Math.round(1000.0 / attackSpeed);
    }

    /**
     * Crée une arme héritant des statistiques par défaut de sa {@link WeaponType}.
     *
     * <p>L'id est dérivé automatiquement du nom (minuscule, espaces remplacés
     * par des underscores). Le cooldown par défaut est calculé à partir de
     * {@code attackSpeed} : {@code 1000 / attackSpeed} millisecondes.
     *
     * @param weaponType famille d'arme dont hériter les statistiques
     * @param name       nom affiché de l'arme
     * @param material   matériau Bukkit de l'item
     */
    public CustomWeapon(WeaponType weaponType, String name, Material material) {
        this (
                name.toLowerCase().replace("", "_"),
                weaponType,
                name,
                weaponType.getBaseDamage(),
                weaponType.getAttackSpeed(),
                weaponType.getReach(),
                material,
                Math.round(1000.0 / weaponType.getAttackSpeed())
        );
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return l'identifiant unique de l'arme, stocké dans le PDC de l'item */
    public String getId() { return id; }

    /** @return la famille de l'arme */
    public WeaponType getWeaponType() { return weaponType; }

    /** @return le nom affiché (sans code couleur) */
    public String getName() { return name; }

    /** @return les dégâts de base de cette arme, avant application des stats */
    public double getBaseDamage() { return baseDamage; }

    /** @return la vitesse d'attaque (attribut Minecraft ATTACK_SPEED) */
    public double getAttackSpeed() { return attackSpeed; }

    /** @return la portée en blocs */
    public double getReach() { return reach; }

    /** @return le matériau Bukkit de l'item */
    public Material getMaterial() { return material; }

    /** @return le cooldown minimum entre deux attaques, en millisecondes */
    public long getCooldownMillis() { return cooldownMillis; }

    // -------------------------------------------------------------------------
    // Création et identification de l'ItemStack
    // -------------------------------------------------------------------------

    /**
     * Crée un nouvel {@link ItemStack} représentant cette arme, avec son lore
     * et sa clé PDC d'identification déjà appliqués.
     *
     * @param plugin l'instance du plugin Combat, nécessaire pour construire la {@link NamespacedKey}
     * @return un item prêt à être donné à un joueur
     */
    public ItemStack createItemStack(Plugin plugin) {
        ItemStack item = new ItemStack(material);
        applyMetadata(item, plugin);
        return item;
    }

    /**
     * Applique le nom, le lore et la clé d'identification PDC sur un item existant.
     *
     * @param item   l'item à modifier — doit avoir un {@link ItemMeta} valide
     * @param plugin l'instance du plugin Combat, pour la {@link NamespacedKey}
     */
    public void applyMetadata(ItemStack item, Plugin plugin) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(Component.text(name, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Type: ", NamedTextColor.GRAY)
                .append(Component.text(weaponType.getDisplayName(), NamedTextColor.WHITE)));
        lore.add(Component.text(" -> Dégâts: ", NamedTextColor.RED)
                .append(Component.text(String.format("%.1f", baseDamage), NamedTextColor.WHITE)));
        lore.add(Component.text(" -> Vitesse: ", NamedTextColor.YELLOW)
                .append(Component.text(String.format("%.1f", attackSpeed), NamedTextColor.WHITE)));
        lore.add(Component.text(" -> Portée: ", NamedTextColor.AQUA)
                .append(Component.text(String.format("%.1f", reach) + " blocs", NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text(weaponType.getSpecialAbility(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        // On vide les attribute modifiers natifs de Minecraft (attack damage/speed)
        // pour que SEULS les calculs du DamageManager déterminent les dégâts réels.
        meta.setAttributeModifiers(HashMultimap.create());

        NamespacedKey key = new NamespacedKey(plugin, "weapon_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);

        item.setItemMeta(meta);
    }

    // -------------------------------------------------------------------------
    // Hook d'effet — surchargé par les armes spéciales
    // -------------------------------------------------------------------------

    /**
     * Appelé par le pipeline de dégâts après application des dégâts, lorsque
     * cette arme a porté un coup. Ne fait rien par défaut.
     *
     * <p>À surcharger pour les armes ayant un effet spécial au contact
     * (ex : applique un effet de statut). Reste vide tant que
     * {@code fundamentalis-status} n'existe pas.
     *
     * @param info le contexte complet du dégât porté
     */
    public void onHitEffect(DamageInfo info) {
        // Pas d'effet par défaut — à surcharger par les sous-classes d'arme.
    }

    public void onPreHitEffect(DamageInfo info) {
        // Pas d'effet par défaut — à surcharger par les sous-classes d'arme.
    }


}
