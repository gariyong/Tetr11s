package com.tetr11s.game.tetromino;

import java.util.ArrayList;
import java.util.List;

public record Block(Tetromino type, int rotation, int row, int col, List<Cell> shape) {
    public Block {
        if (type == null) {
            throw new IllegalArgumentException("type은 null일 수 없음");
        }
        rotation = Math.floorMod(rotation, Tetromino.ROTATION_COUNT);
        shape = shape == null ? List.of() : List.copyOf(shape);
    }

    public static Block spawn(Tetromino type, int boardWidth) {
        int col = (boardWidth - type.size()) / 2;
        return new Block(type, 0, 0, col);
    }

    public Block(Tetromino type, int rotation, int row, int col) {
        this(type, rotation, row, col, List.of());
    }

    public static Block special(List<Cell> shape, int boardWidth) {
        int width = shape.stream().mapToInt(Cell::col).max().orElse(0) + 1;
        return new Block(Tetromino.O, 0, 0, (boardWidth - width) / 2, shape);
    }

    public Block move(int dRow, int dCol) {
        return new Block(type, rotation, row + dRow, col + dCol, shape);
    }

    public Block rotate() {
        return shape.isEmpty() ? new Block(type, rotation + 1, row, col) : this;
    }

    public List<Cell> cells() {
        List<Cell> result = new ArrayList<>();
        for (Cell cell : shape.isEmpty() ? type.cells(rotation) : shape) {
            result.add(new Cell(row + cell.row(), col + cell.col()));
        }
        return List.copyOf(result);
    }
}
