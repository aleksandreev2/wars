package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.game.Side;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

/** Runtime objective/spawn coordinates derived from the source schematic crop. */
public final class ArenaCoordinates {
    // Verified against the supplied schematic: solid castle floor at source Y=26, clear walkable space at Y=27.
    private static final int OBJECTIVE_SOURCE_X = 55;
    private static final int OBJECTIVE_SOURCE_Y = 27;
    private static final int OBJECTIVE_SOURCE_Z = 40;

    private static final int SPAWN_SOURCE_X = 55;
    private static final int SPAWN_SOURCE_Y = 27;
    private static final int SPAWN_SOURCE_Z = 60;

    private ArenaCoordinates() {
    }

    public static BlockPos bedFoot(BlockPos center, Side side) {
        return transformSource(center, side, OBJECTIVE_SOURCE_X, OBJECTIVE_SOURCE_Y, OBJECTIVE_SOURCE_Z);
    }

    public static Direction bedFacing(Side side) {
        return side == Side.RED ? Direction.SOUTH : Direction.NORTH;
    }

    public static BlockPos bedHead(BlockPos center, Side side) {
        return bedFoot(center, side).relative(bedFacing(side));
    }

    public static BlockPos spawn(BlockPos center, Side side) {
        return transformSource(center, side, SPAWN_SOURCE_X, SPAWN_SOURCE_Y, SPAWN_SOURCE_Z);
    }

    public static boolean isObjectiveBed(BlockPos center, Side side, BlockPos pos) {
        return bedFoot(center, side).equals(pos) || bedHead(center, side).equals(pos);
    }

    public static Side objectiveOwnerAt(BlockPos center, BlockPos pos) {
        if (isObjectiveBed(center, Side.RED, pos)) return Side.RED;
        if (isObjectiveBed(center, Side.BLUE, pos)) return Side.BLUE;
        return null;
    }

    public static boolean insideArena(BlockPos center, BlockPos pos) {
        BlockPos red = ArenaLayout.redAnchor(center);
        BlockPos blue = ArenaLayout.blueAnchor(center);
        int minX = Math.min(red.getX(), blue.getX()) - 16;
        int maxX = Math.max(red.getX(), blue.getX()) + ArenaLayout.CROP_WIDTH + 15;
        int minZ = Math.min(red.getZ(), blue.getZ()) - 16;
        int maxZ = Math.max(red.getZ(), blue.getZ()) + ArenaLayout.CROP_LENGTH + 15;
        int minY = center.getY() - 8;
        int maxY = center.getY() + ArenaLayout.CROP_HEIGHT + 32;
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static BlockPos transformSource(BlockPos center, Side side, int sourceX, int sourceY, int sourceZ) {
        int localX = sourceX - ArenaLayout.CROP_MIN_X;
        int localY = sourceY - ArenaLayout.CROP_MIN_Y;
        int localZ = sourceZ - ArenaLayout.CROP_MIN_Z;
        BlockPos anchor = side == Side.RED ? ArenaLayout.redAnchor(center) : ArenaLayout.blueAnchor(center);

        if (side == Side.RED) {
            return anchor.offset(localX, localY, localZ);
        }
        return anchor.offset(
                ArenaLayout.CROP_WIDTH - 1 - localX,
                localY,
                ArenaLayout.CROP_LENGTH - 1 - localZ
        );
    }
}
