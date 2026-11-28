package com.tetr11s.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

class SettingsStoreTest {

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
    void 설정_파일은_사용자_홈의_tetr11s_폴더에_저장된다() {
        assertEquals(tempHome.resolve(".tetr11s").resolve("save.properties"), SettingsStore.path());

        Properties properties = new Properties();
        properties.setProperty("a", "b");
        SettingsStore.save(properties);

        assertTrue(Files.exists(SettingsStore.path()));
        assertEquals("b", SettingsStore.load().getProperty("a"));

        SettingsStore.delete();
        assertFalse(Files.exists(SettingsStore.path()));
        SettingsStore.delete(); // 없는 파일을 지워도 예외 없음
    }

    @Test
    void 대전_키_이름은_플레이어마다_다르고_1_2번만_허용한다() {
        assertEquals("active.battle.p1.key.HOLD", SettingsStore.battleActiveKey(1, GameAction.HOLD));
        assertEquals("active.battle.p2.key.HOLD", SettingsStore.battleActiveKey(2, GameAction.HOLD));
        assertThrows(IllegalArgumentException.class, () -> SettingsStore.battleActiveKey(0, GameAction.HOLD));
        assertThrows(IllegalArgumentException.class, () -> SettingsStore.battleActiveKey(3, GameAction.HOLD));
    }

    @Test
    void 저장_키_이름은_서로_겹치지_않는다() {
        Set<String> names = new HashSet<>();
        for (GameAction action : GameAction.values()) {
            names.add(SettingsStore.activeKey(action));
            names.add(SettingsStore.keyPresetKey("p", action));
        }
        for (Tetromino type : Tetromino.values()) {
            names.add(SettingsStore.colorKey("p", type));
        }
        assertEquals(GameAction.values().length * 2 + Tetromino.values().length, names.size());
    }

    @Test
    void 잘못된_키_이름은_기본값으로_읽는다() {
        assertEquals(KeyCode.SPACE, SettingsStore.parseKey("SPACE", KeyCode.A));
        assertEquals(KeyCode.A, SettingsStore.parseKey("없는키", KeyCode.A));
        assertEquals(KeyCode.A, SettingsStore.parseKey(null, KeyCode.A));
    }

    @Test
    void 잘못된_색은_기본값으로_읽고_색은_16진수로_저장한다() {
        assertEquals(Color.web("#FF8000"), SettingsStore.parseColor("#FF8000", Color.BLACK));
        assertEquals(Color.BLACK, SettingsStore.parseColor("색아님", Color.BLACK));
        assertEquals(Color.BLACK, SettingsStore.parseColor(null, Color.BLACK));
        assertEquals("#FF8000", SettingsStore.toHex(Color.rgb(255, 128, 0)));
    }

    @Test
    void 화면_배율은_정해진_3가지만_허용한다() {
        assertEquals(1.0, SettingsStore.loadScale());

        SettingsStore.saveScale(1.25);
        assertEquals(1.25, SettingsStore.loadScale());

        SettingsStore.saveScale(3.0);
        assertEquals(1.0, SettingsStore.loadScale());

        Properties properties = SettingsStore.load();
        properties.setProperty("active.scale", "크게");
        SettingsStore.save(properties);
        assertEquals(1.0, SettingsStore.loadScale());
    }

    @Test
    void 해상도_프리셋이_없거나_손상되면_예외_범위밖이면_기본배율() {
        assertThrows(IllegalArgumentException.class, () -> SettingsStore.loadResolutionPreset("없음"));

        SettingsStore.saveResolutionPreset("big", 2.0);
        assertEquals(1.0, SettingsStore.loadResolutionPreset("big"));

        Properties properties = SettingsStore.load();
        properties.setProperty("preset.resolution.broken", "abc");
        SettingsStore.save(properties);
        assertThrows(IllegalArgumentException.class, () -> SettingsStore.loadResolutionPreset("broken"));
    }

    @Test
    void 아이템_버그판_모드는_기본으로_꺼져있고_저장된다() {
        assertFalse(SettingsStore.loadItemBugMode());
        SettingsStore.saveItemBugMode(true);
        assertTrue(SettingsStore.loadItemBugMode());
        SettingsStore.saveItemBugMode(false);
        assertFalse(SettingsStore.loadItemBugMode());
    }
}
