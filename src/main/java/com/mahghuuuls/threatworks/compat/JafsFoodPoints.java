package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.jafs.api.IFoodService;
import com.mahghuuuls.jafs.api.JafsApi;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.api.PropertyValue;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

final class JafsFoodPoints implements ToDoubleFunction<EntityLivingBase> {
    private static final String PROPERTY = "threatworks:threat_points";
    // JAFS sums an int-sized list of modifiers in double precision before returning it.
    private static final double MAX_MODIFIER = Double.MAX_VALUE / (1L << 32);
    private final IFoodService food;
    private final Logger logger;
    private boolean warned;

    JafsFoodPoints(IFoodService food, Logger logger) {
        this.food = Objects.requireNonNull(food);
        this.logger = logger;
    }

    static JafsFoodPoints register(Logger logger) throws ReflectiveOperationException {
        // Read the installed API version rather than the compile-time constant.
        Object version = JafsApi.class.getField("API_VERSION").get(null);
        if (!"1.0".equals(version) || !JafsApi.isBound() || JafsApi.isRegistrationFrozen()) {
            throw new IllegalStateException("Expected a bound, unfrozen JAFS API 1.0, found " + version);
        }
        JafsFoodPoints source = new JafsFoodPoints(JafsApi.getFoodService(), logger);
        JafsApi.registerProperty(property(), (player, flat, percent) -> {
            // The combat path reads the authoritative aggregate on demand.
        });
        return source;
    }

    static PropertySpec property() {
        return PropertySpec.builder(PROPERTY).operation(PropertyOperation.FLAT, -MAX_MODIFIER, MAX_MODIFIER).build();
    }

    @Override
    public double applyAsDouble(EntityLivingBase wearer) {
        if (!(wearer instanceof EntityPlayerMP)) {
            return 0;
        }
        try {
            PropertyValue current = food.getPropertyValue((EntityPlayerMP) wearer, PROPERTY);
            double flat = current.flat();
            if (Double.isFinite(flat) && current.percent() == 0) {
                return flat;
            }
            warnOnce("non-finite flat total or unsupported percent total", null);
        } catch (RuntimeException | LinkageError failure) {
            warnOnce("could not read the active food property", failure);
        }
        return 0;
    }

    private void warnOnce(String reason, Throwable failure) {
        if (!warned) {
            warned = true;
            logger.warn("Ignoring JAFS threat points: " + reason, failure);
        }
    }
}
