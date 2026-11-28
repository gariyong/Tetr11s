package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 아이템 모드 규칙 중 ItemGameManagerTest가 다루지 않는 부분
 * - 10줄마다 다음 블럭이 아이템이 된다 (2차 요구사항)
 * - 무게추의 자동 낙하·소프트드롭·홀드
 * - 홀드에서 꺼낸 아이템 블럭, 생성 자리가 막혔을 때 게임오버
 */
class ItemGameManagerRuleTest {

    private static final int BOTTOM = Board.HEIGHT - 1;

    /** O 블럭만 나오는 아이템 게임 */
    private static ItemGameManager newGame(boolean bugMode) {
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, bugMode, () -> Tetromino.O);
        game.setRandomSeed(1L);
        game.start();
        return game;
    }

    /** 맨 아래 2줄을 O가 떨어질 4~5열만 비우고 채운 뒤 하드드롭 → 2줄 삭제 */
    private static void clearTwoLines(ItemGameManager game) {
        Board board = game.getBoard();
        for (int row = BOTTOM - 1; row <= BOTTOM; row++) {
            for (int col = 0; col < board.width(); col++) {
                if (col != 4 && col != 5) {
                    board.fillCell(row, col, Tetromino.T, null);
                }
            }
        }
        game.hardDrop();
        assertTrue(game.isClearing());
        game.finishLineClear();
    }

    /** 다음 블럭을 아이템으로 지정하고 지금 블럭을 떨어뜨려, 그 아이템 블럭을 현재 블럭으로 만든다 */
    private static ItemGameManager startWithItem(Item item) {
        ItemGameManager game = newGame(false);
        game.forceNextItem(item);
        game.hardDrop();
        game.getBoard().clear();
        assertEquals(item, game.getCurrentItem());
        return game;
    }

    @Test
    void 기본_생성자들은_Normal_난이도로_시작한다() {
        assertEquals(Difficulty.NORMAL, new ItemGameManager().getDifficulty());
        assertEquals(Difficulty.HARD, new ItemGameManager(Difficulty.HARD).getDifficulty());
        assertEquals(Difficulty.EASY, new ItemGameManager(Difficulty.EASY, true).getDifficulty());
    }

    @Test
    void 줄을_10줄_지우면_다음_블럭이_아이템이_된다() {
        ItemGameManager game = newGame(false);

        for (int i = 0; i < 4; i++) {
            clearTwoLines(game);
        }
        assertEquals(8, game.getLines());
        assertNull(game.getNextItemType(), "10줄 전에는 아이템이 나오지 않는다");

        clearTwoLines(game);
        assertEquals(10, game.getLines());
        assertNotNull(game.getNextItemType(), "10줄을 채우면 NEXT에 아이템이 예약된다");
    }

    @Test
    void 테스트용_버그판_모드는_4번_고정할_때마다_아이템이_나온다() {
        ItemGameManager game = newGame(true);
        for (int i = 0; i < 3; i++) {
            game.hardDrop();
            assertNull(game.getNextItemType());
        }
        game.hardDrop();
        assertNotNull(game.getNextItemType());
    }

    @Test
    void 줄을_지우는_중이_아니면_삭제_마무리는_아무것도_안한다() {
        ItemGameManager game = newGame(false);
        int score = game.getScore();
        game.finishLineClear();
        assertFalse(game.isClearing());
        assertEquals(score, game.getScore());
    }

    @Test
    void 무게추는_자동_낙하와_소프트드롭에_점수를_주지_않고_바닥에서_고정된다() {
        ItemGameManager game = startWithItem(Item.W);
        int score = game.getScore();
        int row = game.getCurrent().row();

        assertTrue(game.tick());
        assertTrue(game.softDrop());
        assertEquals(row + 2, game.getCurrent().row());
        assertEquals(score, game.getScore());

        // 바닥에 닿을 때까지 자동 낙하 → 닿으면 고정되고 다음 블럭이 나온다
        for (int i = 0; i < Board.HEIGHT && game.getCurrentItem() == Item.W; i++) {
            game.tick();
        }
        assertNull(game.getCurrentItem());
        assertEquals(Item.W, game.getBoard().getItem(BOTTOM, 4));
        assertEquals(score, game.getScore());
    }

    @Test
    void 무게추가_블럭을_부수기_시작하면_좌우이동과_홀드가_막힌다() {
        ItemGameManager game = startWithItem(Item.W);
        Board board = game.getBoard();
        for (int row = 10; row <= BOTTOM; row++) {
            board.fillCell(row, 4, Tetromino.T, null);
        }
        assertTrue(game.canHold());
        assertTrue(game.moveLeft());
        assertTrue(game.moveRight());

        while (game.getCurrent().row() < 9) {
            game.softDrop();
        }
        game.softDrop();   // 10행의 블럭을 부순다

        assertFalse(game.canHold());
        assertFalse(game.hold());
        assertFalse(game.moveLeft());
        assertFalse(game.moveRight());
    }

    @Test
    void 일시정지_중에는_무게추가_움직이지_않는다() {
        ItemGameManager game = startWithItem(Item.W);
        int row = game.getCurrent().row();
        game.togglePause();

        assertFalse(game.tick());
        assertFalse(game.softDrop());
        assertEquals(0, game.hardDrop());
        assertEquals(row, game.getCurrent().row());
    }

    @Test
    void 홀드한_무게추는_다시_꺼내도_무게추다() {
        ItemGameManager game = startWithItem(Item.W);
        assertTrue(game.hold());
        assertEquals(Item.W, game.getHeldItemType());
        assertNull(game.getCurrentItem());

        game.hardDrop();          // 일반 블럭을 내려놓아야 다시 홀드할 수 있다
        assertTrue(game.hold());
        assertEquals(Item.W, game.getCurrentItem());
        assertFalse(game.getCurrentItemCells().isEmpty());
    }

    @Test
    void 홀드한_메테오는_다시_꺼내도_메테오다() {
        ItemGameManager game = startWithItem(Item.M);
        assertTrue(game.hold());
        game.hardDrop();
        game.getBoard().clear();
        assertTrue(game.hold());
        assertEquals(Item.M, game.getCurrentItem());
    }

    @Test
    void 무게추가_나올_자리가_막혀_있으면_게임오버() {
        ItemGameManager game = newGame(false);
        game.forceNextItem(Item.W);
        Board board = game.getBoard();
        // 무게추 생성 자리(3~6열 위쪽)를 막는다. 지금 O 블럭(4~5열)은 피해서 바깥 3, 6열만 채운다
        for (int row = 0; row <= BOTTOM; row++) {
            board.fillCell(row, 3, Tetromino.T, null);
            board.fillCell(row, 6, Tetromino.T, null);
        }
        game.hardDrop();
        assertTrue(game.isGameOver());
    }

    @Test
    void 효과가_없을_때_흔들림은_0이고_연출도_없다() {
        ItemGameManager game = newGame(false);
        assertArrayEquals(new double[] {0.0, 0.0}, game.getShakeOffset(1000L));
        assertFalse(game.hasActiveVisualEffects());
        assertFalse(game.isItemEffectAnimating(0L));
        assertTrue(game.getActiveMeteorFragments(0L).isEmpty());
        assertTrue(game.getActiveExplosions(0L).isEmpty());
    }

    @Test
    void 줄을_지우는_동안에는_현재_블럭의_아이템_칸이_없다() {
        ItemGameManager game = newGame(false);
        Board board = game.getBoard();
        for (int row = BOTTOM - 1; row <= BOTTOM; row++) {
            for (int col = 0; col < board.width(); col++) {
                if (col != 4 && col != 5) {
                    board.fillCell(row, col, Tetromino.T, null);
                }
            }
        }
        game.hardDrop();
        assertTrue(game.isClearing());
        assertTrue(game.getCurrentItemCells().isEmpty());
    }
}
