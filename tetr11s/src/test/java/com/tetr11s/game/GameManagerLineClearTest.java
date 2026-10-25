package com.tetr11s.game;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 줄 삭제 상태(꽉 찬 줄을 바로 지우지 않고 잠깐 멈추는 상태) 테스트.
 *
 * 준비: 맨 아래 줄(21행)의 0~7열을 I 블럭 2개로 미리 채워 두고,
 * O 블럭만 나오게 한 뒤 O를 오른쪽 끝(8~9열)으로 옮겨 하드 드롭하면 21행이 꽉 찬다.
 */
class GameManagerLineClearTest {

    private static final int BOTTOM = Board.HEIGHT - 1;   // 21행

    private Board board;
    private GameManager game;

    @BeforeEach
    void setUp() {
        board = new Board();
        game = new GameManager(board, () -> Tetromino.O);
        game.start();

        // I 블럭 회전 0은 블럭 안의 1행에 가로로 4칸 → row 20에 두면 보드 21행
        board.lock(new Block(Tetromino.I, 0, BOTTOM - 1, 0));   // 21행 0~3열
        board.lock(new Block(Tetromino.I, 0, BOTTOM - 1, 4));   // 21행 4~7열
    }

    // O 블럭(4~5열에서 생성)을 8~9열로 옮겨 떨어뜨린다 → 21행이 꽉 참
    private void dropOToRightEdge() {
        for (int i = 0; i < 4; i++) {
            game.moveRight();
        }
        game.hardDrop();
    }

    @Test
    void 줄이_꽉_차면_바로_지우지_않고_삭제_중_상태가_된다() {
        dropOToRightEdge();

        assertTrue(game.isClearing());
        assertEquals(List.of(BOTTOM), game.getClearingRows());
        assertEquals(List.of(BOTTOM), board.fullRows());   // 아직 보드에 남아 있음
        assertNull(game.getCurrent());                     // 다음 블럭은 아직 안 나옴
    }

    @Test
    void 삭제_중에는_조작과_자동_낙하가_무시된다() {
        dropOToRightEdge();

        assertFalse(game.moveLeft());
        assertFalse(game.moveRight());
        assertFalse(game.softDrop());
        assertFalse(game.rotateClockwise());
        assertEquals(0, game.hardDrop());
        assertFalse(game.tick());

        assertTrue(game.isClearing());
        assertNull(game.getCurrent());
    }

    @Test
    void 점수와_줄_수는_삭제_중_상태가_되자마자_오른다() {
        dropOToRightEdge();

        assertEquals(1, game.getLines());
        assertTrue(game.getScore() >= 100);   // 1줄 보너스 100 + 하드 드롭 칸 수
    }

    @Test
    void finishLineClear를_부르면_줄이_지워지고_다음_블럭이_나온다() {
        dropOToRightEdge();
        int score = game.getScore();

        game.finishLineClear();

        assertFalse(game.isClearing());
        assertEquals(List.of(), game.getClearingRows());
        assertEquals(List.of(), board.fullRows());
        assertNotNull(game.getCurrent());
        assertEquals(score, game.getScore());   // 점수가 두 번 오르지 않음

        // 21행이 지워지고 그 위(20행)에 있던 O의 윗부분이 21행으로 내려옴
        for (int col = 0; col < Board.WIDTH; col++) {
            boolean expected = col >= 8;
            assertEquals(expected, board.get(BOTTOM, col) != null, "21행 " + col + "열");
        }
    }

    @Test
    void 삭제_중이_아닐_때_finishLineClear는_아무_일도_하지_않는다() {
        Block before = game.getCurrent();

        game.finishLineClear();

        assertEquals(before, game.getCurrent());
        assertFalse(game.isClearing());
    }

    @Test
    void 꽉_찬_줄이_없으면_삭제_중_상태가_되지_않는다() {
        game.hardDrop();   // 4~5열 그대로 떨어져 I 위(19~20행)에 쌓임 → 꽉 찬 줄 없음

        assertFalse(game.isClearing());
        assertNotNull(game.getCurrent());
    }

    @Test
    void 삭제_중에도_일시정지는_된다() {
        dropOToRightEdge();

        game.togglePause();
        assertTrue(game.isPaused());
        game.togglePause();
        assertFalse(game.isPaused());
        assertTrue(game.isClearing());
    }

    @Test
    void 줄을_지운_뒤_생성_자리가_막혀_있으면_게임오버() {
        // 생성 자리(0~1행 4~5열)를 미리 막아 둔다 (지울 줄과는 상관없는 위치)
        for (int i = 0; i < 4; i++) {
            game.moveRight();
        }
        board.lock(new Block(Tetromino.O, 0, 0, 4));
        game.hardDrop();
        assertTrue(game.isClearing());

        game.finishLineClear();

        assertTrue(game.isGameOver());
    }

    @Test
    void 새_게임을_시작하면_삭제_중_상태가_풀린다() {
        dropOToRightEdge();

        game.start();

        assertFalse(game.isClearing());
        assertNotNull(game.getCurrent());
    }
}