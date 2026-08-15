package com.aleksandreev.castlewars.command;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaBuildService;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import com.aleksandreev.castlewars.game.CastleGuardService;
import com.aleksandreev.castlewars.game.MatchFeedbackService;
import com.aleksandreev.castlewars.game.MatchManager;
import com.aleksandreev.castlewars.game.MatchState;
import com.aleksandreev.castlewars.game.Side;
import com.aleksandreev.castlewars.game.SiegeGateService;
import com.aleksandreev.castlewars.game.TestNpcService;
import com.aleksandreev.castlewars.game.WarCampService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
                                        context.getSource().sendSuccess(new StringTextComponent(
                                                "Castle Wars v0.2: building castles + three-route siege battlefield..."), true);
                                        return 1;
                                    } catch (IOException ex) {
                                        CastleWarsMod.LOGGER.error("Failed to start arena build", ex);
                                        context.getSource().sendFailure(new StringTextComponent(
                                                "Castle Wars: failed to load bundled schematic. Check server log."));
                                        return 0;
                                    }
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    String text = ArenaBuildService.isBuilding()
                                            ? "Castle Wars: arena build " + ArenaBuildService.progressPercent() + "%"
                                            : ArenaBuildService.hasArena()
                                                ? "Castle Wars: v0.2 siege arena is ready."
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
                                                            "Castle Wars v0.2 started: RED " + red.getGameProfile().getName()
                                                                    + " vs BLUE " + blue.getGameProfile().getName()
                                                                    + ". Destroy the Castle Core, then defeat the defender. First to 10."), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("score").executes(context -> showScore(context.getSource())))
                        .then(Commands.literal("stop").executes(context -> {
                            MatchManager.stop();
                            context.getSource().sendSuccess(new StringTextComponent("Castle Wars match stopped."), true);
                            return 1;
                        })))
                .then(Commands.literal("test")
                        .then(Commands.literal("start")
                                .executes(context -> startSolo(context.getSource(), Side.RED))
                                .then(Commands.literal("red").executes(context -> startSolo(context.getSource(), Side.RED)))
                                .then(Commands.literal("blue").executes(context -> startSolo(context.getSource(), Side.BLUE))))
                        .then(Commands.literal("score").executes(context -> showScore(context.getSource())))
                        .then(Commands.literal("stop").executes(context -> {
                            MatchManager.stop();
                            context.getSource().sendSuccess(new StringTextComponent("Castle Wars solo-test stopped."), true);
                            return 1;
                        }))));
    }

    private static int startSolo(CommandSource source, Side playerSide) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(new StringTextComponent("Castle Wars: solo-test must be started by a player."));
            return 0;
        }

        String error = MatchManager.startSolo(player, playerSide);
        if (error != null) {
            source.sendFailure(new StringTextComponent("Castle Wars: " + error));
            return 0;
        }

        source.sendSuccess(new StringTextComponent(
                "Castle Wars v0.2 solo-test: you are " + playerSide
                        + ", " + TestNpcService.displayName() + " is " + playerSide.opponent()
                        + ". Central gate, two flank breaches, War Camp and Castle Core are active."), true);
        return 1;
    }

    private static int showScore(CommandSource source) {
        MatchState match = MatchManager.state();
        if (match == null) {
            source.sendFailure(new StringTextComponent("Castle Wars: no match exists."));
            return 0;
        }
        String npcText = TestNpcService.side() == null ? "" : " | NPC=" + TestNpcService.side();
        String camp = WarCampService.controller() == null ? "NEUTRAL" : WarCampService.controller().name();
        source.sendSuccess(new StringTextComponent(
                "Castle Wars " + MatchManager.stageName()
                        + " | RED " + match.score(Side.RED) + " : " + match.score(Side.BLUE) + " BLUE"
                        + " | Core R=" + (match.isBedDestroyed(Side.RED) ? "DOWN" : "UP")
                        + " B=" + (match.isBedDestroyed(Side.BLUE) ? "DOWN" : "UP")
                        + " | Guards " + CastleGuardService.livingGuards(Side.RED) + ":" + CastleGuardService.livingGuards(Side.BLUE)
                        + " | Gates " + SiegeGateService.health(Side.RED) + ":" + SiegeGateService.health(Side.BLUE)
                        + " | War Camp=" + camp + npcText), false);
        return 1;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!MatchManager.isRunning() || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.getLevel() != MatchManager.world()) return;
        Side attacker = MatchManager.sideOf(player.getUUID());
        if (attacker == null) return;

        BlockPos center = MatchManager.center();
        Side objectiveOwner = ArenaCoordinates.objectiveOwnerAt(center, event.getPos());
        if (objectiveOwner != null) {
            event.setCanceled(true);
            if (!MatchManager.isCombatActive() || objectiveOwner == attacker) return;
            if (MatchManager.destroyObjective(objectiveOwner, attacker)) {
                player.displayClientMessage(new StringTextComponent(
                        "§6Castle Core destroyed. §fDefeat the defender to capture the castle!"), false);
            }
            return;
        }

        Side gateOwner = SiegeGateService.ownerAt(center, event.getPos());
        if (gateOwner != null) {
            event.setCanceled(true);
            if (!MatchManager.isCombatActive() || gateOwner == attacker || SiegeGateService.isDestroyed(gateOwner)) return;
            int damage = siegeDamage(player);
            SiegeGateService.DamageResult result = SiegeGateService.damage(
                    MatchManager.world(), center, gateOwner, attacker, damage);
            MatchFeedbackService.onGateDamaged(player, gateOwner, result);
            return;
        }

        if (!MatchManager.canParticipantBreak(player.getUUID(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static int siegeDamage(ServerPlayerEntity player) {
        if (player.getMainHandItem().getItem() instanceof AxeItem) return 35;
        if (player.getMainHandItem().getItem() instanceof PickaxeItem) return 22;
        if (player.getMainHandItem().getItem() instanceof SwordItem) return 8;
        return 12;
    }

    @SubscribeEvent
    public static void onObjectiveInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!MatchManager.isRunning() || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.getLevel() != MatchManager.world() || MatchManager.sideOf(player.getUUID()) == null) return;
        if (ArenaCoordinates.objectiveOwnerAt(MatchManager.center(), event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!MatchManager.isRunning()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) entity;
        if (player.getLevel() != MatchManager.world()) return;
        MatchManager.recordPlayerPlacedBlock(player.getUUID(), event.getPos());
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!MatchManager.isRunning()) return;

        Entity attacker = event.getSource().getEntity();
        Entity victim = event.getEntityLiving();
        Side attackingGuard = CastleGuardService.sideOf(attacker);
        Side victimGuard = CastleGuardService.sideOf(victim);
        Side attackingNpc = TestNpcService.sideOf(attacker);
        Side victimNpc = TestNpcService.sideOf(victim);
        Side attackingPlayer = attacker instanceof ServerPlayerEntity ? MatchManager.sideOf(attacker.getUUID()) : null;
        Side victimPlayer = victim instanceof ServerPlayerEntity ? MatchManager.sideOf(victim.getUUID()) : null;

        if (!MatchManager.isCombatActive()) {
            if (attackingGuard != null || victimGuard != null || attackingNpc != null || victimNpc != null
                    || attackingPlayer != null || victimPlayer != null) {
                event.setCanceled(true);
            }
            return;
        }

        // Guards may damage only the opposing participant (real player or solo-test NPC).
        if (attackingGuard != null
                && victimPlayer != attackingGuard.opponent()
                && victimNpc != attackingGuard.opponent()) {
            event.setCanceled(true);
            return;
        }

        // Solo NPC attacks only the opposing real participant.
        if (attackingNpc != null && victimPlayer != attackingNpc.opponent()) {
            event.setCanceled(true);
            return;
        }

        // Friendly participants cannot damage their own guards/NPC.
        if (victimGuard != null && attackingPlayer == victimGuard) {
            event.setCanceled(true);
            return;
        }
        if (victimNpc != null && attackingPlayer == victimNpc) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!MatchManager.isRunning() || !MatchManager.isCombatActive()) return;

        Entity defeated = event.getEntityLiving();
        Side npcSide = TestNpcService.sideOf(defeated);
        if (npcSide != null) {
            Entity sourceEntity = event.getSource().getEntity();
            Side defeatingSide = sourceEntity instanceof ServerPlayerEntity
                    ? MatchManager.sideOf(sourceEntity.getUUID()) : CastleGuardService.sideOf(sourceEntity);
            MatchState.KillResult result = MatchManager.onParticipantDefeated(defeated.getUUID(), defeatingSide);
            event.setCanceled(true);
            if (MatchManager.world() != null && MatchManager.center() != null) {
                TestNpcService.onDefeated(MatchManager.world(), MatchManager.center(), result);
            }
            return;
        }

        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity victim = (ServerPlayerEntity) event.getEntityLiving();
        if (victim.getLevel() != MatchManager.world() || MatchManager.sideOf(victim.getUUID()) == null) return;

        Entity sourceEntity = event.getSource().getEntity();
        Side defeatingSide = sourceEntity instanceof ServerPlayerEntity
                ? MatchManager.sideOf(sourceEntity.getUUID()) : CastleGuardService.sideOf(sourceEntity);
        if (defeatingSide == null) defeatingSide = TestNpcService.sideOf(sourceEntity);
        MatchManager.onParticipantDefeated(victim.getUUID(), defeatingSide);
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
}
