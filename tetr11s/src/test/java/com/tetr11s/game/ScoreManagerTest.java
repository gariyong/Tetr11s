package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ScoreManagerTest {

    @Test
    void 레벨_1은_난이도와_상관없이_1초() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(1000, ScoreManager.dropIntervalMillis(1, difficulty));
        }
    }

    @Test
    void Normal은_기존_공식과_같다() {
        for (int level = 1; level <= ScoreManager.MAX_LEVEL; level++) {
            assertEquals(ScoreManager.dropIntervalMillis(level),
                    ScoreManager.dropIntervalMillis(level, Difficulty.NORMAL));
        }
    }

    @Test
    void 레벨이_오르면_Easy가_가장_느리고_Hard가_가장_빠르다() {
        for (int level = 2; level <= ScoreManager.MAX_LEVEL; level++) {
            long easy = ScoreManager.dropIntervalMillis(level, Difficulty.EASY);
            long normal = ScoreManager.dropIntervalMillis(level, Difficulty.NORMAL);
            long hard = ScoreManager.dropIntervalMillis(level, Difficulty.HARD);
            assertTrue(easy > normal && normal > hard, "레벨 " + level + ": " + easy + " / " + normal + " / " + hard);
        }
    }

    @Test
    void 정해둔_간격() {
        assertEquals(827, ScoreManager.dropIntervalMillis(2, Difficulty.EASY));
        assertEquals(761, ScoreManager.dropIntervalMillis(2, Difficulty.HARD));
        assertEquals(408, ScoreManager.dropIntervalMillis(5, Difficulty.EASY));
        assertEquals(314, ScoreManager.dropIntervalMillis(5, Difficulty.HARD));
        assertEquals(79, ScoreManager.dropIntervalMillis(10, Difficulty.EASY));
        assertEquals(54, ScoreManager.dropIntervalMillis(10, Difficulty.HARD));
    }

    @Test
    void 최대_레벨에서도_1ms_아래로_내려가지_않는다() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(ScoreManager.dropIntervalMillis(ScoreManager.MAX_LEVEL, difficulty) >= 1);
            assertTrue(ScoreManager.dropIntervalMillis(99, difficulty) >= 1);
        }
    }

    @Test
    void 줄을_지워_레벨이_오르면_그_난이도의_간격이_된다() {
        ScoreManager hard = new ScoreManager(Difficulty.HARD);
        assertEquals(1000, hard.dropIntervalMillis());

        hard.addLineClear(4);
        hard.addLineClear(4);
        hard.addLineClear(4);   // 12줄 → 레벨 2

        assertEquals(2, hard.level());
        assertEquals(761, hard.dropIntervalMillis());
    }

    @Test
    void 기본_생성자는_Normal() {
        assertEquals(Difficulty.NORMAL, new ScoreManager().difficulty());
    }
}