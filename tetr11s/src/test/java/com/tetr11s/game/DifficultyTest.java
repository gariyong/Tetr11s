package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.tetromino.RouletteWheelGenerator;
import com.tetr11s.game.tetromino.Tetromino;

class DifficultyTest {

    @Test
    void 저장된_이름으로_난이도를_읽는다() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(difficulty, Difficulty.fromSaved(difficulty.name()));
        }
    }

    @Test
    void 소문자나_앞뒤_공백이_있어도_읽는다() {
        assertEquals(Difficulty.HARD, Difficulty.fromSaved("  hard "));
    }

    @Test
    void 저장된_값이_없거나_알_수_없으면_Normal() {
        assertEquals(Difficulty.NORMAL, Difficulty.fromSaved(null));
        assertEquals(Difficulty.NORMAL, Difficulty.fromSaved(""));
        assertEquals(Difficulty.NORMAL, Difficulty.fromSaved("VERY_HARD"));
    }

    @Test
    void 속도_증가_배율은_Easy_0_8_Normal_1_0_Hard_1_2() {
        assertEquals(0.8, Difficulty.EASY.speedUpRate());
        assertEquals(1.0, Difficulty.NORMAL.speedUpRate());
        assertEquals(1.2, Difficulty.HARD.speedUpRate());
        assertTrue(Difficulty.EASY.speedUpRate() < Difficulty.HARD.speedUpRate());
    }

    @Test
    void I_블럭_가중치는_Easy_12_Normal_10_Hard_8() {
        assertEquals(12, Difficulty.EASY.iBlockWeight());
        assertEquals(RouletteWheelGenerator.DEFAULT_WEIGHT, Difficulty.NORMAL.iBlockWeight());
        assertEquals(8, Difficulty.HARD.iBlockWeight());
    }

    @Test
    void Easy는_I_블럭이_Normal보다_자주_Hard는_덜_나온다() {
        double easy = new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(Difficulty.EASY.iBlockWeight())).probability(Tetromino.I);
        double normal = new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(Difficulty.NORMAL.iBlockWeight())).probability(Tetromino.I);
        double hard = new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(Difficulty.HARD.iBlockWeight())).probability(Tetromino.I);

        assertTrue(easy > normal);
        assertTrue(normal > hard);
    }

    @Test
    void next와_previous는_순서대로_돌고_끝에서_반대쪽으로_넘어간다() {
        assertEquals(Difficulty.NORMAL, Difficulty.EASY.next());
        assertEquals(Difficulty.HARD, Difficulty.NORMAL.next());
        assertEquals(Difficulty.EASY, Difficulty.HARD.next());

        assertEquals(Difficulty.HARD, Difficulty.EASY.previous());
        assertEquals(Difficulty.EASY, Difficulty.NORMAL.previous());
        assertEquals(Difficulty.NORMAL, Difficulty.HARD.previous());
    }
}