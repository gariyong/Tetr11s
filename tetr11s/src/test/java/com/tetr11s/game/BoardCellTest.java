package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Tetromino;

/** 아이템·물 칸을 다루는 Board 메서드와 잘못된 입력 처리 */
class BoardCellTest {

    @Test
    void 크기가_0이하인_보드는_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new Board(0, 20));
        assertThrows(IllegalArgumentException.class, () -> new Board(10, -1));
    }

    @Test
    void 보드_밖의_아이템을_읽거나_쓰면_예외() {
        Board board = new Board();
        assertThrows(IllegalArgumentException.class, () -> board.getItem(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> board.setItem(0, board.width(), Item.L));
    }

    @Test
    void 아이템_묶음_번호는_아이템을_지우면_0이_된다() {
        Board board = new Board();
        board.fillCell(10, 3, Tetromino.T, null);
        board.setItem(10, 3, Item.H, 7);
        assertEquals(Item.H, board.getItem(10, 3));
        assertEquals(7, board.getItemGroup(10, 3));

        board.setItem(10, 3, null, 7);
        assertNull(board.getItem(10, 3));
        assertEquals(0, board.getItemGroup(10, 3));
    }

    @Test
    void 이미_찬_칸이나_보드_밖은_채우지_않는다() {
        Board board = new Board();
        board.fillCell(5, 5, Tetromino.T, null);
        board.fillCell(5, 5, Tetromino.Z, Item.L);
        board.fillCell(-1, 5, Tetromino.Z, null);

        assertEquals(Tetromino.T, board.get(5, 5));
        assertNull(board.getItem(5, 5));
    }

    @Test
    void 물_칸은_아이템을_지우고_시작_시각을_기억한다() {
        Board board = new Board();
        assertFalse(board.hasWater());

        board.fillWaterCell(15, 2, Tetromino.I, 1234L);
        board.fillWaterCell(15, 2, Tetromino.Z, 9999L);   // 이미 찬 칸은 무시
        board.fillWaterCell(99, 2, Tetromino.Z, 9999L);   // 보드 밖도 무시

        assertTrue(board.hasWater());
        assertTrue(board.isWater(15, 2));
        assertFalse(board.isWater(15, 3));
        assertEquals(1234L, board.getWaterStartMs(15, 2));
        assertEquals(Tetromino.I, board.get(15, 2));
    }

    @Test
    void 칸_옮기기는_블럭과_아이템을_함께_옮긴다() {
        Board board = new Board();
        board.fillCell(10, 1, Tetromino.S, Item.O);

        board.moveItem(10, 1, 12, 4);

        assertNull(board.get(10, 1));
        assertEquals(Tetromino.S, board.get(12, 4));
        assertEquals(Item.O, board.getItem(12, 4));
    }

    @Test
    void 빈칸에서_옮기거나_찬칸으로_옮기거나_보드밖이면_아무것도_안한다() {
        Board board = new Board();
        board.fillCell(10, 1, Tetromino.S, null);
        board.fillCell(10, 2, Tetromino.Z, null);

        board.moveItem(11, 1, 12, 1);     // 출발 칸이 비었음
        board.moveItem(10, 1, 10, 2);     // 도착 칸이 찼음
        board.moveItem(10, 1, 10, 99);    // 도착 칸이 보드 밖
        board.moveItem(-5, 1, 12, 1);     // 출발 칸이 보드 밖

        assertEquals(Tetromino.S, board.get(10, 1));
        assertEquals(Tetromino.Z, board.get(10, 2));
        assertNull(board.get(12, 1));
    }

    @Test
    void 놓을_수_없는_자리에_고정하면_예외() {
        Board board = new Board();
        Block block = Block.spawn(Tetromino.O, board.width());
        board.lock(block);
        assertThrows(IllegalStateException.class, () -> board.lock(block));
    }
}
