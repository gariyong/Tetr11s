package com.tetr11s.game.battle;

/**
 * 대전 모드 종류
 *
 * NORMAL: 각자 테트리스를 하고, 지운 줄로 상대를 공격한다
 * ITEM: 아이템이 등장하는 것 외에 NORMAL과 같다
 * TIME_LIMIT: NORMAL 규칙 + 제한 시간이 끝나면 점수가 높은 쪽이 승리
 */
public enum BattleMode {
    NORMAL("일반"),
    ITEM("아이템"),
    TIME_LIMIT("시간제한");

    private final String displayName;

    BattleMode(String displayName) {
        this.displayName = displayName;
    }

    /** 화면에 보여줄 이름 */
    public String displayName() {
        return displayName;
    }
}