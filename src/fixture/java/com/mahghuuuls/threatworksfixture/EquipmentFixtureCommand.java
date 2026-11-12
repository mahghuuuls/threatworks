package com.mahghuuuls.threatworksfixture;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class EquipmentFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");

    @Override
    public String getName() { return "twequipment"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twequipment inspect|equip|unequip|swap or /twequipment reforge <x> <y> <z>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 1 && "inspect".equals(args[0])) {
            LOGGER.info("equipment_player uuid={} entityId={} instance={} dimension={} tick={}", player.getUniqueID(),
                    player.getEntityId(), System.identityHashCode(player), player.dimension, player.world.getTotalWorldTime());
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                report(slot.getName(), player.getItemStackFromSlot(slot));
            }
            if (Loader.isModLoaded("baubles")) {
                BaubleFixture.inspect(player);
            }
            report("inventory_9", player.inventory.getStackInSlot(9));
        } else if (args.length == 1 && "swap".equals(args[0])) {
            ItemStack carried = player.inventory.getStackInSlot(9);
            player.inventory.setInventorySlotContents(9, player.getHeldItemOffhand());
            player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, carried);
        } else if (args.length == 1 && ("equip".equals(args[0]) || "unequip".equals(args[0]))
                && Loader.isModLoaded("baubles")) {
            BaubleFixture.moveRing(player, "equip".equals(args[0]));
        } else if (args.length == 4 && "reforge".equals(args[0]) && Loader.isModLoaded("qualitytools")) {
            reforge(player, parseBlockPos(sender, args, 1, false));
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private static void report(String slot, ItemStack stack) {
        LOGGER.info("equipment_probe slot={} item={} quality={}", slot, stack.getItem().getRegistryName(),
                stack.getSubCompound("Quality"));
    }

    private static void reforge(EntityPlayerMP player, BlockPos position) throws CommandException {
        TileEntity tile = player.world.getTileEntity(position);
        ItemStack stack = player.getHeldItemOffhand();
        if (!(tile instanceof IInventory) || !tile.getClass().getName().equals(
                "com.tmtravlr.qualitytools.reforging.TileEntityReforgingStation") || stack.isEmpty()) {
            throw new CommandException("Expected a reforging station and an offhand item.");
        }
        IInventory station = (IInventory) tile;
        if (!station.isEmpty()) {
            throw new CommandException("Expected an empty reforging station.");
        }
        report("reforge_before", stack);
        player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        station.setInventorySlotContents(0, stack);
        station.setInventorySlotContents(1, new ItemStack(Items.DIAMOND));
        try {
            // The same server operation as the released reforging packet handler.
            tile.getClass().getMethod("reforgeTool").invoke(tile);
            if (!station.getStackInSlot(1).isEmpty()) {
                throw new CommandException("The station did not consume the test reforge material.");
            }
            report("reforge_after", station.getStackInSlot(0));
        } catch (ReflectiveOperationException failure) {
            throw new CommandException("Could not invoke the installed reforging station: %s", failure.toString());
        } finally {
            player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, station.removeStackFromSlot(0));
            station.removeStackFromSlot(1);
        }
    }

    private static final class BaubleFixture {
        private static final int TEST_RING = 1;

        static void inspect(EntityPlayerMP player) {
            IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                report("baubles_" + slot, handler.getStackInSlot(slot));
            }
        }

        static void moveRing(EntityPlayerMP player, boolean equip) throws CommandException {
            IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
            ItemStack carried = player.inventory.getStackInSlot(9);
            ItemStack ring = handler.getStackInSlot(TEST_RING);
            if (equip ? carried.isEmpty() || !ring.isEmpty() || !handler.isItemValidForSlot(TEST_RING, carried, player)
                    : ring.isEmpty() || !carried.isEmpty()) {
                throw new CommandException("Expected one ring to move and an empty destination.");
            }
            handler.setStackInSlot(TEST_RING, carried);
            player.inventory.setInventorySlotContents(9, ring);
            report("baubles_" + TEST_RING, handler.getStackInSlot(TEST_RING));
        }
    }
}
