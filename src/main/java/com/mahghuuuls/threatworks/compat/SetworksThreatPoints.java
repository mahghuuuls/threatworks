package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.setworks.api.CombinationRule;
import com.mahghuuuls.setworks.api.IEffectKind;
import com.mahghuuuls.setworks.api.RegistrationResult;
import com.mahghuuuls.setworks.api.SetworksApi;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

/** Holds transient values for the exact player instance that received each parent callback. */
final class SetworksThreatPoints implements IEffectKind, ThreatPoints.SetBonusSource {
    private static final String ID = "threatworks:threat_points";
    private final Map<EntityLivingBase, Map<String, Double>> applied = new IdentityHashMap<>();

    static SetworksThreatPoints register() {
        SetworksThreatPoints source = new SetworksThreatPoints();
        RegistrationResult result = SetworksApi.registerEffectKind(source);
        if (!result.isOk()) {
            throw new IllegalStateException(result.message());
        }
        return source;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public CombinationRule combinationRule() {
        return CombinationRule.SUM;
    }

    @Override
    public String validate(Map<String, String> params) {
        return ServerTuning.validItemPoints(amount(params)) ? null : "Expected one finite signed amount";
    }

    @Override
    public String describe(Map<String, String> params) {
        return "Threat points " + params.get("amount");
    }

    @Override
    public void apply(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        applyTo(player, instanceId, params);
    }

    void applyTo(EntityLivingBase player, String instanceId, Map<String, String> params) {
        double value = amount(params);
        if (ServerTuning.validItemPoints(value)) {
            applied.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).put(instanceId, value);
        }
    }

    @Override
    public void withdraw(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        withdrawFrom(player, instanceId);
    }

    void withdrawFrom(EntityLivingBase player, String instanceId) {
        Map<String, Double> entries = applied.get(player);
        if (entries != null) {
            entries.remove(instanceId);
            if (entries.isEmpty()) {
                applied.remove(player);
            }
        }
    }

    @Override
    public void collect(EntityLivingBase wearer, DoubleConsumer points) {
        Map<String, Double> entries = applied.get(wearer);
        if (entries != null) {
            entries.values().forEach(points::accept);
        }
    }

    @Override
    public void discard(EntityLivingBase player) {
        applied.remove(player);
    }

    @Override
    public void clear() {
        applied.clear();
    }

    private static double amount(Map<String, String> params) {
        String text = params.get("amount");
        if (params.size() != 1 || text == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }
}
