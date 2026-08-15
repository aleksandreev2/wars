package com.aleksandreev.castlewars.arena;

import net.minecraft.util.math.BlockPos;

/** PvP-focused crop and placement geometry for the supplied schematic. */
public final class ArenaLayout {
    public static final int CROP_MIN_X = 20;
    public static final int CROP_MIN_Y = 0;
    public static final int CROP_MIN_Z = 14;

    public static final int CROP_WIDTH = 72;
    public static final int CROP_HEIGHT = 57;
    public static final int CROP_LENGTH = 108;

    public static final int HALF_CENTER_GAP = 29;

    private ArenaLayout() {
    }

    public static BlockPos redAnchor(BlockPos center) {
        return center.offset(-CROP_WIDTH / 2, 0, -HALF_CENTER_GAP - CROP_LENGTH);
    }

    public static BlockPos blueAnchor(BlockPos center) {
        return center.offset(-CROP_WIDTH / 2, 0, HALF_CENTER_GAP + 1);
    }
}
