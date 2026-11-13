package com.mahghuuuls.threatworks.server;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

/** A path search treats unknown terrain as impassable. */
final class LoadedPathTerrain implements IBlockAccess {
    // Vanilla compound shapes can read two blocks past the cell being queried.
    private static final int SHAPE_NEIGHBORS = 2;
    private static final int PATH_COLLISION_NEIGHBORS = 4;

    private final WorldServer world;

    LoadedPathTerrain(WorldServer world) {
        this.world = world;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        Chunk chunk = readableShapeChunk(pos);
        return chunk == null ? Blocks.BARRIER.getDefaultState() : chunk.getBlockState(pos);
    }

    private Chunk readableShapeChunk(BlockPos pos) {
        Chunk chunk = readableChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !world.getWorldBorder().contains(pos)) {
            return null;
        }
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        // Vanilla's step/collision probes can read adjacent blocks through the real world.
        int margin = PATH_COLLISION_NEIGHBORS;
        if ((localX < margin || localX >= 16 - margin || localZ < margin || localZ >= 16 - margin)
                && !readableArea(pos.getX() - margin, pos.getZ() - margin, pos.getX() + margin, pos.getZ() + margin)) {
            return null;
        }
        return chunk;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        Chunk chunk = readableChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getMaterial() == Material.AIR;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return getBlockState(pos).getStrongPower(this, pos, direction);
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return lightValue;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        Chunk chunk = readableChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Biomes.PLAINS : chunk.getBiome(pos, world.getBiomeProvider());
    }

    @Override
    public WorldType getWorldType() {
        return world.getWorldInfo().getTerrainType();
    }

    boolean seesSky(BlockPos pos) {
        Chunk chunk = readableChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.canSeeSky(pos);
    }

    boolean isNormalCube(BlockPos pos, boolean unreadable) {
        Chunk chunk = readableShapeChunk(pos);
        if (chunk == null) {
            return unreadable;
        }
        IBlockState state = chunk.getBlockState(pos);
        return state.getBlock().isNormalCube(state, this, pos);
    }

    boolean clearLine(Vec3d from, Vec3d to) {
        BlockPos start = new BlockPos(from);
        BlockPos end = new BlockPos(to);
        int margin = SHAPE_NEIGHBORS;
        return readableArea(Math.min(start.getX(), end.getX()) - margin, Math.min(start.getZ(), end.getZ()) - margin,
                Math.max(start.getX(), end.getX()) + margin, Math.max(start.getZ(), end.getZ()) + margin)
                && world.rayTraceBlocks(from, to, false, true, false) == null;
    }

    private boolean readableArea(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                if (readableChunk(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private Chunk readableChunk(int x, int z) {
        // ChunkProviderServer#getLoadedChunk also cancels a pending unload.
        Chunk chunk = world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(x, z));
        return chunk == null || chunk.unloadQueued ? null : chunk;
    }
}
