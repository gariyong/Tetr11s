package com.tetr11s.game.controller;

/**
 * 게임 조작 목록
 * 게임 코드는 키보드 키(KeyCode)를 직접 보지 않고 이 값만 받는다.
 * 어떤 키가 어떤 동작인지는 KeyBindings가 정한다 (설정에서 바꿀 수 있음).
 */
public enum GameAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    SOFT_DROP,
    HARD_DROP,
    ROTATE_CW,
    PAUSE,
    QUIT
}