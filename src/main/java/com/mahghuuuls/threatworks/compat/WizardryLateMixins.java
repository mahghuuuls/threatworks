package com.mahghuuuls.threatworks.compat;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Selects each optional provider hook only when its exact call site is present. */
public final class WizardryLateMixins implements ILateMixinLoader, IMixinConfigPlugin {
    public static final String SLIME = "electroblob.wizardry.entity.living.EntityMagicSlime";
    public static final String SNOW_GOLEM = "electroblob.wizardry.spell.SummonSnowGolem";
    public static final String CAST = "cast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
            + "Lnet/minecraft/util/EnumHand;ILelectroblob/wizardry/util/SpellModifiers;)Z";
    public static final String ATTACK = "(Lnet/minecraft/util/DamageSource;F)Z";
    public static final String SPAWN = "(Lnet/minecraft/entity/Entity;)Z";
    private static final String SLIME_CONFIG = "mixins.threatworks.wizardry-slime.json";
    private static final String SNOW_GOLEM_CONFIG = "mixins.threatworks.wizardry-snow-golem.json";
    private static final Logger LOGGER = LogManager.getLogger("Threatworks");

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(SLIME_CONFIG, SNOW_GOLEM_CONFIG);
    }

    @Override
    public boolean shouldMixinConfigQueue(String config) {
        ModContainer mod = Loader.instance().getIndexedModList().get("ebwizardry");
        if (mod == null || !WizardryCompatibility.supportedVersion(mod.getVersion())) {
            return false;
        }
        boolean slime = SLIME_CONFIG.equals(config);
        String provider = slime ? SLIME : SNOW_GOLEM;
        try {
            ClassNode type = MixinService.getService().getBytecodeProvider().getClassNode(provider, false);
            int matches = matchingCalls(type, slime);
            if (matches == 1) {
                return true;
            }
            LOGGER.warn("Skipping optional Wizardry hook {}: expected one native call, found {}", provider, matches);
        } catch (ClassNotFoundException | IOException | LinkageError | RuntimeException failure) {
            LOGGER.warn("Skipping optional Wizardry hook {}: cannot inspect provider bytecode", provider, failure);
        }
        return false;
    }

    static int matchingCalls(ClassNode type, boolean slime) {
        int matches = 0;
        for (MethodNode method : type.methods) {
            boolean providerMethod = slime
                    ? "()V".equals(method.desc) && named(method.name, "onUpdate", "func_70071_h_")
                    : CAST.equals(method.name + method.desc);
            if (providerMethod) {
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction instanceof MethodInsnNode && matchesCall((MethodInsnNode) instruction, slime)) {
                        matches++;
                    }
                }
            }
        }
        return matches;
    }

    private static boolean matchesCall(MethodInsnNode call, boolean slime) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && (slime
                ? "net/minecraft/entity/Entity".equals(call.owner) && ATTACK.equals(call.desc)
                    && named(call.name, "attackEntityFrom", "func_70097_a")
                : "net/minecraft/world/World".equals(call.owner) && SPAWN.equals(call.desc)
                    && named(call.name, "spawnEntity", "func_72838_d"));
    }

    private static boolean named(String name, String mcp, String srg) {
        return name.equals(mcp) || name.equals(srg);
    }

    @Override
    public void postApply(String target, ClassNode type, String mixin, IMixinInfo info) {
        LOGGER.info("Applied Wizardry summon observation to {}", target);
    }
}
