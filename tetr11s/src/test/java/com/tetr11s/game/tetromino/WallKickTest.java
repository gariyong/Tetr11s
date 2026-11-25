package com.tetr11s.game.tetromino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;

/**
 * SRS 벽 킥 표 (시계 방향 회전).
 * 표는 {x, y}(y는 위쪽이 +)로 적혀 있고, offsets()는 보드용 (dRow, dCol) = (-y, x)로 바꿔서 준다.
 */
class WallKickTest {

    private static Cell c(int dRow, int dCol) {
        return new Cell(dRow, dCol);
    }

    // ---------- 기본 성질 ----------

    @Test
    void O_블럭은_킥_없이_제자리만_시도한다() {
        for (int from = 0; from < Tetromino.ROTATION_COUNT; from++) {
            assertEquals(List.of(c(0, 0)), WallKick.offsets(Tetromino.O, from));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Tetromino.class, names = "O", mode = EnumSource.Mode.EXCLUDE)
    void O_외의_블럭은_5번_시도하고_첫_시도는_제자리다(Tetromino type) {
        for (int from = 0; from < Tetromino.ROTATION_COUNT; from++) {
            List<Cell> offsets = WallKick.offsets(type, from);
            assertEquals(5, offsets.size(), type + " " + from);
            assertEquals(c(0, 0), offsets.get(0), type + " " + from);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Tetromino.class, names = { "J", "L", "S", "Z" })
    void J_L_S_Z는_T와_같은_표를_쓴다(Tetromino type) {
        for (int from = 0; from < Tetromino.ROTATION_COUNT; from++) {
            assertEquals(WallKick.offsets(Tetromino.T, from), WallKick.offsets(type, from));
        }
    }

    @Test
    void I_블럭은_다른_표를_쓴다() {
        assertFalse(WallKick.offsets(Tetromino.I, 0).equals(WallKick.offsets(Tetromino.T, 0)));
    }

    @Test
    void 회전_상태는_4로_나눈_나머지로_본다() {
        assertEquals(WallKick.offsets(Tetromino.T, 0), WallKick.offsets(Tetromino.T, 4));
        assertEquals(WallKick.offsets(Tetromino.T, 3), WallKick.offsets(Tetromino.T, -1));
        assertEquals(WallKick.offsets(Tetromino.I, 1), WallKick.offsets(Tetromino.I, 5));
    }

    @Test
    void 돌려준_목록은_바꿀_수_없다() {
        List<Cell> offsets = WallKick.offsets(Tetromino.T, 0);
        assertThrows(UnsupportedOperationException.class, () -> offsets.add(c(9, 9)));
    }

    // ---------- 표 값 (x, y) → (dRow, dCol) 변환 ----------

    @Test
    void JLSTZ_0도에서_90도_표를_보드_좌표로_바꾼다() {
        // 표: (0,0) (-1,0) (-1,1) (0,-2) (-1,-2)
        assertEquals(List.of(c(0, 0), c(0, -1), c(-1, -1), c(2, 0), c(2, -1)),
                WallKick.offsets(Tetromino.T, 0));
    }

    @Test
    void I_0도에서_90도_표를_보드_좌표로_바꾼다() {
        // 표: (0,0) (-2,0) (1,0) (-2,-1) (1,2)
        assertEquals(List.of(c(0, 0), c(0, -2), c(0, 1), c(1, -2), c(-2, 1)),
                WallKick.offsets(Tetromino.I, 0));
    }

    @Test
    void JLSTZ_90도에서_180도_표를_보드_좌표로_바꾼다() {
        // 표: (0,0) (1,0) (1,-1) (0,2) (1,2)
        assertEquals(List.of(c(0, 0), c(0, 1), c(1, 1), c(-2, 0), c(-2, 1)),
                WallKick.offsets(Tetromino.T, 1));
    }

    // ---------- SRS 표의 대칭성 ----------

    @ParameterizedTest
    @EnumSource(value = Tetromino.class, names = { "T", "I" })
    void 마주_보는_상태의_킥은_서로_반대_방향이다(Tetromino type) {
        // JLSTZ: 0→90 과 90→180, 180→270 과 270→0 이 서로 부호만 반대
        // I:     0→90 과 180→270, 90→180 과 270→0 이 서로 부호만 반대
        int[][] pairs = type == Tetromino.I
                ? new int[][] { { 0, 2 }, { 1, 3 } }
                : new int[][] { { 0, 1 }, { 2, 3 } };

        for (int[] pair : pairs) {
            List<Cell> a = WallKick.offsets(type, pair[0]);
            List<Cell> b = WallKick.offsets(type, pair[1]);
            for (int i = 0; i < a.size(); i++) {
                String where = type + " " + pair[0] + "/" + pair[1] + " #" + i;
                assertEquals(-a.get(i).row(), b.get(i).row(), where);
                assertEquals(-a.get(i).col(), b.get(i).col(), where);
            }
        }
    }

    // ---------- 게임에서 실제로 쓰일 때 ----------

    @Test
    void 왼쪽_벽에_붙은_T_블럭은_오른쪽으로_밀려서_회전한다() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.T);
        game.start();

        assertTrue(game.rotateClockwise());   // 0도 → 90도 (세로로 서서 상자 왼쪽 열이 빈다)
        while (game.moveLeft()) {
            // 벽에 닿을 때까지
        }
        assertEquals(-1, game.getCurrent().col());   // 상자의 빈 왼쪽 열이 벽 밖에 있다

        // 제자리 회전은 벽 밖으로 나가므로 막히고, 두 번째 킥 (0, +1)로 한 칸 밀려서 회전한다
        assertTrue(game.rotateClockwise());
        assertEquals(2, game.getCurrent().rotation());
        assertEquals(0, game.getCurrent().col());
        assertEquals(0, game.getCurrent().row());
    }

    @Test
    void 모든_킥이_막히면_회전하지_않고_그대로_있는다() {
        // 높이 2줄 보드: 누운 T(0도)는 들어가지만, 세로로 선 T(90도)는 3줄이 필요해서
        // 제자리와 킥 4개가 모두 보드 밖으로 나간다
        GameManager game = new GameManager(new Board(3, 2), () -> Tetromino.T);
        game.start();
        Block before = game.getCurrent();

        assertFalse(game.rotateClockwise());
        assertEquals(before, game.getCurrent());
    }

    @Test
    void O_블럭은_제자리_회전만_하고_위치가_바뀌지_않는다() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O);
        game.start();
        Block before = game.getCurrent();

        assertTrue(game.rotateClockwise());
        assertEquals(before.row(), game.getCurrent().row());
        assertEquals(before.col(), game.getCurrent().col());
        assertEquals(before.cells(), game.getCurrent().cells());
    }
}
