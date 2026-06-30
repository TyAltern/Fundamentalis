package me.tyalternative.fundamentalis.status.effects.special;

import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChainEffect extends StatusEffect implements Listener {
    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final double MAX_CHAIN_RANGE = 6;
    private static final double DAMAGE_SHARED_PER_LEVEL = 0.075;
    private static final long CHAIN_EFFECT_DURATION_TICK = 10 * 20;

    // -------------------------------------------------------------------------
    // Modèle interne : un groupe d'entités enchaînées en étoile autour de "center"
    // -------------------------------------------------------------------------

    /**
     * Remplace les trois structures parallèles (chains / enchainedEntities / enchainedGroup)
     * de la version précédente, qui devaient être maintenues manuellement en synchro
     * (et ne l'étaient pas toujours, cf. le bug de nettoyage partiel dans onTick).
     * Ici, un groupe = sa propre source de vérité : membres, liens visuels, expiration.
     */
    private static final class ChainGroup {
        UUID center; // mutable : réassigné si le centre meurt et qu'il reste des membres vivants
        final Set<UUID> members = new HashSet<>();       // inclut "center"
        final Map<UUID, ChainLink> links = new HashMap<>(); // memberUUID (hors center) -> lien visuel
        long expiresAtTick;

        ChainGroup(UUID center, long expiresAtTick) {
            this.center = center;
            this.members.add(center);
            this.expiresAtTick = expiresAtTick;
        }

        void stopAllLinks() {
            for (ChainLink link : links.values()) {
                if (link.isRunning()) link.stop();
            }
        }
    }

    /** center -> groupe (pour le nettoyage par expiration dans onTick). */
    private Map<UUID, ChainGroup> groupsByCenter;
    /** n'importe quel membre du groupe -> son groupe, pour un lookup O(1) dans onPostDamage. */
    private Map<UUID, ChainGroup> groupByMember;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Plugin plugin;
    private final DamageManager damageManager;

    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public ChainEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,
                       Plugin plugin, DamageManager damageManager) {
        super(holder, statsComponent, meta);
        this.plugin        = plugin;
        this.damageManager = damageManager;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;

        groupsByCenter = new HashMap<>();
        groupByMember = new HashMap<>();
    }

    @Override
    public void onTick() {
        if (groupsByCenter.isEmpty()) return;

        long now = Bukkit.getCurrentTick();

        // Plus besoin de vérifier la validité du centre ici : c'est onPostDamage
        // (via wasKill()) qui détecte sa mort immédiatement, dès qu'elle survient,
        // au lieu d'attendre qu'on tombe dessus au prochain tick. onTick ne gère
        // donc plus que l'expiration naturelle du groupe.
        Iterator<Map.Entry<UUID, ChainGroup>> it = groupsByCenter.entrySet().iterator();
        while (it.hasNext()) {
            ChainGroup group = it.next().getValue();
            if (now < group.expiresAtTick) continue;

            group.stopAllLinks();
            for (UUID member : group.members) {
                groupByMember.remove(member);
            }
            it.remove();
        }
    }

    /**
     * Appelée dès qu'une entité meurt (PostDamageEvent#wasKill()), qu'elle soit
     * centre ou simple membre d'un groupe. Si elle n'appartient à aucun groupe,
     * ne fait rien.
     */
    private void handleEntityDeath(UUID deadId) {
        ChainGroup group = groupByMember.get(deadId);
        if (group == null) return;

        if (deadId.equals(group.center)) {
            groupsByCenter.remove(group.center);
            reassignCenterOrDissolve(group);
        } else {
            removeMemberFromGroup(group, deadId);
        }
    }

    /**
     * Le centre vient de mourir. S'il reste au moins un autre membre vivant,
     * il devient le nouveau centre et les chaînes sont reconstruites en étoile
     * autour de lui ; sinon le groupe est dissous. Appelée hors itération sur
     * groupsByCenter (depuis l'event), donc on peut réinsérer directement.
     */
    private void reassignCenterOrDissolve(ChainGroup group) {
        group.stopAllLinks();
        group.links.clear();

        groupByMember.remove(group.center);
        group.members.remove(group.center);

        UUID newCenter = pickReplacementCenter(group);
        if (newCenter == null) {
            for (UUID member : group.members) {
                groupByMember.remove(member);
            }
            return;
        }

        group.center = newCenter;
        rebuildLinksAroundCenter(group);
        groupsByCenter.put(group.center, group);
    }

    /** Un membre non-centre est mort : on coupe juste son lien et on le retire du groupe. */
    private void removeMemberFromGroup(ChainGroup group, UUID memberId) {
        ChainLink link = group.links.remove(memberId);
        if (link != null && link.isRunning()) link.stop();

        group.members.remove(memberId);
        groupByMember.remove(memberId);
    }

    /** Reconstruit les liens en étoile entre group.center et chacun des autres membres encore vivants. */
    private void rebuildLinksAroundCenter(ChainGroup group) {
        LivingEntity center = resolveLiving(group.center);
        if (center == null) return; // garde-fou, ne devrait pas arriver (vient d'être choisi comme vivant)

        Iterator<UUID> memberIt = group.members.iterator();
        while (memberIt.hasNext()) {
            UUID memberId = memberIt.next();
            if (memberId.equals(group.center)) continue;

            LivingEntity member = resolveLiving(memberId);
            if (member == null) {
                // mort lui aussi entre-temps : on le retire proprement au passage
                memberIt.remove();
                groupByMember.remove(memberId);
                continue;
            }

            ChainLink link = new ChainLink(plugin, center, member).withLeash(MAX_CHAIN_RANGE);
            link.start();
            group.links.put(memberId, link);
        }
    }

    private UUID pickReplacementCenter(ChainGroup group) {
        for (UUID memberId : group.members) {
            if (isValidLiving(memberId)) return memberId;
        }
        return null;
    }

    private LivingEntity resolveLiving(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return (entity instanceof LivingEntity living && living.isValid()) ? living : null;
    }

    private boolean isValidLiving(UUID id) {
        return resolveLiving(id) != null;
    }

    @Override
    public void onRemove() {
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;

        for (ChainGroup group : groupsByCenter.values()) {
            group.stopAllLinks();
        }
        groupsByCenter.clear();
        groupByMember.clear();
    }

    // -------------------------------------------------------------------------
    // Logique principale
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPostDamage(PostDamageEvent event) {
        LivingEntity victim = event.getVictim();
        UUID victimId = victim.getUniqueId();

        LivingEntity attacker = event.getAttacker();
        if (attacker != null && attacker.getUniqueId().equals(getEntity().getUniqueId())) {
            ChainGroup existingGroup = groupByMember.get(victimId);
            if (existingGroup != null) {
                shareDamage(existingGroup, victimId, event.getFinalDamage());
            } else {
                createChainGroup(victim, attacker);
            }
        }

        // wasKill() permet de réagir au moment exact où l'entité meurt, peu importe
        // qui lui a porté le coup fatal, plutôt que d'attendre de le détecter au
        // prochain tick via un sondage de validité.
        if (event.wasKill()) {
            handleEntityDeath(victimId);
        }
    }

    /** Répartit un pourcentage des dégâts subis par "sourceMember" sur le reste du groupe. */
    private void shareDamage(ChainGroup group, UUID sourceMember, double finalDamage) {
        double damage = finalDamage * DAMAGE_SHARED_PER_LEVEL * getLevel();

        // Copie défensive : damageManager.dealDamage() réémet un PostDamageEvent de
        // façon SYNCHRONE (cf. la pile d'appel CombatListener -> callEvent -> onPostDamage).
        // Si ce dégât partagé tue à son tour un autre membre, onPostDamage rentre dans
        // handleEntityDeath, qui modifie group.members (retrait, voire réassignation du
        // centre) — pendant qu'on itère encore dessus ici. Itérer sur une copie évite la
        // ConcurrentModificationException ; les membres visés restent ceux qui étaient
        // dans le groupe au moment du coup, ce qui est le comportement voulu.
        List<UUID> snapshot = new ArrayList<>(group.members);

        for (UUID memberId : snapshot) {
            if (memberId.equals(sourceMember)) continue;

            // Garde-fou : l'entité a pu mourir/se déconnecter entre-temps.
            LivingEntity victim = resolveLiving(memberId);
            if (victim == null) continue;

            DamageInfo info = damageManager.createStatusDamage(
                    victim, DamageSource.STATUS_BURN, damage, DamageType.PHYSICAL);
            info.setCanKnockback(true);
            damageManager.dealDamage(info);
        }
    }

    /** Crée un nouveau groupe en étoile : "target" au centre, relié à chaque entité proche. */
    private void createChainGroup(LivingEntity target, LivingEntity attacker) {
        ChainGroup group = new ChainGroup(target.getUniqueId(), Bukkit.getCurrentTick() + CHAIN_EFFECT_DURATION_TICK);
        groupByMember.put(target.getUniqueId(), group);

        for (LivingEntity nearby : getNearbyEntity(target, attacker)) {
            UUID nearbyId = nearby.getUniqueId();

            // Fix : le lien doit relier target <-> nearby, pas target <-> attacker.
            // C'est ce lien (avec sa laisse) qui empêche physiquement les entités
            // enchaînées de s'éloigner les unes des autres.
            ChainLink link = new ChainLink(plugin, target, nearby).withLeash(MAX_CHAIN_RANGE);
            link.start();

            group.links.put(nearbyId, link);
            group.members.add(nearbyId);
            groupByMember.put(nearbyId, group);
        }

        groupsByCenter.put(target.getUniqueId(), group);
    }

    private List<LivingEntity> getNearbyEntity(LivingEntity centerEntity, LivingEntity attacker) {
        List<LivingEntity> nearby = new ArrayList<>();
        @NotNull List<Entity> nearbyEntities = centerEntity.getNearbyEntities(MAX_CHAIN_RANGE, MAX_CHAIN_RANGE, MAX_CHAIN_RANGE);

        int maxNumber = 0;
        for (Entity candidate : nearbyEntities) {
            if (maxNumber > getLevel()) continue;
            if (!(candidate instanceof LivingEntity livingEntity)) continue;
            if (candidate.getUniqueId().equals(attacker.getUniqueId())) continue;
            if (groupByMember.containsKey(livingEntity.getUniqueId())) continue; // déjà dans un groupe
            nearby.add(livingEntity);

            maxNumber ++;
        }
        return nearby;
    }
}