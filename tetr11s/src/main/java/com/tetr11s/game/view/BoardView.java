package com.tetr11s.game.view;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.item.Item;
import com.tetr11s.game.ItemGameManager;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 보드를 캔버스에 그리는 화면.
 *
 * 보이는 20줄은 테두리 안에 그리고, 위쪽 버퍼 2줄은 테두리 밖(위)에 블럭이 있을 때만 그린다.
 * 그래서 막 생성된 블럭이 보드 위로 살짝 튀어나와 보인다.
 * 그리는 순서: 쌓인 블럭 → 고스트 → 현재 블럭 (나중에 그린 것이 위에 보인다)
 */
public class BoardView extends Canvas {

    static final double CELL_SIZE = 30;
    /** 보드 테두리 위에 버퍼 줄을 그릴 빈 공간의 높이 */
    public static final double BUFFER_HEIGHT = Board.BUFFER_ROWS * CELL_SIZE;

    private static final String EMPTY = "·";
    private static final String GHOST = "□";
    private static final String FLASH = "■";
    private static final Color FLASH_COLOR = Color.WHITE;
    private static final Color BACKGROUND_COLOR = Color.rgb(17, 17, 17);
    private static final Color BORDER_COLOR = Color.rgb(136, 136, 136);
    private static final Color GRID_COLOR = Color.rgb(45, 45, 45);
    private static final Color GARBAGE_COLOR = Color.rgb(120, 120, 120);;
    private static final double CELL_GAP = 1;
    private static final double PADDING = 2;
    /** 보이는 첫 줄(r = 0)의 y 좌표. 버퍼 줄은 r이 음수라 이보다 위에 그려진다. */
    private static final double ORIGIN_Y = BUFFER_HEIGHT + PADDING;

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
        setHeight(BUFFER_HEIGHT + rows * CELL_SIZE + PADDING * 2);
        setFocusTraversable(true);
        setOnMousePressed(event -> requestFocus());
    }

    /** 
     * 게임 상태를 읽어서 화면 전체를 다시 그린다. 
     * @param flashOn 줄 삭제 중일 때, 지울 줄을 흰색으로 그릴 차례인가
    */
    public void render(GameManager game, boolean flashOn) {
        Board board = game.getBoard();
        long nowMs = System.currentTimeMillis();
        clear();

        java.util.List<ItemGameManager.MeteorFragment> activeFragments = (game instanceof ItemGameManager itemGame)
                ? itemGame.getActiveMeteorFragments(nowMs)
                : java.util.List.of();
        java.util.Set<Cell> hiddenTargets = new java.util.HashSet<>();
        for (ItemGameManager.MeteorFragment f : activeFragments) {
            hiddenTargets.add(f.to());
        }

        // 1. 쌓인 블럭 (버퍼 줄은 r이 음수이고, 블럭이 있는 칸만 그린다)
        for (int r = -Board.BUFFER_ROWS; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int boardRow = r + Board.BUFFER_ROWS;
                if (r < 0 && board.get(boardRow, c) == null) {
                    continue;   // 버퍼의 빈칸은 배경 없이 비워 둔다
                }

                // 빈칸처리, 물블럭 처리, 가비지 블럭 처리, 일반블럭 처리 분기
                Tetromino type = board.get(boardRow, c);
                if (type == null || hiddenTargets.contains(new Cell(boardRow, c))) {
                    set(r, c, EMPTY, BACKGROUND_COLOR, null);
                } else if (board.isWater(boardRow, c)) {
                    boolean waterAbove = boardRow > 0 && board.isWater(boardRow - 1, c)
                            && (nowMs - board.getWaterStartMs(boardRow - 1, c)) >= 260L;
                    drawWaterCell(r, c, board.getWaterStartMs(boardRow, c), nowMs, waterAbove);
                } else if (board.isGarbage(boardRow, c)) {
                    set(r, c, palette.getSymbol(type), GARBAGE_COLOR, null);
                } else {
                    set(r, c, palette.getSymbol(type), palette.getColor(type), type);
                }
                Item item = board.getItem(boardRow, c);
                if (item != null) {
                    drawItem(r, c, item);
                }
            }
        }

        // 1-1. 줄 삭제 반짝임: 지울 줄을 흰색으로 덮는다 (흰색 차례일 때만)
        if (flashOn) {
            for (int row : game.getClearingRows()) {
                int r = row - Board.BUFFER_ROWS;
                if (r < 0) {
                    continue;   // 버퍼 줄은 화면에 없음
                }
                for (int c = 0; c < cols; c++) {
                    set(r, c, FLASH, FLASH_COLOR, null);
                }
            }
        }

        Block current = game.getCurrent();
        if (current != null) {
            // 2. 고스트 (게임 중일 때만)
            if (!game.isGameOver()) {
                Color color = palette.getColor(current.type());
                for (Cell cell : game.getGhost().cells()) {
                    draw(cell, GHOST, color, current.type());
                }
            }

            // 3. 현재 블럭
            for (Cell cell : current.cells()) {
                draw(cell, palette.getSymbol(current.type()), palette.getColor(current.type()), current.type());
                if (game instanceof ItemGameManager itemGame && itemGame.getCurrentItem() != null
                        && itemGame.getCurrentItemCells().contains(cell)) {
                    drawItem(cell.row() - Board.BUFFER_ROWS, cell.col(), itemGame.getCurrentItem());
                }
            }
        }

        // 4. 메테오 대각선 비산 파편 및 수소폭탄 폭발 연출
        for (ItemGameManager.MeteorFragment f : activeFragments) {
            drawMeteorFragment(f, nowMs);
        }
        if (game instanceof ItemGameManager itemGame) {
            for (ItemGameManager.ExplosionEffect e : itemGame.getActiveExplosions(nowMs)) {
                drawExplosion(e, nowMs);
            }
        }
    }

    /** 보드 좌표의 칸을 화면에 그린다. 버퍼 줄은 테두리 위쪽에 그린다. */
    private void draw(Cell cell, String text, Color color, Tetromino type) {
        int r = cell.row() - Board.BUFFER_ROWS;
        if (r < -Board.BUFFER_ROWS || r >= rows || cell.col() < 0 || cell.col() >= cols) {
            return;
        }
        set(r, cell.col(), text, color, type);
    }

    // 캔버스 새로 그리기
    private void clear() {
        // 버퍼 영역은 투명하게 비우고, 보이는 20줄에만 배경과 테두리를 그린다
        graphics.clearRect(0, 0, getWidth(), getHeight());
        double boardHeight = getHeight() - BUFFER_HEIGHT;
        graphics.setFill(BACKGROUND_COLOR);
        graphics.fillRect(0, BUFFER_HEIGHT, getWidth(), boardHeight);

        graphics.setStroke(BORDER_COLOR);
        graphics.setLineWidth(2);
        graphics.strokeRect(1, BUFFER_HEIGHT + 1, getWidth() - 2, boardHeight - 2);
    }

    private void set(int r, int c, String text, Color color, Tetromino type) {
        drawCell(r, c, text, color, type);
    }

    // 캔버스에 그릴때 CELL_SIZE 곱해서 잘 그립니다
    private void drawCell(int r, int c, String text, Color color, Tetromino type) {
        double x = PADDING + c * CELL_SIZE;
        double y = ORIGIN_Y + r * CELL_SIZE;
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
        
        // 패턴 그리는 부분
        if (type != null) {
            palette.getPattern(type).draw(graphics, x, y, size, color);
        }
        graphics.setStroke(color.brighter());
        graphics.setLineWidth(1);
        graphics.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
        graphics.setStroke(GRID_COLOR);
        graphics.strokeRect(x, y, size, size);
    }

    /** 바닥에서 위로 차오르며 윗면이 찰랑거리는 물 블록을 그린다. */
    private void drawWaterCell(int r, int c, long startMs, long nowMs, boolean waterAbove) {
        set(r, c, EMPTY, BACKGROUND_COLOR, null);

        long elapsed = nowMs - startMs;
        if (elapsed <= 0) {
            return;
        }

        double progress = Math.min(1.0, elapsed / 260.0);
        double x = PADDING + c * CELL_SIZE;
        double y = ORIGIN_Y + r * CELL_SIZE;
        double size = CELL_SIZE - CELL_GAP;

        double waterHeight = size * progress;
        double baseTopY = y + size - waterHeight;
        double waveAmp = (waterAbove && progress >= 1.0) ? 0.6 : 2.2;

        int segments = 8;
        double[] xs = new double[segments + 3];
        double[] ys = new double[segments + 3];
        double phase = nowMs * 0.008 + c * 1.4 + r * 0.6;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double px = x + t * size;
            double wave = Math.sin(phase + t * Math.PI * 2.0) * waveAmp;
            double py = Math.max(y, Math.min(y + size, baseTopY + wave));
            xs[i] = px;
            ys[i] = py;
        }
        xs[segments + 1] = x + size;
        ys[segments + 1] = y + size;
        xs[segments + 2] = x;
        ys[segments + 2] = y + size;

        graphics.setFill(new javafx.scene.paint.LinearGradient(
                0, baseTopY, 0, y + size, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.rgb(120, 220, 255, 0.88)),
                new javafx.scene.paint.Stop(1, Color.rgb(25, 115, 220, 0.92))));
        graphics.fillPolygon(xs, ys, segments + 3);

        if (!waterAbove || progress < 1.0) {
            graphics.setStroke(Color.rgb(220, 248, 255, 0.9));
            graphics.setLineWidth(1.5);
            graphics.strokePolyline(
                    java.util.Arrays.copyOf(xs, segments + 1),
                    java.util.Arrays.copyOf(ys, segments + 1),
                    segments + 1);
        }
        graphics.setStroke(GRID_COLOR);
        graphics.setLineWidth(1);
        graphics.strokeRect(x, y, size, size);
    }

    /** 메테오 파편을 from -> peak(대각선) -> to(낙하) 경로로 그린다. */
    private void drawMeteorFragment(ItemGameManager.MeteorFragment f, long nowMs) {
        double progress = Math.max(0.0, Math.min(1.0,
                (double) (nowMs - f.startMs()) / ItemGameManager.METEOR_SCATTER_DURATION_MS));
        double curRow;
        double curCol;
        if (progress < 0.45) {
            double t = progress / 0.45;
            curRow = f.from().row() + (f.peak().row() - f.from().row()) * t;
            curCol = f.from().col() + (f.peak().col() - f.from().col()) * t;
        } else {
            double t = (progress - 0.45) / 0.55;
            curRow = f.peak().row() + (f.to().row() - f.peak().row()) * t * t;
            curCol = f.peak().col() + (f.to().col() - f.peak().col()) * t;
        }

        double visRow = curRow - Board.BUFFER_ROWS;
        if (visRow < -Board.BUFFER_ROWS || visRow >= rows || curCol < 0 || curCol >= cols) {
            return;
        }
        double x = PADDING + curCol * CELL_SIZE;
        double y = ORIGIN_Y + visRow * CELL_SIZE;
        double size = CELL_SIZE - CELL_GAP;
        Color color = palette.getColor(f.type());

        graphics.setFill(color);
        graphics.fillRect(x, y, size, size);
        if (f.item() != null) {
            Color itemColor = switch (f.item()) {
                case L -> Color.DODGERBLUE;
                case W -> Color.DIMGRAY;
                case H -> Color.WHITE;
                case O -> Color.LIGHTBLUE;
                case M -> Color.RED;
            };
            graphics.setFill(itemColor.deriveColor(0, 1, 1, 0.85));
            graphics.fillRect(x, y, size, size);
            graphics.setFill(Color.BLACK);
            graphics.setFont(javafx.scene.text.Font.font("Monospaced", javafx.scene.text.FontWeight.BOLD, 14));
            graphics.fillText(f.item().name(), x + 9, y + 19);
        }
        graphics.setStroke(Color.WHITE);
        graphics.setLineWidth(1.2);
        graphics.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    }

    /** 수소폭탄(H2) 3x3 폭발을 원형 섬광으로 그린다. */
    private void drawExplosion(ItemGameManager.ExplosionEffect e, long nowMs) {
        double progress = Math.max(0.0, Math.min(1.0,
                (double) (nowMs - e.startMs()) / ItemGameManager.EXPLOSION_DURATION_MS));
        double visRow = e.row() - Board.BUFFER_ROWS;
        double cx = PADDING + (e.col() + 0.5) * CELL_SIZE;
        double cy = ORIGIN_Y + (visRow + 0.5) * CELL_SIZE;
        double radius = CELL_SIZE * (0.6 + progress * 1.4);
        double alpha = 1.0 - progress;

        graphics.setFill(new javafx.scene.paint.RadialGradient(
                0, 0, cx, cy, radius, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.0, Color.rgb(255, 255, 240, alpha)),
                new javafx.scene.paint.Stop(0.5, Color.rgb(255, 190, 50, alpha * 0.85)),
                new javafx.scene.paint.Stop(1.0, Color.rgb(255, 60, 20, 0.0))));
        graphics.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawItem(int r, int c, Item item) {
        if (r < -Board.BUFFER_ROWS || r >= rows || c < 0 || c >= cols) {
            return;
        } // 보드 범위 가드 (버퍼 줄 포함)
        double x = PADDING + c * CELL_SIZE;
        double y = ORIGIN_Y + r * CELL_SIZE;
        double size = CELL_SIZE - CELL_GAP;
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

}