package com.tetr11s.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.controller.GameAction;

import javafx.scene.input.KeyCode;

class KeyBindingsTest {

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
    void 기본키는_모든_동작에_서로_다른_키가_지정된다() {
        KeyBindings bindings = new KeyBindings();

        Set<KeyCode> used = new HashSet<>();
        for (GameAction action : GameAction.values()) {
            KeyCode key = bindings.keyFor(action);
            assertEquals(action, bindings.actionFor(key));
            used.add(key);
        }
        assertEquals(GameAction.values().length, used.size());
        assertEquals(KeyCode.P, bindings.keyFor(GameAction.PAUSE));
        assertEquals(KeyCode.ESCAPE, bindings.keyFor(GameAction.QUIT));
    }

    @Test
    void 게임에_쓰지_않는_키는_동작이_없다() {
        assertNull(new KeyBindings().actionFor(KeyCode.F12));
    }

    @Test
    void 바꾼_키는_다시_실행해도_유지된다() {
        new KeyBindings().bind(GameAction.HARD_DROP, KeyCode.X);

        KeyBindings reloaded = new KeyBindings();
        assertEquals(KeyCode.X, reloaded.keyFor(GameAction.HARD_DROP));
        assertEquals(GameAction.HARD_DROP, reloaded.actionFor(KeyCode.X));
    }

    @Test
    void 기본값으로_되돌리면_바꾼_키가_사라진다() {
        KeyBindings bindings = new KeyBindings();
        bindings.bind(GameAction.MOVE_LEFT, KeyCode.A);

        bindings.resetToDefault();

        assertEquals(KeyCode.LEFT, bindings.keyFor(GameAction.MOVE_LEFT));
        assertNull(bindings.actionFor(KeyCode.A));
    }

    @Test
    void 프리셋을_저장했다가_불러오면_그때의_키로_돌아간다() {
        KeyBindings bindings = new KeyBindings();
        bindings.bind(GameAction.ROTATE_CW, KeyCode.Z);
        bindings.savePreset("my keys");

        bindings.resetToDefault();
        bindings.loadPreset("my keys");

        assertEquals(KeyCode.Z, bindings.keyFor(GameAction.ROTATE_CW));
        // 불러온 키는 지금 쓰는 키로도 저장된다
        assertEquals(KeyCode.Z, new KeyBindings().keyFor(GameAction.ROTATE_CW));
    }

    @Test
    void 이름이_비어있는_프리셋은_저장할_수_없다() {
        KeyBindings bindings = new KeyBindings();
        assertThrows(IllegalArgumentException.class, () -> bindings.savePreset("   "));
        assertThrows(IllegalArgumentException.class, () -> bindings.savePreset(null));
    }

    @Test
    void 없는_프리셋을_불러오면_예외() {
        KeyBindings bindings = new KeyBindings();
        assertThrows(IllegalArgumentException.class, () -> bindings.loadPreset("없는이름"));
    }

    @Test
    void 홀드가_없던_예전_프리셋은_홀드만_지금_키를_유지한다() {
        Properties properties = SettingsStore.load();
        for (GameAction action : GameAction.values()) {
            if (action != GameAction.HOLD) {
                properties.setProperty(SettingsStore.keyPresetKey("old", action), KeyCode.NUMPAD0.name());
            }
        }
        properties.setProperty(SettingsStore.keyPresetKey("old", GameAction.MOVE_LEFT), KeyCode.J.name());
        SettingsStore.save(properties);

        KeyBindings bindings = new KeyBindings();
        bindings.bind(GameAction.HOLD, KeyCode.V);
        bindings.loadPreset("old");

        assertEquals(KeyCode.J, bindings.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyCode.V, bindings.keyFor(GameAction.HOLD));
    }

    @Test
    void 프리셋을_모두_지우면_키_프리셋만_사라진다() {
        KeyBindings bindings = new KeyBindings();
        bindings.savePreset("a");
        new DefaultPalette().savePreset("colors");

        bindings.deletePresets();

        assertThrows(IllegalArgumentException.class, () -> bindings.loadPreset("a"));
        new DefaultPalette().loadPreset("colors"); // 색상 프리셋은 남아 있다
    }
}
