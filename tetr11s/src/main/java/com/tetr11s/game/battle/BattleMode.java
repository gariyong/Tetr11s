package com.tetr11s.game.battle;

/**
 * 대전 모드 종류
 *
 * NORMAL: 각자 테트리스를 하고, 지운 줄로 상대를 공격한다
 * ITEM: 아이템이 등장하는 것 외에 NORMAL과 같다
 * TIME_LIMIT: NORMAL 규칙 + 제한 시간이 끝나면 점수가 높은 쪽이 승리
 */
public enum BattleMode {
    NORMAL("일반", "2줄 이상 지우면 상대 보드에 공격 줄이 추가됩니다."),
    ITEM("아이템", "아이템(L, W, H, O, M)을 활용해 상대를 공격합니다."),
    TIME_LIMIT("시간제한", "2분 제한 시간 종료 시 점수가 더 높은 플레이어가 승리합니다.");

    private final String displayName;
    private final String description;

    BattleMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** 화면에 보여줄 이름 */
    public String displayName() {
        return displayName;
    }

    public String description(){
        return description;
    }
}