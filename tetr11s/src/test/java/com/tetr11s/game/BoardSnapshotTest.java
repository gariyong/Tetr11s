package com.tetr11s.game;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.battle.TestLines;
import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

class BoardSnapshotTest {

    private static final int BOTTOM = Board.HEIGHT - 1;                // 보드 맨 아래 행
    private static final int VIEW_BOTTOM = Board.VISIBLE_HEIGHT - 1;   // 화면 맨 아래 행

    /** 항상 O 블럭이 나오는 게임 (시작한 상태) */
    private static GameManager oGame() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O);
        game.start();
        return game;
    }

    private static void fill(Board board, int row, String pattern) {
        for (int col = 0; col < pattern.length(); col++) {
            if (pattern.charAt(col) == 'X') {
                board.fillCell(row, col, Tetromino.T, null);
            }
        }
    }

    /** 보드 좌표의 행을 화면 좌표로 */
    private static int view(Cell cell) {
        return cell.row() - Board.BUFFER_ROWS;
    }

    // ---------- 칸 ----------

    @Test
    void 화면에_보이는_줄만_담는다() {
        BoardSnapshot s = BoardSnapshot.of(oGame());

        assertEquals(Board.VISIBLE_HEIGHT, s.rows());
        assertEquals(Board.WIDTH, s.cols());
    }

    @Test
    void 쌓인_블럭의_종류와_아이템과_공격_줄_표시를_담는다() {
        GameManager game = oGame();
        Board board = game.getBoard();
        board.addGarbageLine(List.of(TestLines.hole(Board.WIDTH, 0)));   // 맨 아래 줄: 0열만 빈 공격 줄
        board.fillCell(BOTTOM - 1, 9, Tetromino.T, Item.H);              // 그 위 줄 9열: 수소가 든 T 칸

        BoardSnapshot s = BoardSnapshot.of(game);

        assertNull(s.typeAt(VIEW_BOTTOM, 0));
        assertFalse(s.isGarbage(VIEW_BOTTOM, 0));
        assertEquals(Board.GARBAGE_PLACEHOLDER, s.typeAt(VIEW_BOTTOM, 1));
        assertTrue(s.isGarbage(VIEW_BOTTOM, 1));
        assertNull(s.itemAt(VIEW_BOTTOM, 1));

        assertEquals(Tetromino.T, s.typeAt(VIEW_BOTTOM - 1, 9));
        assertEquals(Item.H, s.itemAt(VIEW_BOTTOM - 1, 9));
        assertFalse(s.isGarbage(VIEW_BOTTOM - 1, 9));
        assertFalse(s.isGhost(VIEW_BOTTOM - 1, 9));
    }

    @Test
    void 현재_블럭과_고스트를_칸에_담는다() {
        GameManager game = oGame();
        for (int i = 0; i < 4; i++) {
            game.tick();   // 버퍼 줄을 벗어나 화면 안으로 내려온다
        }

        BoardSnapshot s = BoardSnapshot.of(game);

        for (Cell cell : game.getCurrent().cells()) {
            assertEquals(Tetromino.O, s.typeAt(view(cell), cell.col()));
            assertFalse(s.isGhost(view(cell), cell.col()));
        }
        for (Cell cell : game.getGhost().cells()) {
            assertEquals(Tetromino.O, s.typeAt(view(cell), cell.col()));
            assertTrue(s.isGhost(view(cell), cell.col()));
        }
        assertNull(s.typeAt(0, 0));
        assertFalse(s.isGhost(0, 0));
    }

    @Test
    void 고스트와_겹치면_현재_블럭으로_보인다() {
        GameManager game = oGame();
        while (game.getCurrent().row() != game.getGhost().row()) {
            game.tick();   // 바닥에 닿을 때까지 (아직 고정 전)
        }

        BoardSnapshot s = BoardSnapshot.of(game);

        for (Cell cell : game.getCurrent().cells()) {
            assertEquals(Tetromino.O, s.typeAt(view(cell), cell.col()));
            assertFalse(s.isGhost(view(cell), cell.col()));
        }
    }

    @Test
    void 아이템전에서는_현재_블럭의_아이템_칸을_담는다() {
        // 버그판: 5번 고정할 때마다 아이템 블럭이 나온다 (어떤 아이템인지는 무작위)
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, true, () -> Tetromino.O);
        game.start();
        for (int i = 0; i < 5; i++) {
            game.hardDrop();
        }
        for (int i = 0; i < 4; i++) {
            game.tick();
        }
        Item item = game.getCurrentItem();
        List<Cell> itemCells = game.getCurrentItemCells();
        assertNotNull(item);
        assertFalse(itemCells.isEmpty());

        BoardSnapshot s = BoardSnapshot.of(game);

        for (Cell cell : game.getCurrent().cells()) {
            Item expected = itemCells.contains(cell) ? item : null;
            assertEquals(expected, s.itemAt(view(cell), cell.col()));
        }
    }

    // ---------- 칸 밖의 정보 ----------

    @Test
    void 지우는_중인_줄을_알려준다() {
        GameManager game = oGame();
        fill(game.getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(game.getBoard(), BOTTOM, "XXXX..XXXX");
        game.hardDrop();   // O 블럭이 4, 5열을 채워서 두 줄이 '줄 삭제 중'이 된다
        assertTrue(game.isClearing());

        BoardSnapshot s = BoardSnapshot.of(game);

        assertTrue(s.isClearing(VIEW_BOTTOM));
        assertTrue(s.isClearing(VIEW_BOTTOM - 1));
        assertFalse(s.isClearing(VIEW_BOTTOM - 2));
        // 줄 삭제 중에는 현재 블럭이 없으니 고스트도 없다
        for (int r = 0; r < s.rows(); r++) {
            for (int c = 0; c < s.cols(); c++) {
                assertFalse(s.isGhost(r, c));
            }
        }
    }

    @Test
    void 다음_블럭과_점수와_대기_중인_공격_줄을_담는다() {
        GameManager game = oGame();
        List<GarbageLine> attack = TestLines.holes(Board.WIDTH, 2, 3);
        game.receiveGarbage(attack);
        game.softDrop();   // 1점

        BoardSnapshot s = BoardSnapshot.of(game);

        assertEquals(Tetromino.O, s.next());
        assertNull(s.nextItem());
        assertEquals(1, s.score());
        assertEquals(1, s.level());
        assertEquals(0, s.lines());
        assertEquals(attack, s.incomingGarbage());
        assertFalse(s.gameOver());
    }

    // ---------- 값으로서의 성질 ----------

    @Test
    void 상태가_같으면_같고_바뀌면_다르다() {
        GameManager a = oGame();
        GameManager b = oGame();
        assertEquals(BoardSnapshot.of(a), BoardSnapshot.of(b));
        assertEquals(BoardSnapshot.of(a).hashCode(), BoardSnapshot.of(b).hashCode());

        b.moveLeft();

        assertNotEquals(BoardSnapshot.of(a), BoardSnapshot.of(b));
    }

    @Test
    void 바깥에서_배열을_바꿔도_스냅샷은_그대로다() {
        BoardSnapshot s = BoardSnapshot.of(oGame());

        s.cells()[0] = 1;

        assertEquals(BoardSnapshot.of(oGame()), s);
    }

    @Test
    void 잘못된_값으로는_만들_수_없다() {
        byte[] ok = new byte[20 * 10];
        byte[] badItem = new byte[20 * 10];
        badItem[0] = 7 << 3;   // 아이템 자리에 없는 번호

        // 칸 수가 rows × cols와 다름
        assertThrows(IllegalArgumentException.class,
                () -> new BoardSnapshot(20, 10, new byte[199], 0, null, null, 0, 1, 0, "", false));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardSnapshot(20, 10, badItem, 0, null, null, 0, 1, 0, "", false));
        // 공격 줄 형식이 잘못됨
        assertThrows(IllegalArgumentException.class,
                () -> new BoardSnapshot(20, 10, ok, 0, null, null, 0, 1, 0, "X?", false));
    }

    // ---------- 직렬화 ----------

    /** 아래 10줄이 차 있고, 대기 중인 공격 줄도 최대(10줄)인 게임 */
    private static GameManager busyGame() {
        GameManager game = oGame();
        for (int row = BOTTOM - 9; row <= BOTTOM; row++) {
            fill(game.getBoard(), row, "XXXX..XXX.");
        }
        game.receiveGarbage(TestLines.holes(Board.WIDTH, 0, 10));
        return game;
    }

    @Test
    void 직렬화했다가_되돌리면_같은_스냅샷이다() throws Exception {
        BoardSnapshot s = BoardSnapshot.of(busyGame());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(s);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(s, in.readObject());
        }
    }

    @Test
    void 이어서_보내는_스냅샷은_400바이트를_넘지_않는다() throws Exception {
        // 한 연결에서 처음 보낼 때만 클래스 설명이 같이 가므로, 두 번째 것의 크기를 잰다
        GameManager game = busyGame();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(BoardSnapshot.of(game));
        out.flush();
        int before = bytes.size();

        game.tick();
        out.writeObject(BoardSnapshot.of(game));
        out.flush();

        int size = bytes.size() - before;
        assertTrue(size <= 400, "스냅샷 크기: " + size + "바이트");
    }
}