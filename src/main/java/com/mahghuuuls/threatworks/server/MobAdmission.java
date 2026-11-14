package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.AncientWarfareCompatibility;
import com.mahghuuuls.threatworks.compat.ChocolateQuestCompatibility;
import com.mahghuuuls.threatworks.compat.DefiledLandsCompatibility;
import com.mahghuuuls.threatworks.compat.DungeonMobsCompatibility;
import com.mahghuuuls.threatworks.compat.FishUndeadRisingCompatibility;
import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.compat.GaiaCompatibility;
import com.mahghuuuls.threatworks.compat.GroundMeleeCompatibility;
import com.mahghuuuls.threatworks.compat.IceAndFireCompatibility;
import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.compat.UnseenDungeonCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;

final class MobAdmission {
    private enum State {
        CONTROL, NATIVE_PHASE, STRAFE_CHOICE, BLACKLISTED, DISABLED, UNSUPPORTED_TYPE, UNSUPPORTED_STATE
    }

    private static final MobAdmission BLACKLISTED = new MobAdmission(null, State.BLACKLISTED);
    private static final MobAdmission DISABLED = new MobAdmission(null, State.DISABLED);
    private static final MobAdmission UNSUPPORTED_TYPE = new MobAdmission(null, State.UNSUPPORTED_TYPE);
    private static final MobAdmission UNSUPPORTED_STATE = new MobAdmission(null, State.UNSUPPORTED_STATE);
    private static final MobAdmission STRAFE_CHOICE = new MobAdmission(MobType.ENDER_DRAGON, State.STRAFE_CHOICE);
    private static final Map<ResourceLocation, MobType> TYPES = new HashMap<>();
    private static final Map<MobType, Class<? extends EntityCreature>> VANILLA_CLASSES = new EnumMap<>(MobType.class);
    private static final Map<MobType, MobAdmission> CONTROL = new EnumMap<>(MobType.class);
    private static final Map<MobType, MobAdmission> NATIVE = new EnumMap<>(MobType.class);

    static {
        VANILLA_CLASSES.put(MobType.ZOMBIE, EntityZombie.class);
        VANILLA_CLASSES.put(MobType.HUSK, EntityHusk.class);
        VANILLA_CLASSES.put(MobType.ZOMBIE_VILLAGER, EntityZombieVillager.class);
        VANILLA_CLASSES.put(MobType.SKELETON, EntitySkeleton.class);
        VANILLA_CLASSES.put(MobType.STRAY, EntityStray.class);
        VANILLA_CLASSES.put(MobType.WITHER_SKELETON, EntityWitherSkeleton.class);
        VANILLA_CLASSES.put(MobType.SPIDER, EntitySpider.class);
        VANILLA_CLASSES.put(MobType.CAVE_SPIDER, EntityCaveSpider.class);
        VANILLA_CLASSES.put(MobType.CREEPER, EntityCreeper.class);
        VANILLA_CLASSES.put(MobType.SILVERFISH, EntitySilverfish.class);
        VANILLA_CLASSES.put(MobType.ENDERMITE, EntityEndermite.class);
        VANILLA_CLASSES.put(MobType.VINDICATOR, EntityVindicator.class);
        VANILLA_CLASSES.put(MobType.BLAZE, EntityBlaze.class);
        VANILLA_CLASSES.put(MobType.SHULKER, EntityShulker.class);
        VANILLA_CLASSES.put(MobType.WITCH, EntityWitch.class);
        VANILLA_CLASSES.put(MobType.WITHER, EntityWither.class);
        for (MobType type : MobType.values()) {
            TYPES.put(new ResourceLocation(type.id), type);
            CONTROL.put(type, new MobAdmission(type, State.CONTROL));
            NATIVE.put(type, new MobAdmission(type, State.NATIVE_PHASE));
        }
    }

    final MobType type;
    private final State state;

    private MobAdmission(MobType type, State state) {
        this.type = type;
        this.state = state;
    }

    boolean controls() {
        return state == State.CONTROL;
    }

    boolean yieldsToNative() {
        return state == State.NATIVE_PHASE;
    }

    boolean usesStrafeChoice() {
        return state == State.STRAFE_CHOICE;
    }

    String status() {
        return state.name().toLowerCase(java.util.Locale.ROOT);
    }

    static MobAdmission resolve(ServerTuning tuning, FoliaathCompatibility foliaath, EntityLivingBase entity) {
        if (entity == null) {
            return UNSUPPORTED_TYPE;
        }
        return resolve(tuning, foliaath, entity, EntityList.getKey(entity));
    }

