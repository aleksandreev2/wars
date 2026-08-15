package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.server.ServerBossInfo;
import net.minecraft.world.server.ServerWorld;

/** Server-driven HUD and event feedback. No client packet registration is required for the v0.2 prototype. */
public final class MatchFeedbackService {
    private static ServerBossInfo bossBar;
    private static boolean redInvaded;
    private static boolean blueInvaded;
    private static int hudTicker;

    private MatchFeedbackService() {
    }

    public static void open(ServerWorld world, MatchState match) {
        close();
        bossBar = new ServerBossInfo(new StringTextComponent("CASTLE WARS"), BossInfo.Color.WHITE, BossInfo.Overlay.NOTCHED_10);
        bossBar.setPercent(0.01F);
        addHumanParticipant(world, match, Side.RED);
        addHumanParticipant(world, match, Side.BLUE);
        redInvaded = false;
        blueInvaded = false;
        hudTicker = 0;
        updateBossBar(match);
    }

    public static void close() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
        bossBar = null;
        redInvaded = false;
        blueInvaded = false;
        hudTicker = 0;
    }

    public static void tick(ServerWorld world, BlockPos center, MatchState match, int startCountdownTicks, int resetTicks) {
        if (world == null || center == null || match == null) return;

        if (bossBar != null) {
            addHumanParticipant(world, match, Side.RED);
            addHumanParticipant(world, match, Side.BLUE);
            updateBossBar(match);
        }

        detectInvasions(world, center, match);

        hudTicker++;
        if (hudTicker % 10 != 0) return;

        for (Side side : Side.values()) {
            ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
            if (player == null) continue;
            player.displayClientMessage(new StringTextComponent(actionBarFor(player, side, center, match, startCountdownTicks, resetTicks)), true);
        }
    }

    public static void onCoreDestroyed(ServerWorld world, MatchState match, Side owner, Side attacker) {
        broadcast(world, match, "§6§l" + owner + " CASTLE CORE DESTROYED §7— §fdefeat the defender to capture the castle!");
        ServerPlayerEntity defender = world.getServer().getPlayerList().getPlayer(match.player(owner));
        if (defender != null) {
            defender.displayClientMessage(new StringTextComponent("§c§lLAST STAND §7— §fYour Core is down. Survive!"), false);
            world.playSound(null, defender.blockPosition(), SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.4F, 0.65F);
        }
        ServerPlayerEntity attackingPlayer = world.getServer().getPlayerList().getPlayer(match.player(attacker));
        if (attackingPlayer != null) {
            world.playSound(null, attackingPlayer.blockPosition(), SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.MASTER, 1.0F, 0.9F);
        }
    }

    public static void onGateDamaged(ServerPlayerEntity attacker, Side owner, SiegeGateService.DamageResult result) {
        if (attacker == null || result == null || !result.damaged) return;
        if (result.destroyed) {
            attacker.displayClientMessage(new StringTextComponent("§6§l" + owner + " GATE BREACHED! §fThe central route is open."), false);
        } else {
            attacker.displayClientMessage(new StringTextComponent("§7Siege Gate §f" + result.health + "§7/§f" + SiegeGateService.MAX_HEALTH + " HP"), true);
        }
    }

    public static void onPoint(ServerWorld world, MatchState match, Side scoringSide, boolean won) {
        if (won) {
            broadcast(world, match, "§6§lCASTLE WARS WON — §f" + scoringSide + " §6reached 10 points!");
        } else {
            broadcast(world, match, "§6§lCASTLE CAPTURED — §f" + scoringSide + " §a+1 §7| §cRED "
                    + match.score(Side.RED) + " §7— §9" + match.score(Side.BLUE) + " BLUE");
        }
    }

    public static void onRoundStarted(ServerWorld world, MatchState match) {
        broadcast(world, match, "§e§lASSAULT STARTED §7— §fChoose the gate, ruins, or trench route.");
    }

    private static void updateBossBar(MatchState match) {
        if (bossBar == null) return;
        int red = match.score(Side.RED);
        int blue = match.score(Side.BLUE);
        int leader = Math.max(red, blue);
        bossBar.setPercent(Math.max(0.01F, Math.min(1.0F, leader / (float) CaptureRules.DEFAULT_WIN_SCORE)));
        bossBar.setName(new StringTextComponent(
                "§cRED " + red
                        + " §7[§fCore " + coreGlyph(match, Side.RED) + "§7]"
                        + " §7[§fG " + CastleGuardService.livingGuards(Side.RED) + "§7]"
                        + " §8— §e" + MatchManager.stageName()
                        + " §8— §7[§fG " + CastleGuardService.livingGuards(Side.BLUE) + "§7]"
                        + " §7[§fCore " + coreGlyph(match, Side.BLUE) + "§7]"
                        + " §9" + blue + " BLUE"));
    }

    private static String coreGlyph(MatchState match, Side side) {
        return match.isBedDestroyed(side) ? "§c✖" : "§a◆";
    }

    private static String actionBarFor(ServerPlayerEntity player, Side side, BlockPos center, MatchState match,
                                       int startCountdownTicks, int resetTicks) {
        if (resetTicks >= 0) {
            int seconds = Math.max(1, (resetTicks + 19) / 20);
            return "§6CASTLE CAPTURED §8| §fNext assault in §e" + seconds;
        }
        if (startCountdownTicks > 0) {
            int seconds = Math.max(1, (startCountdownTicks + 19) / 20);
            return "§e§lPREPARE §8| §fAssault begins in §e" + seconds;
        }
        if (match.isBedDestroyed(side)) {
            return "§c§lLAST STAND §8| §fYour Castle Core is destroyed — survive!";
        }
        if (match.isBedDestroyed(side.opponent())) {
            return "§6§lENEMY CORE DOWN §8| §fDefeat the defender to score +1";
        }

        Side capturing = WarCampService.capturingSide();
        if (capturing != null) {
            return "§eWAR CAMP §8| §f" + capturing + " capturing §e" + WarCampService.capturePercent() + "%";
        }

        if (ArenaCoordinates.insideCastle(center, side.opponent(), player.blockPosition())) {
            return "§cENEMY FORTRESS §8| §fGuards " + CastleGuardService.livingGuards(side.opponent())
                    + " §8| §fCore §a◆";
        }

        Side gateNear = nearestEnemyGate(player, side, center);
        if (gateNear != null && !SiegeGateService.isDestroyed(gateNear)) {
            return "§6SIEGE GATE §8| §f" + SiegeGateService.health(gateNear) + "/" + SiegeGateService.MAX_HEALTH
                    + " HP §8| §7Axes deal the most siege damage";
        }

        String camp = WarCampService.controller() == null ? "NEUTRAL" : WarCampService.controller().name();
        return "§eWar Camp: §f" + camp
                + " §8| §cRED Gate " + gateText(Side.RED)
                + " §8| §9BLUE Gate " + gateText(Side.BLUE);
    }

    private static String gateText(Side side) {
        return SiegeGateService.isDestroyed(side) ? "BREACHED" : String.valueOf(SiegeGateService.health(side));
    }

    private static Side nearestEnemyGate(ServerPlayerEntity player, Side side, BlockPos center) {
        Side enemy = side.opponent();
        return player.blockPosition().distSqr(ArenaCoordinates.gateCenter(center, enemy)) <= 14.0D * 14.0D ? enemy : null;
    }

    private static void detectInvasions(ServerWorld world, BlockPos center, MatchState match) {
        boolean redNow = opposingHumanInside(world, center, match, Side.RED);
        boolean blueNow = opposingHumanInside(world, center, match, Side.BLUE);
        if (redNow && !redInvaded) notifyInvasion(world, match, Side.RED);
        if (blueNow && !blueInvaded) notifyInvasion(world, match, Side.BLUE);
        redInvaded = redNow;
        blueInvaded = blueNow;
    }

    private static boolean opposingHumanInside(ServerWorld world, BlockPos center, MatchState match, Side castleOwner) {
        ServerPlayerEntity attacker = world.getServer().getPlayerList().getPlayer(match.player(castleOwner.opponent()));
        return attacker != null && attacker.isAlive() && ArenaCoordinates.insideCastle(center, castleOwner, attacker.blockPosition());
    }

    private static void notifyInvasion(ServerWorld world, MatchState match, Side castleOwner) {
        ServerPlayerEntity defender = world.getServer().getPlayerList().getPlayer(match.player(castleOwner));
        ServerPlayerEntity attacker = world.getServer().getPlayerList().getPlayer(match.player(castleOwner.opponent()));
        if (defender != null) {
            defender.displayClientMessage(new StringTextComponent("§c§l⚠ ENEMY IN YOUR CASTLE §7— §fDefend the Core!"), false);
            world.playSound(null, defender.blockPosition(), SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.5F, 0.7F);
        }
        if (attacker != null) {
            attacker.displayClientMessage(new StringTextComponent("§6FORTRESS BREACHED §7— §fFind and destroy the Castle Core."), false);
        }
    }

    private static void addHumanParticipant(ServerWorld world, MatchState match, Side side) {
        if (bossBar == null) return;
        ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
        if (player != null && !bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
    }

    private static void broadcast(ServerWorld world, MatchState match, String text) {
        if (world == null || match == null) return;
        for (Side side : Side.values()) {
            ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(match.player(side));
            if (player != null) {
                player.displayClientMessage(new StringTextComponent(text), false);
            }
        }
    }
}
