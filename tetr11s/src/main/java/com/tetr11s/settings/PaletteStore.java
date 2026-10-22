package com.tetr11s.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.paint.Color;

// 유저 홈에 프리셋을 저장함 

final class PaletteStore {
    private static final String FILE_NAME = "colorblind-presets.properties";

    private PaletteStore() {
    }

    static Path path() {
        return Paths.get(System.getProperty("user.home"), ".tetr11s", FILE_NAME);
    }

    static Properties load() {
        Properties properties = new Properties();
        Path path = path();
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("색상 프리셋을 읽을 수 없습니다: " + e.getMessage());
        }
        return properties;
    }

    static void save(Properties properties) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Tetr11s colorblind palette presets");
            }
        } catch (IOException e) {
            throw new IllegalStateException("색상 프리셋을 저장할 수 없습니다: " + path, e);
        }
    }

    static String colorKey(String presetName, Tetromino type) {
        return "preset." + presetName + ".color." + type.name();
    }

    static Color parseColor(String value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Color.web(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
