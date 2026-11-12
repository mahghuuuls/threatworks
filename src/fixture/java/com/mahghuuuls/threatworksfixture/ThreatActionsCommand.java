package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

final class ThreatActionsCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");

    @Override
    public String getName() {
        return "twfixture";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twfixture <grant|taunt|offthread-grant|offthread-taunt> <mob> <participant> [amount]"
                + " or /twfixture rewind-age <mob> <ticks>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        String action = args[0];
        if ("rewind-age".equals(action) && args.length == 3) {
            EntityLivingBase mob = getEntity(server, sender, args[1], EntityLivingBase.class);
            int before = mob.ticksExisted;
            mob.ticksExisted = Math.max(0, before - parseInt(args[2], 1, 100));
            LOGGER.info("mob_clock mob={} before={} after={} serverTick={} worldTick={}", mob.getUniqueID(),
                    before, mob.ticksExisted, server.getTickCounter(), mob.world.getTotalWorldTime());
            return;
        }
        boolean offThread = action.startsWith("offthread-");
        String operation = offThread ? action.substring("offthread-".length()) : action;
        boolean grant = "grant".equals(operation);
        if ((grant && args.length != 4) || (!grant && (!"taunt".equals(operation) || args.length != 3))) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityLivingBase mob = getEntity(server, sender, args[1], EntityLivingBase.class);
        EntityLivingBase participant = getEntity(server, sender, args[2], EntityLivingBase.class);
        double amount = grant ? rawAmount(args[3]) : 0.0;
        ThreatActions api = ThreatworksMod.threatActions();
        Supplier<ThreatActions.Result> invoke = grant ? () -> api.grant(mob, participant, amount)
                : () -> api.taunt(mob, participant);
        String call = "action=" + action + " mob=" + mob.getUniqueID() + " participant=" + participant.getUniqueID()
                + (grant ? " amount=" + amount : "") + " requestTick=" + mob.world.getTotalWorldTime();
        if (offThread) {
            new Thread(() -> report(call, invoke.get()), "Threatworks fixture API probe").start();
        } else {
            ThreatActions.Result result = invoke.get();
            report(call, result);
            sender.sendMessage(new TextComponentString("Threatworks fixture " + result));
        }
    }

    private static double rawAmount(String text) throws CommandException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException invalid) {
            throw new CommandException("Expected a numeric amount.");
        }
    }

    private static void report(String call, ThreatActions.Result result) {
        LOGGER.info("api {} status={} result={}", call, result.status(), result);
    }
}
