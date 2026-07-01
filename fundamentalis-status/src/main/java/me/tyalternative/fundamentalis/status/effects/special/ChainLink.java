package me.tyalternative.fundamentalis.status.effects.special;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Relie deux entités par une chaîne visuelle composée de plusieurs BlockDisplay
 * (bloc CHAIN), orientés et mis à l'échelle pour suivre exactement le segment
 * qui les sépare.
 *
 * Fonctionnement :
 *  - Les BlockDisplay sont créés UNE SEULE FOIS, puis seule leur Transformation
 *    est mise à jour chaque tick (téléportation/scale interpolés côté client),
 *    ce qui est fluide et peu coûteux comparé à un respawn.
 *  - Si la distance entre les deux entités dépasse TARGET_LINK_LENGTH, le
 *    nombre de maillons augmente automatiquement, et la distance est répartie
 *    EXACTEMENT entre tous les maillons (tous ont la même longueur : pas de
 *    maillon étiré/déformé).
 *  - Le point d'ancrage utilisé sur chaque entité est le centre de son hitbox.
 *
 * Utilisation :
 *   ChainLink chain = new ChainLink(plugin, livingEntityA, livingEntityB);
 *   chain.start();
 *   ...
 *   chain.stop(); // à appeler quand l'effet doit se terminer (et dans onDisable)
 */
public class ChainLink {

    /** Longueur visée d'un maillon, en blocs (ajustée légèrement pour que la
     *  distance totale soit divisée en parts égales). */
    private static final double TARGET_LINK_LENGTH = 0.6;

    /** Épaisseur visuelle des maillons (échelle sur les axes X/Z). */
    private static final float THICKNESS = 1.0f;

    /** Durée d'interpolation côté client, en ticks, pour lisser à la fois le déplacement
     *  (teleport) et les changements de Transformation (rotation/échelle). Une valeur
     *  un peu plus grande que l'intervalle de mise à jour (1 tick) donne un mouvement
     *  fluide plutôt que des micro-saccades. */
    private static final int SMOOTHING_TICKS = 3;

