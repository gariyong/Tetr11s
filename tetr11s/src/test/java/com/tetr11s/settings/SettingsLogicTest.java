package com.tetr11s.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

class SettingsLogicTest {

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
    void 색맹_프리셋_적용과_커스텀_체크박스_토글이_즉시_색상에_반영된다() {
        DefaultPalette palette = new DefaultPalette();
        Color defaultI = palette.getColor(Tetromino.I);
        assertFalse(palette.isColorblindMode());

        palette.applyPreset(ColorblindPreset.RED_GREEN);
        assertTrue(palette.isColorblindMode());
        Color redGreenI = ColorblindPreset.RED_GREEN.colors().get(Tetromino.I);
        assertEquals(redGreenI, palette.getColor(Tetromino.I));

        // 커스텀 사용을 끄면 기본 표준 색상이 반환되고, 다시 켜면 적용했던 프리셋 색상이 복원된다
        palette.setColorblindMode(false);
        assertEquals(defaultI, palette.getColor(Tetromino.I));

        palette.setColorblindMode(true);
        assertEquals(redGreenI, palette.getColor(Tetromino.I));
    }

    @Test
    void 색맹_프리셋과_버그판_설정이_서로_덮어쓰지_않고_공존한다() {
        DefaultPalette palette = new DefaultPalette();
        palette.applyPreset(ColorblindPreset.DEUTAN);
        SettingsStore.saveItemBugMode(true);

        DefaultPalette reloaded = new DefaultPalette();
        assertTrue(reloaded.isColorblindMode());
        assertEquals(ColorblindPreset.DEUTAN.colors().get(Tetromino.T), reloaded.getColor(Tetromino.T));
        assertTrue(SettingsStore.loadItemBugMode());
    }

    @Test
    void 조작키를_중복키로_변경하면_기존_동작과_서로_맞바뀌고_저장된다() {
        KeyBindings bindings = new KeyBindings();
        assertEquals(KeyCode.LEFT, bindings.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyCode.RIGHT, bindings.keyFor(GameAction.MOVE_RIGHT));

        bindings.bind(GameAction.MOVE_LEFT, KeyCode.RIGHT);

        assertEquals(KeyCode.RIGHT, bindings.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyCode.LEFT, bindings.keyFor(GameAction.MOVE_RIGHT));

        KeyBindings reloaded = new KeyBindings();
        assertEquals(KeyCode.RIGHT, reloaded.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyCode.LEFT, reloaded.keyFor(GameAction.MOVE_RIGHT));
    }

    @Test
    void 싱글키_대전키_난이도_해상도_팔레트가_한_파일에_서로_안덮어쓰고_공존한다() {
        DefaultPalette palette = new DefaultPalette();
        KeyBindings single = new KeyBindings();
        com.tetr11s.game.controller.BattleKeyBindings battle = new com.tetr11s.game.controller.BattleKeyBindings();

        palette.applyPreset(ColorblindPreset.TRITAN);
        single.bind(GameAction.HARD_DROP, KeyCode.F);
        battle.bind(1, GameAction.HARD_DROP, KeyCode.G);
        battle.bind(2, GameAction.HARD_DROP, KeyCode.SLASH);
        SettingsStore.saveDifficulty(com.tetr11s.game.Difficulty.HARD);
        SettingsStore.saveScale(1.25);
        SettingsStore.saveItemBugMode(true);

        assertTrue(new DefaultPalette().isColorblindMode());
        assertEquals(KeyCode.F, new KeyBindings().keyFor(GameAction.HARD_DROP));
        com.tetr11s.game.controller.BattleKeyBindings reloadedBattle = new com.tetr11s.game.controller.BattleKeyBindings();
        assertEquals(KeyCode.G, reloadedBattle.keyFor(1, GameAction.HARD_DROP));
        assertEquals(KeyCode.SLASH, reloadedBattle.keyFor(2, GameAction.HARD_DROP));
        assertEquals(com.tetr11s.game.Difficulty.HARD, SettingsStore.loadDifficulty());
        assertEquals(1.25, SettingsStore.loadScale());
        assertTrue(SettingsStore.loadItemBugMode());
    }

    @Test
    void 손상된_설정파일이어도_예외없이_기본값으로_복구한다() throws Exception {
        java.nio.file.Files.createDirectories(SettingsStore.path().getParent());
        java.nio.file.Files.writeString(SettingsStore.path(), "active.difficulty=\\u12\n");

        assertEquals(com.tetr11s.game.Difficulty.NORMAL, SettingsStore.loadDifficulty());
        assertEquals(1.0, SettingsStore.loadScale());
    }
}
