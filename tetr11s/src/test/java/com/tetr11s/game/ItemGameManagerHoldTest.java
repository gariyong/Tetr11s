package com.tetr11s.game;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 아이템 모드 홀드 테스트
 * 아이템이 붙은 블럭(L, H, O)과 아이템 블럭(W, M)도 홀드할 수 있고,
 * 꺼내면 같은 아이템이 같은 칸에 붙은 채로 다시 나온다.
 */
class ItemGameManagerHoldTest {

    /** 다음 블럭에 아이템을 붙이고, 지금 블럭을 고정해서 아이템 블럭이 나오게 한다 (블럭은 모두 T) */
    private static ItemGameManager startWithItem(Item item) {
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, false, () -> Tetromino.T);
        game.setRandomSeed(7L);
        game.start();
        game.forceNextItem(item);
        game.hardDrop();
        game.getBoard().clear();
        return game;
    }

    /** 아이템 블럭을 홀드하고, 다음 블럭을 고정한 뒤 다시 꺼낸다 */
    private static void holdAndTakeBack(ItemGameManager game) {
        assertTrue(game.hold());
        game.hardDrop();
        game.getBoard().clear();
        assertTrue(game.hold());
    }

    @Test
    void 아이템이_붙은_블럭을_홀드하면_아이템도_함께_들어간다() {
        ItemGameManager game = startWithItem(Item.L);
        List<Integer> indices = currentIndices(game);

        game.hold();

        assertEquals(Tetromino.T, game.getHeld());
        assertEquals(Item.L, game.getHeldItemType());
        assertEquals(indices, game.getHeldItemIndices());
        assertNull(game.getCurrentItem(), "다음 블럭은 아이템이 없는 일반 블럭이어야 함");
    }

    @Test
    void 꺼낸_블럭에는_같은_아이템이_같은_칸에_붙어_있다() {
        ItemGameManager game = startWithItem(Item.H);
        List<Integer> indices = currentIndices(game);

        holdAndTakeBack(game);

        assertEquals(Item.H, game.getCurrentItem());
        assertEquals(indices, currentIndices(game));
        assertNull(game.getHeldItemType(), "맞바꾼 일반 블럭에는 아이템이 없어야 함");
    }

    @Test
    void 무게추도_홀드했다가_다시_꺼낼_수_있다() {
        ItemGameManager game = startWithItem(Item.W);

        holdAndTakeBack(game);

        assertEquals(Item.W, game.getCurrentItem());
        assertEquals(6, game.getCurrent().cells().size(), "무게추 모양(6칸)으로 다시 나와야 함");
    }

    @Test
    void 메테오도_홀드했다가_다시_꺼낼_수_있다() {
        ItemGameManager game = startWithItem(Item.M);

        holdAndTakeBack(game);

        assertEquals(Item.M, game.getCurrentItem());
        assertEquals(1, game.getCurrent().cells().size(), "메테오 모양(1칸)으로 다시 나와야 함");
    }

    @Test
    void 무게추가_블럭을_부수기_시작하면_홀드할_수_없다() {
        ItemGameManager game = startWithItem(Item.W);
        // 무게추는 0~1행, 3~6열에 생긴다. 바로 아래 칸을 채워 두고 한 칸 내리면 부수기 시작한다
        game.getBoard().fillCell(2, 4, Tetromino.I, null);

        game.softDrop();

        assertFalse(game.canHold());
        assertFalse(game.hold());
        assertEquals(Item.W, game.getCurrentItem());
    }

    @Test
    void 새_게임을_시작하면_홀드_칸의_아이템도_비워진다() {
        ItemGameManager game = startWithItem(Item.O);
        game.hold();

        game.start();

        assertNull(game.getHeld());
        assertNull(game.getHeldItemType());
        assertTrue(game.getHeldItemIndices().isEmpty());
    }

    // 지금 블럭에서 아이템이 붙은 칸의 번호 (블럭 칸 목록 안의 위치)
    private static List<Integer> currentIndices(ItemGameManager game) {
        List<Cell> cells = game.getCurrent().cells();
        return game.getCurrentItemCells().stream().map(cells::indexOf).toList();
    }
}
