package me.tyalternative.fundamentalis.status.effects.special;

import me.tyalternative.fundamentalis.api.FundamentalisAPI;
import me.tyalternative.fundamentalis.api.combat.DamageType;
import me.tyalternative.fundamentalis.api.entity.IEntityService;
import me.tyalternative.fundamentalis.api.event.combat.PostDamageEvent;
import me.tyalternative.fundamentalis.combat.damage.DamageInfo;
import me.tyalternative.fundamentalis.combat.damage.DamageManager;
import me.tyalternative.fundamentalis.combat.damage.DamageSource;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinateur central et UNIQUE du système Chain — une seule instance pour
 * tout le serveur, enregistrée une fois comme {@link Listener} dans
 * {@code FundamentalisStatusPlugin#onEnable}.
 *
 * <p>Remplace l'ancien modèle où chaque {@code ChainEffect} (côté attaquant)
 * portait sa propre map de groupes et son propre listener : désormais,
 * {@link ChainCasterEffect} (très courte durée, appliqué comme
 * {@code onHitEffect} d'arme) se contente de déclencher {@link #createGroup}
 * une fois, puis {@link ChainVictimEffect} (durée réelle de la chaîne,
 * appliqué à CHAQUE membre) délègue tout son cycle de vie ici via
 * {@link #join} / {@link #leave}. Le partage de dégâts n'est donc calculé
 * qu'UNE fois par coup, ici, plutôt qu'une fois par membre du groupe.
 */
public class ChainGroupRegistry implements Listener {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    /** Portée de recherche des entités proches, et distance max de la laisse visuelle. */
    public static final double MAX_CHAIN_RANGE = 6;

    /** Pourcentage de dégâts partagé par niveau de groupe (7.5% * niveau). */
    private static final double DAMAGE_SHARED_PER_LEVEL = 0.075;

    // -------------------------------------------------------------------------
    // Modèle interne : un groupe d'entités enchaînées en étoile autour de "center"
    // -------------------------------------------------------------------------

    private static final class ChainGroup {
        final UUID id;
        UUID center; // mutable : réassigné si le centre quitte le groupe et qu'il en reste d'autres
        final int level; // capturé depuis le niveau du palier CHAIN du lanceur, à la création
        final Set<UUID> members = new HashSet<>();
        final Map<UUID, ChainLink> links = new HashMap<>(); // memberUUID (hors center) -> lien visuel

        ChainGroup(UUID id, UUID center, int level) {
            this.id = id;
            this.center = center;
            this.level = level;
        }

        void stopAllLinks() {
            for (ChainLink link : links.values()) {
                if (link.isRunning()) link.stop();
            }
            links.clear();
        }
    }

    private final Map<UUID, ChainGroup> groupsById = new HashMap<>();
    /** N'importe quel membre -> son groupe, pour un lookup O(1) dans onPostDamage. */
    private final Map<UUID, UUID> groupIdByMember = new HashMap<>();

    /** Garde-fou anti-récursion : membres en train de recevoir un dégât répercuté. */
    private final Set<UUID> currentlySplashing = new HashSet<>();

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final Plugin plugin;
    private final DamageManager damageManager;
    private final IEntityService entityService;

    public ChainGroupRegistry(Plugin plugin, DamageManager damageManager, IEntityService entityService) {
        this.plugin = plugin;
        this.damageManager = damageManager;
        this.entityService = entityService;
    }

    // -------------------------------------------------------------------------
    // Création de groupe — appelée par ChainCasterEffect
    // -------------------------------------------------------------------------

    /**
     * Crée un nouveau groupe en étoile autour de "target" et applique le
     * palier {@link StatusEffectTypes#CHAIN_LINK} à target + chaque entité
     * proche trouvée. C'est ce palier (durée bien plus longue que CHAIN) qui
     * gère la durée réelle de la chaîne — voir {@link ChainVictimEffect}.
     *
     * <p>Toute la construction du groupe (membres, liens visuels) passe par
     * les callbacks {@link #join} déclenchés en cascade et de façon
     * synchrone par {@code applyEffect} — une seule source de vérité, pas de
     * double comptabilité entre cette méthode et {@link #join}.
     */
    public void createGroup(LivingEntity target, LivingEntity attacker, int level) {
        UUID groupId = UUID.randomUUID();
        groupsById.put(groupId, new ChainGroup(groupId, target.getUniqueId(), level));

        applyLinkEffect(target, level, groupId);

        for (LivingEntity nearby : findNearbyTargets(target, attacker, level)) {
            applyLinkEffect(nearby, level, groupId);
        }
    }

    private void applyLinkEffect(LivingEntity entity, int level, UUID groupId) {
        entityService.get(entity)
                .flatMap(holder -> holder.get(FundamentalisAPI.get().getStatusComponentKey()))
                .ifPresent(status -> status.applyEffect(
                        StatusEffectTypes.CHAIN_LINK, level,
                        StatusEffectTypes.CHAIN_LINK.getDefaultDurationTicks(), groupId.toString()));
    }

    private List<LivingEntity> findNearbyTargets(LivingEntity center, LivingEntity attacker, int level) {
        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity candidate : center.getNearbyEntities(MAX_CHAIN_RANGE, MAX_CHAIN_RANGE, MAX_CHAIN_RANGE)) {
            if (nearby.size() >= level) break;
            if (!(candidate instanceof LivingEntity living)) continue;
            if (attacker != null && candidate.getUniqueId().equals(attacker.getUniqueId())) continue;
            if (groupIdByMember.containsKey(living.getUniqueId())) continue; // déjà dans un groupe
            nearby.add(living);
        }
        return nearby;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie d'un membre — appelé par ChainVictimEffect
    // -------------------------------------------------------------------------

    /** Un membre rejoint son groupe : appelé depuis {@code ChainVictimEffect#onApply()}. */
    public void join(UUID groupId, LivingEntity member) {
        ChainGroup group = groupsById.get(groupId);
        if (group == null) return; // groupe déjà dissous entre-temps (rare, coup simultané)

        group.members.add(member.getUniqueId());
        groupIdByMember.put(member.getUniqueId(), groupId);

        if (!member.getUniqueId().equals(group.center)) {
            LivingEntity center = resolveLiving(group.center);
            if (center != null && !group.links.containsKey(member.getUniqueId())) {
                ChainLink link = new ChainLink(plugin, center, member).withLeash(MAX_CHAIN_RANGE);
                link.start();
                group.links.put(member.getUniqueId(), link);
            }
        }
    }

    /** Un membre quitte son groupe : appelé depuis {@code ChainVictimEffect#onRemove()}. */
    public void leave(UUID memberId) {
        UUID groupId = groupIdByMember.remove(memberId);
        if (groupId == null) return;

        ChainGroup group = groupsById.get(groupId);
        if (group == null) return;

        if (memberId.equals(group.center)) {
            reassignCenterOrDissolve(group);
            return;
        }

        ChainLink link = group.links.remove(memberId);
        if (link != null && link.isRunning()) link.stop();
        group.members.remove(memberId);

        // Un "groupe" à un seul membre restant n'a plus de sens : on dissout.
        if (group.members.size() < 2) {
            dissolveGroup(group);
        }
    }

    /** Le centre vient de partir : promeut un membre restant, ou dissout s'il n'en reste pas assez. */
    private void reassignCenterOrDissolve(ChainGroup group) {
        group.stopAllLinks();
        group.members.remove(group.center);

        if (group.members.size() < 2) {
            dissolveGroup(group);
            return;
        }

        UUID newCenter = pickReplacementCenter(group);
        LivingEntity center = newCenter != null ? resolveLiving(newCenter) : null;
        if (center == null) {
            dissolveGroup(group);
            return;
        }

        group.center = newCenter;
        for (UUID memberId : new ArrayList<>(group.members)) {
            if (memberId.equals(newCenter)) continue;

            LivingEntity member = resolveLiving(memberId);
            if (member == null) {
                group.members.remove(memberId);
                groupIdByMember.remove(memberId);
                continue;
            }

            ChainLink link = new ChainLink(plugin, center, member).withLeash(MAX_CHAIN_RANGE);
            link.start();
            group.links.put(memberId, link);
        }
    }

    /** Dissout entièrement le groupe : coupe les liens et force le retrait de CHAIN_LINK sur tous les membres. */
    private void dissolveGroup(ChainGroup group) {
        if (groupsById.remove(group.id) == null) return; // déjà dissous (garde-fou de ré-entrance)
        group.stopAllLinks();

        for (UUID memberId : new ArrayList<>(group.members)) {
            groupIdByMember.remove(memberId);
            LivingEntity member = resolveLiving(memberId);
            if (member == null) continue;

            entityService.get(member)
                    .flatMap(holder -> holder.get(FundamentalisAPI.get().getStatusComponentKey()))
                    .ifPresent(status -> status.removeEffect(StatusEffectTypes.CHAIN_LINK));
        }
        group.members.clear();
    }

    private UUID pickReplacementCenter(ChainGroup group) {
        for (UUID memberId : group.members) {
            if (resolveLiving(memberId) != null) return memberId;
        }
        return null;
    }

    private LivingEntity resolveLiving(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return (entity instanceof LivingEntity living && living.isValid()) ? living : null;
    }

    // -------------------------------------------------------------------------
    // Partage de dégâts — un seul listener pour tout le serveur
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPostDamage(PostDamageEvent event) {
        LivingEntity victim = event.getVictim();
        UUID victimId = victim.getUniqueId();

        // Dégât qu'on vient nous-mêmes de répercuter : on ne re-partage pas dessus,
        // sinon damageManager.dealDamage() (synchrone) provoquerait une cascade.
        if (currentlySplashing.contains(victimId)) return;

        UUID groupId = groupIdByMember.get(victimId);
        if (groupId != null) {
            shareDamage(groupsById.get(groupId), victimId, event.getFinalDamage());
        }
    }

    /** Répartit un pourcentage des dégâts subis par "sourceMember" sur le reste du groupe. */
    private void shareDamage(ChainGroup group, UUID sourceMember, double finalDamage) {
        if (group == null) return;

        double damage = finalDamage * DAMAGE_SHARED_PER_LEVEL * group.level;
        if (damage <= 0) return;

        // Copie défensive : dealDamage() peut, en cascade, tuer un membre et donc
        // modifier group.members (via leave()) pendant qu'on itère dessus ici.
        List<UUID> snapshot = new ArrayList<>(group.members);

        for (UUID memberId : snapshot) {
            if (memberId.equals(sourceMember)) continue;

            LivingEntity member = resolveLiving(memberId);
            if (member == null) continue;

            currentlySplashing.add(memberId);
            try {
                DamageInfo info = damageManager.createStatusDamage(
                        member, DamageSource.STATUS_CHAIN, damage, DamageType.PHYSICAL);
                info.setCanKnockback(false);
                damageManager.dealDamage(info);
            } finally {
                currentlySplashing.remove(memberId);
            }
        }
    }
}
