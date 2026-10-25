package com.tetr11s.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ScoreEntryTest {

    @Test
    void 이름_앞뒤_공백은_지운다() {
        assertEquals("예영", new ScoreEntry("  예영 ", 100).name());
    }

    @Test
    void 빈_이름은_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreEntry("   ", 100));
        assertThrows(IllegalArgumentException.class, () -> new ScoreEntry(null, 100));
    }

    @Test
    void 음수_점수는_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreEntry("A", -1));
    }

    @Test
    void 이름은_8자까지_허용한다() {
        assertEquals("가나다라마바사아", new ScoreEntry("가나다라마바사아", 100).name());
        assertThrows(IllegalArgumentException.class, () -> new ScoreEntry("가나다라마바사아자", 100));
    }

    @Test
    void 글자_수는_앞뒤_공백을_빼고_센다() {
        assertEquals("ABCDEFGH", new ScoreEntry("  ABCDEFGH  ", 100).name());
    }
}