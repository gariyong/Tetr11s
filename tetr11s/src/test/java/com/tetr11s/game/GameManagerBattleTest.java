package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.battle.TestLines;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 대전 모드: GameManager의 공격 보내기, 공격 받기, 다음 블럭 전에 공격 줄 추가, top out.
 *
 * 블럭은 항상 O. O는 0~1행, 4~5열에 생성되고 하드 드롭하면 20~21행에 놓인다.
 */
class GameManagerBattleTest {

    private static final int W = Board.WIDTH;
    private static final int BOTTOM = Board.HEIGHT - 1;   // 21

    private static Supplier<Tetromino> alwaysO() {
        return () -> Tetromino.O;
    }

    /** row 행을 pattern대로 채운다 ('X'는 블럭) */
    private static void fill(Board b, int row, String pattern) {
        for (int col = 0; col < pattern.length(); col++) {
            if (pattern.charAt(col) == 'X') {
                b.fillCell(row, col, Tetromino.T, null);
            }
        }
    }

    /** 보낸 공격을 모아 두는 게임 */
    private static GameManager gameRecording(List<List<GarbageLine>> sent) {
        GameManager g = new GameManager(new Board(), alwaysO());
        g.setOnAttack(sent::add);
        g.start();
        return g;
    }

    // ---------- 공격 보내기 ----------

    @Test
    void 두_줄을_지우면_마지막_블럭_칸을_뺀_모양으로_공격한다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = gameRecording(sent);
        fill(g.getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");

        g.hardDrop();   // O가 두 줄을 채움

        assertEquals(1, sent.size());
        assertEquals(List.of(TestLines.of("XXXX..XXXX"), TestLines.of("XXXX..XXXX")), sent.get(0));
    }

    @Test
    void 한_줄만_지우면_공격하지_않는다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = gameRecording(sent);
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");

        g.hardDrop();

