package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatQueries;
import net.minecraft.entity.EntityLivingBase;

/** This consumer uses only the public mod entry point and API types. */
final class ParticipantQueryConsumer {
    private final ThreatQueries queries = ThreatworksMod.threatQueries();

    ThreatQueries.View read(EntityLivingBase mob, EntityLivingBase participant) {
        return queries.query(mob, participant);
    }
}
