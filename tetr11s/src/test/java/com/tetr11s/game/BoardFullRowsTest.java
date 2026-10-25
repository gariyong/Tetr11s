package com.tetr11s.game;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Tetromino;

class BoardFullRowsTest {

    // I 블럭 회전 0은 블럭 안의 1행에 가로 4칸 → row-1 위치에 두면 보드 row행 0~3열 / 4~7열
    private static void fillRow(Board board, int row) {
        board.lock(new Block(Tetromino.I, 0, row - 1, 0));
        board.lock(new Block(Tetromino.I, 0, row - 1, 4));
        board.lock(new Block(Tetromino.O, 0, row - 1, 8));   // row-1, row행의 8~9열
    }

    @Test
    void 빈_보드에는_꽉_찬_줄이_없다() {
        assertEquals(List.of(), new Board().fullRows());
    }

    @Test
    void 꽉_찬_줄을_위에서_아래_순서로_알려준다() {
        Board board = new Board();
        fillRow(board, 21);
        fillRow(board, 18);

        assertEquals(List.of(18, 21), board.fullRows());
    }

    @Test
    void fullRows는_보드를_바꾸지_않는다() {
        Board board = new Board();
        fillRow(board, 21);

        board.fullRows();

        assertEquals(List.of(21), board.fullRows());
        assertEquals(1, board.clearFullLines());
        assertEquals(List.of(), board.fullRows());
    }
}