package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.arena.ArenaFeatureService;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

/** Neutral map objective that rewards leaving the castle without changing the 10-point win condition. */
public final class WarCampService {
    public static final int CAPTURE_TICKS = 8 * 20;
    public static final int MOMENTUM_TICKS = 45 * 20;

    private static Side controller;
    private static Side capturingSide;
    private static int captureTicks;

    private WarCampService() {
    }

    public static void reset(ServerWorld world, BlockPos center) {
        controller = null;
        capturingSide = null;
        captureTicks = 0;
        if (world != null && center != null) {
            ArenaFeatureService.paintWarCamp(world, center, null);
        }
    }

    public static void clear() {
        controller = null;
        capturingSide = null;
        captureTicks = 0;
    }

    public static void tick(ServerWorld world, BlockPos center, MatchState match) {
        if (world == null || center == null || match == null || match.isFinished()) {
            return;
        }

        boolean redPresent = participantInCamp(world, center, match, Side.RED);
        boolean bluePresent = participantInCamp(world, center, match, Side.BLUE);

        if (redPresent == bluePresent) {
            decayCapture();
            return;
        }

        Side present = redPresent ? Side.RED : Side.BLUE;
        if (present == controller) {
            capturingSide = null;
            captureTicks = 0;
            return;
        }

        if (capturingSide != present) {
            capturingSide = present;
            captureTicks = 0;
        }
        captureTicks++;

        if (captureTicks >= CAPTURE_TICKS) {
            controller = present;
            capturingSide = null;
            captureTicks = 0;
            ArenaFeatureService.paintWarCamp(world, center, controller);
            grantMomentum(world, match, controller);
            broadcast(world, match, controller + " captured the War Camp: Siege Momentum for 45 seconds!");
        }
    }

    public static Side controller() {
        return controller;
    }

    public static Side capturingSide() {
        return capturingSide;
    }

    public static int capturePercent() {
        return Math.min(100, (captureTicks * 100) / CAPTURE_TICKS);
    }

    private static boolean participantInCamp(ServerWorld world, BlockPos center, MatchState match, Side side) {
        ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
        return player != null && player.isAlive() && ArenaCoordinates.insideWarCamp(center, player.blockPosition());
    }

    private static void decayCapture() {
        if (captureTicks > 0) {
            captureTicks = Math.max(0, captureTicks - 2);
        }
        if (captureTicks == 0) {
            capturingSide = null;
        }
    }

    private static void grantMomentum(ServerWorld world, MatchState match, Side side) {
        ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
        if (player == null) return;
        player.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, MOMENTUM_TICKS, 0, false, true));
        player.addEffect(new EffectInstance(Effects.DIG_SPEED, MOMENTUM_TICKS, 0, false, true));
    }

    private static void broadcast(ServerWorld world, MatchState match, String text) {
        for (Side side : Side.values()) {
            ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
            if (player != null) {
                player.displayClientMessage(new StringTextComponent(text), false);
            }
        }
    }
}
