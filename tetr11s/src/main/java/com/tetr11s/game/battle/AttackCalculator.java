package com.tetr11s.game.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tetr11s.game.Board;
import com.tetr11s.game.tetromino.Cell;

public final class AttackCalculator {

    // 공격이 발생하는 최소 줄 수
    public static final int MIN_LINES_FOR_ATTACK = 2;

    private AttackCalculator() {
    }

    public static List<GarbageLine> calculate(Board board, List<Integer> clearedRows, List<Cell> lastBlockCells) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(clearedRows, "clearedRows");
        Objects.requireNonNull(lastBlockCells, "lastBlockCells");

        // 2줄 미만이면 공격 없음
        if (clearedRows.size() < MIN_LINES_FOR_ATTACK) {
            return List.of();
        }

        // 지운 줄마다 마지막 블럭 칸을 뺀 모양을 만든다
        List<GarbageLine> result = new ArrayList<>();
        for (int row : clearedRows) {
            boolean[] filled = new boolean[board.width()];
            boolean any = false;

            for (int col = 0; col < board.width(); col++) {
                boolean occupied = board.get(row, col) != null;
                boolean lastBlock = lastBlockCells.contains(new Cell(row, col));
                filled[col] = occupied && !lastBlock;
                any |= filled[col];
            }

            // 빈 줄은 보내지 않는다
            if (any) {
                result.add(new GarbageLine(filled));
            }
        }

        // 위에서 아래 순서 그대로 반환
        return List.copyOf(result);
    }
}