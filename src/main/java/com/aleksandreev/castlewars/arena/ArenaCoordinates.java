package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.game.Side;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runtime gameplay coordinates derived from the supplied castle crop plus v0.2 battlefield features. */
public final class ArenaCoordinates {
    // Verified against the supplied schematic: solid castle floor at source Y=26, clear walkable space at Y=27.
    private static final int CORE_SOURCE_X = 55;
    private static final int CORE_SOURCE_Y = 27;
    private static final int CORE_SOURCE_Z = 40;

    private static final int SPAWN_SOURCE_X = 55;
    private static final int SPAWN_SOURCE_Y = 27;
    private static final int SPAWN_SOURCE_Z = 60;

    // Five physically separate 3x3-clear posts verified against medieval_castle.schem.
    private static final int[][] GUARD_POSTS = {
            {37, 27, 34},
            {70, 27, 31},
            {52, 27, 43}, // closest verified post to the Core: Royal Guard
            {40, 27, 68},
            {70, 27, 68}
    };

    public static final int BATTLEFIELD_FLOOR_Y = 26;
    public static final int FLANK_OFFSET_X = 24;
    public static final int WAR_CAMP_RADIUS = 6;

    private ArenaCoordinates() {
    }

    public static BlockPos core(BlockPos center, Side side) {
        return transformSource(center, side, CORE_SOURCE_X, CORE_SOURCE_Y, CORE_SOURCE_Z);
    }

    /** Compatibility alias retained while pure match rules still call the objective a bed internally. */
    public static BlockPos bedFoot(BlockPos center, Side side) {
        return core(center, side);
    }

    /** Compatibility alias; v0.2 has a single-block Castle Core. */
    public static BlockPos bedHead(BlockPos center, Side side) {
        return core(center, side);
    }

    public static BlockPos spawn(BlockPos center, Side side) {
        return transformSource(center, side, SPAWN_SOURCE_X, SPAWN_SOURCE_Y, SPAWN_SOURCE_Z);
    }

    public static List<BlockPos> guardPosts(BlockPos center, Side side) {
        BlockPos[] posts = new BlockPos[GUARD_POSTS.length];
        for (int i = 0; i < GUARD_POSTS.length; i++) {
            int[] post = GUARD_POSTS[i];
            posts[i] = transformSource(center, side, post[0], post[1], post[2]);
        }
        return Arrays.asList(posts);
    }

    public static boolean isObjectiveBed(BlockPos center, Side side, BlockPos pos) {
        return core(center, side).equals(pos);
    }

    public static Side objectiveOwnerAt(BlockPos center, BlockPos pos) {
        if (core(center, Side.RED).equals(pos)) return Side.RED;
        if (core(center, Side.BLUE).equals(pos)) return Side.BLUE;
        return null;
    }

    public static int frontZ(BlockPos center, Side side) {
        return side == Side.RED
                ? ArenaLayout.redAnchor(center).getZ() + ArenaLayout.CROP_LENGTH - 1
                : ArenaLayout.blueAnchor(center).getZ();
    }

    public static int inwardZ(Side side) {
        return side == Side.RED ? -1 : 1;
    }

    public static BlockPos gateCenter(BlockPos center, Side side) {
        return new BlockPos(center.getX(), center.getY() + 29, frontZ(center, side));
    }

    /** Two-block-thick, seven-wide and five-high destructible central gate. */
    public static List<BlockPos> gateBlocks(BlockPos center, Side side) {
        List<BlockPos> blocks = new ArrayList<>();
        int front = frontZ(center, side);
        int inward = inwardZ(side);
        for (int depth = 0; depth < 2; depth++) {
            int z = front + inward * depth;
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = center.getY() + 27; y <= center.getY() + 31; y++) {
                    blocks.add(new BlockPos(center.getX() + dx, y, z));
                }
            }
        }
        return blocks;
    }

    public static Side gateOwnerAt(BlockPos center, BlockPos pos) {
        for (Side side : Side.values()) {
            if (gateBlocks(center, side).contains(pos)) {
                return side;
            }
        }
        return null;
    }

    public static BlockPos flankEntrance(BlockPos center, Side side, boolean west) {
        int x = center.getX() + (west ? -FLANK_OFFSET_X : FLANK_OFFSET_X);
        return new BlockPos(x, center.getY() + 27, frontZ(center, side));
    }

    public static BlockPos warCampCenter(BlockPos center) {
        return center.offset(0, BATTLEFIELD_FLOOR_Y + 1, 0);
    }

    public static boolean insideWarCamp(BlockPos center, BlockPos pos) {
        BlockPos camp = warCampCenter(center);
        return Math.abs(pos.getX() - camp.getX()) <= WAR_CAMP_RADIUS
                && Math.abs(pos.getZ() - camp.getZ()) <= WAR_CAMP_RADIUS
                && Math.abs(pos.getY() - camp.getY()) <= 4;
    }

    public static boolean insideCastle(BlockPos center, Side side, BlockPos pos) {
        BlockPos anchor = side == Side.RED ? ArenaLayout.redAnchor(center) : ArenaLayout.blueAnchor(center);
        int minX = anchor.getX();
        int maxX = anchor.getX() + ArenaLayout.CROP_WIDTH - 1;
        int minZ = anchor.getZ();
        int maxZ = anchor.getZ() + ArenaLayout.CROP_LENGTH - 1;
        int minY = anchor.getY();
        int maxY = anchor.getY() + ArenaLayout.CROP_HEIGHT + 8;
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
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