    static MobAdmission resolve(ServerTuning tuning, FoliaathCompatibility foliaath, EntityLivingBase entity,
                                ResourceLocation id) {
        if (id != null && tuning != null && tuning.isBlacklisted(id.toString())) {
            return BLACKLISTED;
        }
        MobType type = TYPES.get(id);
        if (type == MobType.ENDER_DRAGON) {
            if (entity == null || entity.getClass() != EntityDragon.class) {
                return UNSUPPORTED_TYPE;
            }
            if (tuning == null || !tuning.isEnabled(type)) {
                return DISABLED;
            }
            return DragonTargeting.isActive((EntityDragon) entity) ? STRAFE_CHOICE : UNSUPPORTED_STATE;
        }
        if (FishUndeadRisingCompatibility.usesFlyingCharge(type)) {
            if (!(entity instanceof EntityCreature)
                    || !FishUndeadRisingCompatibility.matchesFlyingCharge(type, (EntityCreature) entity)) {
                return UNSUPPORTED_TYPE;
            }
            if (tuning == null || !tuning.isEnabled(type)) {
                return DISABLED;
            }
            EntityCreature mob = (EntityCreature) entity;
            if (!(mob.world instanceof WorldServer) || !FishFlyingMobPolicy.hasNativeContract(type, mob)) {
                return UNSUPPORTED_STATE;
            }
            return FishFlyingMobPolicy.isNativePhase(mob) ? NATIVE.get(type) : CONTROL.get(type);
        }
        boolean ghast = GhastMobPolicy.matchesType(type);
        if (entity instanceof EntityLiving && (ghast || SlimeMobPolicy.matchesType(type))) {
            EntityLiving mob = (EntityLiving) entity;
            if (!(ghast ? GhastMobPolicy.matches(type, mob) : SlimeMobPolicy.matches(type, mob))) {
                return UNSUPPORTED_TYPE;
            }
            if (tuning == null || !tuning.isEnabled(type)) {
                return DISABLED;
            }
            if (!(mob.world instanceof WorldServer)
                    || !(ghast ? GhastMobPolicy.hasNativeContract(type, mob) : SlimeMobPolicy.hasNativeContract(mob))) {
                return UNSUPPORTED_STATE;
            }
            boolean nativePhase = ghast ? GhastMobPolicy.isNativePhase(mob) : SlimeMobPolicy.isNativePhase(mob);
            return nativePhase ? NATIVE.get(type) : CONTROL.get(type);
        }
        if (type == null || !(entity instanceof EntityCreature) || !matches(type, (EntityCreature) entity, foliaath)) {
            return UNSUPPORTED_TYPE;
        }
        if (tuning == null || !tuning.isEnabled(type)) {
            return DISABLED;
        }
        EntityCreature mob = (EntityCreature) entity;
        if (!(mob.world instanceof WorldServer)) {
            return UNSUPPORTED_STATE;
        }
        if (type == MobType.WITHER) {
            EntityWither wither = (EntityWither) mob;
            if (!WitherMobPolicy.hasNativeContract(wither)) {
                return UNSUPPORTED_STATE;
            }
            return WitherMobPolicy.isNativePhase(wither) ? NATIVE.get(type) : CONTROL.get(type);
        }
        if (type == MobType.FOLIAATH) {
            if (!foliaath.matchesGoals(mob)) {
                return UNSUPPORTED_STATE;
            }
            switch (foliaath.phase(mob)) {
                case CONTROL:
                    return CONTROL.get(type);
                case NATIVE:
                    return NATIVE.get(type);
                default:
                    return UNSUPPORTED_STATE;
            }
        }
        if (!MobTargets.matchesBehavior(mob, type)) {
            return UNSUPPORTED_STATE;
        }
        return MobTargets.inNativePhase(mob, type) ? NATIVE.get(type) : CONTROL.get(type);
    }

    private static boolean matches(MobType type, EntityCreature mob, FoliaathCompatibility foliaath) {
        Class<? extends EntityCreature> entityClass = VANILLA_CLASSES.get(type);
        if (entityClass != null) {
            return mob.getClass() == entityClass;
        }
        if (mob instanceof EntityWitch) {
            return SpecialMobsCompatibility.matchesWitch(type, (EntityWitch) mob);
        }
        return type == MobType.FOLIAATH ? foliaath != null && foliaath.matches(mob)
                : SpecialMobsCompatibility.matches(type, mob) || DefiledLandsCompatibility.matches(type, mob)
                || GaiaCompatibility.matches(type, mob) || DungeonMobsCompatibility.matches(type, mob)
                || IceAndFireCompatibility.matches(type, mob) || FishUndeadRisingCompatibility.matches(type, mob)
                || GroundMeleeCompatibility.matches(type, mob) || UnseenDungeonCompatibility.matches(type, mob)
                || ChocolateQuestCompatibility.matches(type, mob) || AncientWarfareCompatibility.matches(type, mob);
    }
}
