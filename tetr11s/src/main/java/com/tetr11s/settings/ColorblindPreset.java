package com.tetr11s.settings;

import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.paint.Color;

public enum ColorblindPreset {
    RED_GREEN("적록색맹"),
    PROTAN("적색약"),
    DEUTAN("녹색약");

    private final String displayName;

    ColorblindPreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Map<Tetromino, Color> colors() {
        Map<Tetromino, Color> colors = new EnumMap<>(Tetromino.class);
        switch (this) {
            case RED_GREEN -> {
                colors.put(Tetromino.I, Color.web("#56B4E9"));
                colors.put(Tetromino.O, Color.web("#E69F00"));
                colors.put(Tetromino.T, Color.web("#CC79A7"));
                colors.put(Tetromino.S, Color.web("#009E73"));
                colors.put(Tetromino.Z, Color.web("#D55E00"));
                colors.put(Tetromino.J, Color.web("#0072B2"));
                colors.put(Tetromino.L, Color.web("#F0E442"));
            }
            case PROTAN -> {
                colors.put(Tetromino.I, Color.web("#4FADE3"));
                colors.put(Tetromino.O, Color.web("#F2C14E"));
                colors.put(Tetromino.T, Color.web("#B784C8"));
                colors.put(Tetromino.S, Color.web("#5AAE61"));
                colors.put(Tetromino.Z, Color.web("#C77C3D"));
                colors.put(Tetromino.J, Color.web("#2C6EAA"));
                colors.put(Tetromino.L, Color.web("#E9D66B"));
            }
            case DEUTAN -> {
                colors.put(Tetromino.I, Color.web("#56B4E9"));
                colors.put(Tetromino.O, Color.web("#E5B84B"));
                colors.put(Tetromino.T, Color.web("#B47CC7"));
                colors.put(Tetromino.S, Color.web("#6AA84F"));
                colors.put(Tetromino.Z, Color.web("#C86B45"));
                colors.put(Tetromino.J, Color.web("#3C78A8"));
                colors.put(Tetromino.L, Color.web("#D9C34A"));
            }
        }
        return colors;
    }
}
