package com.aleksandreev.castlewars.arena;

import com.aleksandreev.castlewars.CastleWarsMod;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.IOException;
import java.io.InputStream;

/** Minimal Sponge Schematic v3 reader for the bundled arena asset. */
public final class SpongeV3Schematic {
    private final int width;
    private final int height;
    private final int length;
    private final BlockState[] palette;
    private final int[] blockPaletteIndices;

    private SpongeV3Schematic(int width, int height, int length, BlockState[] palette, int[] blockPaletteIndices) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.blockPaletteIndices = blockPaletteIndices;
    }

    public static SpongeV3Schematic load(InputStream input) throws IOException {
        CompoundNBT root = CompressedStreamTools.readCompressed(input);
        CompoundNBT schematic = root.contains("Schematic", 10) ? root.getCompound("Schematic") : root;

        int version = schematic.getInt("Version");
        if (version != 3) {
            throw new IOException("Expected Sponge Schematic v3, got version " + version);
        }

        int width = schematic.getInt("Width");
        int height = schematic.getInt("Height");
        int length = schematic.getInt("Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Invalid schematic dimensions: " + width + "x" + height + "x" + length);
        }

        CompoundNBT blocks = schematic.getCompound("Blocks");
        CompoundNBT paletteTag = blocks.getCompound("Palette");
        int maxPaletteIndex = -1;
        for (String key : paletteTag.getAllKeys()) {
            maxPaletteIndex = Math.max(maxPaletteIndex, paletteTag.getInt(key));
        }
        if (maxPaletteIndex < 0) {
            throw new IOException("Schematic palette is empty");
        }

        BlockState[] palette = new BlockState[maxPaletteIndex + 1];
        int fallbackCount = 0;
        for (String encodedState : paletteTag.getAllKeys()) {
            int index = paletteTag.getInt(encodedState);
            BlockState state = parseStateWithCompatibility(encodedState);
            if (state == null) {
                state = Blocks.AIR.defaultBlockState();
                fallbackCount++;
            }
            palette[index] = state;
        }
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = Blocks.AIR.defaultBlockState();
                fallbackCount++;
            }
        }

        int expectedBlocks;
        try {
            expectedBlocks = Math.multiplyExact(Math.multiplyExact(width, height), length);
        } catch (ArithmeticException ex) {
            throw new IOException("Schematic dimensions overflow", ex);
        }

        int[] blockPaletteIndices = decodeVarInts(blocks.getByteArray("Data"), expectedBlocks);
        if (fallbackCount > 0) {
            CastleWarsMod.LOGGER.warn("Schematic loaded with {} palette fallbacks for Minecraft 1.16.5", fallbackCount);
        }
        CastleWarsMod.LOGGER.info("Loaded bundled schematic {}x{}x{} with {} palette entries", width, height, length, palette.length);
        return new SpongeV3Schematic(width, height, length, palette, blockPaletteIndices);
    }

    public BlockState stateAt(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
            return Blocks.AIR.defaultBlockState();
        }
        int linearIndex = ((y * length) + z) * width + x;
        int paletteIndex = blockPaletteIndices[linearIndex];
        if (paletteIndex < 0 || paletteIndex >= palette.length) {
            return Blocks.AIR.defaultBlockState();
        }
        return palette[paletteIndex];
    }

    private static BlockState parseStateWithCompatibility(String encodedState) {
        String remapped = remapFor1165(encodedState);
        BlockState parsed = parseState(remapped);
        if (parsed != null) {
            return parsed;
        }

        int properties = remapped.indexOf('[');
        if (properties > 0) {
            parsed = parseState(remapped.substring(0, properties));
            if (parsed != null) {
                CastleWarsMod.LOGGER.warn("Dropped unsupported properties from schematic state {}", encodedState);
                return parsed;
            }
        }

        CastleWarsMod.LOGGER.warn("Unsupported schematic state {}, replacing with air", encodedState);
        return null;
    }

    private static BlockState parseState(String encodedState) {
        try {
            BlockStateParser parser = new BlockStateParser(new StringReader(encodedState), true);
            parser.parse(false);
            return parser.getState();
        } catch (CommandSyntaxException ex) {
            return null;
        }
    }

    private static String remapFor1165(String encodedState) {
        if (encodedState.equals("minecraft:light") || encodedState.startsWith("minecraft:light[")) {
            return "minecraft:air";
        }
        if (encodedState.equals("minecraft:piglin_head")) {
            return "minecraft:player_head";
        }
        if (encodedState.startsWith("minecraft:piglin_head[")) {
            return "minecraft:player_head" + encodedState.substring("minecraft:piglin_head".length());
        }
        return encodedState;
    }

    private static int[] decodeVarInts(byte[] encoded, int expectedValues) throws IOException {
        int[] result = new int[expectedValues];
        int resultIndex = 0;
        int byteIndex = 0;

        while (byteIndex < encoded.length && resultIndex < expectedValues) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (byteIndex >= encoded.length) {
                    throw new IOException("Truncated VarInt block data at value " + resultIndex);
                }
                int current = encoded[byteIndex++] & 0xFF;
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IOException("Invalid VarInt in schematic block data");
                }
            }
            result[resultIndex++] = value;
        }

        if (resultIndex != expectedValues) {
            throw new IOException("Expected " + expectedValues + " block states but decoded " + resultIndex);
        }
        if (byteIndex != encoded.length) {
            CastleWarsMod.LOGGER.debug("Schematic block data has {} trailing bytes", encoded.length - byteIndex);
        }
        return result;
    }
}
