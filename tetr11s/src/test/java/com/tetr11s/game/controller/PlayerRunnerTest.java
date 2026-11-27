package com.tetr11s.game.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.Board;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.battle.BattleManager;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.view.BattleView;

class PlayerRunnerTest {

    @Test
    void ignoresPauseAndQuitInBattle() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        PlayerRunner runner = new PlayerRunner(game, () -> 0L);
        runner.reset(0L);

        assertFalse(runner.onPress(GameAction.PAUSE, 10L));
        assertFalse(runner.onPress(GameAction.QUIT, 20L));
        assertFalse(game.isPaused());
    }

    @Test
    void twoPlayersStepIndependentlyDuringLineClearAndAttackTransfers() {
        Board board1 = new Board();
        Board board2 = new Board();
        GameManager p1 = new GameManager(board1, () -> Tetromino.O, Difficulty.NORMAL);
        GameManager p2 = new GameManager(board2, () -> Tetromino.O, Difficulty.NORMAL);
        BattleManager battle = new BattleManager(BattleMode.NORMAL, p1, p2, 120_000L);
        battle.start();

        // battle.start()가 보드를 비운 뒤에 P1 바닥 2줄(O블록 4~5열 제외)을 채운다
        for (int row = board1.height() - 2; row < board1.height(); row++) {
            for (int col = 0; col < board1.width(); col++) {
                if (col != 4 && col != 5) {
                    board1.fillCell(row, col, Tetromino.T, null);
                }
            }
        }

        PlayerRunner runner1 = new PlayerRunner(p1, () -> 0L);
        PlayerRunner runner2 = new PlayerRunner(p2, () -> 0L);
        runner1.reset(0L);
        runner2.reset(0L);

        // P1 하드드롭 → 2줄 삭제 연출 진입 + P2 대기열에 공격 줄 2줄 도착
        assertTrue(runner1.onPress(GameAction.HARD_DROP, 50L));
        assertTrue(p1.isClearing());
        assertEquals(2, p2.getIncomingGarbage().size());

        // P1이 줄 삭제 연출(400ms) 중인 동안에도 P2는 독립적으로 좌측 이동 가능
        int p2InitialCol = p2.getCurrent().col();
        assertTrue(runner2.onPress(GameAction.MOVE_LEFT, 100L));
        assertEquals(p2InitialCol - 1, p2.getCurrent().col());

        // 연출 200ms 경과: 아직 반짝임 중
        runner1.step(250L, 200L);
        assertTrue(p1.isClearing());

        // 연출 누적 400ms 도달: 실제 줄 삭제 완료 & 다음 블록 스폰
        runner1.step(450L, 200L);
        assertFalse(p1.isClearing());
        assertEquals(2, p1.getLines());

        // P2가 블록 고정하면 대기하던 2줄 공격 줄이 P2 보드 바닥에 삽입되고 대기열은 비워짐
        runner2.onPress(GameAction.HARD_DROP, 500L);
        assertTrue(p2.getIncomingGarbage().isEmpty());
        assertTrue(board2.isGarbage(board2.height() - 1, 0));
    }

    @Test
    void simultaneousTopOutOnSameFrameJudgesAsDraw() {
        Board board1 = new Board();
        Board board2 = new Board();
        GameManager p1 = new GameManager(board1, () -> Tetromino.O, Difficulty.NORMAL);
        GameManager p2 = new GameManager(board2, () -> Tetromino.O, Difficulty.NORMAL);
        BattleManager battle = new BattleManager(BattleMode.NORMAL, p1, p2, 120_000L);
        battle.start();

        // 양쪽 모두 버퍼 바로 아래(2행)부터 바닥까지 막아 다음 낙하(고정) 시 버퍼 영역 락아웃으로 동시 게임오버 유도
        for (int row = Board.BUFFER_ROWS; row < board1.height(); row++) {
            board1.fillCell(row, 4, Tetromino.T, null);
            board2.fillCell(row, 4, Tetromino.T, null);
        }

        PlayerRunner runner1 = new PlayerRunner(p1, () -> 0L);
        PlayerRunner runner2 = new PlayerRunner(p2, () -> 0L);
        runner1.reset(0L);
        runner2.reset(0L);

        long dropInterval = p1.getDropIntervalMillis();
        runner1.step(dropInterval, dropInterval);
        runner2.step(dropInterval, dropInterval);
        battle.update(dropInterval);

        assertTrue(p1.isGameOver());
        assertTrue(p2.isGameOver());
        assertTrue(battle.isFinished());
        assertEquals(BattleResult.DRAW, battle.getResult());
    }

    @Test
    void formatRemainingTimeRoundsUpSubSecondAndClampsNegative() {
        assertEquals("02:00", BattleView.formatRemainingTime(120_000));
        assertEquals("01:00", BattleView.formatRemainingTime(59_001));
        assertEquals("00:01", BattleView.formatRemainingTime(1));
        assertEquals("00:00", BattleView.formatRemainingTime(0));
        assertEquals("00:00", BattleView.formatRemainingTime(-500));
    }

    @Test
    void syncDropClockPreventsBurstDropAfterPause() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        PlayerRunner runner = new PlayerRunner(game, () -> 0L);
        runner.reset(0L);

        int initialRow = game.getCurrent().row();
        // 10초 동안 일시정지 후 재개 시점(10_000ms)으로 낙하 기준 시각을 맞춘다
        runner.syncDropClock(10_000L);
        assertFalse(runner.step(10_016L, 16L));
        assertEquals(initialRow, game.getCurrent().row());
    }
}
