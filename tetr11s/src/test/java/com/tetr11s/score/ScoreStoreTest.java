package com.tetr11s.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.tetr11s.game.Difficulty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실제 기록 파일(~/.tetr11s)을 건드리지 않도록, 테스트마다 JUnit이 만들어 주는 임시 폴더를 쓴다
 */
class ScoreStoreTest {

    @TempDir
    Path tempDir;

    private Path file;
    private ScoreStore store;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("tetr11s").resolve("scores.properties");   // 아직 없는 폴더
        store = new ScoreStore(file);
    }

    private static List<String> names(ScoreBoard board) {
        List<String> names = new ArrayList<>();
        for (ScoreEntry entry : board.entries()) {
            names.add(entry.name());
        }
        return names;
    }

    private static ScoreBoard boardOf(String... namesAndScores) {
        ScoreBoard board = new ScoreBoard();
        for (int i = 0; i < namesAndScores.length; i += 2) {
            board.add(new ScoreEntry(namesAndScores[i], Integer.parseInt(namesAndScores[i + 1])));
        }
        return board;
    }

    @Test
    void 파일이_없으면_모든_난이도가_빈_순위표() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(store.load(difficulty).entries().isEmpty());
        }
    }

    @Test
    void 저장한_기록을_순서와_이름_그대로_다시_불러온다() {
        ScoreBoard board = boardOf("예영", "1200", "a=b: #!c", "900", "동점", "900");   // Properties에서 특별한 의미가 있는 문자 포함

        store.save(Difficulty.NORMAL, board);

        assertTrue(Files.exists(file), "없던 폴더도 만들어서 저장해야 함");
        assertEquals(board.entries(), new ScoreStore(file).load(Difficulty.NORMAL).entries());
    }

    @Test
    void 다시_저장하면_그_난이도의_이전_내용을_덮어쓴다() {
        store.save(Difficulty.NORMAL, boardOf("A", "300", "B", "200", "C", "100"));
        store.save(Difficulty.NORMAL, boardOf("ONLY", "50"));

        assertEquals(List.of("ONLY"), names(store.load(Difficulty.NORMAL)));
    }

    @Test
    void 난이도별로_따로_저장된다() {
        store.save(Difficulty.EASY, boardOf("E1", "100"));
        store.save(Difficulty.HARD, boardOf("H1", "300", "H2", "200"));

        assertEquals(List.of("E1"), names(store.load(Difficulty.EASY)));
        assertEquals(List.of("H1", "H2"), names(store.load(Difficulty.HARD)));
        assertTrue(store.load(Difficulty.NORMAL).entries().isEmpty());
    }

    @Test
    void 한_난이도를_저장해도_다른_난이도_기록은_남는다() {
        store.save(Difficulty.EASY, boardOf("E1", "100"));
        store.save(Difficulty.HARD, boardOf("H1", "300"));

        store.save(Difficulty.EASY, boardOf("E2", "500", "E1", "100"));

        assertEquals(List.of("E2", "E1"), names(store.load(Difficulty.EASY)));
        assertEquals(List.of("H1"), names(store.load(Difficulty.HARD)));
    }

    @Test
    void 난이도_구분이_없던_예전_기록은_읽지_않고_다음_저장_때_지워진다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "0.name=old\n0.score=100\n");

        assertTrue(store.load(Difficulty.NORMAL).entries().isEmpty());

        store.save(Difficulty.NORMAL, boardOf("NEW", "50"));
        assertFalse(Files.readString(file).contains("0.name=old"));
        assertEquals(List.of("NEW"), names(store.load(Difficulty.NORMAL)));
    }

    @Test
    void 망가진_줄은_건너뛰고_나머지는_읽는다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n",
                "normal.0.name=ok", "normal.0.score=100",
                "normal.1.name=notNumber", "normal.1.score=abc",
                "normal.2.name=negative", "normal.2.score=-5",
                "normal.3.name=   ", "normal.3.score=50",
                "normal.4.name=noScore",
                "normal.6.name=gap", "normal.6.score=300"));   // 5번은 비어 있음

        assertEquals(List.of("gap", "ok"), names(store.load(Difficulty.NORMAL)));
    }

    @Test
    void 유니코드_이스케이프가_깨진_파일은_빈_순위표로_읽는다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "normal.0.name=\\u12\nnormal.0.score=100\n");

        assertTrue(store.load(Difficulty.NORMAL).entries().isEmpty());
    }

    @Test
    void delete는_모든_난이도의_기록을_지우고_없을_때_불러도_괜찮다() {
        store.save(Difficulty.EASY, boardOf("E1", "100"));
        store.save(Difficulty.HARD, boardOf("H1", "300"));

        store.delete();
        assertFalse(Files.exists(file));
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(store.load(difficulty).entries().isEmpty());
        }

        store.delete();   // 이미 없는 파일을 다시 지워도 예외 없음
    }
}