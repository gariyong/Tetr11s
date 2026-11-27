package com.tetr11s.game.view;

import java.util.List;

import com.tetr11s.game.Board;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.battle.GarbageQueue;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 상대에게서 넘어와 대기 중인 공격 줄(최대 10줄)을 미리 보여 주는 미니 캔버스.
 *
 * GameManager가 아니라 List<GarbageLine>만 받으므로 로컬 대전과 P2P(BoardSnapshot.incomingGarbage())
 * 양쪽에서 그대로 재사용할 수 있다.
 */
public class GarbagePreviewView extends Canvas {

    private static final int MAX_ROWS = GarbageQueue.MAX_GARBAGE_LINES;
    private static final int COLS = Board.WIDTH;
    private static final int DANGER_THRESHOLD = 8;

    private static final double CELL_SIZE = 12;
    private static final double CELL_GAP = 1;
    private static final double PADDING = 2;

    private static final Color BACKGROUND_COLOR = Color.rgb(17, 17, 17);
    private static final Color BORDER_COLOR = Color.rgb(136, 136, 136);
    private static final Color DANGER_BORDER_COLOR = Color.rgb(220, 70, 70);
    private static final Color GRID_COLOR = Color.rgb(45, 45, 45);
    private static final Color GARBAGE_COLOR = Color.rgb(140, 140, 140);

    private final int rows;
    private final int cols;
    private final GraphicsContext graphics;

    public GarbagePreviewView() {
        this(COLS, MAX_ROWS);
    }

    public GarbagePreviewView(int cols, int rows) {
        this.rows = rows;
        this.cols = cols;
        this.graphics = getGraphicsContext2D();

        setWidth(cols * CELL_SIZE + PADDING * 2);
        setHeight(rows * CELL_SIZE + PADDING * 2);
        setFocusTraversable(false);
    }

    /**
     * 대기 중인 공격 줄 목록을 아래쪽 정렬로 그린다.
     * - 로컬 대전: render(gameManager.getIncomingGarbage())
     * - P2P 대전: render(snapshot.incomingGarbage())
     */
    public void render(List<GarbageLine> incoming) {
        List<GarbageLine> lines = (incoming == null) ? List.of() : incoming;
        int count = Math.min(lines.size(), rows);

        clear(count >= DANGER_THRESHOLD);

        // 1. 전체 10x10 영역에 빈 격자 배경을 먼저 그린다
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                draw(r, c, false);
            }
        }

        // 2. 대기 중인 공격 줄을 바닥부터 쌓아 올리도록 startRow부터 그린다
        int startRow = rows - count;
        for (int i = 0; i < count; i++) {
            GarbageLine line = lines.get(i);
            int limitCols = Math.min(cols, line.width());
            for (int c = 0; c < limitCols; c++) {
                if (line.isFilled(c)) {
                    draw(startRow + i, c, true);
                }
            }
        }
    }

    private void draw(int r, int c, boolean filled) {
        double x = PADDING + c * CELL_SIZE;
        double y = PADDING + r * CELL_SIZE;
        double size = CELL_SIZE - CELL_GAP;

        if (!filled) {
            graphics.setFill(BACKGROUND_COLOR);
            graphics.fillRect(x, y, size, size);
            graphics.setStroke(GRID_COLOR);
            graphics.setLineWidth(1);
            graphics.strokeRect(x, y, size, size);
            return;
        }

        graphics.setFill(GARBAGE_COLOR);
        graphics.fillRect(x, y, size, size);
        graphics.setStroke(GARBAGE_COLOR.brighter());
        graphics.setLineWidth(1);
        graphics.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    }

    private void clear(boolean danger) {
        graphics.setFill(BACKGROUND_COLOR);
        graphics.fillRect(0, 0, getWidth(), getHeight());

        graphics.setStroke(danger ? DANGER_BORDER_COLOR : BORDER_COLOR);
        graphics.setLineWidth(2);
        graphics.strokeRect(1, 1, getWidth() - 2, getHeight() - 2);
    }
}
