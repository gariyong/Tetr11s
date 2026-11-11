package com.tetr11s.game.battle;

/** 대전 결과 */
public enum BattleResult {
    PLAYER1_WIN,
    PLAYER2_WIN,
    /** 동시에 게임오버, 또는 시간제한 모드에서 시간 종료 시 동점 */
    DRAW
}