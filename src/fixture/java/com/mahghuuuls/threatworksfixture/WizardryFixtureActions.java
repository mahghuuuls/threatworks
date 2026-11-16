package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mojang.authlib.GameProfile;
import electroblob.wizardry.entity.living.EntityLightningWraith;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.entity.projectile.EntityMagicArrow;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

final class WizardryFixtureActions {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");

    private WizardryFixtureActions() {}

    static EntityLivingBase magicArrowCaster(Entity projectile) {
        return projectile instanceof EntityMagicArrow ? ((EntityMagicArrow) projectile).getCaster() : null;
    }

    static boolean cast(String spellId, EntityLiving caster, EntityLivingBase target) {
        Spell spell = Spell.get(spellId);
        if (spell == null) {
            return false;
        }
        SpellModifiers modifiers = caster instanceof ISpellCaster
                ? ((ISpellCaster) caster).getModifiers() : new SpellModifiers();
        return spell.cast(caster.world, caster, EnumHand.MAIN_HAND, 0, target, modifiers);
    }

    static void specialSmoke(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();
        int floorY = Math.min(230, Math.max(120, world.getHeight(spawn).getY() + 8));
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 12; z++) {
                world.setBlockState(new BlockPos(spawn.getX() + x, floorY, spawn.getZ() + z),
                        Blocks.STONE.getDefaultState());
            }
        }
        FakePlayer caster = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "SpecialSummonProbe")) {
            @Override
            public Vec3d getPositionVector() {
                return new Vec3d(posX, posY, posZ);
            }

            @Override
            public boolean isEntityInvulnerable(DamageSource source) {
                return false;
            }
        };
        caster.setHealth(caster.getMaxHealth());
        caster.capabilities.disableDamage = false;
        caster.setPosition(spawn.getX() + 0.5, floorY + 1, spawn.getZ() + 0.5);
        require(world.spawnEntity(caster), "Could not insert special summon caster");
        try {
            WizardrySpecialFixture.slime(caster);
            WizardrySpecialFixture.snow(caster);
            LOGGER.info("w2_special_smoke_passed");
        } finally {
            world.removeEntityDangerously(caster);
        }
    }

    static void smoke(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();
        double x = spawn.getX() + 0.5;
        double y = Math.min(245, world.getHeight(spawn).getY() + 5);
        double z = spawn.getZ() + 0.5;
        FakePlayer caster = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "WizardryProbe")) {
            @Override
            public Vec3d getPositionVector() {
                return new Vec3d(posX, posY, posZ);
            }

            @Override
            public boolean isEntityInvulnerable(DamageSource source) {
                return false;
            }
        };
        EntityLightningWraith wraith = new EntityLightningWraith(world);
        EntityZombie victim = new EntityZombie(world);
        wraith.setOwnerId(caster.getUniqueID());
        wraith.setLifetime(200);
        wraith.setNoAI(true);
        victim.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        victim.setHealth(80);
        victim.addTag("tw_summon_probe");
        caster.setHealth(caster.getMaxHealth());
        caster.capabilities.disableDamage = false;
        caster.setPosition(x + 2, y, z + 2);
        wraith.setLocationAndAngles(x, y, z, 0, 0);
        victim.setPosition(x, y, z + 2);
        require(world.spawnEntity(caster) && world.spawnEntity(wraith) && world.spawnEntity(victim),
                "Could not spawn the Arc fixture");
        try {
            float before = victim.getHealth();
            require(cast("ebwizardry:arc", wraith, victim) && victim.getHealth() < before,
                    "Native Arc did not damage the untracked victim");
            require(!EncounterCoordinator.INSTANCE.inspect(victim, 8).tracked, "Summon Arc opened an encounter");
            LOGGER.info("wizardry_arc_untracked lost={} caster={} actor={}", before - victim.getHealth(),
                    caster.getUniqueID(), wraith.getUniqueID());

            victim.hurtResistantTime = 0;
            require(EncounterCoordinator.supports(victim), "Arc victim has no supported combat policy");
            victim.attackEntityFrom(DamageSource.causePlayerDamage(caster), 2.0F);
            EncounterCoordinator.Inspection opened = EncounterCoordinator.INSTANCE.inspect(victim, 8);
            require(opened.tracked, "Player hit did not open the Arc encounter");
            double casterScore = score(opened, caster);
            require(casterScore > 0, "Opening player did not earn damage threat");

            victim.hurtResistantTime = 0;
            victim.addTag("tw_cancel_summon_reissue");
            before = victim.getHealth();
            cast("ebwizardry:arc", wraith, victim);
            EncounterCoordinator.Inspection canceled = EncounterCoordinator.INSTANCE.inspect(victim, 8);
            require(!victim.getTags().contains("tw_cancel_summon_reissue") && before == victim.getHealth()
                    && score(canceled, wraith) == 0 && score(canceled, caster) == casterScore,
                    "Canceled reissue did not preserve health and threat");
            LOGGER.info("wizardry_arc_canceled health={} casterScore={} actorScore=0", before, casterScore);

            victim.hurtResistantTime = 0;
            cast("ebwizardry:arc", wraith, victim);
            EncounterCoordinator.Inspection applied = EncounterCoordinator.INSTANCE.inspect(victim, 8);
            double lost = before - victim.getHealth();
            LOGGER.info("wizardry_arc_applied lost={} actorScore={} casterScore={} victim={}", lost,
                    score(applied, wraith), casterScore, victim.getUniqueID());
            require(lost > 0 && score(applied, wraith) == lost && score(applied, caster) == casterScore,
                    "Applied Arc did not credit exactly the summon health delta");
        } finally {
            victim.setDead();
            wraith.setDead();
            world.removeEntityDangerously(caster);
        }
    }

    static double score(EncounterCoordinator.Inspection view, EntityLivingBase participant) {
        return view.participants.stream().filter(entry -> entry.id.equals(participant.getUniqueID()))
                .mapToDouble(entry -> entry.score).findFirst().orElse(0);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
