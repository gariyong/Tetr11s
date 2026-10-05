package com.tetr11s.game.controller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 키를 꾹 누르고 있을 때의 반복 입력을 계산
 * 
 * 처음 누른 순간: 바로 1번 실행
 * 좌우 이동: DAS만큼 기다린 뒤, ARR 간격으로 반복
 * 소프트 드롭: 기다리지 않고 ARR 간격으로 반복
 * 회전, 하드 드롭, 일시정지, 종료: 반복하지 않음 (꾹 눌러도 1번)
 * 시간은 밀리초(ms)로 받음
 */
public class KeyRepeater {

    // DAS (Delayed Auto Shift): 꾹 눌렀을 때 반복이 시작되기까지의 시간
    public static final long DEFAULT_DAS_MS = 170;
    //ARR (Auto Repeat Rate): 반복 간격
    public static final long DEFAULT_ARR_MS = 50;

    private final long das;
    private final long arr;

    private final Set<GameAction> held = EnumSet.noneOf(GameAction.class);
    private final Map<GameAction, Long> nextFire = new EnumMap<>(GameAction.class);

    public KeyRepeater() {
        this(DEFAULT_DAS_MS, DEFAULT_ARR_MS);
    }

    public KeyRepeater(long das, long arr) {
        if (das < 0 || arr <= 0) {
            throw new IllegalArgumentException("DAS는 0 이상, ARR은 1 이상이어야 함");
        }
        this.das = das;
        this.arr = arr;
    }

    // 꾹 눌렀을 때 반복하는 동작인지 확인
    public static boolean isRepeatable(GameAction action) {
        return action == GameAction.MOVE_LEFT
                || action == GameAction.MOVE_RIGHT
                || action == GameAction.SOFT_DROP;
    }

    /**
     * 키를 눌렀을 때 호출
     * @return 처음 누른 순간이면 true (바로 1번 실행해야 함). 이미 누르고 있던 키면 false
     */
    public boolean press(GameAction action, long nowMs) {
        if (!held.add(action)) {
            return false;   // 운영체제 자동 반복 → 무시
        }
        if (isRepeatable(action)) {
            long delay = (action == GameAction.SOFT_DROP) ? arr : das;
            nextFire.put(action, nowMs + delay);
        }
        // 좌우를 동시에 누르면 나중에 누른 방향만 반복한다
        GameAction opposite = opposite(action);
        if (opposite != null) {
            nextFire.remove(opposite);
        }
        return true;
    }

    // 키를 뗐을 때 호출
    public void release(GameAction action, long nowMs) {
        held.remove(action);
        nextFire.remove(action);

        // 반대 방향 키를 아직 누르고 있으면 그쪽 반복을 다시 시작
        GameAction opposite = opposite(action);
        if (opposite != null && held.contains(opposite)) {
            nextFire.put(opposite, nowMs + das);
        }
    }

    /**
     * 시간이 흐를 때마다(매 프레임) 호출한다.
     * @return 이번에 반복 실행해야 할 동작들
     */
    public List<GameAction> update(long nowMs) {
        List<GameAction> fired = new ArrayList<>();
        for (Map.Entry<GameAction, Long> e : nextFire.entrySet()) {
            long next = e.getValue();
            while (nowMs >= next) {
                fired.add(e.getKey());
                next += arr;
            }
            e.setValue(next);
        }
        return fired;
    }

    /**
     * 반복만 멈춘다. 누르고 있는 키는 계속 기억한다 (일시정지, 게임오버 때 사용).
     * 키를 계속 누르고 있어도 다시 실행되지 않고, 뗐다가 다시 눌러야 실행된다.
     */
    public void cancelRepeats() {
        nextFire.clear();
    }

    /** 누르고 있던 키를 모두 잊는다 (창 포커스를 잃어서 키를 뗀 신호를 받을 수 없을 때 사용). */
    public void clear() {
        held.clear();
        nextFire.clear();
    }

    private static GameAction opposite(GameAction action) {
        return switch (action) {
            case MOVE_LEFT -> GameAction.MOVE_RIGHT;
            case MOVE_RIGHT -> GameAction.MOVE_LEFT;
            default -> null;
        };
    }
}