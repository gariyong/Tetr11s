package com.tetr11s.game.controller;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import com.tetr11s.settings.SettingsStore;

import javafx.scene.input.KeyCode;

/**
 * 로컬 2인 대전용 키 매핑 (저장·불러오기 및 P1/P2 간 중복 자동 스왑 지원).
 *
 * 기본 키는 왼손 구역(P1: W/A/S/D + Space)과 오른손 구역(P2: 방향키 + Enter)으로 완전히 분리된다.
 * 사용자가 설정 화면에서 변경한 키는 SettingsStore(save.properties)에 저장되며,
 * ESC는 대전 종료 공용 키로 예약되어 있어 바인딩할 수 없다.
 */
public class BattleKeyBindings {

    /** 로컬 대전에서 플레이어별 설정 가능한 5개 조작 동작 */
    public static final List<GameAction> BATTLE_ACTIONS = List.of(
            GameAction.MOVE_LEFT,
            GameAction.MOVE_RIGHT,
            GameAction.SOFT_DROP,
            GameAction.ROTATE_CW,
            GameAction.HARD_DROP,
            GameAction.HOLD
    );

    private final Map<GameAction, KeyCode> p1Keys = new EnumMap<>(GameAction.class);
    private final Map<GameAction, KeyCode> p2Keys = new EnumMap<>(GameAction.class);
    private final Map<KeyCode, PlayerInput> keyToInput = new HashMap<>();

    public BattleKeyBindings() {
        resetToDefault();
        loadActive();
    }

    /** 기본 대전 키(P1: WASD+Space, P2: 방향키+Enter)로 초기화한다. */
    public void resetToDefault() {
        p1Keys.clear();
        p2Keys.clear();
        keyToInput.clear();

        putDirect(1, GameAction.MOVE_LEFT, KeyCode.A);
        putDirect(1, GameAction.MOVE_RIGHT, KeyCode.D);
        putDirect(1, GameAction.SOFT_DROP, KeyCode.S);
        putDirect(1, GameAction.ROTATE_CW, KeyCode.W);
        putDirect(1, GameAction.HARD_DROP, KeyCode.SPACE);
        putDirect(1, GameAction.HOLD, KeyCode.Q);

        putDirect(2, GameAction.MOVE_LEFT, KeyCode.LEFT);
        putDirect(2, GameAction.MOVE_RIGHT, KeyCode.RIGHT);
        putDirect(2, GameAction.SOFT_DROP, KeyCode.DOWN);
        putDirect(2, GameAction.ROTATE_CW, KeyCode.UP);
        putDirect(2, GameAction.HARD_DROP, KeyCode.ENTER);
        // JavaFX는 왼쪽/오른쪽 Shift를 구분하지 못해서(둘 다 SHIFT) 오른쪽 Shift 옆의 '/'를 쓴다
        putDirect(2, GameAction.HOLD, KeyCode.SLASH);
    }

    /** 현재 대전 키 매핑을 설정 파일에 저장한다. */
    public void saveActive() {
        Properties properties = SettingsStore.load();
        for (int player : new int[] {1, 2}) {
            for (GameAction action : BATTLE_ACTIONS) {
                properties.setProperty(
                        SettingsStore.battleActiveKey(player, action),
                        keyFor(player, action).name());
            }
        }
        SettingsStore.save(properties);
    }

    /**
     * 특정 플레이어의 동작에 새 키를 지정하고 저장한다.
     * 이미 다른 동작(같은 플레이어 또는 상대 플레이어)이 쓰고 있는 키라면 두 키를 서로 맞바꾼다.
     *
     * @throws IllegalArgumentException ESCAPE, UNDEFINED 또는 대전에서 쓰지 않는 동작인 경우
     */
    public void bind(int player, GameAction action, KeyCode code) {
        validateBattleAction(action);
        Objects.requireNonNull(code, "code");
        if (code == KeyCode.ESCAPE || code == KeyCode.UNDEFINED) {
            throw new IllegalArgumentException("대전 조작 키로 지정할 수 없는 키입니다: " + code);
        }
        applyBind(player, action, code);
        saveActive();
    }

