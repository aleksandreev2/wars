package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.CastleWarsMod;
import net.minecraft.block.BlockState;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public final class ArenaBuildService {
    private static final String SCHEMATIC_RESOURCE_PREFIX = "/data/castlewars/structures/medieval_castle.schem.b64.";
    private static final int SCHEMATIC_CHUNKS = 7;
    private static final int BLOCKS_PER_TICK = 8_000;

    private static SpongeV3Schematic cachedSchematic;
    private static BuildTask activeTask;
    private static BlockPos arenaCenter;

    private ArenaBuildService() {
    }

    public static synchronized boolean start(ServerWorld world, BlockPos center) throws IOException {
        if (activeTask != null) {
            return false;
        }
        SpongeV3Schematic schematic = getSchematic();
        arenaCenter = center.immutable();
        activeTask = new BuildTask(world, arenaCenter, schematic);
        CastleWarsMod.LOGGER.info("Arena build started at {}", center);
        return true;
    }

    public static synchronized boolean isBuilding() {
        return activeTask != null;
    }

    public static synchronized boolean hasArena() {
        return arenaCenter != null && activeTask == null;
    }

    public static synchronized BlockPos arenaCenter() {
        return arenaCenter;
    }

    public static synchronized int progressPercent() {
        return activeTask == null ? 100 : activeTask.progressPercent();
    }

    public static synchronized void tick() {
        if (activeTask == null) {
            return;
        }
        if (activeTask.tick(BLOCKS_PER_TICK)) {
            activeTask.finishFeatures();
            CastleWarsMod.LOGGER.info("Arena build completed at {} with v0.2 siege battlefield", arenaCenter);
            activeTask = null;
        }
    }

    private static SpongeV3Schematic getSchematic() throws IOException {
        if (cachedSchematic != null) {
            return cachedSchematic;
        }

        ByteArrayOutputStream encoded = new ByteArrayOutputStream(32_768);
        byte[] buffer = new byte[4096];
        for (int chunk = 0; chunk < SCHEMATIC_CHUNKS; chunk++) {
            String resource = SCHEMATIC_RESOURCE_PREFIX + String.format("%02d", chunk);
            try (InputStream input = ArenaBuildService.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Missing bundled schematic resource " + resource);
                }
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    encoded.write(buffer, 0, read);
                }
            }
        }

        final byte[] compressed;
        try {
            compressed = Base64.getMimeDecoder().decode(encoded.toByteArray());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Bundled schematic Base64 is invalid", ex);
        }

        try (InputStream decoded = new ByteArrayInputStream(compressed)) {
            cachedSchematic = SpongeV3Schematic.load(decoded);
        }
        return cachedSchematic;
    }

    private static final class BuildTask {
        private final ServerWorld world;
        private final BlockPos center;
        private final SpongeV3Schematic schematic;
        private final BlockPos redAnchor;
        private final BlockPos blueAnchor;
        private final int cellsPerCastle;
        private final int totalCells;
        private int cursor;

        private BuildTask(ServerWorld world, BlockPos center, SpongeV3Schematic schematic) {
            this.world = world;
            this.center = center;
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

        private void finishFeatures() {
            ArenaFeatureService.buildStaticFeatures(world, center);
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
