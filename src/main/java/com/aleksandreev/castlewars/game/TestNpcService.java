package com.aleksandreev.castlewars.game;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.aleksandreev.castlewars.arena.ArenaCoordinates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.monster.VindicatorEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

import java.util.UUID;

/** Solo-test opponent used when only one real Minecraft client is available. */
public final class TestNpcService {
    private static VindicatorEntity npc;
    private static Side npcSide;

    private TestNpcService() {
    }

    public static UUID spawn(ServerWorld world, BlockPos center, Side side) {
        clear();

        VindicatorEntity created = new VindicatorEntity(EntityType.VINDICATOR, world);
        created.setPersistenceRequired();
        created.setCustomName(new StringTextComponent("Castle Bot [" + side.name() + "]"));
        created.setCustomNameVisible(true);
        created.setHealth(created.getMaxHealth());
        created.setInvulnerable(false);
        teleportToSpawn(created, center, side);
        if (!world.addFreshEntity(created)) {
            throw new IllegalStateException("Could not add Castle Wars test NPC to the world");
        }

        npc = created;
        npcSide = side;
        CastleWarsMod.LOGGER.info("Spawned solo-test NPC {} on {}", created.getUUID(), side);
        return created.getUUID();
    }

    public static void reset(ServerWorld world, BlockPos center, MatchState match) {
        if (npc == null || npcSide == null || match == null || match.isFinished()) return;
        npc.setInvulnerable(false);
        npc.setHealth(npc.getMaxHealth());
        npc.setTarget(null);
        npc.getNavigation().stop();
        teleportToSpawn(npc, center, npcSide);
    }

    public static void tick(ServerWorld world, BlockPos center, MatchState match) {
        if (npc == null || npcSide == null || world == null || center == null || match == null || match.isFinished()) return;
        if (!npc.isAlive()) return;

        if (!ArenaCoordinates.insideCastle(center, npcSide, npc.blockPosition())) {
            reset(world, center, match);
            return;
        }

        UUID enemyId = match.player(npcSide.opponent());
        ServerPlayerEntity enemy = world.getServer().getPlayerList().getPlayer(enemyId);
        if (enemy != null && enemy.isAlive() && ArenaCoordinates.insideCastle(center, npcSide, enemy.blockPosition())) {
            npc.setTarget(enemy);
        } else {
            npc.setTarget(null);
            // Light defensive patrol so the bot is not a motionless mannequin before the player reaches it.
            BlockPos spawn = ArenaCoordinates.spawn(center, npcSide);
            BlockPos patrol = spawn.offset((world.getGameTime() / 100L & 1L) == 0L ? 3 : -3, 0, 0);
            if (npc.blockPosition().distSqr(patrol) > 2.0D) {
                npc.getNavigation().moveTo(patrol.getX() + 0.5D, patrol.getY(), patrol.getZ() + 0.5D, 0.65D);
            }
        }
    }

    /** Called from LivingDeathEvent; the event itself is cancelled so this NPC can keep a stable match UUID. */
    public static void onDefeated(ServerWorld world, BlockPos center, MatchState.KillResult result) {
        if (npc == null || npcSide == null) return;
        npc.setHealth(npc.getMaxHealth());
        npc.setTarget(null);
        npc.getNavigation().stop();

        if (result == MatchState.KillResult.MATCH_WON) {
            clear();
            return;
        }

        teleportToSpawn(npc, center, npcSide);
        npc.setInvulnerable(result == MatchState.KillResult.POINT);
    }

    public static void holdAtSpawn(BlockPos center) {
        if (npc == null || npcSide == null || center == null) return;
        npc.setTarget(null);
        npc.getNavigation().stop();
        teleportToSpawn(npc, center, npcSide);
    }

    public static void setInvulnerable(boolean invulnerable) {
        if (npc != null) npc.setInvulnerable(invulnerable);
    }

    public static Side sideOf(Entity entity) {
        if (entity == null || npc == null || npcSide == null) return null;
        return npc.getUUID().equals(entity.getUUID()) ? npcSide : null;
    }

    public static boolean isNpc(Entity entity) {
        return sideOf(entity) != null;
    }

    public static boolean isNpcParticipant(UUID id) {
        return id != null && npc != null && npc.getUUID().equals(id);
    }

    public static String displayName() {
        return npc == null ? "none" : npc.getName().getString();
    }

    public static Side side() {
        return npcSide;
    }

    public static void clear() {
        if (npc != null && npc.isAlive()) npc.remove();
        npc = null;
        npcSide = null;
    }

    private static void teleportToSpawn(VindicatorEntity entity, BlockPos center, Side side) {
        BlockPos spawn = ArenaCoordinates.spawn(center, side);
        float yaw = side == Side.RED ? 0.0F : 180.0F;
        entity.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, yaw, 0.0F);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }
}
