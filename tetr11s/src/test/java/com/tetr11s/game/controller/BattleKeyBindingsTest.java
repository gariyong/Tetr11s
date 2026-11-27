package com.tetr11s.game.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.input.KeyCode;

class BattleKeyBindingsTest {

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
    void p1AndP2KeysDoNotOverlapAndMapBackCorrectly() {
        BattleKeyBindings bindings = new BattleKeyBindings();
        Set<KeyCode> usedKeys = new HashSet<>();

        for (int player : new int[] {1, 2}) {
            for (GameAction action : BattleKeyBindings.BATTLE_ACTIONS) {
                KeyCode code = bindings.keyFor(player, action);
                assertNotNull(code, "P" + player + "의 " + action + " 키가 비어 있음");
                assertTrue(usedKeys.add(code), "중복된 키: " + code);
                assertEquals(Optional.of(new PlayerInput(player, action)), bindings.inputFor(code));
            }
        }

        assertEquals(10, usedKeys.size());
        assertEquals(10, bindings.allBindings().size());
    }

    @Test
    void defaultKeysSeparateLeftAndRightZones() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        assertEquals(Optional.of(new PlayerInput(1, GameAction.MOVE_LEFT)), bindings.inputFor(KeyCode.A));
        assertEquals(Optional.of(new PlayerInput(1, GameAction.MOVE_RIGHT)), bindings.inputFor(KeyCode.D));
        assertEquals(Optional.of(new PlayerInput(1, GameAction.SOFT_DROP)), bindings.inputFor(KeyCode.S));
        assertEquals(Optional.of(new PlayerInput(1, GameAction.ROTATE_CW)), bindings.inputFor(KeyCode.W));
        assertEquals(Optional.of(new PlayerInput(1, GameAction.HARD_DROP)), bindings.inputFor(KeyCode.SPACE));

        assertEquals(Optional.of(new PlayerInput(2, GameAction.MOVE_LEFT)), bindings.inputFor(KeyCode.LEFT));
        assertEquals(Optional.of(new PlayerInput(2, GameAction.MOVE_RIGHT)), bindings.inputFor(KeyCode.RIGHT));
        assertEquals(Optional.of(new PlayerInput(2, GameAction.SOFT_DROP)), bindings.inputFor(KeyCode.DOWN));
        assertEquals(Optional.of(new PlayerInput(2, GameAction.ROTATE_CW)), bindings.inputFor(KeyCode.UP));
        assertEquals(Optional.of(new PlayerInput(2, GameAction.HARD_DROP)), bindings.inputFor(KeyCode.ENTER));

        assertNotEquals(bindings.keyFor(1, GameAction.HARD_DROP), bindings.keyFor(2, GameAction.HARD_DROP));
    }

    @Test
    void bindPersistsAcrossReloadAndSwapsOnCollisionBetweenPlayers() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        // P1 하드드롭(SPACE)을 새 키 F로 변경
        bindings.bind(1, GameAction.HARD_DROP, KeyCode.F);
        assertEquals(KeyCode.F, bindings.keyFor(1, GameAction.HARD_DROP));

        // P1 왼쪽 이동(A)을 현재 P2 왼쪽 이동(LEFT) 키로 바꾸면 두 키가 서로 교환된다
        bindings.bind(1, GameAction.MOVE_LEFT, KeyCode.LEFT);
        assertEquals(KeyCode.LEFT, bindings.keyFor(1, GameAction.MOVE_LEFT));
        assertEquals(KeyCode.A, bindings.keyFor(2, GameAction.MOVE_LEFT));
        assertEquals(10, bindings.allBindings().size());

        // 다시 로드해도 변경된 키가 그대로 유지된다
        BattleKeyBindings reloaded = new BattleKeyBindings();
        assertEquals(KeyCode.F, reloaded.keyFor(1, GameAction.HARD_DROP));
        assertEquals(KeyCode.LEFT, reloaded.keyFor(1, GameAction.MOVE_LEFT));
        assertEquals(KeyCode.A, reloaded.keyFor(2, GameAction.MOVE_LEFT));

        // 초기화 후 저장하면 기본값으로 복구된다
        reloaded.resetToDefault();
        reloaded.saveActive();
        BattleKeyBindings afterReset = new BattleKeyBindings();
        assertEquals(KeyCode.SPACE, afterReset.keyFor(1, GameAction.HARD_DROP));
        assertEquals(KeyCode.A, afterReset.keyFor(1, GameAction.MOVE_LEFT));
        assertEquals(KeyCode.LEFT, afterReset.keyFor(2, GameAction.MOVE_LEFT));
    }

    @Test
    void bindRejectsEscapeUndefinedAndNonBattleActions() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        assertThrows(IllegalArgumentException.class, () -> bindings.bind(1, GameAction.HARD_DROP, KeyCode.ESCAPE));
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(2, GameAction.MOVE_LEFT, KeyCode.UNDEFINED));
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(1, GameAction.PAUSE, KeyCode.P));
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(1, GameAction.QUIT, KeyCode.Q));
    }

    @Test
    void escapePauseAndUnboundKeysReturnEmpty() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        assertTrue(bindings.inputFor(KeyCode.ESCAPE).isEmpty());
        assertTrue(bindings.inputFor(KeyCode.P).isEmpty());
        assertTrue(bindings.inputFor(KeyCode.Z).isEmpty());
        assertTrue(bindings.inputFor(null).isEmpty());
    }

    @Test
    void helpTextContainsBoundKeyNames() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        String p1Help = bindings.helpTextFor(1);
        String p2Help = bindings.helpTextFor(2);

        assertTrue(p1Help.contains("A") && p1Help.contains("D") && p1Help.contains("Space"));
        assertTrue(p2Help.contains("Left") && p2Help.contains("Right") && p2Help.contains("Enter"));
    }

    @Test
    void invalidPlayerNumberThrowsException() {
        BattleKeyBindings bindings = new BattleKeyBindings();

        assertThrows(IllegalArgumentException.class, () -> new PlayerInput(0, GameAction.MOVE_LEFT));
        assertThrows(IllegalArgumentException.class, () -> new PlayerInput(3, GameAction.MOVE_LEFT));
        assertThrows(IllegalArgumentException.class, () -> bindings.keyFor(0, GameAction.MOVE_LEFT));
        assertThrows(IllegalArgumentException.class, () -> bindings.helpTextFor(3));
    }
}
