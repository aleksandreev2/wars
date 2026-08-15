package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaBuildService;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.arena.ArenaFeatureService;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative bridge between pure match rules and the Minecraft world. */
public final class MatchManager {
    private static final int ROUND_CAPTURE_PAUSE_TICKS = 5 * 20;
    private static final int ROUND_START_COUNTDOWN_TICKS = 3 * 20;

    private static MatchState match;
    private static ServerWorld world;
    private static BlockPos center;
    private static final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
    private static int roundResetTicks = -1;
    private static int startCountdownTicks = -1;

    private MatchManager() {
    }

    public static synchronized String start(ServerPlayerEntity red, ServerPlayerEntity blue) {
        String validation = validateArenaReady();
        if (validation != null) return validation;
        if (red.getLevel() != blue.getLevel()) return "Both players must be in the same dimension.";

        if (match != null) stop();

        match = new MatchState(red.getUUID(), blue.getUUID(), CaptureRules.DEFAULT_WIN_SCORE);
        world = red.getLevel();
        center = ArenaBuildService.arenaCenter().immutable();
        beginMatchWorldState();
        preparePlayer(red, Side.RED, true);
        preparePlayer(blue, Side.BLUE, true);
        CastleGuardService.reset(world, center, match);
        MatchFeedbackService.open(world, match);
        startCountdownTicks = ROUND_START_COUNTDOWN_TICKS;
        CastleWarsMod.LOGGER.info("Castle Wars v0.2 match started: RED={} BLUE={}", red.getGameProfile().getName(), blue.getGameProfile().getName());
        return null;
    }

    /** Starts a full scoring match against a server-side combat NPC for one-client testing. */
    public static synchronized String startSolo(ServerPlayerEntity player, Side playerSide) {
        String validation = validateArenaReady();
        if (validation != null) return validation;
        if (playerSide == null) return "Player side is required.";

        if (match != null) stop();

        world = player.getLevel();
        center = ArenaBuildService.arenaCenter().immutable();
        roundResetTicks = -1;
        startCountdownTicks = -1;
        playerPlacedBlocks.clear();
        ArenaFeatureService.restoreRoundFeatures(world, center);
        SiegeGateService.reset(world, center);
        WarCampService.reset(world, center);

        Side npcSide = playerSide.opponent();
        final UUID npcId;
        try {
            npcId = TestNpcService.spawn(world, center, npcSide);
        } catch (RuntimeException ex) {
            CastleWarsMod.LOGGER.error("Failed to spawn solo-test NPC", ex);
            TestNpcService.clear();
            world = null;
            center = null;
            return "Failed to create the test NPC. Check the server log.";
        }

        UUID redId = playerSide == Side.RED ? player.getUUID() : npcId;
        UUID blueId = playerSide == Side.BLUE ? player.getUUID() : npcId;
        match = new MatchState(redId, blueId, CaptureRules.DEFAULT_WIN_SCORE);

        preparePlayer(player, playerSide, true);
        TestNpcService.reset(world, center, match);
        CastleGuardService.reset(world, center, match);
        MatchFeedbackService.open(world, match);
        startCountdownTicks = ROUND_START_COUNTDOWN_TICKS;
        CastleWarsMod.LOGGER.info("Castle Wars v0.2 solo-test started: player={} side={} npcSide={}",
                player.getGameProfile().getName(), playerSide, npcSide);
        return null;
    }

    private static void beginMatchWorldState() {
        roundResetTicks = -1;
        startCountdownTicks = -1;
        playerPlacedBlocks.clear();
        TestNpcService.clear();
        ArenaFeatureService.restoreRoundFeatures(world, center);
        SiegeGateService.reset(world, center);
        WarCampService.reset(world, center);
    }

    private static String validateArenaReady() {
        if (match != null && !match.isFinished()) return "A Castle Wars match is already running.";
        if (ArenaBuildService.isBuilding()) return "The arena is still building.";
        if (!ArenaBuildService.hasArena() || ArenaBuildService.arenaCenter() == null) {
            return "Build the arena first with /wars arena build.";
        }
        return null;
    }

    public static synchronized void stop() {
        setHumanInvulnerability(false);
        MatchFeedbackService.close();
        TestNpcService.clear();
        CastleGuardService.clear();
        SiegeGateService.clear();
        WarCampService.clear();
        if (world != null) {
            clearPlayerPlacedBlocks();
            if (center != null) ArenaFeatureService.restoreRoundFeatures(world, center);
        }
        match = null;
        world = null;
        center = null;
        roundResetTicks = -1;
        startCountdownTicks = -1;
        playerPlacedBlocks.clear();
        CastleWarsMod.LOGGER.info("Castle Wars match stopped");
    }

    public static synchronized boolean isRunning() {
        return match != null && !match.isFinished();
    }

