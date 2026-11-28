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

    @Test
    void lineClearFlashesThenFinishesAfter400ms() {
        Board board = new Board();
        GameManager game = new GameManager(board, () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        for (int row = board.height() - 2; row < board.height(); row++) {
            for (int col = 0; col < board.width(); col++) {
                if (col != 4 && col != 5) {
                    board.fillCell(row, col, Tetromino.T, null);
                }
            }
        }
        PlayerRunner runner = new PlayerRunner(game, () -> 0L);
        runner.reset(0L);

        assertTrue(runner.onPress(GameAction.HARD_DROP, 0L));
        assertTrue(game.isClearing());
        assertTrue(runner.isFlashOn());                 // 0~79ms: 켜짐

        assertTrue(runner.step(80L, 80L));
        assertFalse(runner.isFlashOn());                // 80~159ms: 꺼짐
        assertTrue(game.isClearing());

        // 연출 중에는 시간이 많이 흘러도 자동 낙하가 일어나지 않고, 400ms가 차면 삭제를 끝낸다
        assertTrue(runner.step(400L, 320L));
        assertFalse(game.isClearing());
        assertFalse(runner.isFlashOn());
        for (int col = 0; col < board.width(); col++) {
            assertEquals(null, board.get(board.height() - 1, col));
        }
    }

    @Test
    void autoDropMovesOneRowPerIntervalAndClampsLongGaps() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        PlayerRunner runner = new PlayerRunner(game);
        runner.reset(0L);
        int startRow = game.getCurrent().row();

        assertFalse(runner.step(999L, 999L));
        assertTrue(runner.step(1000L, 1L));
        assertEquals(startRow + 1, game.getCurrent().row());

        // 창이 멈춰 10초가 튀어도 한 프레임에 한 칸만 떨어진다
        assertTrue(runner.step(11_000L, 10_000L));
        assertEquals(startRow + 2, game.getCurrent().row());
    }

    @Test
    void holdRestartsDropClockAndReleaseStopsRepeat() {
        int[] calls = {0};
        Tetromino[] order = {Tetromino.O, Tetromino.I, Tetromino.T, Tetromino.S};
        GameManager game = new GameManager(new Board(), () -> order[calls[0]++ % order.length], Difficulty.NORMAL);
        game.start();
        PlayerRunner runner = new PlayerRunner(game, () -> 0L);
        runner.reset(0L);

        assertTrue(runner.onPress(GameAction.HOLD, 900L));
        int rowAfterHold = game.getCurrent().row();
        // 홀드 시각(900ms)부터 다시 세므로 1000ms에는 떨어지지 않는다
        assertFalse(runner.step(1000L, 100L));
        assertEquals(rowAfterHold, game.getCurrent().row());

        int col = game.getCurrent().col();
        assertTrue(runner.onPress(GameAction.MOVE_LEFT, 1000L));
        runner.onRelease(GameAction.MOVE_LEFT, 1050L);
        runner.onRelease(null, 1050L);
        runner.onRelease(GameAction.PAUSE, 1050L);
        runner.step(1500L, 450L);                       // 뗀 뒤에는 반복 이동이 없다
        assertEquals(col - 1, game.getCurrent().col());

        runner.cancelRepeats();
        runner.clearKeys();
        assertFalse(runner.onPress(null, 1600L));
        assertTrue(runner.game() == game);
    }

    @Test
    void nothingHappensAfterGameOver() {
        Board board = new Board();
        GameManager game = new GameManager(board, () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        // O 블럭이 나오는 4~5열을 보이는 첫 줄(버퍼 바로 아래)부터 바닥까지 막는다
        // → 하드드롭한 블럭이 버퍼 줄에만 고정되어 게임오버 (락 아웃)
        for (int row = Board.BUFFER_ROWS; row < board.height(); row++) {
            board.fillCell(row, 4, Tetromino.I, null);
            board.fillCell(row, 5, Tetromino.I, null);
        }
        PlayerRunner runner = new PlayerRunner(game, () -> 0L);
        runner.reset(0L);
        runner.onPress(GameAction.HARD_DROP, 0L);
        assertTrue(game.isGameOver());

        assertFalse(runner.onPress(GameAction.MOVE_LEFT, 6000L));
        assertFalse(runner.step(7000L, 1000L));
    }
}
