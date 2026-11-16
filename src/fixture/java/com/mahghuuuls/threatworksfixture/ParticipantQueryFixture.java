package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation;
import com.mahghuuuls.threatworks.api.ThreatQueries.Focus;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import com.mahghuuuls.threatworks.server.DragonStrafe;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.GuardianBeam;
import net.minecraft.command.CommandException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAICreeperSwell;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.boss.dragon.phase.PhaseStrafePlayer;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ParticipantQueryFixture {
    private static final ParticipantQueryConsumer CONSUMER = new ParticipantQueryConsumer();

    private ParticipantQueryFixture() {}

    static void smoke(EntityPlayerMP player) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityPlayerMP other = fake(world, "HitraChallenger", player.posX + 2, player.posY, player.posZ + 1);
        EntityZombie mob = new EntityZombie(world);
        mob.setChild(false);
        mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
        mob.setPosition(player.posX + 1, player.posY, player.posZ + 1);
        EntityCow unsupported = new EntityCow(world);
        unsupported.setPosition(player.posX - 5, player.posY, player.posZ);
        require(world.spawnEntity(mob) && world.spawnEntity(unsupported), "Could not insert query mobs");
        try {
            require(read("unsupported", unsupported, player).state() == State.UNSUPPORTED, "Unsupported type reported support");
            require(read("inactive", mob, player).state() == State.INACTIVE, "New mob already tracked");
            View offThread = ThreatQueryCommand.offThread(mob, player);
            ThreatQueryCommand.report("offthread", mob, player, offThread);
            require(offThread.state() == State.WRONG_THREAD && !offThread.threat().isPresent(), "Off-thread state leaked");

            mob.setAttackTarget(player);
            open(mob, player);
            View incumbent = read("opening_incumbent", mob, player);
            View challenger = read("opening_challenger", mob, other);
            require(incumbent.state() == State.TRACKED && incumbent.threat().getAsDouble() > 0
                    && incumbent.aggro() == Confirmation.YES && incumbent.focus() == Focus.ORDINARY,
                    "Confirmed incumbent was not available");
            require(challenger.threat().getAsDouble() == 0 && challenger.aggro() == Confirmation.NO
                    && challenger.eligibility() == Confirmation.UNKNOWN, "Live unscored challenger was not a known zero");

            ReentrantRead reentrant = new ReentrantRead(mob, other);
            MinecraftForge.EVENT_BUS.register(reentrant);
            try {
                require(ThreatworksMod.threatActions().taunt(mob, other) == ThreatActions.Result.APPLIED,
                        "Taunt fixture did not apply");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(reentrant);
            }
            require(reentrant.view != null && reentrant.view.aggro() == Confirmation.UNKNOWN
                    && reentrant.view.tauntActive() == Confirmation.UNKNOWN && reentrant.view.threat().getAsDouble() == 0,
                    "Taunt became visible before commit");
            View taunted = read("committed_taunt", mob, other);
            require(mob.getAttackTarget() == other && taunted.tauntActive() == Confirmation.YES
                    && taunted.tauntForcingFocus() == Confirmation.YES && taunted.tauntTicks().getAsInt() > 0
                    && taunted.aggro() == Confirmation.YES, "Committed taunt and native target disagreed");
            for (int poll = 0; poll < 128; poll++) {
                View repeated = CONSUMER.read(mob, other);
                require(repeated.threat().equals(taunted.threat()) && repeated.tauntTicks().equals(taunted.tauntTicks())
                        && repeated.aggro() == taunted.aggro(), "Repeated reads changed the encounter");
            }
            require(mob.getAttackTarget() == other, "Polling changed the native target");

            ScorePlayerTeam team = world.getScoreboard().createTeam("h1_query_team");
            world.getScoreboard().addPlayerToTeam(mob.getCachedUniqueIdString(), team.getName());
            world.getScoreboard().addPlayerToTeam(other.getName(), team.getName());
            View allied = read("allied_target", mob, other);
            require(allied.eligibility() == Confirmation.NO && allied.aggro() == Confirmation.UNKNOWN
                    && allied.tauntForcingFocus() == Confirmation.NO, "Team change retained eligibility or taunt influence");
            world.getScoreboard().removeTeam(team);

            other.capabilities.disableDamage = true;
            View ineligible = read("target_ineligible", mob, other);
            require(ineligible.eligibility() == Confirmation.NO && ineligible.tauntActive() == Confirmation.NO
                    && ineligible.threat().equals(taunted.threat()), "Ineligibility lost score or retained an active taunt");
            other.capabilities.disableDamage = false;
            require(ThreatworksMod.threatActions().taunt(mob, player) == ThreatActions.Result.APPLIED,
                    "Could not replace active taunt");
            require(read("superseded_taunt", mob, player).aggro() == Confirmation.YES && mob.getAttackTarget() == player
                    && read("previous_taunter", mob, other).aggro() == Confirmation.NO, "Superseded taunt retained focus");
            wither(player, other);
            guardian(player, other);
            creeper(player);
            evoker(player);
            dragon(player);
            rejectedAssignment(player, other);
            sameUuidReplacement(player);
            other.setHealth(0);
            require(read("dead_participant", mob, other).state() == State.INVALID_PARTICIPANT, "Dead query participant accepted");
            mob.setDead();
            require(read("dead_mob", mob, player).state() == State.MOB_UNAVAILABLE, "Dead mob remained available");
        } finally {
            world.removeEntityDangerously(other);
            mob.setDead();
            unsupported.setDead();
        }
    }

    static void forgeSmoke(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();
        int ground = Math.min(230, Math.max(120, world.getHeight(spawn).getY() + 8));
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 10; z++) {
                world.setBlockState(new BlockPos(spawn.getX() + x, ground, spawn.getZ() + z), Blocks.STONE.getDefaultState());
            }
        }
        EntityPlayerMP caster = null;
        try {
            caster = fake(world, "ThreatQueryProbe", spawn.getX() + 0.5, ground + 1, spawn.getZ() + 0.5);
            smoke(caster);
        } catch (CommandException failure) {
            throw new IllegalStateException("Threat query fixture failed", failure);
        } finally {
            if (caster != null) world.removeEntityDangerously(caster);
        }
    }

    private static void wither(EntityPlayerMP player, EntityPlayerMP other) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityWither wither = new EntityWither(world);
        wither.setPosition(player.posX, player.posY, player.posZ + 5);
        require(world.spawnEntity(wither), "Could not insert Wither");
        try {
            wither.setAttackTarget(player);
            open(wither, player);
            wither.tasks.onUpdateTasks();
            wither.updateWatchedTargetId(0, player.getEntityId());
            wither.updateWatchedTargetId(1, other.getEntityId());
            View center = read("wither_center", wither, player);
            View side = read("wither_side", wither, other);
            require(center.focus() == Focus.WITHER_CENTER && center.aggro() == Confirmation.YES
                    && side.focus() == Focus.WITHER_CENTER && side.aggro() == Confirmation.NO,
                    "Wither center and side heads were not independent");
            require(ThreatworksMod.threatActions().taunt(wither, other) == ThreatActions.Result.APPLIED,
                    "Wither taunt did not update the setter");
            require(read("wither_setter_ahead_of_center", wither, other).aggro() == Confirmation.UNKNOWN,
                    "Wither confirmed focus before the center state refreshed");
            wither.updateWatchedTargetId(0, other.getEntityId());
            require(read("wither_watcher_ahead_of_goal", wither, other).aggro() == Confirmation.UNKNOWN,
                    "Wither treated the watcher as proof despite a stale ranged goal");
            wither.tasks.onUpdateTasks();
            require(read("wither_updated_center", wither, other).aggro() == Confirmation.YES,
                    "Wither aim did not refresh through the existing ranged goal");
            wither.setInvulTime(100);
            View nativeMode = read("wither_native", wither, player);
            require(nativeMode.state() == State.TRACKED && nativeMode.ordinarySwitching() == Confirmation.NO
                    && nativeMode.aggro() == Confirmation.UNKNOWN && nativeMode.threat().isPresent(),
                    "Native Wither phase lost score or claimed focus");
        } finally {
            wither.setDead();
        }
    }

    private static void guardian(EntityPlayerMP player, EntityPlayerMP other) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityGuardian guardian = new EntityGuardian(world);
        guardian.setPosition(player.posX, player.posY, player.posZ + 5);
        require(world.spawnEntity(guardian), "Could not insert Guardian");
        try {
            require(ThreatworksMod.threatActions().taunt(guardian, player) == ThreatActions.Result.APPLIED,
                    "Guardian taunt did not open tracking");
            EntityAIBase beam = guardian.tasks.taskEntries.stream().map(entry -> entry.action)
                    .filter(goal -> goal.getClass().getSimpleName().equals("AIGuardianAttack"))
                    .findFirst().orElseThrow(() -> new CommandException("Missing native Guardian beam goal"));
            require(beam.shouldExecute(), "Native beam refused the player target");
            beam.startExecuting();
            for (int tick = 0; tick < 10; tick++) beam.updateTask();
            require(((GuardianBeam) guardian).threatworks$beamTargetId() == player.getEntityId(), "Beam ID did not commit");
            View during = read("guardian_beam", guardian, player);
            require(during.focus() == Focus.GUARDIAN_BEAM && during.aggro() == Confirmation.YES
                    && during.tauntActive() == Confirmation.YES && during.tauntForcingFocus() == Confirmation.NO,
                    "Beam focus and existing taunt timer were conflated");
            ReflectionHelper.setPrivateValue(EntityLiving.class, guardian, other, "attackTarget", "field_70696_bz");
            require(guardian.getAttackTarget() == other && read("guardian_beam_differs_from_setter", guardian, player)
                    .aggro() == Confirmation.UNKNOWN, "Guardian claimed focus while setter and beam disagreed");
            ReflectionHelper.setPrivateValue(EntityLiving.class, guardian, player, "attackTarget", "field_70696_bz");
            require(ThreatworksMod.threatActions().taunt(guardian, other) != ThreatActions.Result.APPLIED,
                    "Competing taunt displaced an active beam");
            View otherDuring = read("guardian_rejected_taunt", guardian, other);
            require(otherDuring.aggro() == Confirmation.NO && otherDuring.tauntTicks().equals(during.tauntTicks()),
                    "Rejection changed beam or taunt state");
            for (int tick = 0; tick <= guardian.getAttackDuration() && guardian.getAttackTarget() != null; tick++) {
                beam.updateTask();
            }
            require(player.isEntityAlive() && guardian.getAttackTarget() == null
                    && ((GuardianBeam) guardian).threatworks$beamTargetId() == player.getEntityId()
                    && read("guardian_beam_completed_before_reset", guardian, player).aggro() == Confirmation.UNKNOWN,
                    "Guardian claimed the completed beam as current focus");
        } finally {
            guardian.setDead();
        }
    }

    private static void creeper(EntityPlayerMP player) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityCreeper creeper = new EntityCreeper(world);
        creeper.setPosition(player.posX - 1.1, player.posY, player.posZ);
        require(world.spawnEntity(creeper), "Could not insert Creeper");
        try {
            require(ThreatworksMod.threatActions().taunt(creeper, player) == ThreatActions.Result.APPLIED,
                    "Creeper taunt did not apply before its state transition");
            require(read("creeper_ordinary", creeper, player).tauntForcingFocus() == Confirmation.YES,
                    "Creeper never saw ordinary focus");
            EntityAITaskEntry swell = creeper.tasks.taskEntries.stream()
                    .filter(entry -> entry.action.getClass() == EntityAICreeperSwell.class).findFirst()
                    .orElseThrow(() -> new CommandException("Missing native Creeper swell goal"));
            swell.using = true;
            require(read("creeper_swell_before_fuse_flag", creeper, player).ordinarySwitching() == Confirmation.NO,
                    "Started Creeper swell goal retained ordinary mode");
            swell.using = false;
            creeper.setFire(1);
            View nativeMode = read("creeper_burning_before_tick", creeper, player);
            require(nativeMode.ordinarySwitching() == Confirmation.NO && nativeMode.aggro() == Confirmation.UNKNOWN
                    && nativeMode.tauntForcingFocus() == Confirmation.NO, "Changed Creeper mode retained forced focus");
        } finally {
            creeper.setDead();
        }
    }

    private static void evoker(EntityPlayerMP player) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityEvoker evoker = new EntityEvoker(world);
        evoker.setPosition(player.posX + 2, player.posY, player.posZ);
        require(world.spawnEntity(evoker), "Could not insert Evoker");
        try {
            require(ThreatworksMod.threatActions().taunt(evoker, player) == ThreatActions.Result.APPLIED,
                    "Evoker taunt did not open tracking");
            EntityAITaskEntry retreat = evoker.tasks.taskEntries.stream()
                    .filter(entry -> entry.priority == 2 && entry.action.getClass() == EntityAIAvoidEntity.class)
                    .findFirst().orElseThrow(() -> new CommandException("Missing native Evoker retreat goal"));
            retreat.using = true;
            require(read("evoker_retreat_before_tick", evoker, player).ordinarySwitching() == Confirmation.NO,
                    "Started Evoker retreat retained ordinary mode");
        } finally {
            evoker.setDead();
        }
    }

    private static void rejectedAssignment(EntityPlayerMP player, EntityPlayerMP other) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityZombie mob = new EntityZombie(world);
        mob.setPosition(player.posX + 1, player.posY, player.posZ + 1);
        require(world.spawnEntity(mob), "Could not insert rejected assignment fixture");
        ReentrantRead rejection = new ReentrantRead(mob, other, true);
        MinecraftForge.EVENT_BUS.register(rejection);
        try {
            mob.setAttackTarget(player);
            open(mob, player);
            require(ThreatworksMod.threatActions().taunt(mob, other) == ThreatActions.Result.TARGET_ASSIGNMENT_REJECTED,
                    "Expected a native assignment rejected during the setter");
            View after = read("rejected_assignment", mob, other);
            require(after.tauntActive() != Confirmation.YES && after.aggro() == Confirmation.UNKNOWN
                    && after.threat().getAsDouble() == 0, "Rejected taunt published a score or focus");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(rejection);
            other.capabilities.disableDamage = false;
            mob.setDead();
        }
    }

    private static void sameUuidReplacement(EntityPlayerMP player) throws CommandException {
        WorldServer world = player.getServerWorld();
        EntityZombie mob = new EntityZombie(world);
        mob.setPosition(player.posX + 1, player.posY, player.posZ + 1);
        require(world.spawnEntity(mob), "Could not insert identity replacement fixture");
        EntityPlayerMP original = fake(world, "HitraOriginalEntity", player.posX + 2, player.posY, player.posZ + 1);
        EntityPlayerMP replacement = CoverageFixtureCommand.fixturePlayer(world, "", original.getName());
        replacement.setUniqueId(original.getUniqueID());
        replacement.setPosition(original.posX, original.posY, original.posZ);
        try {
            require(ThreatworksMod.threatActions().taunt(mob, original) == ThreatActions.Result.APPLIED,
                    "Replacement fixture failed to establish a committed taunt");
            View previous = read("before_same_uuid_replacement", mob, original);
            require(previous.tauntActive() == Confirmation.YES && previous.tauntTicks().getAsInt() > 0,
                    "Replacement fixture lacked an unexpired timer");
            world.removeEntityDangerously(original);
            require(world.spawnEntity(replacement), "Could not insert same-UUID participant");
            View stale = read("old_same_uuid_reference", mob, original);
            View next = read("new_same_uuid_reference", mob, replacement);
            require(stale.state() == State.INVALID_PARTICIPANT && next.state() == State.TRACKED
                    && next.eligibility() == Confirmation.UNKNOWN && next.tauntActive() == Confirmation.NO
                    && next.threat().equals(previous.threat()), "Replacement inherited taunt or lost UUID-scoped threat");
            require(ThreatworksMod.threatActions().grant(mob, player, 100) == ThreatActions.Result.APPLIED,
                    "Could not score competing participant");
            ((EncounterCoordinator) ThreatworksMod.threatActions()).onWorldTick(
                    new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
            View settled = read("same_uuid_after_reconciliation", mob, player);
            require(mob.getAttackTarget() == player && settled.tauntActive() == Confirmation.NO
                    && settled.aggro() == Confirmation.YES, "Stale taunt blocked the eligible competing participant");
        } finally {
            if (original.isAddedToWorld()) world.removeEntityDangerously(original);
            if (replacement.isAddedToWorld()) world.removeEntityDangerously(replacement);
            mob.setDead();
        }
    }

    private static void dragon(EntityPlayerMP overworldPlayer) throws CommandException {
        WorldServer end = overworldPlayer.getServer().getWorld(1);
        BlockPos spawn = end.getSpawnPoint();
        end.getChunk(spawn);
        EntityPlayerMP player = fake(end, "HitraEnd", spawn.getX() + 0.5, 80, spawn.getZ() + 0.5);
        EntityDragon dragon = new EntityDragon(end);
        dragon.setPosition(player.posX, player.posY + 8, player.posZ);
        require(end.spawnEntity(dragon), "Could not insert Dragon");
        try {
            float before = dragon.getHealth();
            dragon.attackEntityFromPart(dragon.dragonPartHead, DamageSource.causePlayerDamage(player), 2);
            require(dragon.getHealth() < before, "Dragon part took no applied damage");
            require(read("cross_world", dragon, overworldPlayer).state() == State.INVALID_PARTICIPANT,
                    "Participant from another dimension was accepted");
            dragon.getPhaseManager().setPhase(PhaseList.STRAFE_PLAYER);
            PhaseStrafePlayer phase = dragon.getPhaseManager().getPhase(PhaseList.STRAFE_PLAYER);
            phase.setTarget(player);
            require(read("dragon_unconfirmed_strafe", dragon, player).aggro() == Confirmation.UNKNOWN,
                    "Unconfirmed Dragon strafe claimed aggro");
            ((DragonStrafe) phase).threatworks$markOrdinaryStrafe();
            View confirmed = read("dragon_confirmed_strafe", dragon, player);
            require(((DragonStrafe) phase).threatworks$strafeTarget() == player && confirmed.state() == State.TRACKED
                    && confirmed.focus() == Focus.DRAGON_STRAFE && confirmed.aggro() == Confirmation.YES,
                    "Confirmed Dragon strafe focus unavailable");
            dragon.getPhaseManager().setPhase(PhaseList.HOLDING_PATTERN);
            View after = read("dragon_after_strafe", dragon, player);
            require(after.aggro() == Confirmation.UNKNOWN && after.threat().equals(confirmed.threat()),
                    "Prior Dragon target survived a different phase");
        } finally {
            dragon.setDead();
            end.removeEntityDangerously(player);
        }
    }

    static EntityPlayerMP fake(WorldServer world, String name, double x, double y, double z) throws CommandException {
        EntityPlayerMP fake = CoverageFixtureCommand.fixturePlayer(world, "", name);
        fake.dimension = world.provider.getDimension();
        fake.setPosition(x, y, z);
        fake.capabilities.disableDamage = false;
        require(world.spawnEntity(fake), "Could not insert query participant");
        return fake;
    }

    private static void open(EntityLivingBase mob, EntityPlayerMP player) throws CommandException {
        float before = mob.getHealth();
        mob.attackEntityFrom(DamageSource.causePlayerDamage(player), 2);
        require(mob.getHealth() < before, "Player fixture hit did not remove health");
    }

    private static View read(String label, EntityLivingBase mob, EntityLivingBase participant) {
        View view = CONSUMER.read(mob, participant);
        ThreatQueryCommand.report(label, mob, participant, view);
        return view;
    }

    private static void require(boolean condition, String message) throws CommandException {
        if (!condition) throw new CommandException(message);
    }

    public static final class ReentrantRead {
        private final EntityZombie mob;
        private final EntityPlayerMP participant;
        private final boolean reject;
        private View view;

        ReentrantRead(EntityZombie mob, EntityPlayerMP participant) {
            this(mob, participant, false);
        }

        ReentrantRead(EntityZombie mob, EntityPlayerMP participant, boolean reject) {
            this.mob = mob;
            this.participant = participant;
            this.reject = reject;
        }

        @SubscribeEvent
        public void targetChanged(LivingSetAttackTargetEvent event) {
            if (event.getEntityLiving() == mob && event.getTarget() == participant) {
                view = read("in_target_setter", mob, participant);
                if (reject) participant.capabilities.disableDamage = true;
            }
        }
    }
}
