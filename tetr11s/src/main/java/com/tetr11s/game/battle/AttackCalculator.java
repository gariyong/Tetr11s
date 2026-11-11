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

    /**
     * 공격 규칙(2줄 이상)은 보지 않고, 지운 줄을 공격 줄 모양으로만 바꾼다.
     * 한 번의 고정에서 줄이 여러 번 나눠 지워질 때(줄 삭제 아이템 + 꽉 찬 줄) 모아서 판단하려고 쓴다.
     * 마지막 블럭 칸을 빼고 나서 완전히 빈 줄은 결과에 넣지 않는다.
     *
     * @return 공격 줄 (위에서 아래 순서)
     */
    public static List<GarbageLine> linesOf(Board board, List<Integer> clearedRows, List<Cell> lastBlockCells) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(clearedRows, "clearedRows");
        Objects.requireNonNull(lastBlockCells, "lastBlockCells");
 
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
        return List.copyOf(result);
    }
}