package com.tetr11s.game.view;

import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** 다음 블럭을 4x4 픽셀 영역에 그린다. (가장 큰 I블럭의 상자가 4x4) */
public class NextBlockView extends Canvas {

    private static final int SIZE = 4;
    private static final double PADDING = 2;
    private static final double CELL_GAP = 1;

    private final DefaultPalette palette;
    private final GraphicsContext graphics;

    public NextBlockView(DefaultPalette palette) {
        this.palette = palette;
        this.graphics = getGraphicsContext2D();

        setWidth(SIZE * BoardView.CELL_SIZE + PADDING * 2);
        setHeight(SIZE * BoardView.CELL_SIZE + PADDING * 2);
        setFocusTraversable(false);
    }

    public void render(Tetromino next) {
        clear();
        if (next == null) {
            return;
        }

        for (Cell c : next.cells(0)) {
            draw(c, next);
        }
    }

    // 다음 블럭 표시할 땐 블럭만 보이도록 그리드를 지움
    private void clear() {
        graphics.clearRect(0, 0, getWidth(), getHeight());
    }

    /** 다음 블럭의 Cell 좌표를 캔버스 픽셀 영역으로 변환해 그린다. */
    private void draw(Cell cell, Tetromino type) {
        drawCell(cell.row(), cell.col(), type);
    }

    private void drawCell(int row, int col, Tetromino type) {
        double x = PADDING + col * BoardView.CELL_SIZE;
        double y = PADDING + row * BoardView.CELL_SIZE;
        double size = BoardView.CELL_SIZE - CELL_GAP;
        Color color = palette.getColor(type);

        graphics.setFill(color);
        graphics.fillRect(x, y, size, size);
        palette.getPattern(type).draw(graphics, x, y, size, color);
        graphics.setStroke(color.brighter());
        graphics.setLineWidth(1);
        graphics.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    }
}
