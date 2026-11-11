package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.TestLines;
import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Tetromino;

/** 대전 모드: 공격 줄을 보드 아래에 추가하는 기능. 4x6 작은 보드로 확인한다. */
class BoardGarbageTest {

    private static final int W = 4;
    private static final int H = 6;

    /** 보드 한 줄을 문자열로: '.' 빈칸, 'G' 공격 줄, 그 외는 블럭 종류 첫 글자. */
    private static String row(Board b, int r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < b.width(); c++) {
            Tetromino t = b.get(r, c);
            sb.append(t == null ? '.' : b.isGarbage(r, c) ? 'G' : t.name().charAt(0));
        }
        return sb.toString();
    }

    /** O 블럭(2x2)을 topRow, col 위치에 고정. topRow, topRow+1행의 col, col+1열을 채운다. */
    private static void lockO(Board b, int topRow, int col) {
        b.lock(new Block(Tetromino.O, 0, topRow, col));
    }

    /** 보드 전체에서 공격 줄 표시가 몇 칸인지 */
    private static int garbageCount(Board b) {
        int count = 0;
        for (int r = 0; r < b.height(); r++) {
            for (int c = 0; c < b.width(); c++) {
                if (b.isGarbage(r, c)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---------- 요구사항: 아래쪽에 넘어간 형태 그대로 ----------

    @Test
    void 공격_줄은_보드_맨_아래에_모양_그대로_추가된다() {
        Board b = new Board(W, H);
        boolean toppedOut = b.addGarbageLine(List.of(TestLines.of("XX.X"), TestLines.of(".XXX")));

        assertFalse(toppedOut);
        assertEquals("GG.G", row(b, H - 2));   // 리스트의 첫 줄이 위
        assertEquals(".GGG", row(b, H - 1));   // 마지막 줄이 맨 아래
        assertEquals("....", row(b, H - 3));
    }

    @Test
    void 기존_블럭은_추가한_줄_수만큼_위로_밀린다() {
        Board b = new Board(W, H);
        lockO(b, H - 2, 0);                    // 아래 2줄 왼쪽에 O
        b.addGarbageLine(List.of(TestLines.of("XXX.")));

        assertEquals("OO..", row(b, H - 3));
        assertEquals("OO..", row(b, H - 2));
        assertEquals("GGG.", row(b, H - 1));
    }

    @Test
    void 아이템도_블럭과_함께_밀려_올라간다() {
        Board b = new Board(W, H);
        lockO(b, H - 2, 0);
        b.setItem(H - 1, 0, Item.L);
        b.addGarbageLine(List.of(TestLines.of("XXX.")));

        assertEquals(Item.L, b.getItem(H - 2, 0));
        assertEquals(null, b.getItem(H - 1, 0));   // 공격 줄에는 아이템 없음
    }

    @Test
    void 여러_번_추가하면_새_줄이_맨_아래로_들어가고_이전_공격_줄은_위로_밀린다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("X...")));
        b.addGarbageLine(List.of(TestLines.of("...X")));

        assertEquals("G...", row(b, H - 2));
        assertEquals("...G", row(b, H - 1));
    }

    // ---------- 요구사항: 직접 쌓은 블럭과 구분 ----------

    @Test
    void 공격_줄_칸만_isGarbage가_true다() {
        Board b = new Board(W, H);
        lockO(b, H - 3, 0);
        b.addGarbageLine(List.of(TestLines.of("X..X")));

        assertTrue(b.isGarbage(H - 1, 0));
        assertFalse(b.isGarbage(H - 1, 1));    // 빈 칸
        assertFalse(b.isGarbage(H - 2, 0));    // 직접 쌓은 블럭
        assertEquals(Board.GARBAGE_PLACEHOLDER, b.get(H - 1, 0));
    }

    @Test
    void 공격_줄_칸에는_블럭을_놓을_수_없다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("XX.."), TestLines.of("XX..")));
        assertFalse(b.canPlace(new Block(Tetromino.O, 0, H - 2, 0)));
        assertTrue(b.canPlace(new Block(Tetromino.O, 0, H - 2, 2)));
    }

    // ---------- top out ----------

    @Test
    void 맨_위_줄에_블럭이_있으면_밀려나서_top_out이다() {
        Board b = new Board(W, H);
        lockO(b, 0, 0);                        // 0, 1행
        assertTrue(b.addGarbageLine(List.of(TestLines.of("XXX."))));
    }

    @Test
    void 밀려나는_줄_밖에만_블럭이_있으면_top_out이_아니다() {
        Board b = new Board(W, H);
        lockO(b, 1, 0);                        // 1, 2행. 1줄 추가하면 0행만 밀려나는데 0행은 비어 있음
        assertFalse(b.addGarbageLine(List.of(TestLines.of("XXX."))));
        assertEquals("OO..", row(b, 0));
    }

    @Test
    void 빈_리스트면_아무것도_바뀌지_않는다() {
        Board b = new Board(W, H);
        lockO(b, H - 2, 0);
        assertFalse(b.addGarbageLine(List.of()));
        assertEquals("OO..", row(b, H - 1));
    }

    // ---------- 줄 삭제, 칸 삭제와 함께 ----------

    @Test
    void 공격_줄이_꽉_차서_지워지면_위의_공격_줄도_표시와_함께_내려온다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("X..X"), TestLines.of("XX.X")));
        b.lock(new Block(Tetromino.I, 1, 2, 0));   // I 세로: col 2, 2~5행 → 맨 아래 줄만 꽉 참

        assertEquals(1, b.clearFullLines());
        assertEquals("G.IG", row(b, H - 1));       // 위에 있던 공격 줄이 내려옴
        assertEquals("..I.", row(b, H - 2));       // I 칸은 공격 줄이 아님
    }

    @Test
    void clearRows로_지워도_공격_줄_표시가_함께_움직인다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("X...")));
        lockO(b, H - 3, 2);                        // 공격 줄 바로 위 2줄
        b.clearRows(List.of(H - 2));

        assertEquals("..OO", row(b, H - 2));
        assertEquals("G...", row(b, H - 1));
    }

    @Test
    void clearFullLines로_아래_공격_줄이_지워지면_위_공격_줄의_표시가_내려온다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("X..."), TestLines.of("XXXX")));   // 맨 아래는 꽉 찬 줄
        b.clearFullLines();

        assertEquals("G...", row(b, H - 1));
        assertEquals(1, garbageCount(b));          // 표시가 남거나 엉뚱한 칸에 있으면 안 됨
    }

    @Test
    void clearRows로_아래_공격_줄이_지워지면_위_공격_줄의_표시가_내려온다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("X..."), TestLines.of("...X")));
        b.clearRows(List.of(H - 1));

        assertTrue(b.isGarbage(H - 1, 0));
        assertFalse(b.isGarbage(H - 1, 3));
        assertEquals(1, garbageCount(b));
    }

    @Test
    void 공격_줄을_지우면_표시도_사라진다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("XXXX")));   // 꽉 찬 공격 줄
        b.clearFullLines();
        assertEquals(0, garbageCount(b));
    }

    @Test
    void clearCell은_공격_줄_표시도_지운다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("XXX.")));
        b.clearCell(H - 1, 0);

        assertFalse(b.isGarbage(H - 1, 0));        // 빈 칸이 된 곳은 공격 줄이 아님
        assertTrue(b.isGarbage(H - 1, 1));
    }

    @Test
    void clear하면_공격_줄_표시도_모두_사라진다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("XXX."), TestLines.of(".XXX")));
        b.clear();
        assertEquals(0, garbageCount(b));
    }

    // ---------- 잘못된 입력 ----------

    @Test
    void 보드보다_좁은_줄은_예외이고_보드는_그대로다() {
        Board b = new Board(W, H);
        lockO(b, H - 2, 0);
        assertThrows(IllegalArgumentException.class,
            () -> b.addGarbageLine(List.of(TestLines.of("XX."))));
        assertEquals("OO..", row(b, H - 1));
    }

    @Test
    void 보드보다_넓은_줄도_예외이고_보드는_그대로다() {
        Board b = new Board(W, H);
        lockO(b, H - 2, 0);
        assertThrows(IllegalArgumentException.class,
            () -> b.addGarbageLine(List.of(TestLines.of("XX.XX"))));
        assertEquals("OO..", row(b, H - 1));
    }

    @Test
    void 보드_높이보다_많은_줄은_예외() {
        Board b = new Board(W, 2);
        assertThrows(IllegalArgumentException.class, () -> b.addGarbageLine(List.of(
            TestLines.of("X..."), TestLines.of("X..."), TestLines.of("X..."))));
    }

    @Test
    void isGarbage에_보드_밖_칸을_넣으면_예외() {
        Board b = new Board(W, H);
        assertThrows(IllegalArgumentException.class, () -> b.isGarbage(H, 0));
        assertThrows(IllegalArgumentException.class, () -> b.isGarbage(0, -1));
    }
}