    /** Transformation à échelle nulle utilisée pour rendre un maillon invisible
     *  sans le supprimer (il reste en place, prêt à être ré-affiché). */
    private static final Transformation HIDDEN_TRANSFORM = new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0f, 0f, 0f), new Quaternionf());

    private final Plugin plugin;
    private final EntityRef entityA;
    private final EntityRef entityB;

    /** Nombre de maillons supplémentaires gardés en réserve, en place mais invisibles
     *  (échelle 0), prêts à être révélés instantanément si la chaîne s'allonge — au lieu
     *  d'être spawnés à la volée loin de leur position finale. */
    private static final int POOL_LOOKAHEAD = 3;

    private final List<BlockDisplay> segments = new ArrayList<>();
    private BukkitTask task;
    private boolean running = false;

    // --- Contrainte de laisse (optionnelle) ---------------------------------
    private boolean leashEnabled = false;
    private double leashMaxDistance = 5.0;
    private boolean leashSymmetric = true;
    private double leashPullStrength = 0.06;   // accélération par bloc de dépassement
    private double leashMaxPullSpeed = 0.6;    // vitesse max injectée par tick (plafond)

    public ChainLink(Plugin plugin, LivingEntity entityA, LivingEntity entityB) {
        this.plugin = plugin;
        this.entityA = EntityRef.of(entityA);
        this.entityB = EntityRef.of(entityB);
    }

    public ChainLink(Plugin plugin, UUID entityA, UUID entityB) {
        this.plugin = plugin;
        this.entityA = EntityRef.of(entityA);
        this.entityB = EntityRef.of(entityB);
    }

    /** Active une contrainte de laisse douce : au-delà de maxDistance, les entités
     *  sont rappelées l'une vers l'autre (symétrique : les deux se rapprochent). */
    public ChainLink withLeash(double maxDistance) {
        return withLeash(maxDistance, true);
    }

    /** @param symmetric true = les deux entités se rapprochent l'une de l'autre ;
     *                   false = seule l'entité B (2e paramètre du constructeur) est tirée vers A. */
    public ChainLink withLeash(double maxDistance, boolean symmetric) {
        this.leashEnabled = true;
        this.leashMaxDistance = maxDistance;
        this.leashSymmetric = symmetric;
        return this;
    }

    /** Variante avancée pour régler finement la "raideur" et la vitesse max du rappel. */
    public ChainLink withLeash(double maxDistance, boolean symmetric, double pullStrength, double maxPullSpeed) {
        withLeash(maxDistance, symmetric);
        this.leashPullStrength = pullStrength;
        this.leashMaxPullSpeed = maxPullSpeed;
        return this;
    }

    /** Démarre la mise à jour de la chaîne (appelée à chaque tick). */
    public void start() {
        if (running) return;
        running = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    /** Arrête la chaîne et supprime tous les BlockDisplay créés. */
    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearSegments();
    }

    public boolean isRunning() {
        return running;
    }

    private void tick() {
        Entity a = entityA.resolve();
        Entity b = entityB.resolve();

        // Si une des deux entités a disparu (mort, déconnexion, etc.), on coupe la chaîne.
        if (a == null || b == null || !a.isValid() || !b.isValid() || a.getWorld() != b.getWorld()) {
            stop();
            return;
        }

        Location locA = anchor(a);
        Location locB = anchor(b);
        World world = locA.getWorld();

        Vector3f start = new Vector3f((float) locA.getX(), (float) locA.getY(), (float) locA.getZ());
        Vector3f end = new Vector3f((float) locB.getX(), (float) locB.getY(), (float) locB.getZ());

        Vector3f delta = new Vector3f(end).sub(start);
        float distance = delta.length();

        if (distance < 1.0e-3f) {
            hideAllSegments();
            return;
        }

        Vector3f direction = new Vector3f(delta).normalize();

        if (leashEnabled) {
            applyLeash(a, b, direction, distance);
        }

        // Nombre de maillons nécessaires pour ne jamais dépasser TARGET_LINK_LENGTH.
        int linkCount = Math.max(1, (int) Math.ceil(distance / TARGET_LINK_LENGTH));
        float segmentLength = distance / linkCount; // identique pour tous les maillons

        // Rotation alignant l'axe Y (axe par défaut du modèle du bloc CHAIN) sur la direction du segment.
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
        Vector3f scale = new Vector3f(THICKNESS, segmentLength, THICKNESS);

        growPool(world, linkCount, start);

        // Affiche/actualise tous les maillons nécessaires.
        for (int i = 0; i < linkCount; i++) {
            showSegment(segments.get(i), i, start, direction, segmentLength, rotation, scale);
        }

        // Maillons en réserve (pas encore nécessaires) : on les cache ET on les regroupe
        // au niveau du DERNIER maillon visible, à chaque tick. Comme ça, même si le
        // joueur bouge, ils suivent la chaîne au lieu de rester figés ailleurs sur la
        // carte — et le jour où il faut en révéler un, il est déjà au bon endroit.
        Vector3f tailCenter = new Vector3f(direction).mul(segmentLength * (linkCount - 0.5f)).add(start);
        Location tailLoc = new Location(world, tailCenter.x, tailCenter.y, tailCenter.z);
        for (int i = linkCount; i < segments.size(); i++) {
            BlockDisplay reserve = segments.get(i);
            hideSegment(reserve);
            if (reserve.getLocation().distanceSquared(tailLoc) > 1.0e-4) {
                reserve.teleport(tailLoc);
            }
        }
    }

    /**
     * Rappelle a et b l'un vers l'autre si distance > leashMaxDistance, de façon DOUCE :
     * on annule la composante de vitesse qui éloigne encore l'entité, puis on ajoute
     * une accélération vers l'autre entité, plafonnée par leashMaxPullSpeed.
     * Aucune téléportation : seule la vélocité est modifiée, donc ça reste fluide et
     * n'interfère pas avec le reste du mouvement (saut, esquive, etc.).
     */
    private void applyLeash(Entity a, Entity b, Vector3f direction, float distance) {
        if (distance <= leashMaxDistance) return;

        double overshoot = distance - leashMaxDistance;
        double pull = Math.min(overshoot * leashPullStrength, leashMaxPullSpeed);

        Vector dirAtoB = new Vector(direction.x, direction.y, direction.z); // pointe de a vers b
        Vector dirBtoA = dirAtoB.clone().multiply(-1);

        if (leashSymmetric) {
            // chacun reçoit la moitié du rappel : ils se rejoignent "au milieu"
            pullEntityToward(b, dirBtoA, pull * 0.5);
            pullEntityToward(a, dirAtoB, pull * 0.5);
        } else {
            // seule l'entité b (2e paramètre du constructeur) est ramenée vers a
            pullEntityToward(b, dirBtoA, pull);
        }
    }

    private void pullEntityToward(Entity entity, Vector inwardDir, double pullSpeed) {
        Vector vel = entity.getVelocity();
        Vector outwardDir = inwardDir.clone().multiply(-1);

        double outwardSpeed = vel.dot(outwardDir);
        Vector newVel = vel.clone();
        if (outwardSpeed > 0) {
            // annule uniquement la partie de la vitesse qui continue d'éloigner l'entité
            newVel.subtract(outwardDir.clone().multiply(outwardSpeed));
        }
        newVel.add(inwardDir.clone().multiply(pullSpeed));
        entity.setVelocity(newVel);
    }

    /**
     * Agrandit le pool si besoin, en pré-allouant POOL_LOOKAHEAD maillons de plus que
     * nécessaire. Les nouveaux maillons sont spawnés directement près du point "start"
     * (au lieu du spawn du monde) et cachés (échelle 0) s'ils ne sont pas encore requis,
     * pour qu'ils n'aient jamais besoin de "voler" depuis un endroit éloigné.
     */
    private void growPool(World world, int neededCount, Vector3f start) {
        int target = neededCount + POOL_LOOKAHEAD;
        Location spawnLoc = new Location(world, start.x, start.y, start.z);

        while (segments.size() < target) {
            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, d -> {
                d.setBlock(Material.CHAIN.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15)); // optionnel : toujours bien éclairé
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(SMOOTHING_TICKS); // lisse les changements de Transformation
                d.setTeleportDuration(SMOOTHING_TICKS);       // lisse les déplacements (teleport)
                d.setTransformation(HIDDEN_TRANSFORM);        // invisible tant qu'il n'est pas montré
            });
            segments.add(display);
        }
    }

    /** Positionne, oriente et rend visible le maillon "index". */
    private void showSegment(BlockDisplay display, int index, Vector3f start, Vector3f direction,
                             float segmentLength, Quaternionf rotation, Vector3f scale) {
        // Centre du maillon le long du segment A -> B.
        Vector3f center = new Vector3f(direction).mul(segmentLength * (index + 0.5f)).add(start);
        Location loc = new Location(display.getWorld(), center.x, center.y, center.z);

        // Le modèle de bloc occupe [0,1]³ par défaut : on recentre via la translation
        // (après rotation/scale) pour que le maillon soit centré exactement sur "loc".
        Vector3f translation = rotation.transform(
                new Vector3f(-0.5f * scale.x, -0.5f * scale.y, -0.5f * scale.z), new Vector3f());

        Transformation transformation = new Transformation(translation, rotation, scale, new Quaternionf());

        if (display.getLocation().distanceSquared(loc) > 1.0e-4) {
            display.teleport(loc);
        }
        display.setTransformation(transformation);
    }

    /** Rend le maillon invisible (échelle 0) sans le supprimer ni le déplacer. */
    private void hideSegment(BlockDisplay display) {
        display.setTransformation(HIDDEN_TRANSFORM);
    }

    /** Cache tous les maillons du pool, sans le vider. */
    private void hideAllSegments() {
        for (BlockDisplay display : segments) {
            hideSegment(display);
        }
    }

    private void clearSegments() {
        for (BlockDisplay seg : segments) {
            if (!seg.isDead()) seg.remove();
        }
        segments.clear();
    }

    /** Point d'ancrage = centre du hitbox de l'entité. */
    private Location anchor(Entity entity) {
        Location base = entity.getLocation();
        double halfHeight = entity.getBoundingBox().getHeight() / 2.0;
        return base.clone().add(0, halfHeight, 0);
    }

    /** Référence souple vers une entité : instance directe ou UUID résolu à chaque tick. */
    private static final class EntityRef {
        private final LivingEntity direct;
        private final UUID uuid;

        private EntityRef(LivingEntity direct, UUID uuid) {
            this.direct = direct;
            this.uuid = uuid;
        }

        static EntityRef of(LivingEntity entity) {
            return new EntityRef(entity, entity.getUniqueId());
        }

        static EntityRef of(UUID uuid) {
            return new EntityRef(null, uuid);
        }

        Entity resolve() {
            if (direct != null && direct.isValid()) {
                return direct;
            }
            // Bukkit ne fournit pas de lookup global par UUID : on parcourt les mondes chargés.
            for (World world : Bukkit.getWorlds()) {
                Entity e = world.getEntity(uuid);
                if (e != null) return e;
            }
            return null;
        }
    }
}