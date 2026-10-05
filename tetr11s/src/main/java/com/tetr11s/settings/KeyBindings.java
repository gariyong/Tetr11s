package com.tetr11s.settings;

import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.game.controller.GameAction;

import javafx.scene.input.KeyCode;

/**
 * 키 → 게임 동작 매핑.
 *
 * <p>게임 코드는 이 클래스로 키를 GameAction으로 바꿔서 쓴다.
 * 설정 화면에서 키를 바꾸면 bind()를 호출한다.
 */
public class KeyBindings {

    private final Map<GameAction, KeyCode> keys = new EnumMap<>(GameAction.class);

    public KeyBindings() {
        resetToDefault();
    }

    /** 기본 키로 되돌린다. */
    public void resetToDefault() {
        keys.clear();
        keys.put(GameAction.MOVE_LEFT, KeyCode.LEFT);
        keys.put(GameAction.MOVE_RIGHT, KeyCode.RIGHT);
        keys.put(GameAction.SOFT_DROP, KeyCode.DOWN);
        keys.put(GameAction.ROTATE_CW, KeyCode.UP);
        keys.put(GameAction.HARD_DROP, KeyCode.SPACE);
        keys.put(GameAction.PAUSE, KeyCode.P);
        keys.put(GameAction.QUIT, KeyCode.ESCAPE);
    }

    /** 눌린 키에 해당하는 동작. 게임에 쓰이지 않는 키면 null */
    public GameAction actionFor(KeyCode code) {
        for (Map.Entry<GameAction, KeyCode> e : keys.entrySet()) {
            if (e.getValue() == code) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 동작에 지정된 키 */
    public KeyCode keyFor(GameAction action) {
        return keys.get(action);
    }

    /**
     * 동작에 새 키를 지정한다.
     * 그 키를 이미 다른 동작이 쓰고 있으면, 두 동작의 키를 서로 맞바꾼다 (한 키가 두 동작에 걸리지 않도록).
     */
    public void bind(GameAction action, KeyCode code) {
        KeyCode oldKey = keys.get(action);
        GameAction other = actionFor(code);
        if (other != null && other != action) {
            keys.put(other, oldKey);
        }
        keys.put(action, code);
    }
}