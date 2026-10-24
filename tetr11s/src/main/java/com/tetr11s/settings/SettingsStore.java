package com.tetr11s.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

/**
 * 색상, 키, 화면 크기, 프리셋과 스코어보드를 하나의 파일에 저장한다.
 * 스코어 관련은 아직 구현 안됨
 */
public final class SettingsStore {
    private static final String FILE_NAME = "save.properties";
    private static final String ACTIVE_KEY_PREFIX = "active.key.";
    private static final String KEY_PRESET_PREFIX = "preset.";
    private static final String COLOR_PRESET_PREFIX = "preset.";

    private SettingsStore() {
    }

    public static Path path() {
        return Paths.get(System.getProperty("user.home"), ".tetr11s", FILE_NAME);
    }

    // 파일을 Properties로 읽는 메서드
    public static Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(path())) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path())) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("설정 파일을 읽을 수 없습니다: " + e.getMessage());
        }
        return properties;
    }

    // Properties를 파일로 저장하는 메서드
    public static void save(Properties properties) {
        try {
            Files.createDirectories(path().getParent());
            try (OutputStream output = Files.newOutputStream(path())) {
                properties.store(output, "Tetr11s settings and presets");
            }
        } catch (IOException e) {
            throw new IllegalStateException("설정 파일을 저장할 수 없습니다: " + path(), e);
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(path());
        } catch (IOException e) {
            throw new IllegalStateException("설정 파일을 삭제할 수 없습니다: " + path(), e);
        }
    }

    // 게임 액션별로 저장된 키를 가져오는 메서드
    public static String activeKey(GameAction action) {
        return ACTIVE_KEY_PREFIX + action.name();
    }

    // 프리셋 이름과 게임 액션별로 저장된 키를 가져오는 메서드
    public static String keyPresetKey(String preset, GameAction action) {
        return KEY_PRESET_PREFIX + preset + ".key." + action.name();
    }

    // 프리셋 이름과 테트로미노별로 저장된 색상을 가져오는 메서드
    public static String colorKey(String preset, Tetromino type) {
        return COLOR_PRESET_PREFIX + preset + ".color." + type.name();
    }

    // 문자열을 KeyCode로 변환하는 메서드
    public static KeyCode parseKey(String value, KeyCode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return KeyCode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static Color parseColor(String value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Color.web(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    public static double loadScale() {
        String value = load().getProperty("active.scale", "1.0");
        try {
            double scale = Double.parseDouble(value);
            return isSupportedScale(scale) ? scale : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public static void saveScale(double scale) {
        Properties properties = load();
        properties.setProperty("active.scale", Double.toString(scale));
        save(properties);
    }

    private static boolean isSupportedScale(double scale) {
        return scale == 0.75 || scale == 1.0 || scale == 1.25;
    }
}
