package com.tetr11s.game;

import java.util.ArrayList;
import java.util.List;

import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.item.Item;

/**
 * 블럭이 쌓이는 격자.
 *
 * 좌표는 Cell과 같은 (row, col)이고 0행이 맨 위다.
 * 위쪽 BUFFER_ROWS줄은 블럭이 생성되는 숨겨진 영역이라 화면에 그리지 않는다.
 * 각 칸에는 그 칸을 채운 블럭 종류를 저장하고, 빈 칸은 null이다.
 *
 * 화면, 타이머, 점수는 모른다. 놓을 수 있는지 검사, 고정, 줄 삭제만 한다.
 */
public class Board{
    public static final int WIDTH = 10;
    public static final int VISIBLE_HEIGHT = 20;
    public static final int BUFFER_ROWS = 2;
    public static final int HEIGHT = VISIBLE_HEIGHT + BUFFER_ROWS;

    private final int width;
    private final int height;
    private final Tetromino[][] grid;
    private final Item[][] items;

    // 기본 보드(너비 10, 보이는 20줄 + 버퍼 2줄)
    public Board(){
        this(WIDTH, HEIGHT);
    }

    public Board(int width, int height){
        if(width <= 0 || height <= 0){
            throw new IllegalArgumentException("보드 크기는 1 이상이어야 함: " + width + "x" + height);
        }

        this.width = width;
        this.height = height;
        this.grid = new Tetromino[height][width];
        this.items = new Item[height][width];
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    // 칸이 보드 범위 안에 있는가
    public boolean isInside(Cell cell){
        return cell.row() >= 0 && cell.row() < height && cell.col() >= 0 && cell.col() < width;
    }

    // 해당 칸의 블럭 종류, 빈칸이면 null
    public Tetromino get(int row, int col){
        if(!isInside(new Cell(row, col))){
            throw new IllegalArgumentException("보드 밖의 칸: (" + row + ", " + col + ")");
        }

        return grid[row][col];
    }

    public Item getItem(int row, int col) {
        if (!isInside(new Cell(row, col))) {
            throw new IllegalArgumentException("보드 밖의 칸: (" + row + ", " + col + ")");
        }
        return items[row][col];
    }

    public void setItem(int row, int col, Item item) {
        if (!isInside(new Cell(row, col))) {
            throw new IllegalArgumentException("보드 밖의 칸: (" + row + ", " + col + ")");
        }
        items[row][col] = item;
    }

    public void fillCell(int row, int col, Tetromino type, Item item) {
        if (!isInside(new Cell(row, col)) || grid[row][col] != null) {
            return;
        }
        grid[row][col] = type;
        items[row][col] = item;
    }

    public void moveItem(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isInside(new Cell(fromRow, fromCol)) || !isInside(new Cell(toRow, toCol))
                || grid[fromRow][fromCol] == null || grid[toRow][toCol] != null) {
            return;
        }
        Tetromino type = grid[fromRow][fromCol];
        Item item = items[fromRow][fromCol];
        clearCell(fromRow, fromCol);
        fillCell(toRow, toCol, type, item);
    }

    public void clearCell(int row, int col) {
        if (!isInside(new Cell(row, col))) {
            return;
        }
        grid[row][col] = null;
        items[row][col] = null;
    }

    // 블럭의 모든 칸이 보드 안에 있고 비어있으면 true
    public boolean canPlace(Block block){
        for(Cell c : block.cells()){
            if(!isInside(c) || grid[c.row()][c.col()] != null){
                return false;
            }
        }

        return true;
    }

    // 블럭을 보드에 고정(각 칸에 블럭 종류를 기록)
    public void lock(Block block){
        if (!canPlace(block)) {
            throw new IllegalStateException("놓을 수 없는 위치에 고정하려 함: " + block);
        }

        for(Cell c : block.cells()){
            grid[c.row()][c.col()] = block.type();
        }
    }

    /**
     * 꽉 찬 줄의 행 번호를 위에서 아래 순서로 돌려준다. 보드는 바꾸지 않는다.
     * (줄 삭제 연출 동안 어느 줄을 반짝일지 알려 주는 용도)
     *
     * @return 꽉 찬 줄이 없으면 빈 리스트
     */
    public List<Integer> fullRows() {
        List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            if (isRowFull(row)) {
                rows.add(row);
            }
        }
        return List.copyOf(rows);
    }

    /**
     * 꽉 찬 줄을 모두 지우고, 그 위의 줄들을 아래로 내린다.
     *
     * 아래에서 위로 읽으면서 꽉 차지 않은 줄만 아래쪽부터 다시 채운다.
     * 남은 위쪽 줄은 새 빈 줄로 채운다.
     *
     * @return 지운 줄 수 (0~4)
     */
    public int clearFullLines() {
        int write = height - 1;
        int cleared = 0;

        for (int read = height - 1; read >= 0; read--) {
            if (isRowFull(read)) {
                cleared++;
                continue;
            }

            grid[write] = grid[read];
            items[write] = items[read];
            write--;
        }

        // 남은 위쪽 줄은 반드시 새 배열로 (같은 줄 배열을 두 곳이 공유하지 않도록)
        for (; write >= 0; write--) {
            grid[write] = new Tetromino[width];
            items[write] = new Item[width];
        }
        return cleared;
    }

    public void clearRows(List<Integer> rows) {
        boolean[] removed = new boolean[height];
        for (int row : rows) {
            if (row >= 0 && row < height) {
                removed[row] = true;
            }
        }
        int write = height - 1;
        for (int read = height - 1; read >= 0; read--) {
            if (removed[read]) {
                continue;
            }
            grid[write] = grid[read];
            items[write] = items[read];
            write--;
        }
        while (write >= 0) {
            grid[write] = new Tetromino[width];
            items[write] = new Item[width];
            write--;
        }
    }

    // 보드 비우기
    public void clear() {
        for (int r = 0; r < height; r++) {
            grid[r] = new Tetromino[width];
            items[r] = new Item[width];
        }
    }

    private boolean isRowFull(int row) {
        for (Tetromino t : grid[row]) {
            if (t == null) {
                return false;
            }
        }
        return true;
    }
}