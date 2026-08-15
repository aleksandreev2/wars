package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.CastleWarsMod;
import net.minecraft.block.BlockState;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.io.IOException;
import java.io.InputStream;

public final class ArenaBuildService {
    private static final String SCHEMATIC_RESOURCE = "/data/castlewars/structures/medieval_castle.schem";
    private static final int BLOCKS_PER_TICK = 8_000;

    private static SpongeV3Schematic cachedSchematic;
    private static BuildTask activeTask;

    private ArenaBuildService() {
    }

    public static synchronized boolean start(ServerWorld world, BlockPos center) throws IOException {
        if (activeTask != null) {
            return false;
        }
        SpongeV3Schematic schematic = getSchematic();
        activeTask = new BuildTask(world, center.immutable(), schematic);
        CastleWarsMod.LOGGER.info("Arena build started at {}", center);
        return true;
    }

    public static synchronized boolean isBuilding() {
        return activeTask != null;
    }

    public static synchronized int progressPercent() {
        return activeTask == null ? 100 : activeTask.progressPercent();
    }

    public static synchronized void tick() {
        if (activeTask == null) {
            return;
        }
        if (activeTask.tick(BLOCKS_PER_TICK)) {
            CastleWarsMod.LOGGER.info("Arena build completed");
            activeTask = null;
        }
    }

    private static SpongeV3Schematic getSchematic() throws IOException {
        if (cachedSchematic != null) {
            return cachedSchematic;
        }
        try (InputStream input = ArenaBuildService.class.getResourceAsStream(SCHEMATIC_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing bundled schematic resource " + SCHEMATIC_RESOURCE);
            }
            cachedSchematic = SpongeV3Schematic.load(input);
            return cachedSchematic;
        }
    }

    private static final class BuildTask {
        private final ServerWorld world;
        private final SpongeV3Schematic schematic;
        private final BlockPos redAnchor;
        private final BlockPos blueAnchor;
        private final int cellsPerCastle;
        private final int totalCells;
        private int cursor;

        private BuildTask(ServerWorld world, BlockPos center, SpongeV3Schematic schematic) {
            this.world = world;
            this.schematic = schematic;
            this.redAnchor = ArenaLayout.redAnchor(center);
            this.blueAnchor = ArenaLayout.blueAnchor(center);
            this.cellsPerCastle = ArenaLayout.CROP_WIDTH * ArenaLayout.CROP_HEIGHT * ArenaLayout.CROP_LENGTH;
            this.totalCells = cellsPerCastle * 2;
        }

        private boolean tick(int budget) {
            int stop = Math.min(totalCells, cursor + budget);
            while (cursor < stop) {
                placeCell(cursor++);
            }
            return cursor >= totalCells;
        }

        private int progressPercent() {
            return totalCells == 0 ? 100 : (int) ((cursor * 100L) / totalCells);
        }

        private void placeCell(int globalIndex) {
            boolean blue = globalIndex >= cellsPerCastle;
            int localIndex = blue ? globalIndex - cellsPerCastle : globalIndex;

            int x = localIndex % ArenaLayout.CROP_WIDTH;
            int z = (localIndex / ArenaLayout.CROP_WIDTH) % ArenaLayout.CROP_LENGTH;
            int y = localIndex / (ArenaLayout.CROP_WIDTH * ArenaLayout.CROP_LENGTH);

            int sourceX = ArenaLayout.CROP_MIN_X + x;
            int sourceY = ArenaLayout.CROP_MIN_Y + y;
            int sourceZ = ArenaLayout.CROP_MIN_Z + z;
            BlockState state = schematic.stateAt(sourceX, sourceY, sourceZ);

            BlockPos anchor = blue ? blueAnchor : redAnchor;
            int worldX = blue ? anchor.getX() + (ArenaLayout.CROP_WIDTH - 1 - x) : anchor.getX() + x;
            int worldZ = blue ? anchor.getZ() + (ArenaLayout.CROP_LENGTH - 1 - z) : anchor.getZ() + z;
            BlockPos target = new BlockPos(worldX, anchor.getY() + y, worldZ);

            if (blue) {
                state = state.rotate(Rotation.CLOCKWISE_180);
            }
            world.setBlock(target, state, 2);
        }
    }
}
