package com.mahghuuuls.threatworks.command;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntityNotFoundException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ThreatworksCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("Threatworks");
    private static final int MAX_SHOWN_SCORES = 16;
    private static final String SELECT_ONE_MOB = "Select exactly one loaded mob, or use its UUID.";

    @Override
    public String getName() {
        return "threatworks";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/threatworks inspect <single mob selector or UUID> | work <ticks|result>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && "work".equals(args[0])) {
            if ("result".equals(args[1])) {
                String result = EncounterCoordinator.INSTANCE.takeWorkSample(sender.getEntityWorld());
                if (result == null) {
                    throw new CommandException("No completed Threatworks sample in this world.");
                }
                report(sender, result);
            } else if (!EncounterCoordinator.INSTANCE.beginWorkSample(sender.getEntityWorld(),
                    parseInt(args[1], 1, 1000))) {
                throw new CommandException("A sample already exists in this world, or the world is unavailable.");
            }
            return;
        }
        if (args.length != 2 || !"inspect".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityLivingBase mob;
        try {
            mob = getEntity(server, sender, args[1], EntityLivingBase.class);
        } catch (EntityNotFoundException invalidSelection) {
            throw new CommandException(SELECT_ONE_MOB);
        }
        EncounterCoordinator.Inspection inspection = EncounterCoordinator.INSTANCE.inspect(mob, MAX_SHOWN_SCORES);
        report(sender, "mob=" + mob.getUniqueID()
                + " type=" + inspection.typeId + " status=" + inspection.status
                + " tracked=" + inspection.tracked + " target="
                + (inspection.target == null ? "none" : inspection.target)
                + " taunt=" + (inspection.taunter == null ? "none" : inspection.taunter)
                + " tauntTicks=" + inspection.tauntTicks
                + " resetTicks=" + (inspection.resetTicks == null ? "none" : inspection.resetTicks));
        for (EncounterCoordinator.Participant participant : inspection.participants) {
            report(sender, "participant=" + participant.id + " threat=" + participant.score
                    + " eligibility=" + participant.eligibility);
        }
        if (inspection.omitted > 0) {
            report(sender, "omitted=" + inspection.omitted);
        }
    }

    private static void report(ICommandSender sender, String line) {
        sender.sendMessage(new TextComponentString("Threatworks " + line));
        LOGGER.info("inspect {}", line);
    }
}