    public static synchronized boolean isCombatActive() {
        return isRunning() && roundResetTicks < 0 && startCountdownTicks <= 0;
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

    public static synchronized int roundResetTicks() {
        return roundResetTicks;
    }

    public static synchronized int startCountdownTicks() {
        return startCountdownTicks;
    }

    public static synchronized String stageName() {
        if (match == null) return "SKIRMISH";
        int total = match.score(Side.RED) + match.score(Side.BLUE);
        if (total <= 3) return "SKIRMISH";
        if (total <= 8) return "SIEGE";
        if (total <= 13) return "TOTAL WAR";
        return "FINAL ASSAULT";
    }

    public static synchronized boolean destroyObjective(Side coreOwner, Side attacker) {
        if (!isCombatActive() || !match.destroyBed(coreOwner, attacker)) return false;
        ArenaFeatureService.removeCore(world, center, coreOwner);
        MatchFeedbackService.onCoreDestroyed(world, match, coreOwner, attacker);
        CastleWarsMod.LOGGER.info("{} destroyed {} Castle Core", attacker, coreOwner);
        return true;
    }

    public static synchronized MatchState.KillResult onPlayerKilled(UUID victimId, UUID killerId) {
        if (killerId == null) return MatchState.KillResult.NONE;
        return onParticipantDefeated(victimId, sideOf(killerId));
    }

    public static synchronized MatchState.KillResult onParticipantDefeated(UUID victimId, Side defeatingSide) {
        if (!isCombatActive()) return MatchState.KillResult.NONE;
        MatchState.KillResult result = match.onDefeat(victimId, defeatingSide);
        if (result == MatchState.KillResult.POINT) {
            MatchFeedbackService.onPoint(world, match, defeatingSide, false);
            roundResetTicks = ROUND_CAPTURE_PAUSE_TICKS;
            setHumanInvulnerability(true);
            CastleWarsMod.LOGGER.info("Castle captured. Score RED {} : {} BLUE", match.score(Side.RED), match.score(Side.BLUE));
        } else if (result == MatchState.KillResult.MATCH_WON) {
            MatchFeedbackService.onPoint(world, match, defeatingSide, true);
            setHumanInvulnerability(false);
            TestNpcService.clear();
            CastleGuardService.clear();
            clearPlayerPlacedBlocks();
            ArenaFeatureService.restoreRoundFeatures(world, center);
            SiegeGateService.clear();
            WarCampService.clear();
            CastleWarsMod.LOGGER.info("Castle Wars finished. Score RED {} : {} BLUE", match.score(Side.RED), match.score(Side.BLUE));
        }
        return result;
    }

    public static synchronized void recordPlayerPlacedBlock(UUID playerId, BlockPos pos) {
        if (!isCombatActive() || sideOf(playerId) == null || center == null || !ArenaCoordinates.insideArena(center, pos)) return;
        playerPlacedBlocks.add(pos.immutable());
    }

    public static synchronized boolean canParticipantBreak(UUID playerId, BlockPos pos) {
        if (!isRunning() || sideOf(playerId) == null || center == null || !ArenaCoordinates.insideArena(center, pos)) return true;
        if (!isCombatActive()) return false;
        return playerPlacedBlocks.contains(pos);
    }

    public static synchronized void onPlayerRespawn(ServerPlayerEntity player) {
        if (match == null || world == null || center == null) return;
        Side side = match.sideOf(player.getUUID());
        if (side != null) {
            preparePlayer(player, side, !isCombatActive());
        }
    }

    public static synchronized void tick() {
        if (!isRunning()) return;

        MatchFeedbackService.tick(world, center, match, startCountdownTicks, roundResetTicks);

        if (roundResetTicks >= 0) {
            holdHumansAtSpawns();
            if (roundResetTicks > 0) {
                roundResetTicks--;
                return;
            }
            resetRound();
            roundResetTicks = -1;
            return;
        }

        if (startCountdownTicks > 0) {
            holdHumansAtSpawns();
            TestNpcService.holdAtSpawn(center);
            startCountdownTicks--;
            if (startCountdownTicks == 0) {
                setHumanInvulnerability(false);
                TestNpcService.setInvulnerable(false);
                MatchFeedbackService.onRoundStarted(world, match);
            }
            return;
        }

        TestNpcService.tick(world, center, match);
        CastleGuardService.tick(world, center, match);
        WarCampService.tick(world, center, match);
    }

    private static void resetRound() {
        if (match == null || world == null || center == null || match.isFinished()) return;
        clearPlayerPlacedBlocks();
        match.resetRoundObjectives();
        ArenaFeatureService.restoreRoundFeatures(world, center);
        SiegeGateService.reset(world, center);
        WarCampService.reset(world, center);

        ServerPlayerEntity red = realPlayer(Side.RED);
        ServerPlayerEntity blue = realPlayer(Side.BLUE);
        if (red != null) preparePlayer(red, Side.RED, true);
        if (blue != null) preparePlayer(blue, Side.BLUE, true);
        TestNpcService.reset(world, center, match);
        TestNpcService.setInvulnerable(true);
        CastleGuardService.reset(world, center, match);
        startCountdownTicks = ROUND_START_COUNTDOWN_TICKS;
        CastleWarsMod.LOGGER.info("Castle Wars round reset; countdown started");
    }

    private static void clearPlayerPlacedBlocks() {
        if (world == null) return;
        for (BlockPos pos : playerPlacedBlocks) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        playerPlacedBlocks.clear();
    }

    private static ServerPlayerEntity realPlayer(Side side) {
        return match == null || world == null ? null : world.getServer().getPlayerList().getPlayer(match.player(side));
    }

    private static void setHumanInvulnerability(boolean invulnerable) {
        if (match == null || world == null) return;
        for (Side side : Side.values()) {
            ServerPlayerEntity player = realPlayer(side);
            if (player != null) player.setInvulnerable(invulnerable);
        }
    }

    private static void holdHumansAtSpawns() {
        if (match == null || world == null || center == null) return;
        for (Side side : Side.values()) {
            ServerPlayerEntity player = realPlayer(side);
            if (player != null) holdPlayerAtSpawn(player, side);
        }
    }

    private static void holdPlayerAtSpawn(ServerPlayerEntity player, Side side) {
        BlockPos spawn = ArenaCoordinates.spawn(center, side);
        float yaw = side == Side.RED ? 0.0F : 180.0F;
        player.teleportTo(world, spawn.getX() + 0.5D, spawn.getY() + 0.1D, spawn.getZ() + 0.5D, yaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static void preparePlayer(ServerPlayerEntity player, Side side, boolean invulnerable) {
        holdPlayerAtSpawn(player, side);
        player.setInvulnerable(invulnerable);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.removeAllEffects();
    }
}
