package com.tetr11s.game.view;

import java.util.List;

import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.item.Item;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** 다음 블럭을 4x4 픽셀 영역에 그린다. (가장 큰 I블럭의 상자가 4x4) */
public class NextBlockView extends Canvas {

    private static final int SIZE = 4;
    private static final double PADDING = 2;
    private static final double CELL_GAP = 1;

    private static final List<Cell> METEOR_CELLS = List.of(new Cell(1, 1));
    private static final List<Cell> WEIGHT_CELLS = List.of(
            new Cell(1, 1), new Cell(1, 2),
            new Cell(2, 0), new Cell(2, 1), new Cell(2, 2), new Cell(2, 3));

    private final DefaultPalette palette;
    private final GraphicsContext graphics;

    // true면 블럭을 칸 가운데에 그린다 (홀드 칸). false면 블럭 모양 좌표 그대로 그린다 (NEXT 칸)
    private boolean centered;
    // 가운데 정렬할 때 모든 칸에 더하는 픽셀 이동량
    private double offsetX;
    private double offsetY;

    public NextBlockView(DefaultPalette palette) {
        this.palette = palette;
        this.graphics = getGraphicsContext2D();

        setWidth(SIZE * BoardView.CELL_SIZE + PADDING * 2);
        setHeight(SIZE * BoardView.CELL_SIZE + PADDING * 2);
        setFocusTraversable(false);
    }

    /** 블럭을 칸 가운데에 그릴지 정한다. 다음 render()부터 반영된다. */
    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public void render(Tetromino next) {
        render(next, null, java.util.List.of());
    }

    public void render(Tetromino next, Item item) {
        render(next, item, java.util.List.of());
    }

    // 렌더
    public void render(Tetromino next, Item item, java.util.List<Integer> itemIndices) {
        clear();
        if (item == Item.M) {
            centerOn(METEOR_CELLS);
            drawItem(METEOR_CELLS.get(0), Item.M);
            return;
        }
        if (item == Item.W) {
            centerOn(WEIGHT_CELLS);
            for (Cell c : WEIGHT_CELLS) {
                drawItem(c, Item.W);
            }
            return;
        }
        if (next == null) {
            return;
        }

        java.util.List<Cell> cells = next.cells(0);
        centerOn(cells);
        for (Cell c : cells) {
            draw(c, next);
        }
        if (item != null && !cells.isEmpty()) {
            if (itemIndices != null && !itemIndices.isEmpty()) {
                for (int idx : itemIndices) {
                    if (idx >= 0 && idx < cells.size()) {
                        drawItem(cells.get(idx), item);
                    }
                }
            } else {
                drawItem(cells.get(0), item);
            }
        }
    }

    /** 가운데 정렬이면, 블럭이 차지하는 범위의 중심이 4x4 칸의 중심에 오도록 이동량을 정한다. */
    private void centerOn(List<Cell> cells) {
        if (!centered || cells.isEmpty()) {
            offsetX = 0;
            offsetY = 0;
            return;
        }
        int minRow = cells.stream().mapToInt(Cell::row).min().orElse(0);
        int maxRow = cells.stream().mapToInt(Cell::row).max().orElse(0);
        int minCol = cells.stream().mapToInt(Cell::col).min().orElse(0);
        int maxCol = cells.stream().mapToInt(Cell::col).max().orElse(0);
        double cell = BoardView.CELL_SIZE;
        offsetX = (SIZE - (maxCol - minCol + 1)) * cell / 2 - minCol * cell;
        offsetY = (SIZE - (maxRow - minRow + 1)) * cell / 2 - minRow * cell;
    }

    private void drawItem(Cell cell, Item item) {
        double x = PADDING + offsetX + cell.col() * BoardView.CELL_SIZE;
        double y = PADDING + offsetY + cell.row() * BoardView.CELL_SIZE;
        double size = BoardView.CELL_SIZE - CELL_GAP;
        Color color = switch (item) {
            case L -> Color.DODGERBLUE;
            case W -> Color.DIMGRAY;
            case H -> Color.WHITE;
            case O -> Color.LIGHTBLUE;
            case M -> Color.RED;
        };
        if (item == Item.M) {
            graphics.setFill(new javafx.scene.paint.RadialGradient(0, 0, x + size / 2, y + size / 2,
                    size / 2, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, Color.WHITE),
                    new javafx.scene.paint.Stop(0.45, Color.YELLOW),
                    new javafx.scene.paint.Stop(1, Color.RED)));
        } else {
            graphics.setFill(color.deriveColor(0, 1, 1, 0.85));
        }
        graphics.fillRect(x, y, size, size);
        graphics.setFill(Color.BLACK);
        graphics.setFont(javafx.scene.text.Font.font("Monospaced", javafx.scene.text.FontWeight.BOLD, 14));
        graphics.fillText(item.name(), x + 9, y + 19);
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
        double x = PADDING + offsetX + col * BoardView.CELL_SIZE;
        double y = PADDING + offsetY + row * BoardView.CELL_SIZE;
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
