package me.tyalternative.fundamentalis.status;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.status.IStatusComponent;
import me.tyalternative.fundamentalis.api.status.IStatusEffectRegistry;
import me.tyalternative.fundamentalis.api.status.StatusEffectType;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.status.command.StatusCommand;

import me.tyalternative.fundamentalis.status.effects.CC.FreezeEffect;
import me.tyalternative.fundamentalis.status.effects.CC.GravityPulseEffect;
import me.tyalternative.fundamentalis.status.effects.CC.RootingEffect;
import me.tyalternative.fundamentalis.status.effects.CC.SlowEffect;
import me.tyalternative.fundamentalis.status.effects.DoT.BleedEffect;
import me.tyalternative.fundamentalis.status.effects.DoT.BurnEffect;
import me.tyalternative.fundamentalis.status.effects.DoT.InfernalBurnEffect;
import me.tyalternative.fundamentalis.status.effects.DoT.PoisonEffect;

import me.tyalternative.fundamentalis.status.effects.StatModifier.*;
import me.tyalternative.fundamentalis.status.effects.special.*;
import me.tyalternative.fundamentalis.status.listener.BlockVanillaEffectListener;
import me.tyalternative.fundamentalis.status.listener.CrowdControlListener;
import me.tyalternative.fundamentalis.status.listener.DeathCleanupListener;
import me.tyalternative.fundamentalis.status.listener.StatusAttachListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée de {@code fundamentalis-status}.
 *
 * <p>Dépend de {@code fundamentalis-api}, {@code fundamentalis-core} (stats)
 * et {@code fundamentalis-combat} (pipeline de dégâts pour les DoT), déclarés
 * dans {@code plugin.yml}.
 *
 * <h2>Ordre d'initialisation ({@code onEnable})</h2>
 * <ol>
 *   <li>Récupération des services du Core/Combat via {@link FundamentalisAPI#get()}.</li>
 *   <li>Enregistrement des 10 {@link StatusEffectTypes} intégrés dans {@link IStatusEffectRegistry}
 *       (Stealth/Furry exclu, sa mécanique reste à définir — voir {@link StatusEffectTypes}).</li>
 *   <li>Association de chaque effet à sa {@link StatusEffectFactory}, c'est-à-dire
 *       une référence de constructeur vers sa classe dédiée (ex : {@code PoisonEffect::new}).</li>
 *   <li>Enregistrement de {@link StatusAttachListener} et {@link CrowdControlListener}.</li>
 *   <li>Démarrage du {@link StatusTicker}.</li>
 *   <li>Enregistrement de la commande admin {@code /status}.</li>
 * </ol>
 *
 * <h2>Extensibilité pour un plugin tiers</h2>
 * Un module tiers (ex : {@code fundamentalis-classes}) qui veut ajouter un
 * effet custom suit le même schéma que ceux ci-dessous : créer une classe
 * {@code extends StatusEffect}, l'enregistrer dans
 * {@link IStatusEffectRegistry} (API publique) puis dans
 * {@link StatusEffectFactoryRegistry} via {@link #getFactoryRegistry()}.
 */
public final class FundamentalisStatusPlugin extends JavaPlugin {

    // -------------------------------------------------------------------------
    // Sous-systèmes
    // -------------------------------------------------------------------------

    private StatusEffectFactoryRegistry factoryRegistry;
    private StatusTicker                ticker;
    private IEntityService              entityService;
    ComponentKey<IStatusComponent>      statusKey;

    // -------------------------------------------------------------------------
    // onEnable
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        getLogger().info("[Status] Démarrage…");

        // Étape 1 — Services du Core/Combat via le Service Locator
        entityService = FundamentalisAPI.get().getEntityService();
        var statsKey = FundamentalisAPI.get().getStatsComponentKey();
        DamageManager damageManager  = resolveDamageManager();

        // Étape 2 — Enregistrement des effets intégrés
        IStatusEffectRegistry effectRegistry = new StatusEffectRegistryImpl(getLogger());
        statusKey = StatusComponent.KEY;
        FundamentalisAPI.registerStatusServices(statusKey, effectRegistry);

        registerBuiltinEffectTypes(effectRegistry);
        getLogger().info("[1/6] " + effectRegistry.getAll().size() + " effets enregistrés dans le registre.");

        // Étape 3 — Fabriques (une classe dédiée par effet, comme l'ancienne version)
        ChainGroupRegistry chainGroupRegistry = new ChainGroupRegistry(this, damageManager, entityService);
        factoryRegistry = new StatusEffectFactoryRegistry(getLogger());
        registerBuiltinFactories(factoryRegistry, damageManager, chainGroupRegistry);
        getLogger().info("[2/6] Fabriques d'effets associées.");

        // Étape 4 — Listeners
        getServer().getPluginManager().registerEvents(new BlockVanillaEffectListener(), this);
        getServer().getPluginManager().registerEvents(new StatusAttachListener(factoryRegistry, statsKey), this);
        getServer().getPluginManager().registerEvents(new CrowdControlListener(entityService, statusKey), this);
        getServer().getPluginManager().registerEvents(new DeathCleanupListener(entityService, statusKey), this);
        getLogger().info("[3/6] Listeners enregistrés.");

        // Étape 5 — Ticker central
        ticker = new StatusTicker(this, entityService, statusKey);
        ticker.start();
        getLogger().info("[4/6] StatusTicker démarré.");

        // Étape 6 — Commande admin
        initCommands(entityService, effectRegistry, statusKey);
        getLogger().info("[5/6] Commande /status enregistrée.");

        getLogger().info("[6/6] [Status] Démarré.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.stop();

        if (entityService != null && statusKey != null) {
            int cleared = 0;
            for (var holder : entityService.getAll()) {
                var status = holder.get(statusKey).orElse(null);
                if (status != null) {
                    status.clearAllEffects();
                    cleared++;
                }
            }
            getLogger().info("[Status] " + cleared + " composants de statut nettoyés avant arrêt.");
        }

        getLogger().info("[Status] Arrêt terminé.");
    }

    // -------------------------------------------------------------------------
    // Résolution de DamageManager
    // -------------------------------------------------------------------------

    /**
     * Récupère l'instance de {@link DamageManager} depuis le plugin Combat.
     *
     * <p>{@code DamageManager} n'est pas exposé par {@code FundamentalisAPI}
     * (ce n'est pas un contrat de l'API, juste une classe interne à Combat) —
     * Status y accède directement via le plugin Bukkit, ce qui est cohérent
     * avec la dépendance Maven directe déjà déclarée vers {@code fundamentalis-combat}.
     */
    private DamageManager resolveDamageManager() {
        var combatPlugin = (me.tyalternative.fundamentalis.combat.FundamentalisCombatPlugin) getServer().getPluginManager().getPlugin("fundamentalis-combat");
        if (combatPlugin == null) {
            throw new IllegalStateException(
                    "fundamentalis-combat introuvable — vérifiez qu'il est bien chargé avant fundamentalis-status.");
        }
        return combatPlugin.getDamageManager();
    }

    // -------------------------------------------------------------------------
    // Enregistrement des types d'effets intégrés
    // -------------------------------------------------------------------------

    private void registerBuiltinEffectTypes(IStatusEffectRegistry registry) {
        registry.register(StatusEffectTypes.POISON);
        registry.register(StatusEffectTypes.BURN);
        registry.register(StatusEffectTypes.INFERNAL_BURN);
        registry.register(StatusEffectTypes.BLEED);

        registry.register(StatusEffectTypes.FREEZE);
        registry.register(StatusEffectTypes.SLOW);
        registry.register(StatusEffectTypes.ROOTING);
        registry.register(StatusEffectTypes.GRAVITY_PULSE);

        registry.register(StatusEffectTypes.STRENGTH);
        registry.register(StatusEffectTypes.SPEED);
        registry.register(StatusEffectTypes.REGENERATION);
        registry.register(StatusEffectTypes.TOUGHNESS);
        registry.register(StatusEffectTypes.HEAL_BURST);
        registry.register(StatusEffectTypes.ABSORPTION);
        registry.register(StatusEffectTypes.ADRENALINE);

        registry.register(StatusEffectTypes.VAMPIRISM);
        registry.register(StatusEffectTypes.THORNS);
        registry.register(StatusEffectTypes.DENIAL);
        registry.register(StatusEffectTypes.BLOODLUST);
        registry.register(StatusEffectTypes.CHAIN);
        registry.register(StatusEffectTypes.CHAIN_LINK);
    }

    // -------------------------------------------------------------------------
    // Enregistrement des fabriques — une classe dédiée par effet
    // -------------------------------------------------------------------------

    /**
     * Associe chaque {@link StatusEffectType} intégré à une référence de
     * constructeur vers sa classe d'effet dédiée. C'est ici que se concrétise
     * le choix retenu : "une classe Java par effet", chacune capable de
     * porter son propre état (compteurs internes, listeners temporaires…).
     */
    private void registerBuiltinFactories(StatusEffectFactoryRegistry registry, DamageManager damageManager, ChainGroupRegistry chainGroupRegistry) {

        // ----- DoT — chaque effet gère sa propre cadence de tick en interne -----
        registry.register(StatusEffectTypes.POISON,
                (holder, stats, meta) -> new PoisonEffect(holder, stats, meta, damageManager));
        registry.register(StatusEffectTypes.BURN,
                (holder, stats, meta) -> new BurnEffect(holder, stats, meta, this, damageManager));
        registry.register(StatusEffectTypes.INFERNAL_BURN,
                (holder, stats, meta) -> new InfernalBurnEffect(holder, stats, meta, damageManager));
        registry.register(StatusEffectTypes.BLEED,
                (holder, stats, meta) -> new BleedEffect(holder, stats, meta, damageManager));

        // ----- CC -----
        registry.register(StatusEffectTypes.FREEZE,
                (holder, stats, meta) -> new FreezeEffect(holder, stats, meta, this));
        registry.register(StatusEffectTypes.ROOTING,
                (holder, stats, meta) -> new RootingEffect(holder, stats, meta, this));
        registry.register(StatusEffectTypes.GRAVITY_PULSE,
                (holder, stats, meta) -> new GravityPulseEffect(holder, stats, meta, this));
        registry.register(StatusEffectTypes.SLOW,
                SlowEffect::new);

        // ----- StatModifier -----
        registry.register(StatusEffectTypes.STRENGTH,
                StrengthEffect::new);
        registry.register(StatusEffectTypes.TOUGHNESS,
                ToughnessEffect::new);
        registry.register(StatusEffectTypes.ADRENALINE,
                AdrenalineEffect::new);
        registry.register(StatusEffectTypes.SPEED,
                SpeedEffect::new);
        registry.register(StatusEffectTypes.REGENERATION,
                (holder, stats, meta) -> new RegenerationEffect(holder, stats, meta, damageManager));
        registry.register(StatusEffectTypes.HEAL_BURST,
                (holder, stats, meta) -> new HealBurstEffect(holder, stats, meta, damageManager));
        registry.register(StatusEffectTypes.ABSORPTION,
                (holder, stats, meta) -> new AbsorptionEffect(holder, stats, meta, this));


        // ----- Spécial -----
        registry.register(StatusEffectTypes.VAMPIRISM,
                (holder, stats, meta) -> new VampirismEffect(holder, stats, meta, this, damageManager));
        registry.register(StatusEffectTypes.THORNS,
                (holder, stats, meta) -> new ThornsEffect(holder, stats, meta, this, damageManager));
        registry.register(StatusEffectTypes.DENIAL,
                (holder, stats, meta) -> new DenialEffect(holder, stats, meta, this, damageManager));
        registry.register(StatusEffectTypes.BLOODLUST,
                (holder, stats, meta) -> new BloodLustEffect(holder, stats, meta, this, damageManager));
        registry.register(StatusEffectTypes.CHAIN,
                (holder, stats, meta) -> new ChainCasterEffect(holder, stats, meta, this, chainGroupRegistry));
        registry.register(StatusEffectTypes.CHAIN_LINK,
                (holder, stats, meta) -> new ChainVictimEffect(holder, stats, meta, chainGroupRegistry));
    }

    // -------------------------------------------------------------------------
    // Commande
    // -------------------------------------------------------------------------

    private void initCommands(IEntityService entityService, IStatusEffectRegistry effectRegistry, ComponentKey<IStatusComponent> statusKey) {
        StatusCommand statusCommand = new StatusCommand(entityService, effectRegistry, statusKey);
        var command = getCommand("status");
        if (command == null) {
            getLogger().severe("Commande 'status' introuvable dans plugin.yml !");
            return;
        }
        command.setExecutor(statusCommand);
        command.setTabCompleter(statusCommand);
    }

    // -------------------------------------------------------------------------
    // Accesseur — pour les plugins tiers qui veulent enregistrer leurs propres effets
    // -------------------------------------------------------------------------

    /**
     * Expose le registre des fabriques, pour qu'un plugin tiers puisse y
     * enregistrer ses propres effets custom après le chargement de Status
     * (déclarer {@code fundamentalis-status} en {@code depend}).
     *
     * @return le registre des fabriques d'effets
     */
    public StatusEffectFactoryRegistry getFactoryRegistry() {
        return factoryRegistry;
    }
}
