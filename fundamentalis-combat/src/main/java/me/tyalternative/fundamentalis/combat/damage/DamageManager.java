package me.tyalternative.fundamentalis.combat.damage;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentKey;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageCalculationEvent;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageEvent;
import me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.stats.StatType;
import me.tyalternative.fundamentalis.combat.weapon.CustomWeapon;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Point d'entrée unique pour <strong>tous</strong> les dégâts du système
 * Fundamentalis - armes, futurs DoT de statut, futurs sorts.
 *
 * <p>Reproduit fidèlement le pipeline en 13 étapes de l'ancienne version,
 * adapté à la nouvelle architecture multi-module : les stats (FORCE, DEFENSE,
 * DEXTERITE…) sont lues via {@link IStatsComponent#getFinal(StatType)} au lieu
 * de l'ancien {@code EntityStats}, et les points d'extension pour les futurs
 * effets de statut passent par {@link PreDamageEvent} / {@link PostDamageEvent}
 * plutôt que par un couplage direct à un {@code StatusEffectManager}.
 *
 * <h2>Formules</h2>
 * <ul>
 *   <li><strong>Multiplicateur de force</strong> - {@code 1.0 + FORCE.getFinal() * 0.05}
 *       (chaque point de Force ajoute 5% de dégâts).</li>
 *   <li><strong>Réduction de défense</strong> - {@code DEFENSE.getFinal() * 0.02}, plafonnée à 75%
 *       (chaque point de Défense réduit les dégâts reçus de 2%).</li>
 *   <li><strong>Chance de critique</strong> - {@code DEXTERITE.getFinal() * 0.01}, plafonnée à 75%
 *       (chaque point de Dextérité ajoute 1% de chance de critique).</li>
 *   <li><strong>Multiplicateur critique</strong> - fixe à ×1.5.</li>
 * </ul>
 *
 * <p>Ces formules sont calculées directement par {@code DamageManager} (et non
 * stockées dans le Core), conformément au choix de design retenu : Combat reste
 * seul propriétaire de la sémantique "combat" des stats de base.
 *
 * <p>L'accès au {@link IStatsComponent} d'une entité se fait via une
 * {@link ComponentKey} générique fournie au constructeur - Combat ne dépend
 * ainsi d'aucune classe concrète de {@code fundamentalis-core}, seulement de
 * son contrat publié dans l'API.
 */
public class DamageManager {

    // -------------------------------------------------------------------------
    // Constantes de formule
    // -------------------------------------------------------------------------

    private static final double FORCE_DAMAGE_PER_POINT      = 0.05;
    private static final double DEFENSE_REDUCTION_PER_POINT = 0.02;
    private static final double DEFENSE_REDUCTION_CAP       = 0.75;
    private static final double DEXTERITE_CRIT_PER_POINT    = 0.01;
    private static final double BASE_CRIT_CHANCE            = 0.10;
    private static final double CRIT_CHANCE_CAP             = 0.75;
    private static final double CRIT_DAMAGE_MULTIPLIER      = 1.5;
    private static final double MIN_DAMAGE                  = 0.5;
    private static final double BASE_KNOCKBACK              = 0.4;
    private static final double KNOCKBACK_PER_FORCE_POINT   = 0.005;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final IEntityService                entityService;
    private final ComponentKey<IStatsComponent> statsKey;

    // Analytics — dégâts par source globale et par joueur
    private final Map<DamageSource, Long> damageBySource = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Map<DamageSource, Long>> damageByPlayer = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityService service d'accès aux ComponentHolder, pour lire les stats
     *                      de l'attaquant et de la victime
     * @param statsKey      clé typée du composant de stats exposée par le Core
     *                      (ex : {@code StatsComponent.KEY}), passée par le plugin
     *                      Combat pour éviter toute dépendance directe à une classe
     *                      concrète de {@code fundamentalis-core}
     */
    public DamageManager(IEntityService entityService, ComponentKey<IStatsComponent> statsKey) {
        this.entityService = entityService;
        this.statsKey       = statsKey;
    }

    // -------------------------------------------------------------------------
    // POINT D'ENTRÉE UNIQUE
    // -------------------------------------------------------------------------

    /**
     * Traite un dégât de bout en bout : vérifications, calculs de stats,
     * application finale, effets post-dégâts et analytics.
     *
     * @param info le contexte du dégât à traiter
     * @return le résultat complet du traitement
     */
    public DamageResult dealDamage(DamageInfo info) {
        LivingEntity victim = info.getVictim();
        double damage = info.getBaseDamage();

        double originalDamage   = damage;
        double reductionDefense = 0;
        double reductionEffects = 0;
        double bonusEffects     = 0;
        boolean isKill          = false;

        // ========== 1. VÉRIFICATIONS PRÉLIMINAIRES (cooldown d'attaque) ==========

        double attackCooldown = getAttackCooldown(info);
        boolean wasCharged    = attackCooldown > 0;

        if (!wasCharged) {
            info.setWasCharged(false);
            info.setFinalDamage(0);
            return new DamageResult(originalDamage, 0, false, false, false, false, false, 0, 0, 0);
        }

        // ========== 2. PRE-DAMAGE EVENT — point d'extension externe ==========
        // Permet aux futurs modules (status, spells) d'appliquer immunités,
        // résistances ou boucliers avant tout calcul de stats.

        PreDamageEvent preEvent = new PreDamageEvent(info.getAttacker(), victim, info.getType(), damage,
                info.isCritForced(), info.canKnockback(), info.getKnockbackFactor());
        Bukkit.getPluginManager().callEvent(preEvent);

        if (preEvent.isCancelled()) {
            info.setWasImmune(true);
            info.setWasBlocked(true);
            info.setFinalDamage(0);
            return new DamageResult(originalDamage, 0, false, true, true, false, wasCharged, 0, 0, 0);
        }

        info.setForcedCrit(preEvent.isCritForced());
        info.setCanKnockback(preEvent.canKnockBack());
        info.setKnockbackFactor(preEvent.getKnockbackFactor());
        damage = preEvent.getDamage();

        // ========== 3. APPLICATION STATS ATTAQUANT (FORCE) ==========

        if (info.getAttacker() != null && info.getSource().isFromWeapon()) {
            double beforeStats = damage;
            damage = applyAttackerForce(info.getAttacker(), damage);
            bonusEffects += (damage - beforeStats);
        }

        // ========== 4. APPLICATION DÉFENSE VICTIME ==========

        if (info.getSource().isFromWeapon()) {
            double beforeDefense = damage;
            damage = applyVictimDefense(victim, damage);
            reductionDefense = beforeDefense - damage;
        }

        // ========== 5. VÉRIFICATION CRITIQUE ==========

        boolean isCrit = false;
        if (info.canCrit()) {
            isCrit = checkCritical(info);
            if (isCrit) {
                double beforeCrit = damage;
                damage *= CRIT_DAMAGE_MULTIPLIER;
                bonusEffects += (damage-beforeCrit);
                info.setWasCrit(true);

                if (info.getAttacker() instanceof Player attackerPlayer) {
                    attackerPlayer.sendMessage("§e§l⚡ CRITIQUE !");
                }
            }
        }

        // ========== 6. CHARGE DE L'ATTAQUE (cooldown natif Minecraft) ==========
        // Une attaque à charge partielle inflige des dégâts proportionnels,
        // comme dans le système de combat vanilla.

        double attackCooldownMultiplier = attackCooldown >= 1.0 ? 1.0 : Math.max(0.2, attackCooldown);
        double beforeCooldown = damage;
        damage *= attackCooldownMultiplier;
        bonusEffects += (damage - beforeCooldown);

        // ========== 7. DÉGÂTS MINIMUM ==========

        damage = Math.max(MIN_DAMAGE, damage);

        // ========== 8. APPLICATION FINALE ==========

        double victimHealthBefore = victim.getHealth();
        if (victimHealthBefore - damage <= 0) {
            isKill = true;
        }

        info.setFinalDamage(damage);
        info.setWasKill(isKill);


        PostDamageCalculationEvent postDamageCalculationEvent =
                new PostDamageCalculationEvent(info.getAttacker(), victim, info.getType(), damage, isCrit, isKill);
        Bukkit.getPluginManager().callEvent(postDamageCalculationEvent);

        if (postDamageCalculationEvent.isCancelled())
            return new DamageResult(originalDamage, 0, isCrit, true, info.wasImmune(), false, wasCharged, 0, 0, 0);

        damage = postDamageCalculationEvent.getFinalDamage();

        victim.damage(Math.max(damage, 0.01));

        // ========== 9. EFFETS POST-DÉGÂTS (onHitEffect de l'arme) ==========

        if (info.getWeapon() != null) {
            info.getWeapon().onHitEffect(info);
        }

        // ========== 10. KNOCKBACK ==========

        applyKnockback(info);

        // ========== 11. POST-DAMAGE EVENT — point d'extension externe ==========
        // Permet aux futurs modules (vampirisme, riposte de classe…) de réagir
        // au coup porté sans pouvoir modifier son montant.

        Bukkit.getPluginManager().callEvent(
                new PostDamageEvent(info.getAttacker(), victim, info.getType(), damage, isCrit, isKill));

        // ========== 12. ANALYTICS ==========

        trackDamage(info);

        // ========== 13. RÉSULTAT ==========

        return new DamageResult(
                originalDamage, damage, isCrit, false, false, isKill, wasCharged,
                reductionDefense, reductionEffects, bonusEffects
        );
    }

    // -------------------------------------------------------------------------
    // Calculs intermédiaires
    // -------------------------------------------------------------------------

    /**
     * Applique le multiplicateur de FORCE de l'attaquant.
     * Formule : {@code damage * (1.0 + FORCE.getFinal() * 0.05)}.
     */
    private double applyAttackerForce(LivingEntity attacker, double damage) {
        Optional<IStatsComponent> stats = getStats(attacker);
        if (stats.isEmpty()) return damage;

        double force      = stats.get().getFinal(StatType.FORCE);
        double multiplier = 1.0 + (force * FORCE_DAMAGE_PER_POINT);
        return  damage * multiplier;
    }

    /**
     * Applique la réduction de DEFENSE de la victime, plafonnée à 75%.
     * Formule : {@code damage * (1.0 - min(0.75, DEFENSE.getFinal() * 0.02))}.
     */
    private double applyVictimDefense(LivingEntity victim, double damage) {
        Optional<IStatsComponent> stats = getStats(victim);
        if (stats.isEmpty()) return damage;

        double defense   = stats.get().getFinal(StatType.DEFENSE);
        double reduction = Math.min(DEFENSE_REDUCTION_CAP, defense * DEFENSE_REDUCTION_PER_POINT);
        return damage * (1.0 - reduction);
    }

    /**
     * Détermine si une attaque déclenche un critique.
     * Chance de base : {@code DEXTERITE.getFinal() * 0.01}, plafonnée à 75%.
     * Sans attaquant (DoT, environnement) : 10% de chance fixe si {@code canCrit()}.
     */
    private boolean checkCritical(DamageInfo info) {
        if (info.isCritForced()) return true;

        if (info.getAttacker() == null) {
            return Math.random() < 0.10;
        }

        // Un critique ne peut survenir que sur une attaque pleinement chargée
        if (getAttackCooldown(info) < 1.0) return false;

        Optional<IStatsComponent> stats = getStats(info.getAttacker());
        if (stats.isEmpty()) return false;

        double dexterite = stats.get().getFinal(StatType.DEXTERITE);
        double critChance = Math.min(CRIT_CHANCE_CAP, dexterite * DEXTERITE_CRIT_PER_POINT + BASE_CRIT_CHANCE);
        return Math.random() < critChance;
    }

    /**
     * Lit le cooldown d'attaque natif Minecraft (0.0 à 1.0) pour un joueur.
     * Retourne {@code 1.0} pour les entités non-joueurs (toujours "chargées").
     */
    private double getAttackCooldown(DamageInfo info) {
        if (info.getAttacker() instanceof Player player) {
            return player.getAttackCooldown();
        }
        return 1.0;
    }

    /**
     * Applique un recul à la victime, dans la direction de l'attaquant.
     * La force du recul augmente avec la FORCE de l'attaquant.
     */
    private void applyKnockback(DamageInfo info) {
        if (!info.canKnockback()) return;
        if (info.getAttacker() == null) return;

        double knockback = BASE_KNOCKBACK;
        Optional<IStatsComponent> stats = getStats(info.getAttacker());
        if (stats.isPresent()) {
            double force = stats.get().getFinal(StatType.FORCE);
            knockback *= (1.0 + force * KNOCKBACK_PER_FORCE_POINT);
        }

        double knockbackFactor = info.getKnockbackFactor();
        int sign = knockbackFactor >= 0 ? 1 : -1;
        double factor = knockbackFactor/sign;
        double directionX = info.getAttacker().getX() - info.getVictim().getX();
        double directionZ = info.getAttacker().getZ() - info.getVictim().getZ();
        info.getVictim().knockback(knockback * factor,directionX * sign, directionZ * sign);
    }

    /**
     * Récupère le {@link IStatsComponent} d'une entité via l'EntityService,
     * sans jamais lancer d'exception — retourne {@link Optional#empty()} si
     * l'entité n'est pas trackée ou n'a pas de composant de stats.
     */
    private Optional<IStatsComponent> getStats(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        return entityService.get(entity).flatMap(holder -> holder.get(statsKey));
    }

    // -------------------------------------------------------------------------
    // Effets utilitaires
    // -------------------------------------------------------------------------

    /**
     * Soigne une entité, plafonné à ses points de vie maximum.
     *
     * @param entity l'entité à soigner
     * @param amount le montant de soin
     */
    public void healEntity(LivingEntity entity, double amount) {
        var maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr == null) return;

        double maxHealth     = maxHealthAttr.getValue();
        double currentHealth = entity.getHealth();
        double newHealth     = Math.min(currentHealth + amount, maxHealth);
        entity.setHealth(newHealth);
    }

    // -------------------------------------------------------------------------
    // Factories — création de DamageInfo pour les cas d'usage courants
    // -------------------------------------------------------------------------

    /**
     * Crée un {@link DamageInfo} pour une attaque d'arme (mêlée ou projectile).
     *
     * @param attacker   l'entité qui attaque
     * @param victim     l'entité qui subit l'attaque
     * @param weapon     l'arme utilisée, ou {@code null} pour une attaque à mains nues
     * @param attackType le type d'attaque (mêlée ou distance)
     * @return un {@link DamageInfo} prêt pour {@link #dealDamage(DamageInfo)}
     */
    public DamageInfo createWeaponDamage(LivingEntity attacker, LivingEntity victim,
                                         CustomWeapon weapon, AttackType attackType) {
        double baseDamage = weapon != null ? weapon.getBaseDamage() : 1.0;

        return new DamageInfo.Builder()
                .attacker(attacker)
                .victim(victim)
                .baseDamage(baseDamage)
                .source(attackType == AttackType.RANGED ? DamageSource.PROJECTILE : DamageSource.WEAPON)
                .type(DamageType.PHYSICAL)
                .canCrit(true)
                .weapon(weapon)
                .attackType(attackType)
                .build();
    }

    /**
     * Crée un {@link DamageInfo} pour un dégât d'effet de statut (DoT).
     *
     * <p>Réservé pour {@code fundamentalis-status} — Combat fournit la factory
     * mais n'implémente aucun effet de statut lui-même.
     *
     * @param victim l'entité qui subit le DoT
     * @param source la source de statut (doit vérifier {@link DamageSource#isFromStatus()})
     * @param damage le montant de dégât du tick de DoT
     * @param type   la catégorie de dégât
     * @return un {@link DamageInfo} sans critique ni recul, prêt pour {@link #dealDamage(DamageInfo)}
     * @throws IllegalArgumentException si {@code source} n'est pas un statut
     */
    public DamageInfo createStatusDamage(LivingEntity victim, DamageSource source,
                                         double damage, DamageType type) {

        if (!source.isFromStatus()) {
            throw new IllegalArgumentException("La source doit être un effet de statut (STATUS_*) : " + source);
        }

        return new DamageInfo.Builder()
                .victim(victim)
                .baseDamage(damage)
                .source(source)
                .type(type)
                .canCrit(false)
                .canKnockback(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // Analytics
    // -------------------------------------------------------------------------

    private void trackDamage(DamageInfo info) {
        long damage = (long) info.getFinalDamage();
        damageBySource.merge(info.getSource(), damage, Long::sum);

        if (info.getAttacker() instanceof Player player) {
            damageByPlayer
                    .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .merge(info.getSource(), damage, Long::sum);
        }
    }

    /** @return une copie du total de dégâts infligés, groupé par {@link DamageSource} */
    public Map<DamageSource, Long> getDamageBySource() {
        return new HashMap<>(damageBySource);
    }

    /**
     * @param player le joueur dont on veut les statistiques
     * @return une copie du total de dégâts infligés par ce joueur, groupé par {@link DamageSource}
     */
    public Map<DamageSource, Long> getDamageByPlayer(Player player) {
        return new HashMap<>(damageByPlayer.getOrDefault(player.getUniqueId(), new HashMap<>()));
    }

    /**
     * @param player le joueur dont on veut le total
     * @return le total cumulé de dégâts infligés par ce joueur, toutes sources confondues
     */
    public long getTotalDamageDealt(Player player) {
        return damageByPlayer.getOrDefault(player.getUniqueId(), new HashMap<>())
                .values().stream().mapToLong(Long::longValue).sum();
    }

    /** Réinitialise toutes les statistiques d'analytics collectées. */
    public void resetAnalytics() {
        damageBySource.clear();
        damageByPlayer.clear();
    }

}
