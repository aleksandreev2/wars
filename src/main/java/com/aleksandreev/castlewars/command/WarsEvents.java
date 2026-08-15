package com.aleksandreev.castlewars.command;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaBuildService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
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
                                            : "Castle Wars: no arena build is running.";
                                    context.getSource().sendSuccess(new StringTextComponent(text), false);
                                    return 1;
                                }))));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ArenaBuildService.tick();
        }
    }
}
