package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ThreatQueryCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final ParticipantQueryConsumer CONSUMER = new ParticipantQueryConsumer();

    @Override
    public String getName() { return "twquery"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getUsage(ICommandSender source) {
        return "/twquery <read|poll|offthread|watch-logout> <mob> <participant> [expected-state], or /twquery smoke <caster>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && "smoke".equals(args[0])) {
            ParticipantQueryFixture.smoke(getPlayer(server, sender, args[1]));
            return;
        }
        if (args.length == 4 && "match".equals(args[0])) {
            EntityLiving polled = getEntity(server, sender, args[1], EntityLiving.class);
            EntityLiving control = getEntity(server, sender, args[2], EntityLiving.class);
            EntityLivingBase participant = getEntity(server, sender, args[3], EntityLivingBase.class);
            View read = CONSUMER.read(polled, participant);
            View baseline = CONSUMER.read(control, participant);
            if (read.state() != baseline.state() || !read.threat().equals(baseline.threat())
                    || read.aggro() != baseline.aggro() || read.tauntActive() != baseline.tauntActive()
                    || !read.tauntTicks().equals(baseline.tauntTicks()) || polled.getAttackTarget() != control.getAttackTarget()) {
                throw new CommandException("Polled and control encounters differed in threat, timer or native focus");
            }
            LOGGER.info("h1_match state={} threat={} taunt={} ticks={} nativeTarget={}", read.state(), read.threat(),
                    read.tauntActive(), read.tauntTicks(), ArrowFixtureCommand.identity(polled.getAttackTarget()));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityLivingBase mob = getEntity(server, sender, args[1], EntityLivingBase.class);
        EntityLivingBase participant = getEntity(server, sender, args[2], EntityLivingBase.class);
        View view;
        switch (args[0]) {
            case "offthread":
                view = offThread(mob, participant);
                break;
            case "poll":
                for (int reads = 0; reads < 127; reads++) CONSUMER.read(mob, participant);
                view = CONSUMER.read(mob, participant);
                break;
            case "read":
                view = CONSUMER.read(mob, participant);
                break;
            case "watch-logout":
                if (!server.isDedicatedServer() || !(participant instanceof EntityPlayerMP)) {
                    throw new CommandException("Use this probe on a disposable dedicated server with a player");
                }
                ParticipantLifecycleProbe.arm(mob, (EntityPlayerMP) participant);
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        report(args[0], mob, participant, view);
        if (args.length == 4 && !view.state().name().equals(args[3])) {
            throw new CommandException("Query returned %s instead of %s", view.state(), args[3]);
        }
    }

    static View offThread(EntityLivingBase mob, EntityLivingBase participant) throws CommandException {
        FutureTask<View> query = new FutureTask<>(() -> CONSUMER.read(mob, participant));
        new Thread(query, "Threatworks off-thread query probe").start();
        try {
            return query.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CommandException("The off-thread query was interrupted");
        } catch (ExecutionException | TimeoutException exception) {
            throw new CommandException("The off-thread query failed: %s", exception);
        }
    }

    static void report(String label, EntityLivingBase mob, EntityLivingBase participant, View view) {
        LOGGER.info("h1_query case={} mob={} participant={} state={} ordinary={} threat={} eligibility={} focus={} "
                        + "aggro={} taunt={} forcing={} ticks={} takeover={}", label,
                mob == null ? null : mob.getUniqueID(), participant == null ? null : participant.getUniqueID(),
                view.state(), view.ordinarySwitching(), view.threat(), view.eligibility(), view.focus(), view.aggro(),
                view.tauntActive(), view.tauntForcingFocus(), view.tauntTicks(), view.takeover());
    }
}
