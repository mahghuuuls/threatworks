package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.ArrowLaunch;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ArrowFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");

    @Override
    public String getName() {
        return "twarrow";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twarrow inspect|park <arrow> or /twarrow release <arrow> <target>"
                + " or /twarrow emit <target> <shooter|unowned>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 3 && "emit".equals(args[0])) {
            EntityLivingBase target = getEntity(server, sender, args[1], EntityLivingBase.class);
            EntityLivingBase shooter = "unowned".equals(args[2]) ? null
                    : getEntity(server, sender, args[2], EntityLivingBase.class);
            EntityArrow arrow = shooter == null ? new EntityTippedArrow(target.world)
                    : new EntityTippedArrow(target.world, shooter);
            aimAt(arrow, target);
            // Direct motion bypasses the directional launch hook. Vanilla still applies the hit.
            if (!target.world.spawnEntity(arrow)) {
                throw new CommandException("Fixture arrow was not spawned.");
            }
            report("emit", arrow);
            return;
        }
        if (args.length == 2 && ("inspect".equals(args[0]) || "park".equals(args[0]))) {
            EntityArrow arrow = getEntity(server, sender, args[1], EntityArrow.class);
            if ("park".equals(args[0])) {
                arrow.setNoGravity(true);
                arrow.motionX = 0;
                arrow.motionY = 0;
                arrow.motionZ = 0;
            }
            report(args[0], arrow);
            return;
        }
        if (args.length == 3 && "release".equals(args[0])) {
            EntityArrow arrow = getEntity(server, sender, args[1], EntityArrow.class);
            EntityLivingBase target = getEntity(server, sender, args[2], EntityLivingBase.class);
            aimAt(arrow, target);
            report("release", arrow);
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    private static void aimAt(EntityArrow arrow, EntityLivingBase target) {
        arrow.setPosition(target.posX + 4, target.posY + target.height / 2, target.posZ);
        arrow.setNoGravity(true);
        arrow.setIsCritical(false);
        arrow.setDamage(1);
        arrow.motionX = -1;
        arrow.motionY = 0;
        arrow.motionZ = 0;
    }

    private static void report(String action, EntityArrow arrow) {
        boolean snapshot = ((ArrowLaunch) arrow).threatworks$getLaunch() != null;
        LOGGER.info("arrow_{} arrow={} entityId={} shooter={} x={} y={} z={} vx={} vy={} vz={} worldTick={} snapshotPresent={}",
                action, arrow.getUniqueID(), arrow.getEntityId(), identity(arrow.shootingEntity),
                arrow.posX, arrow.posY, arrow.posZ, arrow.motionX, arrow.motionY, arrow.motionZ,
                arrow.world.getTotalWorldTime(), snapshot);
    }

    static String identity(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }
}
