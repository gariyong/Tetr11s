package com.tetr11s.game.view;

import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.settings.DefaultPalette;

import javafx.scene.layout.GridPane;

/** 다음 블럭을 4x4 칸에 그린다. (가장 큰 I블럭의 상자가 4x4) */
public class NextBlockView extends GridPane {

    private static final int SIZE = 4;

    private final DefaultPalette palette;
    private final TextCell[][] cells = new TextCell[SIZE][SIZE];

    public NextBlockView(DefaultPalette palette) {
        this.palette = palette;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                TextCell cell = BoardView.createCell();
                cells[r][c] = cell;
                add(cell, c, r);
            }
        }
    }

    public void render(Tetromino next) {
        for (TextCell[] row : cells) {
            for (TextCell cell : row) {
                cell.clear();
            }
        }
        if (next == null) {
            return;
        }
        for (Cell c : next.cells(0)) {
            cells[c.row()][c.col()].show(palette.getSymbol(next), palette.getColor(next), true);
        }
    }
}