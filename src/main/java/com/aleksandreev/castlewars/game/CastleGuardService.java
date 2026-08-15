package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
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
        CastleWarsMod.LOGGER.info("Spawned {} Castle Wars guards with gate/courtyard/royal roles", guards.size());
    }

    public static void tick(ServerWorld world, BlockPos center, MatchState match) {
        if (world == null || center == null || match == null || match.isFinished()) return;

        ServerPlayerEntity red = world.getServer().getPlayerList().getPlayer(match.player(Side.RED));
        ServerPlayerEntity blue = world.getServer().getPlayerList().getPlayer(match.player(Side.BLUE));

        for (Guard guard : guards) {
            IronGolemEntity golem = guard.golem;
            if (!golem.isAlive()) continue;

            if (!ArenaCoordinates.insideCastle(center, guard.side, golem.blockPosition())) {
                returnToPost(golem, guard.post, guard.side);
                continue;
            }

            ServerPlayerEntity enemy = guard.side == Side.RED ? blue : red;
            if (enemy != null && enemy.isAlive() && ArenaCoordinates.insideCastle(center, guard.side, enemy.blockPosition())) {
                golem.setTarget(enemy);
                continue;
            }

            golem.setTarget(null);
            patrol(world, guard);
        }
    }

    public static void clear() {
        for (Guard guard : guards) {
            if (guard.golem.isAlive()) guard.golem.remove();
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
            if (guard.side == side && guard.golem.isAlive()) count++;
        }
        return count;
    }

    private static void spawnSide(ServerWorld world, BlockPos center, Side side) {
        List<BlockPos> posts = ArenaCoordinates.guardPosts(center, side);
        if (posts.size() != GUARDS_PER_SIDE) {
            throw new IllegalStateException("Expected " + GUARDS_PER_SIDE + " guard posts for " + side + ", got " + posts.size());
        }

        for (int index = 0; index < posts.size(); index++) {
            BlockPos post = posts.get(index);
            GuardRole role = roleFor(index);
            IronGolemEntity golem = new IronGolemEntity(EntityType.IRON_GOLEM, world);
            float yaw = side == Side.RED ? 0.0F : 180.0F;
            golem.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, yaw, 0.0F);
            golem.setPersistenceRequired();
            golem.setCustomName(new StringTextComponent(displayName(role, side)));
            golem.setCustomNameVisible(role == GuardRole.ROYAL);

            if (role == GuardRole.ROYAL) {
                if (golem.getAttribute(Attributes.MAX_HEALTH) != null) {
                    golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(140.0D);
                }
                if (golem.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    golem.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(18.0D);
                }
            } else if (role == GuardRole.GATE && golem.getAttribute(Attributes.MAX_HEALTH) != null) {
                golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(110.0D);
            }
            golem.setHealth(golem.getMaxHealth());
            world.addFreshEntity(golem);

            BlockPos patrol = patrolPoint(post, index, role);
            guards.add(new Guard(side, post.immutable(), patrol.immutable(), role, index, golem));
            sideByEntity.put(golem.getUUID(), side);
        }
    }

    private static GuardRole roleFor(int index) {
        if (index == 2) return GuardRole.ROYAL;
        if (index <= 1) return GuardRole.GATE;
        return GuardRole.COURTYARD;
    }

    private static String displayName(GuardRole role, Side side) {
        if (role == GuardRole.ROYAL) return "Royal Guard [" + side + "]";
        if (role == GuardRole.GATE) return "Gate Guard [" + side + "]";
        return "Courtyard Guard [" + side + "]";
    }

    private static BlockPos patrolPoint(BlockPos post, int index, GuardRole role) {
        if (role == GuardRole.ROYAL) return post;
        int step = index % 2 == 0 ? 2 : -2;
        return role == GuardRole.GATE ? post.offset(step, 0, 0) : post.offset(0, 0, step);
    }

    private static void patrol(ServerWorld world, Guard guard) {
        IronGolemEntity golem = guard.golem;
        BlockPos target;
        if (guard.role == GuardRole.ROYAL) {
            target = guard.post;
        } else {
            long phase = (world.getGameTime() / 80L + guard.index) & 1L;
            target = phase == 0L ? guard.post : guard.patrol;
        }

        if (golem.blockPosition().distSqr(target) > 2.0D) {
            golem.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    guard.role == GuardRole.ROYAL ? 0.65D : 0.8D);
        }
    }

    private static void returnToPost(IronGolemEntity golem, BlockPos post, Side side) {
        float yaw = side == Side.RED ? 0.0F : 180.0F;
        golem.setTarget(null);
        golem.getNavigation().stop();
        golem.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, yaw, 0.0F);
    }

    private enum GuardRole {
        GATE,
        COURTYARD,
        ROYAL
    }

    private static final class Guard {
        private final Side side;
        private final BlockPos post;
        private final BlockPos patrol;
        private final GuardRole role;
        private final int index;
        private final IronGolemEntity golem;

        private Guard(Side side, BlockPos post, BlockPos patrol, GuardRole role, int index, IronGolemEntity golem) {
            this.side = side;
            this.post = post;
            this.patrol = patrol;
            this.role = role;
            this.index = index;
            this.golem = golem;
        }
    }
}
