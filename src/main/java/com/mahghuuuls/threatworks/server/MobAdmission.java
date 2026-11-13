package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Map;

enum MobAdmission {
    ZOMBIE(MobType.ZOMBIE),
    SKELETON(MobType.SKELETON),
    SPECIAL_ZOMBIE(MobType.SPECIAL_ZOMBIE),
    SPECIAL_SKELETON(MobType.SPECIAL_SKELETON),
    FOLIAATH(MobType.FOLIAATH),
    NATIVE_PHASE(MobType.FOLIAATH),
    BLACKLISTED(null),
    DISABLED(null),
    UNSUPPORTED_TYPE(null),
    UNSUPPORTED_STATE(null);

    private static final Map<ResourceLocation, MobAdmission> BINDINGS = new HashMap<>();

    static {
        for (MobAdmission binding : values()) {
            if (binding.type != null && binding != NATIVE_PHASE) {
                BINDINGS.put(new ResourceLocation(binding.type.id), binding);
            }
        }
    }

    final MobType type;

    MobAdmission(MobType type) {
        this.type = type;
    }

    boolean controls() {
        return type != null && this != NATIVE_PHASE;
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
                    return NATIVE_PHASE;
                default:
                    return UNSUPPORTED_STATE;
            }
        }
        return MobTargets.matchesBehavior(mob, binding.type) ? binding : UNSUPPORTED_STATE;
    }

    private boolean matches(EntityCreature mob, FoliaathCompatibility foliaath) {
        switch (this) {
            case ZOMBIE:
                return mob.getClass() == EntityZombie.class;
            case SKELETON:
                return mob.getClass() == EntitySkeleton.class;
            case FOLIAATH:
                return foliaath != null && foliaath.matches(mob);
            default:
                return SpecialMobsCompatibility.matches(type, mob);
        }
    }
}
