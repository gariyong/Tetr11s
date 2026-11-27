package com.tetr11s.game;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.tetr11s.game.battle.GarbageCodec;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 한 플레이어의 화면을 그리는 데 필요한 정보만 담은 값 (만든 뒤에는 바뀌지 않음)
 *
 * 화면에 보이는 줄만 담는다 (버퍼 줄 제외, 0행이 화면 맨 위)
 * 현재 블럭과 고스트도 칸에 미리 넣어 두어서, 받는 쪽은 칸을 순서대로 그리기만 하면 된다
 * 색은 담지 않는다. 블럭 종류만 보내고, 색은 받는 쪽이 자기 팔레트로 정한다
 *
 * 칸 하나는 1바이트다: 비트 0~2 블럭 종류, 비트 3~5 아이템, 비트 6 공격 줄, 비트 7 고스트
 * (3비트씩이라 블럭 종류와 아이템은 각각 7가지까지 담을 수 있다)
 * 값을 꺼낼 때는 typeAt, itemAt, isGarbage, isGhost를 쓴다
 *
 * @param cells        칸 정보 (rows × cols개, 위에서 아래로, 왼쪽에서 오른쪽으로)
 * @param clearingRows 지우는 중인 줄 (비트 r이 1이면 화면 r행). isClearing으로 읽는다
 * @param incoming     받아서 대기 중인 공격 줄 (GarbageCodec 형식). incomingGarbage로 읽는다
 */
