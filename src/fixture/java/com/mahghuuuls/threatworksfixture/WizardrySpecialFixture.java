package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import electroblob.wizardry.entity.living.EntityMagicSlime;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.mahghuuuls.threatworksfixture.WizardryFixtureActions.require;
import static com.mahghuuuls.threatworksfixture.WizardryFixtureActions.score;

public final class WizardrySpecialFixture {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");

    private WizardrySpecialFixture() {}

    static void slime(EntityPlayerMP caster) {
        WorldServer world = caster.getServerWorld();
        EntityZombie victim = new EntityZombie(world);
        placeVictim(victim, caster, 4);
        victim.setChild(false);
        Probe probe = new Probe(victim);
        MinecraftForge.EVENT_BUS.register(probe);
        EntityMagicSlime slime = null;
        float yaw = caster.rotationYaw;
        float pitch = caster.rotationPitch;
        float previousYaw = caster.prevRotationYaw;
        float previousPitch = caster.prevRotationPitch;
        try {
            caster.rotationYaw = caster.prevRotationYaw = 0;
            caster.rotationPitch = caster.prevRotationPitch = 4;
            require(cast(caster, "ebwizardry:slime"), "Wizardry rejected Slime cast");
            List<EntityMagicSlime> attached = world.getEntities(EntityMagicSlime.class,
                    entity -> entity.getRidingEntity() == victim && caster.getUniqueID().equals(entity.getOwnerId()));
            require(attached.size() == 1, "Expected one native Slime attached to the host");
            slime = attached.get(0);
            LOGGER.info("w2_slime_cast actor={} host={} caster={}", slime.getUniqueID(), victim.getUniqueID(),
                    caster.getUniqueID());
            checkSlimeTick("inactive", probe, victim, slime, caster, 1, 0);
            require(!EncounterCoordinator.INSTANCE.inspect(victim, 8).tracked, "Slime opened an encounter");
            activate(victim, caster);
            checkSlimeTick("applied", probe, victim, slime, caster, 1, 1);
            checkSlimeTick("nested_attack", probe, victim, slime, caster, 3, 1);
            checkSlimeTick("nested_hurt", probe, victim, slime, caster, 3, 1);
            checkSlimeTick("canceled", probe, victim, slime, caster, 0, 0);
            victim.setAbsorptionAmount(10);
            checkSlimeTick("absorbed", probe, victim, slime, caster, 0, 0);
            require(victim.getAbsorptionAmount() == 9, "Absorption control did not consume original damage");
            victim.setAbsorptionAmount(0);

            victim.hurtResistantTime = 0;
            float before = victim.getHealth();
            double oldScore = score(EncounterCoordinator.INSTANCE.inspect(victim, 8), slime);
            victim.attackEntityFrom(DamageSource.MAGIC, 2);
            double newScore = score(EncounterCoordinator.INSTANCE.inspect(victim, 8), slime);
            LOGGER.info("w2_slime_unrelated healthBefore={} healthAfter={} scoreBefore={} scoreAfter={}",
                    before, victim.getHealth(), oldScore, newScore);
            require(before - victim.getHealth() == 2 && oldScore == newScore,
                    "Anonymous same-host attack borrowed Slime evidence");

            victim.hurtResistantTime = 0;
            victim.setEntityInvulnerable(true);
            checkSlimeTick("invulnerable", probe, victim, slime, caster, 0, 0);
            victim.setEntityInvulnerable(false);
            checkSlimeTick("recovered", probe, victim, slime, caster, 1, 1);
        } finally {
            caster.rotationYaw = yaw;
            caster.rotationPitch = pitch;
            caster.prevRotationYaw = previousYaw;
            caster.prevRotationPitch = previousPitch;
            if (slime != null) {
                slime.setDead();
            }
            victim.setDead();
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
    }

    private static void checkSlimeTick(String name, Probe probe, EntityZombie victim, EntityMagicSlime slime,
                                       EntityPlayerMP caster, int expectedHealth, int expectedThreat) {
        victim.hurtResistantTime = 0;
        float before = victim.getHealth();
        EncounterCoordinator.Inspection prior = EncounterCoordinator.INSTANCE.inspect(victim, 8);
        probe.mode = name;
        probe.magicEntries = 0;
        // World normally assigns the entity tick before calling onUpdate.
        slime.ticksExisted = slime.ticksExisted / 16 * 16 + 17;
        slime.onUpdate();
        EncounterCoordinator.Inspection after = EncounterCoordinator.INSTANCE.inspect(victim, 8);
        double gained = score(after, slime) - score(prior, slime);
        LOGGER.info("w2_slime_case case={} host={} actor={} magicEntries={} lost={} gained={} target={}",
                name, victim.getUniqueID(), slime.getUniqueID(), probe.magicEntries, before - victim.getHealth(),
                gained, ArrowFixtureCommand.identity(victim.getAttackTarget()));
        require(before - victim.getHealth() == expectedHealth && gained == expectedThreat
                && score(after, caster) == score(prior, caster), "Unexpected Slime damage or threat in " + name);
        if (!"invulnerable".equals(name)) {
            require(probe.magicEntries == (name.startsWith("nested") ? 2 : 1), "Missing original Magic attack entry");
        }
    }

    static void snow(EntityPlayerMP caster) {
        WorldServer world = caster.getServerWorld();
        EntityBlaze blaze = new EntityBlaze(world);
        placeVictim(blaze, caster, 5);
        EntityZombie zombie = new EntityZombie(world);
        placeVictim(zombie, caster, 8);
        zombie.setChild(false);
        Probe probe = new Probe(blaze);
        MinecraftForge.EVENT_BUS.register(probe);
        EntitySnowman castGolem = null;
        EntitySnowman reloaded = null;
        EntitySnowman natural = new EntitySnowman(world);
        try {
            probe.blockSnowSpawn = true;
            cast(caster, "ebwizardry:summon_snow_golem");
            require(probe.rejectedSnow != null && !probe.rejectedSnow.isAddedToWorld(), "No canceled native spawn");
            require(world.spawnEntity(probe.rejectedSnow), "Unable to insert canceled instance for a control");
            LOGGER.info("w2_snow_reinsert_canceled actor={}", probe.rejectedSnow.getUniqueID());
            activate(blaze, caster);
            checkSnowball("canceled_spawn", probe, probe.rejectedSnow, blaze, 3, 0);
            probe.rejectedSnow.setDead();

            probe.insertedSnow = null;
            require(cast(caster, "ebwizardry:summon_snow_golem"), "Wizardry rejected Snow Golem cast");
            castGolem = probe.insertedSnow;
            require(castGolem != null && castGolem.isAddedToWorld(), "Wizardry did not insert a Snow Golem");
            LOGGER.info("w2_snow_cast actor={} caster={}", castGolem.getUniqueID(), caster.getUniqueID());
            checkSnowball("spell_spawn", probe, castGolem, blaze, 3, 3);
            activate(zombie, caster);
            checkSnowball("zero_health", probe, castGolem, zombie, 0, 0);
            castGolem.setPosition(caster.posX + 6, caster.posY, caster.posZ + 5);

            natural.setPosition(caster.posX, caster.posY, caster.posZ + 3);
            require(world.spawnEntity(natural), "Could not spawn the ordinary Snow Golem");
            checkSnowball("ordinary_spawn", probe, natural, blaze, 3, 0);

            Path saved = saveGolem(world, castGolem);
            world.removeEntityDangerously(castGolem);
            castGolem.isDead = false;
            require(world.spawnEntity(castGolem), "Unable to reinsert the removed golem instance");
            LOGGER.info("w2_snow_reinsert_removed actor={} entityId={}", castGolem.getUniqueID(), castGolem.getEntityId());
            checkSnowball("reinserted_instance", probe, castGolem, blaze, 3, 0);
            world.removeEntityDangerously(castGolem);
            reloaded = reloadGolem(world, saved);
            require(reloaded != null && reloaded != castGolem && world.spawnEntity(reloaded)
                    && reloaded.getUniqueID().equals(castGolem.getUniqueID()), "Native NBT loading did not replace golem");
            LOGGER.info("w2_snow_reloaded actor={} oldEntityId={} newEntityId={}", reloaded.getUniqueID(),
                    castGolem.getEntityId(), reloaded.getEntityId());
            checkSnowball("reloaded_instance", probe, reloaded, blaze, 3, 0);
        } finally {
            if (castGolem != null) castGolem.setDead();
            if (reloaded != null) reloaded.setDead();
            if (probe.rejectedSnow != null) probe.rejectedSnow.setDead();
            natural.setDead();
            blaze.setDead();
            zombie.setDead();
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
    }

    private static Path saveGolem(WorldServer world, EntitySnowman golem) {
        NBTTagCompound saved = golem.writeToNBT(new NBTTagCompound());
        saved.setString("id", String.valueOf(EntityList.getKey(golem)));
        try {
            Path saveFile = Files.createTempFile(world.getSaveHandler().getWorldDirectory().toPath(),
                    "threatworks-w2-snow-", ".dat");
            try (OutputStream output = Files.newOutputStream(saveFile)) {
                CompressedStreamTools.writeCompressed(saved, output);
            }
            LOGGER.info("w2_snow_save_disk file={} actor={}", saveFile.getFileName(), golem.getUniqueID());
            return saveFile;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save the control golem", failure);
        }
    }

    private static EntitySnowman reloadGolem(WorldServer world, Path saveFile) {
        try (InputStream input = Files.newInputStream(saveFile)) {
            return (EntitySnowman) EntityList.createEntityFromNBT(CompressedStreamTools.readCompressed(input), world);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not reload the control golem", failure);
        }
    }

    private static void checkSnowball(String name, Probe probe, EntitySnowman golem, EntityLiving victim,
                                      int expectedHealth, int expectedThreat) {
        victim.hurtResistantTime = 0;
        golem.setPosition(victim.posX, victim.posY, victim.posZ - 2.5);
        float before = victim.getHealth();
        double prior = score(EncounterCoordinator.INSTANCE.inspect(victim, 8), golem);
        probe.snowball = null;
        probe.snowImpact = null;
        golem.attackEntityWithRangedAttack(victim, 1.0F);
        EntitySnowball projectile = probe.snowball;
        require(projectile != null && projectile.getThrower() == golem, "Missing native snowball launch in " + name);
        // Begin outside the shooter's hit box so the direct ticks are independent of entity iteration order.
        projectile.setPosition(projectile.posX, projectile.posY, golem.posZ + 0.8);
        for (int tick = 0; tick < 20 && !projectile.isDead; tick++) {
            projectile.ticksExisted++;
            projectile.onUpdate();
        }
        double gained = score(EncounterCoordinator.INSTANCE.inspect(victim, 8), golem) - prior;
        LOGGER.info("w2_snow_case case={} actor={} shot={} victim={} nativeImpact={} lost={} gained={}",
                name, golem.getUniqueID(), projectile.getUniqueID(), victim.getUniqueID(),
                ArrowFixtureCommand.identity(probe.snowImpact), before - victim.getHealth(), gained);
        projectile.setDead();
        require(probe.snowImpact == victim && before - victim.getHealth() == expectedHealth && gained == expectedThreat,
                "Unexpected snowball impact, damage or threat in " + name);
    }

    private static boolean cast(EntityPlayerMP caster, String id) {
        Spell spell = Spell.get(id);
        return spell != null && spell.cast(caster.world, caster, EnumHand.MAIN_HAND, 0, new SpellModifiers());
    }

    private static void placeVictim(EntityLiving victim, EntityPlayerMP caster, double distance) {
        victim.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        victim.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        victim.setHealth(80);
        victim.addTag("tw_summon_probe");
        victim.setPosition(caster.posX, caster.posY, caster.posZ + distance);
        require(caster.world.spawnEntity(victim), "Could not insert special summon victim");
    }

    private static void activate(EntityLivingBase victim, EntityPlayerMP caster) {
        victim.hurtResistantTime = 0;
        float before = victim.getHealth();
        victim.attackEntityFrom(DamageSource.causePlayerDamage(caster), 2);
        require(victim.getHealth() < before && EncounterCoordinator.INSTANCE.inspect(victim, 8).tracked,
                "Player damage did not open the special summon encounter");
    }

    public static final class Probe {
        private final EntityLiving target;
        private String mode = "";
        private int magicEntries;
        private boolean blockSnowSpawn;
        private EntitySnowman rejectedSnow;
        private EntitySnowman insertedSnow;
        private EntitySnowball snowball;
        private Entity snowImpact;

        Probe(EntityLiving target) {
            this.target = target;
        }

        @SubscribeEvent
        public void attack(LivingAttackEvent event) {
            if (event.getEntityLiving() == target && event.getSource() == DamageSource.MAGIC) {
                magicEntries++;
                if ("canceled".equals(mode)) {
                    event.setCanceled(true);
                } else if ("nested_attack".equals(mode)) {
                    nested();
                }
            }
        }

        @SubscribeEvent
        public void hurt(LivingHurtEvent event) {
            if (event.getEntityLiving() == target && event.getSource() == DamageSource.MAGIC
                    && "nested_hurt".equals(mode)) {
                nested();
            }
        }

        private void nested() {
            mode = "";
            target.hurtResistantTime = 0;
            target.attackEntityFrom(DamageSource.MAGIC, 2);
            target.hurtResistantTime = 0;
        }

        @SubscribeEvent
        public void spawn(EntityJoinWorldEvent event) {
            if (event.getWorld() != target.world) return;
            if (event.getEntity() instanceof EntitySnowman) {
                if (blockSnowSpawn) {
                    blockSnowSpawn = false;
                    rejectedSnow = (EntitySnowman) event.getEntity();
                    event.setCanceled(true);
                } else {
                    insertedSnow = (EntitySnowman) event.getEntity();
                }
            } else if (event.getEntity() instanceof EntitySnowball) {
                snowball = (EntitySnowball) event.getEntity();
            }
        }

        @SubscribeEvent
        public void impact(ProjectileImpactEvent.Throwable event) {
            if (event.getThrowable() == snowball) {
                snowImpact = event.getRayTraceResult().entityHit;
            }
        }
    }
}
