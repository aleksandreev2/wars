package com.aleksandreev.castlewars.game;

public enum Side {
    RED,
    BLUE;

    public Side opponent() {
        return this == RED ? BLUE : RED;
    }
}
