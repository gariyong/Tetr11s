package com.tetr11s.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GarbageLineTest {

    @Test
    void 칸마다_블럭이_있는지_저장한다() {
        GarbageLine line = new GarbageLine(new boolean[] {true, true, false, true});
        assertEquals(4, line.width());
        assertTrue(line.isFilled(0));
        assertTrue(line.isFilled(1));
        assertFalse(line.isFilled(2));
        assertTrue(line.isFilled(3));
    }

    @Test
    void toString은_X와_점으로_모양을_보여준다() {
        assertEquals("X..XX", new GarbageLine(new boolean[] {true, false, false, true, true}).toString());
    }

    @Test
    void 모양이_같으면_같은_줄이다() {
        GarbageLine a = new GarbageLine(new boolean[] {true, false, true});
        GarbageLine b = new GarbageLine(new boolean[] {true, false, true});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void 모양이_다르면_다른_줄이다() {
        GarbageLine a = new GarbageLine(new boolean[] {true, false, true});
        assertNotEquals(a, new GarbageLine(new boolean[] {true, true, false}));
        assertNotEquals(a, new GarbageLine(new boolean[] {true, false, true, true}));  // 너비가 다름
        assertNotEquals(a, null);
        assertNotEquals(a, "X.X");
    }

    @Test
    void 생성_후_원본_배열을_바꿔도_영향이_없다() {
        boolean[] cells = {true, true, false};
        GarbageLine line = new GarbageLine(cells);
        cells[2] = true;
        assertFalse(line.isFilled(2));
    }

    @Test
    void 범위_밖_열은_예외() {
        GarbageLine line = new GarbageLine(new boolean[] {true, true});
        assertThrows(IndexOutOfBoundsException.class, () -> line.isFilled(2));
        assertThrows(IndexOutOfBoundsException.class, () -> line.isFilled(-1));
    }

    @Test
    void null_배열은_예외() {
        assertThrows(NullPointerException.class, () -> new GarbageLine(null));
    }

    @Test
    void 빈_배열은_예외() {
        assertThrows(IllegalArgumentException.class, () -> new GarbageLine(new boolean[0]));
    }
}