    /** 눌린 키에 해당하는 플레이어와 동작을 반환한다. 대전 조작 키가 아니면 empty. */
    public Optional<PlayerInput> inputFor(KeyCode code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(keyToInput.get(code));
    }

    /** 특정 플레이어의 동작에 지정된 키를 반환한다. */
    public KeyCode keyFor(int player, GameAction action) {
        Objects.requireNonNull(action, "action");
        return mapFor(player).get(action);
    }

    /** 등록된 전체 키 매핑 (읽기 전용). */
    public Map<KeyCode, PlayerInput> allBindings() {
        return Collections.unmodifiableMap(keyToInput);
    }

    /** 화면 사이드바에 표시할 플레이어별 조작 안내 문자열. */
    public String helpTextFor(int player) {
        return String.join("\n",
                keyName(player, GameAction.MOVE_LEFT) + " / " + keyName(player, GameAction.MOVE_RIGHT) + " : 이동",
                keyName(player, GameAction.SOFT_DROP) + " : 아래로",
                keyName(player, GameAction.ROTATE_CW) + " : 회전",
                keyName(player, GameAction.HARD_DROP) + " : 하드드롭",
                keyName(player, GameAction.HOLD) + " : 홀드");
    }

    private void loadActive() {
        Properties properties = SettingsStore.load();
        for (int player : new int[] {1, 2}) {
            for (GameAction action : BATTLE_ACTIONS) {
                String saved = properties.getProperty(SettingsStore.battleActiveKey(player, action));
                if (saved != null) {
                    KeyCode parsed = SettingsStore.parseKey(saved, null);
                    if (parsed != null && parsed != KeyCode.ESCAPE && parsed != KeyCode.UNDEFINED) {
                        applyBind(player, action, parsed);
                    }
                }
            }
        }
    }

    private void applyBind(int player, GameAction action, KeyCode code) {
        Map<GameAction, KeyCode> targetMap = mapFor(player);
        KeyCode oldKey = targetMap.get(action);
        if (oldKey == code) {
            return;
        }

        PlayerInput existing = keyToInput.get(code);
        if (existing != null) {
            // 이미 다른 동작(같은 플레이어 또는 상대 플레이어)이 쓰고 있던 키면 서로 맞바꾼다
            mapFor(existing.player()).put(existing.action(), oldKey);
        }
        targetMap.put(action, code);
        rebuildReverseIndex();
    }

    private void putDirect(int player, GameAction action, KeyCode code) {
        mapFor(player).put(action, code);
        keyToInput.put(code, new PlayerInput(player, action));
    }

    private void rebuildReverseIndex() {
        keyToInput.clear();
        for (Map.Entry<GameAction, KeyCode> e : p1Keys.entrySet()) {
            keyToInput.put(e.getValue(), new PlayerInput(1, e.getKey()));
        }
        for (Map.Entry<GameAction, KeyCode> e : p2Keys.entrySet()) {
            keyToInput.put(e.getValue(), new PlayerInput(2, e.getKey()));
        }
    }

    private static void validateBattleAction(GameAction action) {
        Objects.requireNonNull(action, "action");
        if (!BATTLE_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("대전 모드에서 설정할 수 없는 동작입니다: " + action);
        }
    }

    private Map<GameAction, KeyCode> mapFor(int player) {
        return switch (player) {
            case 1 -> p1Keys;
            case 2 -> p2Keys;
            default -> throw new IllegalArgumentException("플레이어 번호는 1 또는 2여야 합니다: " + player);
        };
    }

    private String keyName(int player, GameAction action) {
        KeyCode code = keyFor(player, action);
        return code == null ? "-" : code.getName();
    }
}
