package com.tetr11s.settings;

import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.paint.Color;


// 일반 모드 색상 (테트리스 가이드라인 표준 색)
// 블록별 패턴 매핑
public class DefaultPalette {

    private static final String SYMBOL = "■";

    private final Map<Tetromino, Color> colors = new EnumMap<>(Tetromino.class);
    private final Map<Tetromino, TexturePattern> patterns = new EnumMap<>(Tetromino.class);
    private boolean colorblindMode;

    public DefaultPalette() {
        colors.put(Tetromino.I, Color.rgb(0, 240, 240));   // 하늘색
        colors.put(Tetromino.O, Color.rgb(240, 240, 0));   // 노랑
        colors.put(Tetromino.T, Color.rgb(160, 0, 240));   // 보라
        colors.put(Tetromino.S, Color.rgb(0, 240, 0));     // 초록
        colors.put(Tetromino.Z, Color.rgb(240, 0, 0));     // 빨강
        colors.put(Tetromino.J, Color.rgb(0, 0, 240));     // 파랑
        colors.put(Tetromino.L, Color.rgb(240, 160, 0));   // 주황

        patterns.put(Tetromino.I, TexturePattern.NONE);
        patterns.put(Tetromino.T, TexturePattern.DIAGONAL);
        patterns.put(Tetromino.O, TexturePattern.DOTS);
        patterns.put(Tetromino.S, TexturePattern.ZIGZAG);
        patterns.put(Tetromino.Z, TexturePattern.WAVE);
        patterns.put(Tetromino.J, TexturePattern.CROSSHATCH);
        patterns.put(Tetromino.L, TexturePattern.DIAGONAL_REVERSE);

        loadActivePalette();
    }

    // 블럭 종류별 색
    public Color getColor(Tetromino type) {
        return colors.get(type);
    }

    // 블럭 한 칸을 그릴 글자
    public String getSymbol(Tetromino type) {
        return SYMBOL;
    }

    public TexturePattern getPattern(Tetromino type) {
        return patterns.get(type);
    }

    public boolean isColorblindMode() {
        return colorblindMode;
    }

    public void setColorblindMode(boolean colorblindMode) {
        this.colorblindMode = colorblindMode;
        saveActivePalette();
    }

    public void setColor(Tetromino type, Color color) {
        colors.put(type, color);
        saveActivePalette();
    }

    public void applyPreset(ColorblindPreset preset) {
        colors.putAll(preset.colors());
        colorblindMode = true;
        saveActivePalette();
    }

    public void restoreDefaultColors() {
        colors.put(Tetromino.I, Color.rgb(0, 240, 240));
        colors.put(Tetromino.O, Color.rgb(240, 240, 0));
        colors.put(Tetromino.T, Color.rgb(160, 0, 240));
        colors.put(Tetromino.S, Color.rgb(0, 240, 0));
        colors.put(Tetromino.Z, Color.rgb(240, 0, 0));
        colors.put(Tetromino.J, Color.rgb(0, 0, 240));
        colors.put(Tetromino.L, Color.rgb(240, 160, 0));
        saveActivePalette();
    }

    public void savePreset(String name) {
        String normalized = normalizePresetName(name);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("프리셋 이름을 입력해야 합니다.");
        }
        Properties properties = PaletteStore.load();
        for (Tetromino type : Tetromino.values()) {
            properties.setProperty(PaletteStore.colorKey(normalized, type), toHex(colors.get(type)));
        }
        PaletteStore.save(properties);
    }

    public void loadPreset(String name) {
        String normalized = normalizePresetName(name);
        Properties properties = PaletteStore.load();
        for (Tetromino type : Tetromino.values()) {
            String value = properties.getProperty(PaletteStore.colorKey(normalized, type));
            if (value == null) {
                throw new IllegalArgumentException("저장된 프리셋이 없습니다: " + name);
            }
            colors.put(type, PaletteStore.parseColor(value, colors.get(type)));
        }
        colorblindMode = true;
        saveActivePalette();
    }

    private void loadActivePalette() {
        Properties properties = PaletteStore.load();
        colorblindMode = Boolean.parseBoolean(properties.getProperty("active.colorblind", "false"));
        for (Tetromino type : Tetromino.values()) {
            String value = properties.getProperty("active.color." + type.name());
            if (value != null) {
                colors.put(type, PaletteStore.parseColor(value, colors.get(type)));
            }
        }
    }

    private void saveActivePalette() {
        Properties properties = PaletteStore.load();
        properties.setProperty("active.colorblind", Boolean.toString(colorblindMode));
        for (Tetromino type : Tetromino.values()) {
            properties.setProperty("active.color." + type.name(), toHex(colors.get(type)));
        }
        PaletteStore.save(properties);
    }

    private static String normalizePresetName(String name) {
        return name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}