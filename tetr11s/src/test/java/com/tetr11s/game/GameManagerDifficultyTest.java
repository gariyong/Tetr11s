package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GameManagerDifficultyTest {

    @Test
    void 기본_생성자는_Normal() {
        assertEquals(Difficulty.NORMAL, new GameManager().getDifficulty());
    }

    @Test
    void 난이도를_받은_게임은_그_난이도로_시작한다() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameManager game = new GameManager(difficulty);
            game.start();

            assertEquals(difficulty, game.getDifficulty());
            assertEquals(1000, game.getDropIntervalMillis());   // 레벨 1은 모두 1초
        }
    }
}