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

import java.util.Map;
import java.util.UUID;

public final class ThreatworksCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("Threatworks");
    private static final int MAX_SHOWN_SCORES = 16;
    private static final String SELECT_ONE_MOB = "Select exactly one loaded zombie or skeleton, or use its UUID.";

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
        return "/threatworks inspect <single mob selector or UUID>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2 || !"inspect".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityLivingBase mob;
        try {
            mob = getEntity(server, sender, args[1], EntityLivingBase.class);
        } catch (EntityNotFoundException invalidSelection) {
            throw new CommandException(SELECT_ONE_MOB);
        }
        if (!EncounterCoordinator.supports(mob)) {
            throw new CommandException(SELECT_ONE_MOB);
        }
        EncounterCoordinator.Inspection inspection = EncounterCoordinator.INSTANCE.inspect(mob);
        report(sender, "mob=" + mob.getUniqueID()
                + " tracked=" + inspection.tracked + " target="
                + (inspection.target == null ? "none" : inspection.target) + " taunt=none");
        int shown = 0;
        for (Map.Entry<UUID, Double> entry : inspection.scores.entrySet()) {
            if (shown == MAX_SHOWN_SCORES) {
                report(sender, "omitted=" + (inspection.scores.size() - shown));
                break;
            }
            report(sender, "participant=" + entry.getKey() + " threat=" + entry.getValue());
            shown++;
        }
    }

    private static void report(ICommandSender sender, String line) {
        sender.sendMessage(new TextComponentString("Threatworks " + line));
        LOGGER.info("inspect {}", line);
    }
}
