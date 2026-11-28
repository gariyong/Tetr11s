package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.tetromino.Tetromino;

/** 화면이 읽어 가는 GameManager 상태값 (홀드 사용 여부, 고스트, 게임오버 뒤 일시정지) */
class GameManagerStateTest {

    private static final int BOTTOM = Board.HEIGHT - 1;

    @Test
    void 홀드는_블럭_하나당_한번이고_고정하면_다시_쓸_수_있다() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        assertFalse(game.isHoldUsed());

        game.hold();
        assertTrue(game.isHoldUsed());
        assertNull(game.getHeldItemType(), "일반 게임의 홀드 칸에는 아이템이 없다");

        game.hardDrop();
        assertFalse(game.isHoldUsed());
    }

    @Test
    void 줄을_지우는_중에는_고스트가_없다() {
        Board board = new Board();
        GameManager game = new GameManager(board, () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        for (int col = 0; col < board.width(); col++) {
            if (col != 4 && col != 5) {
                board.fillCell(BOTTOM, col, Tetromino.T, null);
                board.fillCell(BOTTOM - 1, col, Tetromino.T, null);
            }
        }
        game.hardDrop();

        assertTrue(game.isClearing());
        assertNull(game.getGhost());
    }

    @Test
    void 게임오버_뒤에는_일시정지가_되지_않는다() {
        Board board = new Board();
        GameManager game = new GameManager(board, () -> Tetromino.O, Difficulty.NORMAL);
        game.start();
        for (int row = Board.BUFFER_ROWS; row <= BOTTOM; row++) {
            board.fillCell(row, 4, Tetromino.I, null);
            board.fillCell(row, 5, Tetromino.I, null);
        }
        game.hardDrop();
        assertTrue(game.isGameOver());

        game.togglePause();
        assertFalse(game.isPaused());
    }
}
