package com.tetr11s.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.paint.Color;

class DefaultPaletteTest {

    @TempDir
    Path tempHome;

    private String originalHome;

    @BeforeEach
    void useTempHome() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
    }

    @Test
    void 기본_색상은_블럭마다_모두_다르다() {
        DefaultPalette palette = new DefaultPalette();
        Set<Color> colors = new HashSet<>();
        for (Tetromino type : Tetromino.values()) {
            colors.add(palette.getColor(type));
        }
        assertEquals(Tetromino.values().length, colors.size());
    }

    @Test
    void 블럭마다_무늬가_모두_다르다() {
        DefaultPalette palette = new DefaultPalette();
        Set<TexturePattern> patterns = EnumSet.noneOf(TexturePattern.class);
        for (Tetromino type : Tetromino.values()) {
            patterns.add(palette.getPattern(type));
            assertEquals("■", palette.getSymbol(type));
        }
        assertEquals(Tetromino.values().length, patterns.size());
    }

    @Test
    void 색을_하나_바꾸면_커스텀_모드가_켜지고_다시_실행해도_유지된다() {
        DefaultPalette palette = new DefaultPalette();
        palette.setColor(Tetromino.T, Color.web("#123456"));

        assertTrue(palette.isColorblindMode());
        DefaultPalette reloaded = new DefaultPalette();
        assertTrue(reloaded.isColorblindMode());
        assertEquals(Color.web("#123456"), reloaded.getColor(Tetromino.T));
    }

    @Test
    void 기본_색상_복원은_바꾼_색을_되돌린다() {
        DefaultPalette palette = new DefaultPalette();
        Color original = palette.getColor(Tetromino.Z);
        palette.setColor(Tetromino.Z, Color.WHITE);

        palette.restoreDefaultColors();

        assertEquals(original, palette.getColor(Tetromino.Z));
    }

    @Test
    void 전체_초기화는_색과_커스텀_모드를_모두_되돌린다() {
        DefaultPalette palette = new DefaultPalette();
        Color original = palette.getColor(Tetromino.S);
        palette.applyPreset(ColorblindPreset.TRITAN);

        palette.resetAll();

        assertFalse(palette.isColorblindMode());
        assertEquals(original, palette.getColor(Tetromino.S));
        assertFalse(new DefaultPalette().isColorblindMode());
    }

    @Test
    void 색상_프리셋을_저장했다가_불러온다() {
        DefaultPalette palette = new DefaultPalette();
        palette.applyPreset(ColorblindPreset.PROTAN);
        palette.savePreset("내 색");

        palette.resetAll();
        palette.loadPreset("내 색");

        assertTrue(palette.isColorblindMode());
        for (Tetromino type : Tetromino.values()) {
            assertEquals(SettingsStore.toHex(ColorblindPreset.PROTAN.colors().get(type)),
                    SettingsStore.toHex(palette.getColor(type)));
        }
    }

    @Test
    void 이름이_비었거나_없는_색상_프리셋은_예외() {
        DefaultPalette palette = new DefaultPalette();
        assertThrows(IllegalArgumentException.class, () -> palette.savePreset(" "));
        assertThrows(IllegalArgumentException.class, () -> palette.loadPreset("없음"));
    }

    @Test
    void 색맹_프리셋을_적용하면_기본색과_달라진다() {
        DefaultPalette palette = new DefaultPalette();
        Color before = palette.getColor(Tetromino.Z);
        palette.applyPreset(ColorblindPreset.RED_GREEN);
        assertNotEquals(before, palette.getColor(Tetromino.Z));
    }
}
