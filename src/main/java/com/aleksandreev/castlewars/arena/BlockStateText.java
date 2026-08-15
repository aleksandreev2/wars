package com.aleksandreev.castlewars.arena;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.command.arguments.BlockStateParser;

public final class BlockStateText {
    private BlockStateText() {
    }

    public static BlockState parseRequired(String encodedState) {
        try {
            BlockStateParser parser = new BlockStateParser(new StringReader(encodedState), true);
            parser.parse(false);
            return parser.getState();
        } catch (CommandSyntaxException ex) {
            throw new IllegalStateException("Invalid built-in block state: " + encodedState, ex);
        }
    }
}
