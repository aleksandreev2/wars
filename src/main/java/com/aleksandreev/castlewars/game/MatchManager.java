package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaBuildService;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.arena.BlockStateText;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative bridge between pure match rules and the Minecraft world. */
public final class MatchManager {
    private static final int ROUND_RESET_DELAY_TICKS = 60;

    private static MatchState match;
    private static ServerWorld world;
    private static BlockPos center;
    private static final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
    private static int roundResetTicks = -1;

    private MatchManager() {
    }

    public static synchronized String start(ServerPlayerEntity red, ServerPlayerEntity blue) {
        if (match != null && !match.isFinished()) {
            return "A Castle Wars match is already running.";
        }
        if (ArenaBuildService.isBuilding()) {
            return "The arena is still building.";
        }
        if (!ArenaBuildService.hasArena() || ArenaBuildService.arenaCenter() == null) {
            return "Build the arena first with /wars arena build.";
        }
        if (red.getLevel() != blue.getLevel()) {
            return "Both players must be in the same dimension.";
        }

        match = new MatchState(red.getUUID(), blue.getUUID(), CaptureRules.DEFAULT_WIN_SCORE);
        world = red.getLevel();
        center = ArenaBuildService.arenaCenter().immutable();
        roundResetTicks = -1;
        playerPlacedBlocks.clear();
        restoreObjectiveBeds();
        preparePlayer(red, Side.RED);
        preparePlayer(blue, Side.BLUE);
        CastleWarsMod.LOGGER.info("Castle Wars match started: RED={} BLUE={}", red.getGameProfile().getName(), blue.getGameProfile().getName());
        return null;
    }

    public static synchronized void stop() {
        if (world != null) {
            clearPlayerPlacedBlocks();
            restoreObjectiveBeds();
        }
        match = null;
        world = null;
        center = null;
        roundResetTicks = -1;
        playerPlacedBlocks.clear();
        CastleWarsMod.LOGGER.info("Castle Wars match stopped");
    }

    public static synchronized boolean isRunning() {
        return match != null && !match.isFinished();
    }

    public static synchronized MatchState state() {
        return match;
    }

    public static synchronized BlockPos center() {
        return center;
    }

    public static synchronized ServerWorld world() {
        return world;
    }

    public static synchronized Side sideOf(UUID playerId) {
        return match == null ? null : match.sideOf(playerId);
    }

    public static synchronized boolean isRoundResetPending() {
        return roundResetTicks >= 0;
    }

    public static synchronized boolean destroyObjective(Side bedOwner, Side attacker) {
        if (!isRunning() || isRoundResetPending() || !match.destroyBed(bedOwner, attacker)) {
            return false;
        }
        removeObjectiveBed(bedOwner);
        CastleWarsMod.LOGGER.info("{} destroyed {} objective bed", attacker, bedOwner);
        return true;
    }

    public static synchronized MatchState.KillResult onPlayerKilled(UUID victimId, UUID killerId) {
        if (!isRunning() || isRoundResetPending()) {
            return MatchState.KillResult.NONE;
        }
        MatchState.KillResult result = match.onKill(victimId, killerId);
        if (result == MatchState.KillResult.POINT) {
            roundResetTicks = ROUND_RESET_DELAY_TICKS;
            CastleWarsMod.LOGGER.info("Castle captured. Score RED {} : {} BLUE", match.score(Side.RED), match.score(Side.BLUE));
        } else if (result == MatchState.KillResult.MATCH_WON) {
            CastleWarsMod.LOGGER.info("Castle Wars finished. Score RED {} : {} BLUE", match.score(Side.RED), match.score(Side.BLUE));
        }
        return result;
    }

    public static synchronized void recordPlayerPlacedBlock(UUID playerId, BlockPos pos) {
        if (!isRunning() || sideOf(playerId) == null || center == null || !ArenaCoordinates.insideArena(center, pos)) {
            return;
        }
        playerPlacedBlocks.add(pos.immutable());
    }

    public static synchronized boolean canParticipantBreak(UUID playerId, BlockPos pos) {
        if (!isRunning() || sideOf(playerId) == null || center == null || !ArenaCoordinates.insideArena(center, pos)) {
            return true;
        }
        return playerPlacedBlocks.contains(pos);
    }

    public static synchronized void onPlayerRespawn(ServerPlayerEntity player) {
        if (match == null || world == null || center == null) return;
        Side side = match.sideOf(player.getUUID());
        if (side != null) {
            preparePlayer(player, side);
        }
    }

    public static synchronized void tick() {
        if (!isRunning() || roundResetTicks < 0) {
            return;
        }
        if (roundResetTicks > 0) {
            roundResetTicks--;
            return;
        }
        resetRound();
        roundResetTicks = -1;
    }

    private static void resetRound() {
        if (match == null || world == null || center == null || match.isFinished()) return;
        clearPlayerPlacedBlocks();
        restoreObjectiveBeds();
        match.resetRoundObjectives();

        ServerPlayerEntity red = world.getServer().getPlayerList().getPlayer(match.player(Side.RED));
        ServerPlayerEntity blue = world.getServer().getPlayerList().getPlayer(match.player(Side.BLUE));
        if (red != null) preparePlayer(red, Side.RED);
        if (blue != null) preparePlayer(blue, Side.BLUE);
        CastleWarsMod.LOGGER.info("Castle Wars round reset");
    }

    private static void clearPlayerPlacedBlocks() {
        if (world == null) return;
        for (BlockPos pos : playerPlacedBlocks) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        playerPlacedBlocks.clear();
    }

    private static void restoreObjectiveBeds() {
        if (world == null || center == null) return;
        restoreObjectiveBed(Side.RED);
        restoreObjectiveBed(Side.BLUE);
    }

    private static void restoreObjectiveBed(Side side) {
        String color = side == Side.RED ? "red" : "blue";
        String facing = side == Side.RED ? "south" : "north";
        world.setBlock(
                ArenaCoordinates.bedFoot(center, side),
                BlockStateText.parseRequired("minecraft:" + color + "_bed[facing=" + facing + ",occupied=false,part=foot]"),
                3
        );
        world.setBlock(
                ArenaCoordinates.bedHead(center, side),
                BlockStateText.parseRequired("minecraft:" + color + "_bed[facing=" + facing + ",occupied=false,part=head]"),
                3
        );
    }

    private static void removeObjectiveBed(Side side) {
        world.setBlock(ArenaCoordinates.bedFoot(center, side), Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(ArenaCoordinates.bedHead(center, side), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void preparePlayer(ServerPlayerEntity player, Side side) {
        BlockPos spawn = ArenaCoordinates.spawn(center, side);
        float yaw = side == Side.RED ? 0.0F : 180.0F;
        player.teleportTo(world, spawn.getX() + 0.5D, spawn.getY() + 0.1D, spawn.getZ() + 0.5D, yaw, 0.0F);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.removeAllEffects();
    }
}
