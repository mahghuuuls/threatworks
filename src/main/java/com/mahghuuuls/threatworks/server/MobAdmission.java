package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
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
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Map;

enum MobAdmission {
    ZOMBIE(MobType.ZOMBIE, EntityZombie.class),
    HUSK(MobType.HUSK, EntityHusk.class),
    ZOMBIE_VILLAGER(MobType.ZOMBIE_VILLAGER, EntityZombieVillager.class),
    SKELETON(MobType.SKELETON, EntitySkeleton.class),
    STRAY(MobType.STRAY, EntityStray.class),
    WITHER_SKELETON(MobType.WITHER_SKELETON, EntityWitherSkeleton.class),
    SPIDER(MobType.SPIDER, EntitySpider.class),
    CAVE_SPIDER(MobType.CAVE_SPIDER, EntityCaveSpider.class),
    SILVERFISH(MobType.SILVERFISH, EntitySilverfish.class),
    ENDERMITE(MobType.ENDERMITE, EntityEndermite.class),
    VINDICATOR(MobType.VINDICATOR, EntityVindicator.class),
    BLAZE(MobType.BLAZE, EntityBlaze.class),
    SHULKER(MobType.SHULKER, EntityShulker.class),
    SPECIAL_ZOMBIE(MobType.SPECIAL_ZOMBIE),
    SPECIAL_SKELETON(MobType.SPECIAL_SKELETON),
    FOLIAATH(MobType.FOLIAATH),
    FOLIAATH_NATIVE(MobType.FOLIAATH, true),
    SPIDER_NATIVE(MobType.SPIDER, true),
    CAVE_SPIDER_NATIVE(MobType.CAVE_SPIDER, true),
    SHULKER_NATIVE(MobType.SHULKER, true),
    BLACKLISTED(null),
    DISABLED(null),
    UNSUPPORTED_TYPE(null),
    UNSUPPORTED_STATE(null);

    private static final Map<ResourceLocation, MobAdmission> BINDINGS = new HashMap<>();

    static {
        for (MobAdmission binding : values()) {
            if (binding.controls()) {
                BINDINGS.put(new ResourceLocation(binding.type.id), binding);
            }
        }
    }

    final MobType type;
    private final Class<? extends EntityCreature> entityClass;
    private final boolean nativePhase;

    MobAdmission(MobType type) {
        this(type, null);
    }

    MobAdmission(MobType type, Class<? extends EntityCreature> entityClass) {
        this(type, entityClass, false);
    }

    MobAdmission(MobType type, boolean nativePhase) {
        this(type, null, nativePhase);
    }

    MobAdmission(MobType type, Class<? extends EntityCreature> entityClass, boolean nativePhase) {
        this.type = type;
        this.entityClass = entityClass;
        this.nativePhase = nativePhase;
    }

    boolean controls() {
        return type != null && !nativePhase;
    }

    boolean yieldsToNative() {
        return nativePhase;
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
        MobAdmission binding = BINDINGS.get(id);
        if (binding == null || !(entity instanceof EntityCreature) || !binding.matches((EntityCreature) entity, foliaath)) {
            return UNSUPPORTED_TYPE;
        }
        if (tuning == null || !tuning.isEnabled(binding.type)) {
            return DISABLED;
        }
        EntityCreature mob = (EntityCreature) entity;
        if (!(mob.world instanceof WorldServer)) {
            return UNSUPPORTED_STATE;
        }
        if (binding == FOLIAATH) {
            if (!foliaath.matchesGoals(mob)) {
                return UNSUPPORTED_STATE;
            }
            switch (foliaath.phase(mob)) {
                case CONTROL:
                    return FOLIAATH;
                case NATIVE:
                    return FOLIAATH_NATIVE;
                default:
                    return UNSUPPORTED_STATE;
            }
        }
        if (!MobTargets.matchesBehavior(mob, binding.type)) {
            return UNSUPPORTED_STATE;
        }
        if (MobTargets.inNativePhase(mob)) {
            switch (binding) {
                case SPIDER:
                    return SPIDER_NATIVE;
                case CAVE_SPIDER:
                    return CAVE_SPIDER_NATIVE;
                case SHULKER:
                    return SHULKER_NATIVE;
                default:
                    throw new IllegalStateException("Missing native phase binding for " + binding);
            }
        }
        return binding;
    }

    private boolean matches(EntityCreature mob, FoliaathCompatibility foliaath) {
        if (entityClass != null) {
            return mob.getClass() == entityClass;
        }
        return this == FOLIAATH ? foliaath != null && foliaath.matches(mob)
                : SpecialMobsCompatibility.matches(type, mob);
    }
}
