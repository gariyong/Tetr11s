package com.tetr11s.game.tetromino;

import java.util.ArrayList;
import java.util.List;

/**
 * SRS 벽 킥 테이블 (시계 방향 회전).
 *
 * 회전한 자리가 막혀 있으면, 표에 적힌 순서대로 블럭을 옮겨 보면서 놓을 수 있는 첫 위치를 쓴다.
 * 첫 번째 시도는 항상 (0, 0), 즉 제자리 회전이다.
 *
 * offsets()에서 보드용 이동량 (dRow, dCol) = (-y, x)로 바꿔서 돌려준다.
 */
public final class WallKick {

    private WallKick() {
    }

    // J, L, S, T, Z 공용. [회전 전 상태] 0˚ → 90˚, 90˚ → 180˚, 180˚ → 270˚, 270˚ → 0˚
    private static final int[][][] JLSTZ = {
        {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},   // 0˚ → 90˚
        {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},       // 90˚ → 180˚
        {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},      // 180˚ → 270˚
        {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}},    // 270˚ → 360˚
    };

    // I 전용
    private static final int[][][] I = {
        {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},     // 0˚ → 90˚
        {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},     // 90˚ →180˚
        {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},     // 180˚ → 270˚
        {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},     // 270˚ → 360˚
    };

    /**
     * 시계 방향으로 회전할 때 차례로 시도할 이동량 목록.
     *
     * @param type 블럭 종류 (O는 킥 없이 제자리만)
     * @param from 회전 전 상태 (0~3)
     * @return (dRow, dCol) 목록. 첫 번째는 항상 (0, 0)
     */
    public static List<Cell> offsets(Tetromino type, int from) {
        if (type == Tetromino.O) {
            return List.of(new Cell(0, 0));
        }
        int[][][] table = (type == Tetromino.I) ? I : JLSTZ;

        List<Cell> result = new ArrayList<>();
        for (int[] xy : table[Math.floorMod(from, Tetromino.ROTATION_COUNT)]) {
            result.add(new Cell(-xy[1], xy[0]));   // {x, y} → (dRow, dCol)
        }
        return List.copyOf(result);
    }
}