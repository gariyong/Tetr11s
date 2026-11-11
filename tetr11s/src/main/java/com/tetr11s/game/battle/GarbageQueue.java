package com.tetr11s.game.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 상대에게서 넘어와 아직 보드에 추가되지 않은 공격 줄 대기열.
 * 화면의 "넘어간 블럭 표시 부분"에 보이는 줄들이다.
 *
 * 줄 순서는 화면에 보이는 순서와 같다: 0번이 가장 위, 마지막이 가장 아래.
 * 보드에 추가할 때도 이 순서 그대로 보드 아래쪽에 들어간다.
 *
 * - 넘길 수 있는 최대 줄 수는 10줄
 * - 여러 번의 공격으로 추가되는 줄은 현재 넘어간 줄의 아래쪽으로 추가
 * - 이미 10줄이 차 있으면 새 공격은 무시
 * - 더해서 10줄이 넘으면 제일 아래쪽 부분을 잘라내고 추가
 */
public class GarbageQueue {

    /** 대기열에 쌓일 수 있는 최대 줄 수 (3차 요구사항 6페이지) */
    public static final int MAX_GARBAGE_LINES = 10;

    private final int width;
    private final List<GarbageLine> lines = new ArrayList<>();

    /**
     * @param width 보드 너비. 이 너비와 다른 줄은 받지 않는다.
     */
    public GarbageQueue(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("너비는 1 이상이어야 함: " + width);
        }

        this.width = width;
    }

    /**
     * 상대가 보낸 공격 줄을 받는다.
     *
     * @param attack 한 번의 공격으로 넘어온 줄들 (위에서 아래 순서)
     * @return 실제로 대기열에 들어간 줄 수 (무시되거나 잘린 줄은 빠짐)
     */
    public int receiveAttack(List<GarbageLine> attack) {
        Objects.requireNonNull(attack, "attack");
        for (GarbageLine line : attack) {
            Objects.requireNonNull(line, "공격 줄에 null이 있음");
            if (line.width() != width) {
                throw new IllegalArgumentException(
                    "줄 너비가 다름: 기대 " + width + ", 실제 " + line.width());
            }
        }

        int spaceLeft = MAX_GARBAGE_LINES - lines.size();
        if (spaceLeft <= 0) {
            return 0;   // 이미 10줄이 차 있으면 무시
        }

        // 남은 자리만큼 위에서부터 넣고, 넘치는 아래쪽은 버린다
        int toAdd = Math.min(spaceLeft, attack.size());
        for (int i = 0; i < toAdd; i++) {
            lines.add(attack.get(i));
        }

        return toAdd;
    }

    /**
     * 대기 중인 줄을 모두 꺼내고 대기열을 비운다.
     * 플레이어가 현재 블럭을 처리한 뒤, 다음 블럭이 생성되기 전에 호출한다.
     *
     * @return 꺼낸 줄들 (위에서 아래 순서). 비어 있으면 빈 리스트.
     */
    public List<GarbageLine> takeAll() {
        List<GarbageLine> result = List.copyOf(lines);
        lines.clear();

        return result;
    }

    /** 대기 중인 줄 (화면 표시용, 위에서 아래 순서). 수정할 수 없는 복사본. 대기열은 그대로다. */
    public List<GarbageLine> lines() {
        return List.copyOf(lines);
    }

    /** 현재 대기열에 있는 줄 수 */
    public int size() {
        return lines.size();
    }

    /** 게임 시작 시 초기화 */
    public void reset() {
        lines.clear();
    }
}