public record BoardSnapshot(
        int rows, int cols, byte[] cells, int clearingRows,
        Tetromino next, Item nextItem,
        int score, int level, int lines,
        String incoming, boolean gameOver) implements Serializable {

    private static final int TYPE_MASK = 0b0000_0111;
    private static final int ITEM_SHIFT = 3;
    private static final int ITEM_MASK = 0b0000_0111;
    private static final int GARBAGE_BIT = 0b0100_0000;
    private static final int GHOST_BIT = 0b1000_0000;

    private static final Tetromino[] TYPES = Tetromino.values();
    private static final Item[] ITEMS = Item.values();

    /**
     * 값을 검사하고 배열을 복사한다
     * 네트워크로 받은 스냅샷을 되살릴 때도 이 생성자를 거치므로, 잘못된 값은 여기서 걸러진다
     */
    public BoardSnapshot {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(incoming, "incoming");
        if (rows <= 0 || rows > Integer.SIZE || cols <= 0 || cells.length != (long) rows * cols) {
            throw new IllegalArgumentException("보드 크기가 잘못됨: " + rows + "x" + cols + ", 칸 " + cells.length + "개");
        }
        cells = cells.clone();   // 바깥에서 배열을 바꿔도 스냅샷은 그대로
        for (byte cell : cells) {
            if ((cell & TYPE_MASK) > TYPES.length || ((cell >> ITEM_SHIFT) & ITEM_MASK) > ITEMS.length) {
                throw new IllegalArgumentException("칸 값이 잘못됨: " + cell);
            }
        }
        GarbageCodec.decode(incoming);   // 형식이 잘못됐으면 여기서 예외
    }

    /** 지금 게임 상태를 스냅샷으로 만든다. BoardView가 그리는 순서(쌓인 블럭 -> 고스트 -> 현재 블럭)와 같다. */
    public static BoardSnapshot of(GameManager game) {
        Board board = game.getBoard();
        int rows = board.height() - Board.BUFFER_ROWS;
        int cols = board.width();
        byte[] cells = new byte[rows * cols];

        // 1. 쌓인 블럭
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int row = r + Board.BUFFER_ROWS;
                cells[r * cols + c] = pack(board.get(row, c), board.getItem(row, c), board.isGarbage(row, c), false);
            }
        }

        Block current = game.getCurrent();
        if (current != null) {
            // 2. 고스트 (게임 중일 때만)
            if (!game.isGameOver()) {
                for (Cell cell : game.getGhost().cells()) {
                    put(cells, rows, cols, cell, pack(current.type(), null, false, true));
                }
            }

            // 3. 현재 블럭 (고스트와 겹치면 현재 블럭이 보인다)
            Item item = null;
            List<Cell> itemCells = List.of();
            if (game instanceof ItemGameManager itemGame) {
                item = itemGame.getCurrentItem();
                itemCells = itemGame.getCurrentItemCells();
            }
            for (Cell cell : current.cells()) {
                put(cells, rows, cols, cell, pack(current.type(), itemCells.contains(cell) ? item : null, false, false));
            }
        }

        int clearing = 0;
        for (int row : game.getClearingRows()) {
            int r = row - Board.BUFFER_ROWS;
            if (r >= 0) {
                clearing |= 1 << r;
            }
        }

        return new BoardSnapshot(rows, cols, cells, clearing,
                game.getNext(), game.getNextItemType(),
                game.getScore(), game.getLevel(), game.getLines(),
                GarbageCodec.encode(game.getIncomingGarbage()), game.isGameOver());
    }

    /** 이 칸의 블럭 종류, 빈 칸이면 null (고스트 칸은 현재 블럭의 종류를 돌려준다) */
    public Tetromino typeAt(int row, int col) {
        int code = cell(row, col) & TYPE_MASK;
        return code == 0 ? null : TYPES[code - 1];
    }

    /** 이 칸의 아이템, 없으면 null */
    public Item itemAt(int row, int col) {
        int code = (cell(row, col) >> ITEM_SHIFT) & ITEM_MASK;
        return code == 0 ? null : ITEMS[code - 1];
    }

    /** 상대의 공격으로 생긴 칸인지 판단 */
    public boolean isGarbage(int row, int col) {
        return (cell(row, col) & GARBAGE_BIT) != 0;
    }

    /** 고스트(떨어질 위치 표시) 칸인지 판단 (테두리만 그린다) */
    public boolean isGhost(int row, int col) {
        return (cell(row, col) & GHOST_BIT) != 0;
    }

    /** 이 줄이 지워지는 중인지 판단 (줄 삭제 연출에서 반짝일 줄) */
    public boolean isClearing(int row) {
        Objects.checkIndex(row, rows);
        return (clearingRows & (1 << row)) != 0;
    }

    /** 받아서 대기 중인 공격 줄 (위에서 아래 순서)
     * 스냅샷의 String incoming을 List<> 객체로
     */
    public List<GarbageLine> incomingGarbage() {
        return GarbageCodec.decode(incoming);
    }

    /** 복사본을 돌려준다 (바깥에서 스냅샷 내용을 바꾸지 못하게) */
    @Override
    public byte[] cells() {
        return cells.clone();
    }

    /** 내용이 같으면 같은 스냅샷이다. record가 만들어 주는 equals는 배열을 주소로 비교해서 직접 정의한다 */
    @Override
    public boolean equals(Object o) {
        return o instanceof BoardSnapshot other
                && rows == other.rows && cols == other.cols
                && Arrays.equals(cells, other.cells)
                && clearingRows == other.clearingRows
                && next == other.next && nextItem == other.nextItem
                && score == other.score && level == other.level && lines == other.lines
                && incoming.equals(other.incoming)
                && gameOver == other.gameOver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, cols, Arrays.hashCode(cells), clearingRows,
                next, nextItem, score, level, lines, incoming, gameOver);
    }

    /* 여기부턴 내부처리용 */
    private int cell(int row, int col) {
        Objects.checkIndex(row, rows);
        Objects.checkIndex(col, cols);
        return cells[row * cols + col];
    }

    private static byte pack(Tetromino type, Item item, boolean garbage, boolean ghost) {
        int value = (type == null ? 0 : type.ordinal() + 1)
                | ((item == null ? 0 : item.ordinal() + 1) << ITEM_SHIFT)
                | (garbage ? GARBAGE_BIT : 0)
                | (ghost ? GHOST_BIT : 0);
        return (byte) value;
    }

    // 보드 좌표의 칸에 값을 넣는다. 버퍼 줄이면 넣지 않는다
    private static void put(byte[] cells, int rows, int cols, Cell cell, byte value) {
        int r = cell.row() - Board.BUFFER_ROWS;
        if (r >= 0 && r < rows && cell.col() >= 0 && cell.col() < cols) {
            cells[r * cols + cell.col()] = value;
        }
    }
}