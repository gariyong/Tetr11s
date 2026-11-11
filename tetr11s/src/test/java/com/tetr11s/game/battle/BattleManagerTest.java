package com.tetr11s.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.Board;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.ItemGameManager;
import com.tetr11s.game.tetromino.Tetromino;

class BattleManagerTest {

    private static final int BOTTOM = Board.HEIGHT - 1;
    private static final long LIMIT = 1000;

    /** 항상 O 블럭이 나오는 게임 */
    private static GameManager oGame() {
        return new GameManager(new Board(), () -> Tetromino.O);
    }

    private static BattleManager battle(BattleMode mode) {
        BattleManager m = new BattleManager(mode, oGame(), oGame(), LIMIT);
        m.start();
        return m;
    }

    private static void fill(Board b, int row, String pattern) {
        for (int col = 0; col < pattern.length(); col++) {
            if (pattern.charAt(col) == 'X') {
                b.fillCell(row, col, Tetromino.T, null);
            }
        }
    }

    /** 현재 블럭 바로 아래(4열)를 막아서 그 자리에 고정되게 하고, 다음 블럭을 놓을 수 없게 만들어 게임오버 */
    private static void makeGameOver(GameManager g) {
        int below = g.getCurrent().cells().stream().mapToInt(c -> c.row()).max().orElseThrow() + 1;
        for (int row = below; row <= BOTTOM; row++) {
            fill(g.getBoard(), row, "....X.....");
        }
        g.hardDrop();
        assertTrue(g.isGameOver());
    }

    // ---------- 생성 ----------

    @Test
    void 일반과_시간제한_모드는_일반_게임으로_만든다() {
        for (BattleMode mode : List.of(BattleMode.NORMAL, BattleMode.TIME_LIMIT)) {
            BattleManager m = BattleManager.create(mode, Difficulty.NORMAL, 1);
            assertFalse(m.getPlayer1() instanceof ItemGameManager);
            assertFalse(m.getPlayer2() instanceof ItemGameManager);
            assertNotSame(m.getPlayer1(), m.getPlayer2());
        }
    }

    @Test
    void 아이템_모드는_아이템_게임으로_만든다() {
        BattleManager m = BattleManager.create(BattleMode.ITEM, Difficulty.NORMAL, 1);
        assertTrue(m.getPlayer1() instanceof ItemGameManager);
        assertTrue(m.getPlayer2() instanceof ItemGameManager);
    }

    @Test
    void 같은_seed면_두_플레이어의_블럭_순서가_같다() {
        for (BattleMode mode : BattleMode.values()) {
            BattleManager m = BattleManager.create(mode, Difficulty.NORMAL, 42);
            m.start();
            List<Tetromino> seq1 = new ArrayList<>();
            List<Tetromino> seq2 = new ArrayList<>();
            for (int i = 0; i < 8 && !m.getPlayer1().isGameOver(); i++) {
                seq1.add(m.getPlayer1().getNext());
                seq2.add(m.getPlayer2().getNext());
                m.getPlayer1().hardDrop();
                m.getPlayer2().hardDrop();
            }
            assertEquals(seq1, seq2, mode.toString());
        }
    }

    // ---------- 공격 연결 ----------

    @Test
    void 플레이어1이_두_줄을_지우면_플레이어2가_공격을_받는다() {
        BattleManager m = battle(BattleMode.NORMAL);
        fill(m.getPlayer1().getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(m.getPlayer1().getBoard(), BOTTOM, "XXXX..XXXX");

        m.getPlayer1().hardDrop();

        assertEquals(2, m.getPlayer2().getIncomingGarbage().size());
        assertEquals(0, m.getPlayer1().getIncomingGarbage().size());
    }

    @Test
    void 플레이어2의_공격은_플레이어1이_받는다() {
        BattleManager m = battle(BattleMode.NORMAL);
        fill(m.getPlayer2().getBoard(), BOTTOM - 1, "XXXX..XXXX");
        fill(m.getPlayer2().getBoard(), BOTTOM, "XXXX..XXXX");

        m.getPlayer2().hardDrop();

        assertEquals(2, m.getPlayer1().getIncomingGarbage().size());
    }

    // ---------- 승패 ----------

    @Test
    void 아무도_게임오버가_아니면_진행_중이다() {
        BattleManager m = battle(BattleMode.NORMAL);
        m.update(16);
        assertNull(m.getResult());
        assertFalse(m.isFinished());
    }

    @Test
    void 플레이어1이_게임오버면_플레이어2_승리() {
        BattleManager m = battle(BattleMode.NORMAL);
        makeGameOver(m.getPlayer1());
        m.update(16);
        assertEquals(BattleResult.PLAYER2_WIN, m.getResult());
        assertTrue(m.isFinished());
    }

    @Test
    void 플레이어2가_게임오버면_플레이어1_승리() {
        BattleManager m = battle(BattleMode.NORMAL);
        makeGameOver(m.getPlayer2());
        m.update(16);
        assertEquals(BattleResult.PLAYER1_WIN, m.getResult());
    }

    @Test
    void 같은_프레임에_둘_다_게임오버면_무승부() {
        BattleManager m = battle(BattleMode.NORMAL);
        makeGameOver(m.getPlayer1());
        makeGameOver(m.getPlayer2());
        m.update(16);
        assertEquals(BattleResult.DRAW, m.getResult());
    }

    @Test
    void 결과가_정해진_뒤에는_바뀌지_않는다() {
        BattleManager m = battle(BattleMode.NORMAL);
        makeGameOver(m.getPlayer1());
        m.update(16);
        makeGameOver(m.getPlayer2());
        m.update(16);
        assertEquals(BattleResult.PLAYER2_WIN, m.getResult());
    }

    // ---------- 시간제한 ----------

    @Test
    void 시간제한_모드는_시간이_끝나면_점수가_높은_쪽이_이긴다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        m.getPlayer1().softDrop();   // 1점

        m.update(LIMIT - 1);
        assertNull(m.getResult());
        m.update(1);
        assertEquals(BattleResult.PLAYER1_WIN, m.getResult());
    }

