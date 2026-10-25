package com.tetr11s.score;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void 파일이_없으면_빈_순위표를_돌려준다() {
        assertTrue(store.load().entries().isEmpty());
    }

    @Test
    void 저장한_기록을_순서와_이름_그대로_다시_불러온다() {
        ScoreBoard board = new ScoreBoard();
        board.add(new ScoreEntry("예영", 1200));
        board.add(new ScoreEntry("a=b: #!c", 900));   // Properties에서 특별한 의미가 있는 문자
        board.add(new ScoreEntry("동점", 900));

        store.save(board);

        assertTrue(Files.exists(file), "없던 폴더도 만들어서 저장해야 함");
        assertEquals(board.entries(), new ScoreStore(file).load().entries());
    }

    @Test
    void 다시_저장하면_이전_내용을_덮어쓴다() {
        ScoreBoard three = new ScoreBoard();
        three.add(new ScoreEntry("A", 300));
        three.add(new ScoreEntry("B", 200));
        three.add(new ScoreEntry("C", 100));
        store.save(three);

        ScoreBoard one = new ScoreBoard();
        one.add(new ScoreEntry("ONLY", 50));
        store.save(one);

        assertEquals(List.of("ONLY"), names(store.load()));
    }

    @Test
    void 망가진_줄은_건너뛰고_나머지는_읽는다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n",
                "0.name=ok", "0.score=100",
                "1.name=notNumber", "1.score=abc",
                "2.name=negative", "2.score=-5",
                "3.name=   ", "3.score=50",
                "4.name=noScore",
                "6.name=gap", "6.score=300"));   // 5번은 비어 있음

        assertEquals(List.of("gap", "ok"), names(store.load()));
    }

    @Test
    void 유니코드_이스케이프가_깨진_파일은_빈_순위표로_읽는다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "0.name=\\u12\n0.score=100\n");

        assertTrue(store.load().entries().isEmpty());
    }

    @Test
    void delete는_파일을_지우고_없을_때_불러도_괜찮다() {
        ScoreBoard board = new ScoreBoard();
        board.add(new ScoreEntry("A", 100));
        store.save(board);

        store.delete();
        assertFalse(Files.exists(file));
        assertTrue(store.load().entries().isEmpty());

        store.delete();   // 이미 없는 파일을 다시 지워도 예외 없음
    }
}