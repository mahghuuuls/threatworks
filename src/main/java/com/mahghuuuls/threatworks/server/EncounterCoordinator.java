package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
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
import java.util.Objects;
import java.util.UUID;

public final class EncounterCoordinator {
    public static final EncounterCoordinator INSTANCE = new EncounterCoordinator();

    private final Map<WorldServer, WorldEncounters> worlds = new IdentityHashMap<>();
    private TargetWrite targetWrite;

    private EncounterCoordinator() {
    }

    public static boolean supports(EntityLivingBase mob) {
        return MobTargets.supports(mob);
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

        EntityCreature mob = (EntityCreature) victim;
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        UUID mobId = mob.getUniqueID();
        if (state == null || world.getEntityFromUuid(mobId) != mob) {
            return;
        }

        UUID playerId = actor.getUniqueID();
        if (world.getEntityFromUuid(playerId) != actor || !MobTargets.isAttackable(mob, (EntityPlayerMP) actor)) {
            return;
        }
        state.recordPlayerHit(mobId, playerId, mob.ticksExisted, before, after,
                () -> incumbent != null && world.getEntityFromUuid(incumbent.getUniqueID()) == incumbent
                        && MobTargets.observe(world, mob, incumbent).isEligible()
                        ? incumbent.getUniqueID() : null);
    }

    public boolean blocksTargetWrite(EntityLiving mob, EntityLivingBase requestedTarget) {
        if (!(mob instanceof EntityCreature) || !(mob.world instanceof WorldServer)) {
            return false;
        }
        TargetWrite write = targetWrite;
        if (write != null && write.mob == mob) {
            return !write.consume(requestedTarget);
        }
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        return state != null && mob.isEntityAlive() && state.blocksTargetWrite(mob.getUniqueID(),
                mob.getAttackTarget() != requestedTarget,
                target -> MobTargets.resolve(world, (EntityCreature) mob, world.getEntityFromUuid(target)) != null);
    }

    public Inspection inspect(EntityLivingBase victim) {
        WorldEncounters state = worlds.get(victim.world);
        Encounter encounter = state == null ? null : state.encounters.get(victim.getUniqueID());
        EntityLivingBase actualTarget = ((EntityLiving) victim).getAttackTarget();
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
        List<Runnable> targetUpdates = new ArrayList<>();
        while (entries.hasNext()) {
            Map.Entry<UUID, Encounter> entry = entries.next();
            Entity entity = world.getEntityFromUuid(entry.getKey());
            if (!(entity instanceof EntityCreature) || !entity.isEntityAlive()) {
                entries.remove();
            } else {
                reconcileTarget(world, state, (EntityCreature) entity, entry.getValue(), targetUpdates);
            }
        }
        // The vanilla setter notifies Forge listeners that can change encounter membership.
        targetUpdates.forEach(Runnable::run);
    }

    private void reconcileTarget(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter encounter,
                                 List<Runnable> updates) {
        UUID previous = encounter.getTarget();
        EntityLivingBase previousTarget = previous == null ? null
                : MobTargets.resolve(world, mob, world.getEntityFromUuid(previous));
        boolean lostTarget = previous != null && previousTarget == null;
        if (!lostTarget && (encounter.holdsOpeningTarget(mob.ticksExisted)
                || !encounter.shouldReevaluate(mob.ticksExisted, mob.getEntityId()))) {
            return;
        }
        UUID selected = encounter.selectTarget(mob.ticksExisted,
                id -> MobTargets.observe(world, mob, world.getEntityFromUuid(id)));
        EntityLivingBase current = mob.getAttackTarget();
        if (selected == null && (current == null || !current.getUniqueID().equals(previous))) {
            return;
        }
        EntityLivingBase target = selected == null ? null
                : MobTargets.resolve(world, mob, world.getEntityFromUuid(selected));
        if (target != current && (target != null || selected == null)) {
            updates.add(() -> applyTarget(world, state, mob, encounter, selected, target, current));
        }
    }

    private void applyTarget(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter encounter,
                             UUID selected, EntityLivingBase target, EntityLivingBase previous) {
        UUID mobId = mob.getUniqueID();
        if (worlds.get(world) != state || state.encounters.get(mobId) != encounter || !mob.isEntityAlive()
                || world.getEntityFromUuid(mobId) != mob || !Objects.equals(encounter.getTarget(), selected)
                || mob.getAttackTarget() == target) {
            return;
        }
        if (target == null ? mob.getAttackTarget() != previous
                : world.getEntityFromUuid(selected) != target || MobTargets.resolve(world, mob, target) == null) {
            return;
        }
        TargetWrite saved = targetWrite;
        targetWrite = new TargetWrite(mob, target);
        try {
            mob.setAttackTarget(target);
        } finally {
            targetWrite = saved;
        }
    }

    static final class TargetWrite {
        private final EntityLiving mob;
        private final EntityLivingBase target;
        private boolean consumed;

        TargetWrite(EntityLiving mob, EntityLivingBase target) {
            this.mob = mob;
            this.target = target;
        }

        boolean consume(EntityLivingBase requested) {
            if (consumed || requested != target) {
                return false;
            }
            consumed = true;
            return true;
        }
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
