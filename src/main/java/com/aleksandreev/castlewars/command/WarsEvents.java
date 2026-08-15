package com.aleksandreev.castlewars.command;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaBuildService;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.game.MatchManager;
import com.aleksandreev.castlewars.game.MatchState;
import com.aleksandreev.castlewars.game.Side;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = CastleWarsMod.MOD_ID)
public final class WarsEvents {
    private WarsEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("wars")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("arena")
                        .then(Commands.literal("build")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    try {
                                        if (MatchManager.isRunning()) {
                                            context.getSource().sendFailure(new StringTextComponent("Castle Wars: stop the current match before rebuilding the arena."));
                                            return 0;
                                        }
                                        if (!ArenaBuildService.start(player.getLevel(), player.blockPosition())) {
                                            context.getSource().sendFailure(new StringTextComponent("Castle Wars: an arena build is already running."));
                                            return 0;
                                        }
                                        context.getSource().sendSuccess(new StringTextComponent("Castle Wars: building two castles around this position..."), true);
                                        return 1;
                                    } catch (IOException ex) {
                                        CastleWarsMod.LOGGER.error("Failed to start arena build", ex);
                                        context.getSource().sendFailure(new StringTextComponent("Castle Wars: failed to load bundled schematic. Check server log."));
                                        return 0;
                                    }
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    String text = ArenaBuildService.isBuilding()
                                            ? "Castle Wars: arena build " + ArenaBuildService.progressPercent() + "%"
                                            : ArenaBuildService.hasArena()
                                                ? "Castle Wars: arena is ready."
                                                : "Castle Wars: no arena has been built in this server session.";
                                    context.getSource().sendSuccess(new StringTextComponent(text), false);
                                    return 1;
                                })))
                .then(Commands.literal("match")
                        .then(Commands.literal("start")
                                .then(Commands.argument("red", EntityArgument.player())
                                        .then(Commands.argument("blue", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayerEntity red = EntityArgument.getPlayer(context, "red");
                                                    ServerPlayerEntity blue = EntityArgument.getPlayer(context, "blue");
                                                    String error = MatchManager.start(red, blue);
                                                    if (error != null) {
                                                        context.getSource().sendFailure(new StringTextComponent("Castle Wars: " + error));
                                                        return 0;
                                                    }
                                                    context.getSource().sendSuccess(new StringTextComponent(
                                                            "Castle Wars started: RED " + red.getGameProfile().getName()
                                                                    + " vs BLUE " + blue.getGameProfile().getName()
                                                                    + ". First to 10 points."), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("score")
                                .executes(context -> {
                                    MatchState match = MatchManager.state();
                                    if (match == null) {
                                        context.getSource().sendFailure(new StringTextComponent("Castle Wars: no match exists."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(new StringTextComponent(
                                            "Castle Wars score: RED " + match.score(Side.RED) + " : " + match.score(Side.BLUE) + " BLUE"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    MatchManager.stop();
                                    context.getSource().sendSuccess(new StringTextComponent("Castle Wars match stopped."), true);
                                    return 1;
                                }))));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!MatchManager.isRunning() || !(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.getLevel() != MatchManager.world()) {
            return;
        }
        Side attacker = MatchManager.sideOf(player.getUUID());
        if (attacker == null) {
            return;
        }

        BlockPos center = MatchManager.center();
        Side objectiveOwner = ArenaCoordinates.objectiveOwnerAt(center, event.getPos());
        if (objectiveOwner != null) {
            event.setCanceled(true);
            if (objectiveOwner == attacker) {
                return;
            }
            if (MatchManager.destroyObjective(objectiveOwner, attacker)) {
                player.sendStatusMessage(new StringTextComponent(
                        "Enemy bed destroyed. Kill the defender to score a point!"), false);
            }
            return;
        }

        if (!MatchManager.canParticipantBreak(player.getUUID(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!MatchManager.isRunning()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) entity;
        if (player.getLevel() != MatchManager.world()) {
            return;
        }
        MatchManager.recordPlayerPlacedBlock(player.getUUID(), event.getPos());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!MatchManager.isRunning() || !(event.getEntityLiving() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity victim = (ServerPlayerEntity) event.getEntityLiving();
        if (victim.getLevel() != MatchManager.world() || MatchManager.sideOf(victim.getUUID()) == null) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        UUIDPair ids = new UUIDPair(victim, sourceEntity instanceof ServerPlayerEntity ? (ServerPlayerEntity) sourceEntity : null);
        MatchState.KillResult result = MatchManager.onPlayerKilled(ids.victimId, ids.killerId);
        if (result == MatchState.KillResult.POINT) {
            ServerPlayerEntity killer = ids.killer;
            if (killer != null) {
                killer.sendStatusMessage(new StringTextComponent("Castle captured: +1 point!"), false);
            }
        } else if (result == MatchState.KillResult.MATCH_WON) {
            ServerPlayerEntity killer = ids.killer;
            if (killer != null) {
                killer.sendStatusMessage(new StringTextComponent("You won Castle Wars!"), false);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            MatchManager.onPlayerRespawn((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ArenaBuildService.tick();
            MatchManager.tick();
        }
    }

    private static final class UUIDPair {
        private final java.util.UUID victimId;
        private final java.util.UUID killerId;
        private final ServerPlayerEntity killer;

        private UUIDPair(ServerPlayerEntity victim, ServerPlayerEntity killer) {
            this.victimId = victim.getUUID();
            this.killer = killer;
            this.killerId = killer == null ? null : killer.getUUID();
        }
    }
}