    @Test
    void 시간제한_모드에서_플레이어2_점수가_높으면_플레이어2_승리() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        m.getPlayer2().softDrop();
        m.update(LIMIT);
        assertEquals(BattleResult.PLAYER2_WIN, m.getResult());
    }

    @Test
    void 시간제한_모드에서_동점이면_무승부() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        m.update(LIMIT);
        assertEquals(BattleResult.DRAW, m.getResult());
    }

    @Test
    void 시간제한_모드에서도_시간_전에_게임오버되면_상대가_이긴다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        m.getPlayer1().softDrop();   // 점수는 1이 높지만
        makeGameOver(m.getPlayer1());
        m.update(16);
        assertEquals(BattleResult.PLAYER2_WIN, m.getResult());
    }

    @Test
    void 남은_시간이_줄어들고_0_아래로는_내려가지_않는다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        assertEquals(LIMIT, m.getRemainingMillis());
        m.update(300);
        assertEquals(700, m.getRemainingMillis());
        m.update(5000);
        assertEquals(0, m.getRemainingMillis());
    }

    @Test
    void 끝난_뒤에는_시간이_흐르지_않는다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        makeGameOver(m.getPlayer1());
        m.update(100);
        m.update(500);
        assertEquals(LIMIT - 100, m.getRemainingMillis());
    }

    @Test
    void 일반_모드는_시간으로_끝나지_않는다() {
        BattleManager m = battle(BattleMode.NORMAL);
        m.update(10 * BattleManager.DEFAULT_TIME_LIMIT_MS);
        assertNull(m.getResult());
        assertEquals(-1, m.getRemainingMillis());
    }

    @Test
    void 음수_시간은_무시한다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        m.update(-500);
        assertEquals(LIMIT, m.getRemainingMillis());
    }

    @Test
    void 기본_제한_시간은_2분이다() {
        assertEquals(120_000, BattleManager.DEFAULT_TIME_LIMIT_MS);
        BattleManager m = BattleManager.create(BattleMode.TIME_LIMIT, Difficulty.NORMAL, 1);
        m.start();
        assertEquals(120_000, m.getRemainingMillis());
    }

    // ---------- 다시 시작 ----------

    @Test
    void start하면_결과와_시간이_초기화된다() {
        BattleManager m = battle(BattleMode.TIME_LIMIT);
        makeGameOver(m.getPlayer1());
        m.update(300);
        assertTrue(m.isFinished());

        m.start();

        assertNull(m.getResult());
        assertEquals(LIMIT, m.getRemainingMillis());
        assertFalse(m.getPlayer1().isGameOver());
    }

    // ---------- 잘못된 입력 ----------

    @Test
    void 같은_게임을_두_플레이어로_넣으면_예외() {
        GameManager g = oGame();
        assertThrows(IllegalArgumentException.class,
            () -> new BattleManager(BattleMode.NORMAL, g, g, LIMIT));
    }

    @Test
    void 제한_시간이_0_이하면_예외() {
        assertThrows(IllegalArgumentException.class,
            () -> new BattleManager(BattleMode.TIME_LIMIT, oGame(), oGame(), 0));
    }

    @Test
    void null_입력은_예외() {
        assertThrows(NullPointerException.class,
            () -> new BattleManager(null, oGame(), oGame(), LIMIT));
        assertThrows(NullPointerException.class,
            () -> new BattleManager(BattleMode.NORMAL, null, oGame(), LIMIT));
        assertThrows(NullPointerException.class,
            () -> BattleManager.create(null, Difficulty.NORMAL, 1));
    }

    @Test
    void 모드마다_화면에_보여줄_이름이_있다() {
        assertEquals("일반", BattleMode.NORMAL.displayName());
        assertEquals("아이템", BattleMode.ITEM.displayName());
        assertEquals("시간제한", BattleMode.TIME_LIMIT.displayName());
    }
}
