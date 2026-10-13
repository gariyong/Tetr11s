package com.tetr11s.game.view;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 보드를 글자로 그리는 화면.
 *
 * <p>칸 하나 = TextCell 하나. 보이는 20줄만 그리고, 위쪽 버퍼 줄은 그리지 않는다.
 * 그리는 순서: 쌓인 블럭 → 고스트 → 현재 블럭 (나중에 그린 것이 위에 보인다)
 */
public class BoardView extends GridPane {

    /** 칸 하나의 크기(px). 이 값만 바꾸면 칸과 블럭이 같이 커지고 작아진다. */
    static final double CELL_SIZE = 30;

    /** 빈 칸의 점(·) 크기에만 영향을 준다. 블럭은 TextCell이 칸에 맞게 늘려서 그린다. */
    static final Font CELL_FONT = Font.font("Monospaced", FontWeight.BOLD, CELL_SIZE * 0.8);

    private static final String EMPTY = "·";
    private static final String GHOST = "□";
    private static final Color EMPTY_COLOR = Color.rgb(70, 70, 70);

    private final DefaultPalette palette;
    private final int rows;
    private final int cols;
    private final TextCell[][] cells;   // [화면 줄][열]

    public BoardView(DefaultPalette palette, int cols, int visibleRows) {
        this.palette = palette;
        this.rows = visibleRows;
        this.cols = cols;
        this.cells = new TextCell[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                TextCell cell = createCell();
                cells[r][c] = cell;
                add(cell, c, r);
            }
        }
        setStyle("-fx-background-color: #111111;"
                + "-fx-border-color: #888888;"
                + "-fx-border-width: 2;");
    }

    /** 게임 상태를 읽어서 화면 전체를 다시 그린다. */
    public void render(GameManager game) {
        Board board = game.getBoard();

        // 1. 쌓인 블럭 (버퍼 줄은 건너뜀)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Tetromino type = board.get(r + Board.BUFFER_ROWS, c);
                if (type == null) {
                    cells[r][c].show(EMPTY, EMPTY_COLOR, false);
                } else {
                    cells[r][c].show(palette.getSymbol(type), palette.getColor(type), true);
                }
            }
        }

        Block current = game.getCurrent();
        if (current == null) {
            return;
        }

        // 2. 고스트 (게임 중일 때만)
        if (!game.isGameOver()) {
            Color color = palette.getColor(current.type());
            for (Cell cell : game.getGhost().cells()) {
                draw(cell, GHOST, color);
            }
        }

        // 3. 현재 블럭
        for (Cell cell : current.cells()) {
            draw(cell, palette.getSymbol(current.type()), palette.getColor(current.type()));
        }
    }

    /** 보드 좌표의 칸에 블럭을 그린다. 버퍼 줄이면 그리지 않는다. */
    private void draw(Cell cell, String symbol, Color color) {
        int r = cell.row() - Board.BUFFER_ROWS;
        if (r < 0 || r >= rows || cell.col() < 0 || cell.col() >= cols) {
            return;
        }
        cells[r][cell.col()].show(symbol, color, true);
    }

    /** 칸 하나. NextBlockView도 같은 모양을 쓰기 위해 함께 사용한다. */
    static TextCell createCell() {
        return new TextCell(CELL_SIZE, CELL_FONT);
    }
}