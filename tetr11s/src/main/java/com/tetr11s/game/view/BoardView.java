package com.tetr11s.game.view;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 보드를 캔버스에 그리는 화면.
 *
 * 보이는 20줄만 그리고, 위쪽 버퍼 줄은 그리지 않는다.
 * 그리는 순서: 쌓인 블럭 → 고스트 → 현재 블럭 (나중에 그린 것이 위에 보인다)
 */
public class BoardView extends Canvas {

    static final double CELL_SIZE = 26;

    private static final String EMPTY = "·";
    private static final String GHOST = "□";
    private static final Color BACKGROUND_COLOR = Color.rgb(17, 17, 17);
    private static final Color BORDER_COLOR = Color.rgb(136, 136, 136);
    private static final Color GRID_COLOR = Color.rgb(45, 45, 45);
    private static final double CELL_GAP = 1;
    private static final double PADDING = 2;

    private final DefaultPalette palette;
    private final int rows;
    private final int cols;
    private final GraphicsContext graphics;

    public BoardView(DefaultPalette palette, int cols, int visibleRows) {
        this.palette = palette;
        this.rows = visibleRows;
        this.cols = cols;
        this.graphics = getGraphicsContext2D();

        setWidth(cols * CELL_SIZE + PADDING * 2);
        setHeight(rows * CELL_SIZE + PADDING * 2);
        setFocusTraversable(true);
        setOnMousePressed(event -> requestFocus());
    }

    /** 게임 상태를 읽어서 화면 전체를 다시 그린다. */
    public void render(GameManager game) {
        Board board = game.getBoard();
        clear();

        // 1. 쌓인 블럭 (버퍼 줄은 건너뜀)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Tetromino type = board.get(r + Board.BUFFER_ROWS, c);
                if (type == null) {
                    set(r, c, EMPTY, BACKGROUND_COLOR);
                } else {
                    set(r, c, palette.getSymbol(type), palette.getColor(type));
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

    /** 보드 좌표의 칸을 화면에 그린다. 버퍼 줄이면 그리지 않는다. */
    private void draw(Cell cell, String text, Color color) {
        int r = cell.row() - Board.BUFFER_ROWS;
        if (r < 0 || r >= rows || cell.col() < 0 || cell.col() >= cols) {
            return;
        }
        set(r, cell.col(), text, color);
    }

    // 캔버스 새로 그리기
    private void clear() {
        graphics.setFill(BACKGROUND_COLOR);
        graphics.fillRect(0, 0, getWidth(), getHeight());

        graphics.setStroke(BORDER_COLOR);
        graphics.setLineWidth(2);
        graphics.strokeRect(1, 1, getWidth() - 2, getHeight() - 2);
    }

    private void set(int r, int c, String text, Color color) {
        drawCell(r, c, text, color);
    }

    // 캔버스에 그릴때 CELL_SIZE 곱해서 잘 그립니다
    private void drawCell(int r, int c, String text, Color color) {
        double x = PADDING + c * CELL_SIZE;
        double y = PADDING + r * CELL_SIZE;
        double size = CELL_SIZE - CELL_GAP;

        if (EMPTY.equals(text)) {
            graphics.setFill(BACKGROUND_COLOR);
            graphics.fillRect(x, y, size, size);
            graphics.setStroke(GRID_COLOR);
            graphics.setLineWidth(1);
            graphics.strokeRect(x, y, size, size);
            return;
        }

        if (GHOST.equals(text)) {
            graphics.setFill(Color.TRANSPARENT);
            graphics.fillRect(x, y, size, size);
            graphics.setStroke(color.deriveColor(0, 1, 1, 0.75));
            graphics.setLineWidth(2);
            graphics.strokeRect(x + 1, y + 1, size - 2, size - 2);
            return;
        }

        graphics.setFill(color);
        graphics.fillRect(x, y, size, size);
        graphics.setStroke(color.brighter());
        graphics.setLineWidth(1);
        graphics.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
        graphics.setStroke(GRID_COLOR);
        graphics.strokeRect(x, y, size, size);
    }

}