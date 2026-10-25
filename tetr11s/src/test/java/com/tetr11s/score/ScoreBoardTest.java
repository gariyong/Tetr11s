package com.tetr11s.score;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ScoreBoardTest {

    // 10점, 20점, ..., 100점 기록 10개로 꽉 찬 순위표
    private static ScoreBoard fullBoard() {
        ScoreBoard board = new ScoreBoard();
        for (int i = 1; i <= ScoreBoard.CAPACITY; i++) {
            board.add(new ScoreEntry("P" + i, i * 10));
        }
        return board;
    }

    private static List<String> names(ScoreBoard board) {
        List<String> names = new ArrayList<>();
        for (ScoreEntry entry : board.entries()) {
            names.add(entry.name());
        }
        return names;
    }

    @Test
    void 빈_순위표에는_0점도_들어간다() {
        ScoreBoard board = new ScoreBoard();

        assertTrue(board.qualifies(0));
        assertEquals(0, board.add(new ScoreEntry("A", 0)));
    }

    @Test
    void 점수_높은_순으로_정렬되고_add는_들어간_자리를_돌려준다() {
        ScoreBoard board = new ScoreBoard();

        assertEquals(0, board.add(new ScoreEntry("A", 100)));
        assertEquals(0, board.add(new ScoreEntry("B", 300)));
        assertEquals(1, board.add(new ScoreEntry("C", 200)));
        assertEquals(List.of("B", "C", "A"), names(board));
    }

    @Test
    void 동점이면_먼저_세운_기록이_위에_온다() {
        ScoreBoard board = new ScoreBoard();
        board.add(new ScoreEntry("먼저", 100));

        assertEquals(1, board.add(new ScoreEntry("나중", 100)));
        assertEquals(List.of("먼저", "나중"), names(board));
    }

    @Test
    void 최대_10개까지만_보존하고_가장_낮은_기록이_빠진다() {
        ScoreBoard board = fullBoard();

        board.add(new ScoreEntry("NEW", 1000));

        assertEquals(ScoreBoard.CAPACITY, board.entries().size());
        assertEquals("NEW", board.entries().get(0).name());
        assertFalse(names(board).contains("P1"), "10점 기록(P1)이 빠져야 함");
    }

    @Test
    void 꽉_찼을_때_꼴찌와_동점이면_들어가지_못한다() {
        ScoreBoard board = fullBoard();

        assertFalse(board.qualifies(10));
        assertEquals(-1, board.add(new ScoreEntry("TIE", 10)));
        assertFalse(names(board).contains("TIE"));
    }

    @Test
    void 꽉_찼을_때_꼴찌보다_높으면_10위로_들어간다() {
        ScoreBoard board = fullBoard();

        assertTrue(board.qualifies(15));
        assertEquals(9, board.add(new ScoreEntry("NEW", 15)));
        assertEquals("NEW", board.entries().get(9).name());
    }

    @Test
    void 저장된_목록으로_만들면_정렬과_개수가_정리된다() {
        List<ScoreEntry> saved = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            saved.add(new ScoreEntry("S" + i, (i * 37) % 101));   // 순서가 뒤섞인 12개
        }

        List<ScoreEntry> entries = new ScoreBoard(saved).entries();

        assertEquals(ScoreBoard.CAPACITY, entries.size());
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i - 1).score() >= entries.get(i).score(), "점수 높은 순이어야 함");
        }
    }

    @Test
    void entries는_바꿀_수_없는_복사본이다() {
        ScoreBoard board = new ScoreBoard();
        board.add(new ScoreEntry("A", 100));

        assertThrows(UnsupportedOperationException.class,
                () -> board.entries().add(new ScoreEntry("B", 200)));
        assertEquals(1, board.entries().size());
    }
}