        assertTrue(g.isClearing());   // 줄은 지워지지만
        assertEquals(0, sent.size()); // 공격은 없음
    }

    @Test
    void 줄을_지우지_않으면_공격하지_않는다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = gameRecording(sent);
        g.hardDrop();
        assertEquals(0, sent.size());
    }

    @Test
    void 공격을_연결하지_않아도_1인_게임은_그대로_동작한다() {
        GameManager g = new GameManager(new Board(), alwaysO());
        g.start();
        fill(g.getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");

        g.hardDrop();
        g.finishLineClear();

        assertEquals(2, g.getLines());
        assertFalse(g.isGameOver());
    }

    // ---------- 공격 받기 ----------

    @Test
    void 받은_공격은_바로_보드에_들어가지_않고_대기한다() {
        GameManager g = gameRecording(new ArrayList<>());
        assertEquals(2, g.receiveGarbage(TestLines.holes(W, 0, 2)));

        assertEquals(2, g.getIncomingGarbage().size());
        assertNull(g.getBoard().get(BOTTOM, 1));        // 보드는 아직 그대로
    }

    @Test
    void 블럭을_처리하면_다음_블럭_생성_전에_공격_줄이_보드_아래에_추가된다() {
        GameManager g = gameRecording(new ArrayList<>());
        g.receiveGarbage(List.of(TestLines.hole(W, 0)));   // ".XXXXXXXXX"

        g.hardDrop();   // 줄을 지우지 않는 고정

        Board b = g.getBoard();
        assertTrue(b.isGarbage(BOTTOM, 1));
        assertNull(b.get(BOTTOM, 0));
        assertEquals(Tetromino.O, b.get(BOTTOM - 1, 4));   // O가 한 칸 위로 밀림
        assertEquals(0, g.getIncomingGarbage().size());
        assertEquals(Tetromino.O, g.getCurrent().type());  // 다음 블럭은 정상 생성
    }

    @Test
    void 줄을_지우는_중에는_연출이_끝난_뒤에_공격_줄이_추가된다() {
        GameManager g = gameRecording(new ArrayList<>());
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");
        g.receiveGarbage(List.of(TestLines.hole(W, 0)));

        g.hardDrop();
        assertTrue(g.isClearing());
        assertFalse(g.getBoard().isGarbage(BOTTOM, 1));   // 아직 안 들어감
        assertEquals(1, g.getIncomingGarbage().size());

        g.finishLineClear();
        assertTrue(g.getBoard().isGarbage(BOTTOM, 1));    // 줄 삭제 후, 다음 블럭 전에 들어감
        assertEquals(0, g.getIncomingGarbage().size());
    }

    @Test
    void 공격_줄_때문에_블럭이_맨_위를_넘으면_게임오버() {
        GameManager g = gameRecording(new ArrayList<>());
        for (int row = 1; row <= BOTTOM; row++) {
            fill(g.getBoard(), row, "X.........");          // 0열 기둥 (1행까지)
        }
        g.receiveGarbage(TestLines.holes(W, 1, 2));        // 2줄 → 0~1행이 밀려나감

        g.hardDrop();

        assertTrue(g.isGameOver());
        assertNull(g.getCurrent());                        // 다음 블럭은 생성되지 않음
    }

    @Test
    void 받을_수_있는_줄은_최대_10줄이다() {
        GameManager g = gameRecording(new ArrayList<>());
        assertEquals(8, g.receiveGarbage(TestLines.holes(W, 0, 8)));
        assertEquals(2, g.receiveGarbage(TestLines.holes(W, 0, 5)));
        assertEquals(0, g.receiveGarbage(TestLines.holes(W, 0, 1)));
        assertEquals(10, g.getIncomingGarbage().size());
    }

    @Test
    void 새_게임을_시작하면_대기_중인_공격이_사라진다() {
        GameManager g = gameRecording(new ArrayList<>());
        g.receiveGarbage(TestLines.holes(W, 0, 3));
        g.start();
        assertEquals(0, g.getIncomingGarbage().size());
    }

    @Test
    void setOnAttack에_null은_예외() {
        GameManager g = new GameManager(new Board(), alwaysO());
        assertThrows(NullPointerException.class, () -> g.setOnAttack(null));
    }

    // ---------- 아이템으로 줄을 지우는 경우 (서브클래스가 recordClearedRows 사용) ----------

    /** 줄 삭제 아이템처럼, 고정한 블럭의 맨 위 줄(top=true) 또는 맨 아래 줄을 꽉 차지 않아도 지운다 */
    private static class RowClearingGame extends GameManager {
        private final boolean top;

        RowClearingGame(boolean top) {
            super(new Board(), alwaysO());
            this.top = top;
        }

        @Override
        protected void onBlockLocked(Block locked) {
            int row = top
                    ? locked.cells().stream().mapToInt(Cell::row).min().orElseThrow()
                    : locked.cells().stream().mapToInt(Cell::row).max().orElseThrow();
            List<Integer> rows = List.of(row);
            recordClearedRows(rows);   // 지우기 전에 기록
            board.clearRows(rows);
            score.addLineClear(rows.size());
        }
    }

    /** 블럭만 지우는 아이템처럼 (줄은 지우지 않음) */
    private static class CellClearingGame extends GameManager {
        CellClearingGame() {
            super(new Board(), alwaysO());
        }

        @Override
        protected void onBlockLocked(Block locked) {
            for (int row = 0; row < board.height(); row++) {
                for (int col = 0; col < board.width(); col++) {
                    board.clearCell(row, col);
                }
            }
        }
    }

    @Test
    void 아이템으로_한_줄만_지우면_공격하지_않는다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = new RowClearingGame(true);
        g.setOnAttack(sent::add);
        g.start();
        fill(g.getBoard(), BOTTOM - 1, "XX........");

        g.hardDrop();   // 아이템이 20행을 지움 (꽉 차지 않음)

        assertEquals(0, sent.size());
    }

    @Test
    void 아이템으로_지운_줄과_꽉_찬_줄을_합쳐_2줄이면_공격한다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = new RowClearingGame(true);
        g.setOnAttack(sent::add);
        g.start();
        fill(g.getBoard(), BOTTOM - 1, "XX........");   // 아이템이 지울 줄
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");       // O가 채워서 꽉 찰 줄

        g.hardDrop();

        assertEquals(1, sent.size());
        assertEquals(List.of(TestLines.of("XX........"), TestLines.of("XXXX..XXXX")), sent.get(0));
    }

    @Test
    void 아이템이_아래_줄을_먼저_지워도_마지막_블럭_칸을_정확히_뺀다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = new RowClearingGame(false);   // 블럭의 맨 아래 줄(21행)을 지움
        g.setOnAttack(sent::add);
        g.start();
        fill(g.getBoard(), BOTTOM - 1, "XXXX..XXXX");   // 꽉 찰 줄 (아이템 후 21행으로 내려옴)
        fill(g.getBoard(), BOTTOM, "......XXXX");       // 아이템이 지울 줄

        g.hardDrop();

        // 내려온 줄에서도 O 칸(4, 5열)이 빠져야 한다
        assertEquals(List.of(TestLines.of("......XXXX"), TestLines.of("XXXX..XXXX")), sent.get(0));
    }

    @Test
    void 블럭만_지우는_아이템은_공격하지_않는다() {
        List<List<GarbageLine>> sent = new ArrayList<>();
        GameManager g = new CellClearingGame();
        g.setOnAttack(sent::add);
        g.start();
        fill(g.getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(g.getBoard(), BOTTOM, "XXXX..XXXX");

        g.hardDrop();

        assertEquals(0, sent.size());
    }

    // ---------- 지운 줄 뒤의 칸 위치 계산 ----------

    @Test
    void 지워진_줄의_칸은_빠지고_아래에서_지워진_줄_수만큼_내려간다() {
        List<Cell> cells = List.of(new Cell(3, 0), new Cell(4, 1), new Cell(5, 2), new Cell(7, 3));
        List<Cell> moved = GameManager.cellsAfterClearing(cells, List.of(4, 6));

        // (3,0): 아래에서 4, 6행이 지워짐 → 2칸 / (4,1): 지워짐 / (5,2): 6행만 → 1칸 / (7,3): 없음
        assertEquals(List.of(new Cell(5, 0), new Cell(6, 2), new Cell(7, 3)), moved);
    }
}
