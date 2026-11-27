package com.tetr11s.game.controller;

import java.util.Objects;

/**
 * 2인 로컬 대전에서 하나의 키 입력이 "어느 플레이어의 어떤 동작인지" 나타내는 불변 값.
 *
 * @param player 플레이어 번호 (1 또는 2)
 * @param action 수행할 게임 동작
 */
public record PlayerInput(int player, GameAction action) {

    public PlayerInput {
        if (player != 1 && player != 2) {
            throw new IllegalArgumentException("플레이어 번호는 1 또는 2여야 합니다: " + player);
        }
        Objects.requireNonNull(action, "action");
    }
}
