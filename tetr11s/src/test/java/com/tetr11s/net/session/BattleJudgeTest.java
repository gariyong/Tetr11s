package com.tetr11s.net.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.BattleResult;

/** 서버가 PLAYER1, 클라이언트가 PLAYER2 */
class BattleJudgeTest {

    @Test
    void 한쪽만_게임오버면_상대가_이긴다() {
        assertEquals(BattleResult.PLAYER2_WIN, BattleJudge.byGameOver(true, false));
        assertEquals(BattleResult.PLAYER1_WIN, BattleJudge.byGameOver(false, true));
    }

    @Test
    void 둘_다_게임오버면_무승부이고_아무도_아니면_아직_모른다() {
        assertEquals(BattleResult.DRAW, BattleJudge.byGameOver(true, true));
        assertNull(BattleJudge.byGameOver(false, false));
    }

    @Test
    void 시간이_끝나면_점수가_높은_쪽이_이기고_같으면_무승부다() {
        assertEquals(BattleResult.PLAYER1_WIN, BattleJudge.byScore(500, 300));
        assertEquals(BattleResult.PLAYER2_WIN, BattleJudge.byScore(300, 500));
        assertEquals(BattleResult.DRAW, BattleJudge.byScore(400, 400));
    }
}
