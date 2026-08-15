package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.arena.ArenaFeatureService;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.EnumMap;
import java.util.Map;

/** Server-authoritative health/state for the two central siege gates. */
public final class SiegeGateService {
    public static final int MAX_HEALTH = 250;

    private static final Map<Side, Integer> health = new EnumMap<>(Side.class);

    private SiegeGateService() {
    }

    public static void reset(ServerWorld world, BlockPos center) {
        for (Side side : Side.values()) {
            health.put(side, MAX_HEALTH);
            ArenaFeatureService.restoreGate(world, center, side);
        }
    }

    public static void clear() {
        health.clear();
    }

    public static Side ownerAt(BlockPos center, BlockPos pos) {
        return center == null ? null : ArenaCoordinates.gateOwnerAt(center, pos);
    }

    public static int health(Side side) {
        Integer value = health.get(side);
        return value == null ? MAX_HEALTH : value;
    }

    public static boolean isDestroyed(Side side) {
        return health(side) <= 0;
    }

    public static DamageResult damage(ServerWorld world, BlockPos center, Side owner, Side attacker, int amount) {
        if (world == null || center == null || owner == null || attacker == null || owner == attacker || amount <= 0) {
            return new DamageResult(false, false, owner == null ? 0 : health(owner), 0);
        }
        int before = health(owner);
        if (before <= 0) {
            return new DamageResult(false, true, 0, 0);
        }
        int after = Math.max(0, before - amount);
        health.put(owner, after);
        boolean destroyed = after == 0;
        if (destroyed) {
            ArenaFeatureService.removeGate(world, center, owner);
        } else {
            // BreakEvent is cancelled, so keep the visual gate fully intact until its shared HP reaches zero.
            ArenaFeatureService.restoreGate(world, center, owner);
        }
        return new DamageResult(true, destroyed, after, before - after);
    }

    public static final class DamageResult {
        public final boolean damaged;
        public final boolean destroyed;
        public final int health;
        public final int damage;

        private DamageResult(boolean damaged, boolean destroyed, int health, int damage) {
            this.damaged = damaged;
            this.destroyed = destroyed;
            this.health = health;
            this.damage = damage;
        }
    }
}
