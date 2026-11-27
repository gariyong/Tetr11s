package com.tetr11s.game;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 홀드 규칙 테스트
 * - 처음 홀드하면 지금 블럭을 넣고 다음 블럭을 꺼낸다
 * - 홀드 칸이 차 있으면 지금 블럭과 맞바꾼다 (꺼낸 블럭은 생성 위치, 회전 0)
 * - 블럭 하나당 한 번만, 블럭을 고정하면 다시 쓸 수 있다
 *
 * 준비: 블럭이 T, I, O, S, Z, J, L 순서로 계속 나온다.
 */
class GameManagerHoldTest {

    private static final List<Tetromino> ORDER = List.of(
            Tetromino.T, Tetromino.I, Tetromino.O, Tetromino.S,
            Tetromino.Z, Tetromino.J, Tetromino.L);

    private Board board;
    private GameManager game;

    private static Supplier<Tetromino> inOrder() {
        int[] index = {0};
        return () -> ORDER.get(index[0]++ % ORDER.size());
    }

    @BeforeEach
    void setUp() {
        board = new Board();
        game = new GameManager(board, inOrder());
        game.start();
    }

    @Test
    void 처음_홀드하면_지금_블럭을_넣고_다음_블럭이_나온다() {
        assertEquals(Tetromino.T, game.getCurrent().type());
        assertNull(game.getHeld());

        assertTrue(game.hold());

        assertEquals(Tetromino.T, game.getHeld());
        assertEquals(Tetromino.I, game.getCurrent().type());
        assertEquals(Tetromino.O, game.getNext(), "다음 블럭도 한 칸 당겨져야 함");
    }

    @Test
    void 블럭_하나당_한_번만_홀드할_수_있다() {
        game.hold();

        assertFalse(game.canHold());
        assertFalse(game.hold());
        assertEquals(Tetromino.T, game.getHeld());
        assertEquals(Tetromino.I, game.getCurrent().type());
    }

    @Test
    void 블럭을_고정하면_다시_홀드할_수_있고_홀드_칸과_맞바꾼다() {
        game.hold();          // 홀드 T, 지금 I
        game.hardDrop();      // I 고정, 지금 O

        assertTrue(game.canHold());
        assertTrue(game.hold());

        assertEquals(Tetromino.T, game.getCurrent().type());
        assertEquals(Tetromino.O, game.getHeld());
        assertEquals(Tetromino.S, game.getNext(), "맞바꿀 때는 다음 블럭이 바뀌지 않아야 함");
    }

    @Test
    void 꺼낸_블럭은_생성_위치에서_회전_0으로_나온다() {
        game.rotateClockwise();
        game.moveLeft();
        game.moveLeft();
        game.softDrop();
        game.hold();          // 돌리고 옮긴 T를 홀드
        game.hardDrop();

        game.hold();          // T를 다시 꺼냄

        assertEquals(Block.spawn(Tetromino.T, board.width()), game.getCurrent());
    }

    @Test
    void 홀드는_점수를_올리지_않는다() {
        int before = game.getScore();

        game.hold();

        assertEquals(before, game.getScore());
    }

    @Test
    void 홀드할_때는_받은_공격_줄이_들어오지_않는다() {
        boolean[] filled = new boolean[Board.WIDTH];
        for (int col = 1; col < Board.WIDTH; col++) {
            filled[col] = true;
        }
        game.receiveGarbage(List.of(new GarbageLine(filled)));

        game.hold();

        assertNull(board.get(Board.HEIGHT - 1, 1), "공격 줄은 블럭을 고정한 뒤에만 들어와야 함");
        assertEquals(1, game.getIncomingGarbage().size());
    }

    @Test
    void 꺼낸_블럭이_나올_자리가_막혀_있으면_게임오버() {
        game.hold();          // 홀드 T
        game.hardDrop();      // 지금 O
        for (Cell c : Block.spawn(Tetromino.T, board.width()).cells()) {
            board.fillCell(c.row(), c.col(), Tetromino.I, null);
        }

        game.hold();

        assertTrue(game.isGameOver());
    }

    @Test
    void 일시정지_중이나_게임오버_뒤에는_홀드할_수_없다() {
        game.togglePause();
        assertFalse(game.hold());
        game.togglePause();

        for (int row = Board.BUFFER_ROWS; row < Board.HEIGHT; row++) {
            board.fillCell(row, 4, Tetromino.I, null);
            board.fillCell(row, 5, Tetromino.I, null);
        }
        for (int i = 0; i < 50 && !game.isGameOver(); i++) {
            game.hardDrop();
            game.finishLineClear();
        }
        assertTrue(game.isGameOver());
        assertFalse(game.hold());
    }

    @Test
    void 새_게임을_시작하면_홀드_칸이_비워진다() {
        game.hold();

        game.start();

        assertNull(game.getHeld());
        assertTrue(game.canHold());
    }
}
