package me.tyalternative.fundamentalis.api;

import me.tyalternative.fundamentalis.api.stats.IStatTypeRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée Bukkit minimal du jar {@code fundamentalis-api}.
 *
 * <p><strong>Ce plugin n'a aucune logique métier.</strong> Son seul rôle est
 * de satisfaire l'exigence de Paper qu'un jar déposé dans {@code /plugins}
 * possède une classe {@code main} valide. Sans cette classe, Paper refuse
 * de charger {@code fundamentalis-api.jar} et toutes ses classes
 * ({@link IStatTypeRegistry}, {@code ComponentKey}, etc.) restent invisibles
 * pour les autres plugins du serveur, ce qui provoque un
 * {@link NoClassDefFoundError} dans {@code fundamentalis-core}.
 *
 * <p>Ne pas confondre avec {@link FundamentalisAPI}, le Service Locator
 * utilisé par les modules pour accéder aux services (totalement différent,
 * ce n'est pas un plugin Bukkit).
 *
 * <p>Ne contient aucune dépendance vers le Core : l'API ne doit jamais
 * dépendre d'une implémentation.
 */
public class FundamentalisAPIPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("fundamentalis-api chargé — bibliothèque de classes prête.");
    }

    @Override
    public void onDisable() {
        // Rien à nettoyer : aucun état, aucune ressource.
    }

}
