package com.tetr11s.game;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 게임오버(top out) 규칙 테스트 (이슈 #16)
 *
 * 팀 결정: 테트리스 가이드라인과 같은 규칙을 쓴다
 * - 블럭 아웃: 새 블럭이 나올 자리가 이미 막혀 있으면 게임오버
 * - 락 아웃: 고정된 블럭의 모든 칸이 숨겨진 버퍼 줄(0~1행)에 있으면 게임오버
 * - 블럭의 일부만 버퍼 줄에 걸쳐 고정되면 게임은 계속된다 (그 칸은 화면에 보이지 않음)
 * - 공격 줄에 밀려 보드 맨 위(0행) 밖으로 블럭이 밀려나면 게임오버
 *
 * 준비: O 블럭만 나온다. O는 0~1행, 4~5열에서 생성된다.
 */
class GameManagerTopOutTest {

    private static final int FIRST_VISIBLE_ROW = Board.BUFFER_ROWS;   // 2행
    private static final int BOTTOM = Board.HEIGHT - 1;              // 21행

    private Board board;
    private GameManager game;

    @BeforeEach
    void setUp() {
        board = new Board();
        game = new GameManager(board, () -> Tetromino.O);
        game.start();
    }

    // col열의 fromRow행부터 맨 아래까지 칸을 채운다 (열 2개 이하만 채우므로 꽉 찬 줄은 생기지 않음)
    private void fillColumn(int col, int fromRow) {
        for (int row = fromRow; row <= BOTTOM; row++) {
            board.fillCell(row, col, Tetromino.I, null);
        }
    }

    // 왼쪽 끝(0열)만 비고 나머지는 찬 공격 줄 1개
    private static List<GarbageLine> oneGarbageLine() {
        boolean[] filled = new boolean[Board.WIDTH];
        for (int col = 1; col < Board.WIDTH; col++) {
            filled[col] = true;
        }
        return List.of(new GarbageLine(filled));
    }

    @Test
    void 블럭_전체가_버퍼_줄에_고정되면_게임오버() {
        // 생성 위치(4~5열) 바로 아래, 보이는 첫 줄부터 끝까지 막는다
        fillColumn(4, FIRST_VISIBLE_ROW);
        fillColumn(5, FIRST_VISIBLE_ROW);

        game.hardDrop();   // 더 내려갈 곳이 없어 0~1행에 그대로 고정된다

        assertTrue(game.isGameOver());
    }

    @Test
    void 블럭_일부만_버퍼_줄에_걸쳐_고정되면_게임은_계속된다() {
        // 0~1열을 3행부터 막아 두면, 왼쪽 끝으로 옮긴 O는 1~2행에 고정된다
        fillColumn(0, FIRST_VISIBLE_ROW + 1);
        fillColumn(1, FIRST_VISIBLE_ROW + 1);
        for (int i = 0; i < 4; i++) {
            game.moveLeft();
        }

        game.hardDrop();

        assertFalse(game.isGameOver());
        assertNotNull(board.get(FIRST_VISIBLE_ROW - 1, 0), "1행(버퍼)에 블럭이 남아 있어야 함");
        assertNotNull(game.getCurrent(), "다음 블럭이 생성되어야 함");
    }

    @Test
    void 새_블럭이_나올_자리가_막혀_있으면_게임오버() {
        // 지금 블럭은 왼쪽 끝으로 치우고, 다음 블럭이 나올 자리(1행 4열)를 막는다
        for (int i = 0; i < 4; i++) {
            game.moveLeft();
        }
        board.fillCell(FIRST_VISIBLE_ROW - 1, 4, Tetromino.I, null);

        game.hardDrop();

        assertTrue(game.isGameOver());
    }

    @Test
    void 공격_줄에_밀려_블럭이_버퍼_줄로_올라가도_게임은_계속된다() {
        board.fillCell(FIRST_VISIBLE_ROW, 0, Tetromino.I, null);   // 보이는 첫 줄의 0열
        game.receiveGarbage(oneGarbageLine());

        game.hardDrop();   // 블럭을 처리한 뒤, 다음 블럭 전에 공격 줄이 들어온다

        assertFalse(game.isGameOver());
        assertNotNull(board.get(FIRST_VISIBLE_ROW - 1, 0), "블럭이 1행(버퍼)으로 밀려 올라가야 함");
    }

    @Test
    void 공격_줄에_밀려_블럭이_보드_밖으로_나가면_게임오버() {
        board.fillCell(0, 0, Tetromino.I, null);   // 맨 윗줄(0행)에 블럭이 있음
        game.receiveGarbage(oneGarbageLine());

        game.hardDrop();

        assertTrue(game.isGameOver());
    }
}
