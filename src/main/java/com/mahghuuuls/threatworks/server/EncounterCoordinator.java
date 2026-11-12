package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EncounterCoordinator {
    public static final EncounterCoordinator INSTANCE = new EncounterCoordinator();
    private static final ResourceLocation ZOMBIE_ID = new ResourceLocation("minecraft", "zombie");

    private final Map<WorldServer, WorldEncounters> worlds = new IdentityHashMap<>();

    private EncounterCoordinator() {
    }

    public static boolean supports(EntityLivingBase mob) {
        return mob.world instanceof WorldServer && mob.getClass() == EntityZombie.class
                && ZOMBIE_ID.equals(EntityList.getKey(mob));
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world instanceof WorldServer && !worlds.containsKey(world)) {
            WorldEncounters state = new WorldEncounters();
            worlds.put((WorldServer) world, state);
            world.addEventListener(state);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world instanceof WorldServer) {
            WorldEncounters state = worlds.remove(world);
            if (state != null) {
                world.removeEventListener(state);
            }
        }
    }

    public void onHealthWritten(EntityLivingBase victim, DamageSource source, EntityLivingBase incumbent,
                                float before, float after) {
        if (source == null || !"player".equals(source.getDamageType())) {
            return;
        }
        Entity actor = source.getTrueSource();
        if (!(actor instanceof EntityPlayerMP) || source.getImmediateSource() != actor) {
            return;
        }

        EntityZombie zombie = (EntityZombie) victim;
        WorldServer world = (WorldServer) zombie.world;
        WorldEncounters state = worlds.get(world);
        UUID mobId = zombie.getUniqueID();
        if (state == null || world.getEntityFromUuid(mobId) != zombie) {
            return;
        }

        UUID playerId = actor.getUniqueID();
        if (world.getEntityFromUuid(playerId) != actor || !ZombieTargets.isAttackable(zombie, (EntityPlayerMP) actor)) {
            return;
        }
        state.recordPlayerHit(mobId, playerId, zombie.ticksExisted, before, after,
                () -> ZombieTargets.canEngage(zombie, incumbent)
                        && world.getEntityFromUuid(incumbent.getUniqueID()) == incumbent
                        ? incumbent.getUniqueID() : null);
    }

    public boolean blocksTargetWrite(EntityLiving mob, EntityLivingBase requestedTarget) {
        if (!supports(mob)) {
            return false;
        }
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        EntityZombie zombie = (EntityZombie) mob;
        EntityLivingBase current = zombie.getAttackTarget();
        return state != null && state.blocksOpeningTargetWrite(zombie.getUniqueID(), zombie.ticksExisted,
                current != requestedTarget, held -> isValidHeldTarget(world, zombie, current, held));
    }

    public Inspection inspect(EntityLivingBase victim) {
        WorldEncounters state = worlds.get(victim.world);
        Encounter encounter = state == null ? null : state.encounters.get(victim.getUniqueID());
        EntityLivingBase actualTarget = ((EntityZombie) victim).getAttackTarget();
        return new Inspection(encounter != null && victim.isEntityAlive(),
                actualTarget == null ? null : actualTarget.getUniqueID(),
                encounter == null || !victim.isEntityAlive() ? Collections.emptyMap() : encounter.copyScores());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        WorldEncounters state = worlds.get(world);
        if (state == null || state.encounters.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Encounter>> entries = state.encounters.entrySet().iterator();
        List<Runnable> targetClears = new ArrayList<>();
        while (entries.hasNext()) {
            Map.Entry<UUID, Encounter> entry = entries.next();
            Entity entity = world.getEntityFromUuid(entry.getKey());
            if (!(entity instanceof EntityZombie) || !entity.isEntityAlive()) {
                entries.remove();
            } else {
                reconcileOpeningTarget(world, (EntityZombie) entity, entry.getValue(), targetClears);
            }
        }
        // The vanilla setter notifies Forge listeners that can change encounter membership.
        targetClears.forEach(Runnable::run);
    }

    private static void reconcileOpeningTarget(WorldServer world, EntityZombie zombie, Encounter encounter,
                                               List<Runnable> targetClears) {
        UUID heldTarget = encounter.getHeldTarget();
        if (heldTarget == null) {
            return;
        }
        if (!encounter.holdsOpeningTarget(zombie.ticksExisted)) {
            encounter.releaseTarget();
            return;
        }
        EntityLivingBase current = zombie.getAttackTarget();
        if (!isValidHeldTarget(world, zombie, current, heldTarget)) {
            encounter.releaseTarget();
            if (current != null && heldTarget.equals(current.getUniqueID())) {
                targetClears.add(() -> {
                    if (zombie.isEntityAlive() && zombie.getAttackTarget() == current
                            && world.getEntityFromUuid(zombie.getUniqueID()) == zombie
                            && !isValidHeldTarget(world, zombie, current, heldTarget)) {
                        zombie.setAttackTarget(null);
                    }
                });
            }
        }
    }

    private static boolean isValidHeldTarget(WorldServer world, EntityZombie zombie, EntityLivingBase current,
                                             UUID heldTarget) {
        return zombie.isEntityAlive() && ZombieTargets.isAttackable(zombie, current)
                && heldTarget.equals(current.getUniqueID()) && world.getEntityFromUuid(heldTarget) == current
                && ZombieTargets.withinPursuitBounds(zombie, current);
    }

    public static final class Inspection {
        public final boolean tracked;
        public final UUID target;
        public final Map<UUID, Double> scores;

        private Inspection(boolean tracked, UUID target, Map<UUID, Double> scores) {
            this.tracked = tracked;
            this.target = target;
            this.scores = scores;
        }
    }
}
