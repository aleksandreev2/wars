package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.game.Side;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/** Builds the v0.2 gameplay layer around the two imported castles. */
public final class ArenaFeatureService {
    private ArenaFeatureService() {
    }

    public static void buildStaticFeatures(ServerWorld world, BlockPos center) {
        buildBattlefield(world, center);
        for (Side side : Side.values()) {
            carveCentralEntrance(world, center, side);
            carveFlankEntrance(world, center, side, true);
            carveFlankEntrance(world, center, side, false);
            buildCoreShrine(world, center, side);
            restoreGate(world, center, side);
        }
        paintWarCamp(world, center, null);
    }

    public static void restoreRoundFeatures(ServerWorld world, BlockPos center) {
        for (Side side : Side.values()) {
            restoreCore(world, center, side);
            restoreGate(world, center, side);
        }
        paintWarCamp(world, center, null);
    }

    public static void restoreCore(ServerWorld world, BlockPos center, Side side) {
        buildCoreShrine(world, center, side);
    }

    public static void removeCore(ServerWorld world, BlockPos center, Side side) {
        world.setBlock(ArenaCoordinates.core(center, side), Blocks.AIR.defaultBlockState(), 3);
    }

    public static void restoreGate(ServerWorld world, BlockPos center, Side side) {
        int front = ArenaCoordinates.frontZ(center, side);
        int inward = ArenaCoordinates.inwardZ(side);
        for (int depth = 0; depth < 2; depth++) {
            int z = front + inward * depth;
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = center.getY() + 27; y <= center.getY() + 31; y++) {
                    boolean frame = Math.abs(dx) == 3 || y == center.getY() + 31;
                    BlockState state = frame ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
                    world.setBlock(new BlockPos(center.getX() + dx, y, z), state, 3);
                }
            }
        }
    }

    public static void removeGate(ServerWorld world, BlockPos center, Side side) {
        for (BlockPos pos : ArenaCoordinates.gateBlocks(center, side)) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Gives the central objective a visible team-color ownership ring. */
    public static void paintWarCamp(ServerWorld world, BlockPos center, Side owner) {
        BlockPos camp = ArenaCoordinates.warCampCenter(center);
        BlockState accent = owner == Side.RED ? Blocks.RED_WOOL.defaultBlockState()
                : owner == Side.BLUE ? Blocks.BLUE_WOOL.defaultBlockState()
                : Blocks.WHITE_WOOL.defaultBlockState();
        int y = camp.getY() - 1;
        for (int i = -5; i <= 5; i++) {
            world.setBlock(new BlockPos(camp.getX() + i, y, camp.getZ() - 5), accent, 3);
            world.setBlock(new BlockPos(camp.getX() + i, y, camp.getZ() + 5), accent, 3);
            world.setBlock(new BlockPos(camp.getX() - 5, y, camp.getZ() + i), accent, 3);
            world.setBlock(new BlockPos(camp.getX() + 5, y, camp.getZ() + i), accent, 3);
        }
    }

    private static void buildBattlefield(ServerWorld world, BlockPos center) {
        int minZ = ArenaCoordinates.frontZ(center, Side.RED) + 1;
        int maxZ = ArenaCoordinates.frontZ(center, Side.BLUE) - 1;
        int centralY = center.getY() + ArenaCoordinates.BATTLEFIELD_FLOOR_Y;

        // Fast central causeway: broad, exposed and aligned directly with the destructible gates.
        for (int z = minZ; z <= maxZ; z++) {
            for (int dx = -4; dx <= 4; dx++) {
                BlockState floor = Math.abs(dx) == 4 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : ((Math.abs(z) + Math.abs(dx)) % 9 == 0
                        ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState());
                setWalkableColumn(world, new BlockPos(center.getX() + dx, centralY, z), floor, 5);
            }
        }

        // West route: ruined battlement lane with alternating cover.
        int westX = center.getX() - ArenaCoordinates.FLANK_OFFSET_X;
        for (int z = minZ; z <= maxZ; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                BlockState floor = (Math.abs(z + dx) % 7 == 0)
                        ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState();
                setWalkableColumn(world, new BlockPos(westX + dx, centralY, z), floor, 5);
            }
            if ((z - minZ) % 11 == 5) {
                int side = ((z - minZ) / 11) % 2 == 0 ? -2 : 2;
                world.setBlock(new BlockPos(westX + side, centralY + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                world.setBlock(new BlockPos(westX + side, centralY + 2, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            }
        }

        // East route: a lower trench. It drops two blocks in the middle and ramps back up at both castles.
        int eastX = center.getX() + ArenaCoordinates.FLANK_OFFSET_X;
        for (int z = minZ; z <= maxZ; z++) {
            int distanceFromEnd = Math.min(z - minZ, maxZ - z);
            int drop = Math.min(2, Math.max(0, distanceFromEnd / 5));
            int floorY = centralY - drop;
            for (int dx = -2; dx <= 2; dx++) {
                BlockState floor = (Math.abs(z * 3 + dx) % 8 == 0)
                        ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                setWalkableColumn(world, new BlockPos(eastX + dx, floorY, z), floor, 5);
            }
            world.setBlock(new BlockPos(eastX - 3, floorY + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
            world.setBlock(new BlockPos(eastX + 3, floorY + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        }

        buildWarCamp(world, center);
    }

    private static void buildWarCamp(ServerWorld world, BlockPos center) {
        BlockPos camp = ArenaCoordinates.warCampCenter(center);
        int floorY = camp.getY() - 1;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (dx * dx + dz * dz > 52) continue;
                BlockState floor = (Math.abs(dx * 13 + dz * 7) % 10 == 0)
                        ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                setWalkableColumn(world, new BlockPos(camp.getX() + dx, floorY, camp.getZ() + dz), floor, 6);
            }
        }

        // Compact war-camp landmark, visible from all three routes.
        world.setBlock(camp, Blocks.CAMPFIRE.defaultBlockState(), 3);
        world.setBlock(camp.offset(-3, 0, -3), Blocks.LANTERN.defaultBlockState(), 3);
        world.setBlock(camp.offset(3, 0, -3), Blocks.LANTERN.defaultBlockState(), 3);
        world.setBlock(camp.offset(-3, 0, 3), Blocks.LANTERN.defaultBlockState(), 3);
        world.setBlock(camp.offset(3, 0, 3), Blocks.LANTERN.defaultBlockState(), 3);
    }

    private static void carveCentralEntrance(ServerWorld world, BlockPos center, Side side) {
        int front = ArenaCoordinates.frontZ(center, side);
        int inward = ArenaCoordinates.inwardZ(side);
        for (int depth = 0; depth < 8; depth++) {
            int z = front + inward * depth;
            for (int dx = -4; dx <= 4; dx++) {
                world.setBlock(new BlockPos(center.getX() + dx, center.getY() + 26, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int y = center.getY() + 27; y <= center.getY() + 32; y++) {
                    world.setBlock(new BlockPos(center.getX() + dx, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void carveFlankEntrance(ServerWorld world, BlockPos center, Side side, boolean west) {
        BlockPos entrance = ArenaCoordinates.flankEntrance(center, side, west);
        int inward = ArenaCoordinates.inwardZ(side);
        for (int depth = 0; depth < 7; depth++) {
            int z = entrance.getZ() + inward * depth;
            for (int dx = -2; dx <= 2; dx++) {
                world.setBlock(new BlockPos(entrance.getX() + dx, center.getY() + 26, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                for (int y = center.getY() + 27; y <= center.getY() + 31; y++) {
                    world.setBlock(new BlockPos(entrance.getX() + dx, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Stone frame makes the intentionally open breach readable as a route rather than map damage.
        int z = entrance.getZ();
        for (int y = center.getY() + 27; y <= center.getY() + 31; y++) {
            world.setBlock(new BlockPos(entrance.getX() - 3, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            world.setBlock(new BlockPos(entrance.getX() + 3, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
    }

    private static void buildCoreShrine(ServerWorld world, BlockPos center, Side side) {
        BlockPos core = ArenaCoordinates.core(center, side);
        BlockState teamGlass = side == Side.RED ? Blocks.RED_STAINED_GLASS.defaultBlockState() : Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        BlockState teamFloor = side == Side.RED ? Blocks.RED_WOOL.defaultBlockState() : Blocks.BLUE_WOOL.defaultBlockState();

        // Clear a compact readable shrine around the imported room without changing its outer shell.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    world.setBlock(core.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockState floor = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? teamFloor : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                world.setBlock(core.offset(dx, -1, dz), floor, 3);
            }
        }

        world.setBlock(core.offset(0, -1, 0), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        world.setBlock(core, Blocks.LODESTONE.defaultBlockState(), 3);

        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] corner : corners) {
            for (int dy = 0; dy <= 2; dy++) {
                world.setBlock(core.offset(corner[0], dy, corner[1]), teamGlass, 3);
            }
        }
    }

    private static void setWalkableColumn(ServerWorld world, BlockPos floor, BlockState floorState, int clearHeight) {
        world.setBlock(floor, floorState, 3);
        for (int dy = 1; dy <= clearHeight; dy++) {
            world.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
