package com.tetr11s.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.Board;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

class AttackCalculatorTest {

    private static final int W = 10;
    private static final int H = 6;

    /** row 행을 pattern대로 채운다 ('X'는 블럭, '.'은 빈칸). 직접 쌓은 블럭으로 취급 */
    private static void fill(Board b, int row, String pattern) {
        for (int col = 0; col < pattern.length(); col++) {
            if (pattern.charAt(col) == 'X') {
                b.fillCell(row, col, Tetromino.T, null);
            }
        }
    }

    /** 보드 전체를 문자열로 (호출 전후 비교용) */
    private static String snapshot(Board b) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < b.height(); r++) {
            for (int c = 0; c < b.width(); c++) {
                sb.append(b.get(r, c) == null ? '.' : b.isGarbage(r, c) ? 'G' : 'X');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * L 블럭(회전 1)을 (3, 7)에 고정: 8열 3~5행 세로 + (5, 9).
     * 나머지 칸을 채워서 3~5행이 꽉 차게 만든다. (PDF 4페이지 예시와 같은 상황)
     */
    private static Block setUpThreeLinesWithL(Board b) {
        fill(b, 3, "XXXXXXXX.X");
        fill(b, 4, "XXXXXXXX.X");
        fill(b, 5, "XXXXXXXX..");
        Block l = new Block(Tetromino.L, 1, 3, 7);
        b.lock(l);
        return l;
    }

    // ---------- 요구사항: 2줄 이상일 때만 ----------

    @Test
    void 지운_줄이_없으면_공격_없음() {
        Board b = new Board(W, H);
        assertEquals(List.of(), AttackCalculator.calculate(b, List.of(), List.of()));
    }

    @Test
    void 한_줄만_지우면_공격_없음() {
        Board b = new Board(W, H);
        Block l = setUpThreeLinesWithL(b);
        assertEquals(List.of(), AttackCalculator.calculate(b, List.of(5), l.cells()));
    }

    @Test
    void 두_줄_이상_지우면_지운_줄_수만큼_공격한다() {
        Board b = new Board(W, H);
        Block l = setUpThreeLinesWithL(b);
        assertEquals(2, AttackCalculator.calculate(b, List.of(4, 5), l.cells()).size());
        assertEquals(3, AttackCalculator.calculate(b, List.of(3, 4, 5), l.cells()).size());
    }

    @Test
    void 네_줄을_지우면_네_줄_공격한다() {
        Board b = new Board(W, H);
        for (int row = 2; row <= 5; row++) {
            fill(b, row, "XXXXXXXXX.");
        }
        Block i = new Block(Tetromino.I, 1, 2, 7);    // I 세로: 9열 2~5행
        b.lock(i);

        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(2, 3, 4, 5), i.cells());
        assertEquals(4, attack.size());
        for (GarbageLine line : attack) {
            assertEquals("XXXXXXXXX.", line.toString());
        }
    }

    // ---------- 요구사항: 마지막 블럭 부분 제외 ----------

    @Test
    void 마지막_블럭_칸만_빈칸이_된다_PDF_예시() {
        Board b = new Board(W, H);
        Block l = setUpThreeLinesWithL(b);

        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(3, 4, 5), l.cells());

        assertEquals(List.of(
            TestLines.of("XXXXXXXX.X"),
            TestLines.of("XXXXXXXX.X"),
            TestLines.of("XXXXXXXX..")), attack);
    }

    @Test
    void 결과는_위에서_아래_순서다() {
        Board b = new Board(W, H);
        fill(b, 2, "X.XXXXXXXX");
        fill(b, 5, "XX.XXXXXXX");
        // 마지막 블럭이 두 줄의 빈칸을 채웠다고 가정 (떨어진 두 줄)
        b.fillCell(2, 1, Tetromino.T, null);
        b.fillCell(5, 2, Tetromino.T, null);
        List<Cell> last = List.of(new Cell(2, 1), new Cell(5, 2));

        assertEquals(List.of(
            TestLines.of("X.XXXXXXXX"),
            TestLines.of("XX.XXXXXXX")), AttackCalculator.calculate(b, List.of(2, 5), last));
    }

    @Test
    void 지운_줄_밖의_마지막_블럭_칸은_결과에_영향이_없다() {
        Board b = new Board(W, H);
        fill(b, 4, "XXXXXXXXX.");
        fill(b, 5, "XXXXXXXXX.");
        Block i = new Block(Tetromino.I, 1, 2, 7);    // 9열 2~5행 중 4, 5행만 지움
        b.lock(i);

        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(4, 5), i.cells());
        assertEquals(List.of(TestLines.of("XXXXXXXXX."), TestLines.of("XXXXXXXXX.")), attack);
    }

    @Test
    void 이미_받은_공격_줄_칸도_함께_넘어간다() {
        Board b = new Board(W, H);
        b.addGarbageLine(List.of(TestLines.of("XXXXX.XXX."), TestLines.of("XXXXX.XXX.")));
        fill(b, 4, ".....X....");
        fill(b, 5, ".....X....");
        Block i = new Block(Tetromino.I, 1, 2, 7);    // 9열 2~5행
        b.lock(i);

        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(4, 5), i.cells());
        assertEquals(List.of(TestLines.of("XXXXXXXXX."), TestLines.of("XXXXXXXXX.")), attack);
    }

    // ---------- 팀 결정: 빈 줄은 보내지 않음 ----------

    @Test
    void 마지막_블럭_칸을_빼서_빈_줄이_되면_보내지_않는다() {
        Board b = new Board(W, H);
        // 줄 삭제 아이템 상황: 4행은 블럭 칸 하나뿐, 5행은 일부만 차 있음
        Block o = new Block(Tetromino.O, 0, 4, 0);    // (4,0) (4,1) (5,0) (5,1)
        fill(b, 5, "..XXX.....");
        b.lock(o);

        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(4, 5), o.cells());
        assertEquals(List.of(TestLines.of("..XXX.....")), attack);
    }

    @Test
    void 모든_줄이_빈_줄이면_공격_없음() {
        Board b = new Board(W, H);
        Block o = new Block(Tetromino.O, 0, 4, 0);
        b.lock(o);
        assertEquals(List.of(), AttackCalculator.calculate(b, List.of(4, 5), o.cells()));
    }

    // ---------- 보드는 바뀌지 않음 ----------

    @Test
    void 보드를_바꾸지_않는다() {
        Board b = new Board(W, H);
        Block l = setUpThreeLinesWithL(b);
        String before = snapshot(b);

        AttackCalculator.calculate(b, List.of(3, 4, 5), l.cells());

        assertEquals(before, snapshot(b));
    }

    @Test
    void 결과_리스트는_수정할_수_없다() {
        Board b = new Board(W, H);
        Block l = setUpThreeLinesWithL(b);
        List<GarbageLine> attack = AttackCalculator.calculate(b, List.of(3, 4, 5), l.cells());
        assertThrows(UnsupportedOperationException.class, () -> attack.add(TestLines.of("X.........")));
    }

    // ---------- 잘못된 입력 ----------

    @Test
    void null_입력은_예외() {
        Board b = new Board(W, H);
        assertThrows(NullPointerException.class, () -> AttackCalculator.calculate(null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> AttackCalculator.calculate(b, null, List.of()));
        assertThrows(NullPointerException.class, () -> AttackCalculator.calculate(b, List.of(), null));
    }

    @Test
    void 보드_밖_행은_예외() {
        Board b = new Board(W, H);
        assertThrows(IllegalArgumentException.class,
            () -> AttackCalculator.calculate(b, List.of(4, H), List.of()));
    }
}
