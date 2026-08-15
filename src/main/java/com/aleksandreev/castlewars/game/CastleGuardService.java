package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the ten castle guard golems for an active match: five RED and five BLUE. */
public final class CastleGuardService {
    public static final int GUARDS_PER_SIDE = 5;

    private static final List<Guard> guards = new ArrayList<>();
    private static final Map<UUID, Side> sideByEntity = new HashMap<>();

    private CastleGuardService() {
    }

    public static void reset(ServerWorld world, BlockPos center, MatchState match) {
        clear();
        spawnSide(world, center, Side.RED);
        spawnSide(world, center, Side.BLUE);
        retarget(world, center, match);
        CastleWarsMod.LOGGER.info("Spawned {} Castle Wars guard golems ({} per side)", guards.size(), GUARDS_PER_SIDE);
    }

    public static void tick(ServerWorld world, BlockPos center, MatchState match) {
        if (world == null || center == null || match == null || match.isFinished()) {
            return;
        }

        for (Guard guard : guards) {
            IronGolemEntity golem = guard.golem;
            if (!golem.isAlive()) {
                continue;
            }

            if (!ArenaCoordinates.insideCastle(center, guard.side, golem.blockPosition())) {
                returnToPost(golem, guard.post, guard.side);
            }
        }

        retarget(world, center, match);
    }

    public static void clear() {
        for (Guard guard : guards) {
            if (guard.golem.isAlive()) {
                guard.golem.remove();
            }
        }
        guards.clear();
        sideByEntity.clear();
    }

    public static Side sideOf(Entity entity) {
        return entity == null ? null : sideByEntity.get(entity.getUUID());
    }

    public static boolean isGuard(Entity entity) {
        return sideOf(entity) != null;
    }

    public static int livingGuards(Side side) {
        int count = 0;
        for (Guard guard : guards) {
            if (guard.side == side && guard.golem.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private static void spawnSide(ServerWorld world, BlockPos center, Side side) {
        List<BlockPos> posts = ArenaCoordinates.guardPosts(center, side);
        if (posts.size() != GUARDS_PER_SIDE) {
            throw new IllegalStateException("Expected " + GUARDS_PER_SIDE + " guard posts for " + side + ", got " + posts.size());
        }

        for (BlockPos post : posts) {
            IronGolemEntity golem = new IronGolemEntity(EntityType.IRON_GOLEM, world);
            float yaw = side == Side.RED ? 0.0F : 180.0F;
            golem.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, yaw, 0.0F);
            golem.setPersistenceRequired();
            golem.setHealth(golem.getMaxHealth());
            world.addFreshEntity(golem);

            guards.add(new Guard(side, post.immutable(), golem));
            sideByEntity.put(golem.getUUID(), side);
        }
    }

    private static void retarget(ServerWorld world, BlockPos center, MatchState match) {
        ServerPlayerEntity red = world.getServer().getPlayerList().getPlayer(match.player(Side.RED));
        ServerPlayerEntity blue = world.getServer().getPlayerList().getPlayer(match.player(Side.BLUE));

        for (Guard guard : guards) {
            IronGolemEntity golem = guard.golem;
            if (!golem.isAlive()) {
                continue;
            }
            ServerPlayerEntity enemy = guard.side == Side.RED ? blue : red;
            if (enemy != null && enemy.isAlive() && ArenaCoordinates.insideCastle(center, guard.side, enemy.blockPosition())) {
                golem.setTarget(enemy);
            } else {
                golem.setTarget(null);
                golem.getNavigation().stop();
            }
        }
    }

    private static void returnToPost(IronGolemEntity golem, BlockPos post, Side side) {
        float yaw = side == Side.RED ? 0.0F : 180.0F;
        golem.setTarget(null);
        golem.getNavigation().stop();
        golem.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, yaw, 0.0F);
    }

    private static final class Guard {
        private final Side side;
        private final BlockPos post;
        private final IronGolemEntity golem;

        private Guard(Side side, BlockPos post, IronGolemEntity golem) {
            this.side = side;
            this.post = post;
            this.golem = golem;
        }
    }
}
