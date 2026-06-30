package me.tyalternative.fundamentalis.status.effects.CC;

import me.tyalternative.fundamentalis.api.component.ComponentHolder;
import me.tyalternative.fundamentalis.api.event.combat.PreDamageEvent;
import me.tyalternative.fundamentalis.api.stats.IStatsComponent;
import me.tyalternative.fundamentalis.api.status.ActiveStatusEffect;
import me.tyalternative.fundamentalis.status.StatusComponent;
import me.tyalternative.fundamentalis.status.StatusEffect;
import me.tyalternative.fundamentalis.status.StatusEffectTypes;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
public class FreezeEffect extends StatusEffect implements Listener {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final double ICE_BREAK_DAMAGE_MULTIPLICATOR = 1.0;

    // -------------------------------------------------------------------------
    // Champs
    // -------------------------------------------------------------------------

    private final NamespacedKey modifierKey;
    private List<BlockDisplay> iceCubeBlockDisplays = new ArrayList<>();

    private final Plugin plugin;
    /** État propre à cette instance : évite un double-enregistrement Bukkit. */
    private boolean listening = false;

    public FreezeEffect(ComponentHolder holder, IStatsComponent statsComponent, ActiveStatusEffect meta,Plugin plugin) {
        super(holder, statsComponent, meta);
        this.modifierKey = new NamespacedKey("fundamentalis", "status_freeze_" + meta.id());
        this.plugin = plugin;
    }

    @Override
    public void onApply() {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;



//        if () {
//
//        }

        LivingEntity entity = getEntity();
        if (entity == null || !entity.isValid()) return;

        entity.sendMessage("size : " + getHolder().require(StatusComponent.KEY).getAllEffectsOfType(StatusEffectTypes.FREEZE).size());

        if (entity instanceof Player) {
            AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            AttributeInstance jumpAttr = entity.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
            AttributeInstance attackSpeedAttr = entity.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (speedAttr != null && jumpAttr != null && attackSpeedAttr != null) {
                var modifier = new AttributeModifier(modifierKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                speedAttr.addModifier(modifier);
                jumpAttr.addModifier(modifier);
                attackSpeedAttr.addModifier(modifier);
            }
        }
        if (entity instanceof Mob) {
            entity.setAI(true);
            ((Mob) entity).setAware(true);
        }

//        buildIceBlock(entity);

        // Effet visuel de glace
        entity.setFreezeTicks(getDurationInt());
        entity.lockFreezeTicks(true);

        // Particules de glace
        entity.getWorld().spawnParticle(
                Particle.SNOWFLAKE,
                entity.getLocation().add(0, 1, 0),
                30, 0.3, 1, 0.3, 0.1
        );

    }

    @Override
    public void onTick() {
        // Rien de périodique.
    }

    @Override
    public void onRemove() {
        if (!listening) return;
        HandlerList.unregisterAll(this);
        listening = false;

        LivingEntity entity = getEntity();
        if (entity == null || !entity.isValid()) return;

        entity.sendMessage("size : " + getHolder().require(StatusComponent.KEY).getAllEffectsOfType(StatusEffectTypes.FREEZE).size());

        if (entity instanceof Player) {
            AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            AttributeInstance jumpAttr = entity.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
            AttributeInstance attackSpeedAttr = entity.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (speedAttr != null && jumpAttr != null && attackSpeedAttr != null) {
                var modifier = new AttributeModifier(modifierKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                speedAttr.removeModifier(modifier);
                jumpAttr.removeModifier(modifier);
                attackSpeedAttr.removeModifier(modifier);
            }
        }
        if (entity instanceof Mob) {
            entity.setAI(true);
            ((Mob) entity).setAware(true);
        }


        entity.lockFreezeTicks(false);


//        breakIceCube();
    }

    private void buildIceBlock(LivingEntity entity) {

        int mobHeight = (int) Math.ceil(entity.getHeight());

        Location loc = entity.getLocation().add(0,mobHeight-1,0);
        loc.setYaw(90);
        loc.setPitch(0);

        iceCubeBlockDisplays.add(entity.getWorld().spawn(loc, BlockDisplay.class, block -> {
            block.setBlock(Material.ICE.createBlockData());
            block.setBrightness(new Display.Brightness(15,15));
            block.setTransformation(
                    new Transformation(
                            new Vector3f(-0.5f,(float)  1.01-mobHeight,-0.5f),
                            new AxisAngle4f(0f,0f,0f,1f),
                            new Vector3f(1, 0, 1),
                            new AxisAngle4f(0f,0f,0f,1f)
                    )
            );
        }));
        iceCubeBlockDisplays.add(entity.getWorld().spawn(loc, BlockDisplay.class, block -> {
            block.setBlock(Material.ICE.createBlockData());
            block.setBrightness(new Display.Brightness(15,15));
            block.setTransformation(
                    new Transformation(
                            new Vector3f(-0.5f,1.0f,-0.5f),
                            new AxisAngle4f(0f,1f,0f,1f),
                            new Vector3f(1, 0, 1),
                            new AxisAngle4f(0f,0f,0f,1f)
                    )
            );
        }));

        for (int yaw = -180; yaw <= 90; yaw+=90) {
            loc = entity.getLocation();
            loc.setYaw(yaw);
            loc.setPitch(0);
            for (int level = 0; level < mobHeight; level++) {
                int finalLevel = level;
                iceCubeBlockDisplays.add(entity.getWorld().spawn(loc, BlockDisplay.class, block -> {
                    block.setBlock(Material.ICE.createBlockData());
                    block.setBrightness(new Display.Brightness(15,15));
                    block.setTransformation(
                            new Transformation(
                                    new Vector3f(-0.5f,(float) finalLevel ,-0.5f),
                                    new AxisAngle4f(0f,0f,0f,1f),
                                    new Vector3f(0, 1, 1),
                                    new AxisAngle4f(0f,0f,0f,1f)
                            )
                    );
                }));
            }
        }
    }

    public void breakIceCube() {
        getHolder().require(StatusComponent.KEY).removeEffect(StatusEffectTypes.FREEZE);

        LivingEntity entity = getEntity();

        if (entity == null) return;
        // Particules de glace
        entity.getWorld().spawnParticle(
                Particle.SNOWFLAKE,
                entity.getLocation().add(0, 1, 0),
                50, 2, 1, 2, 0.3
        );
    }

    @EventHandler
    public void onDamageTaken(PreDamageEvent event) {
        double damage = event.getDamage() * ICE_BREAK_DAMAGE_MULTIPLICATOR;
        event.setDamage(damage);
        event.setForceCrit(true);

        breakIceCube();

    }
}
