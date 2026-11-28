package com.tetr11s.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.Difficulty;

/** 기본 저장 위치와 파일을 쓸 수 없을 때의 처리 */
class ScoreStoreFileTest {

    @TempDir
    Path tempHome;

    private String originalHome;

    @BeforeEach
    void useTempHome() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
    }

    @Test
    void 기본_저장_위치는_사용자_홈이고_일반과_아이템_기록이_따로_남는다() {
        ScoreBoard normal = new ScoreBoard();
        normal.add(new ScoreEntry("일반", 100));
        new ScoreStore().save(Difficulty.NORMAL, normal);

        ScoreBoard item = new ScoreBoard();
        item.add(new ScoreEntry("아이템", 200));
        new ScoreStore(true).save(Difficulty.NORMAL, item);

        assertTrue(Files.exists(tempHome.resolve(".tetr11s").resolve("scores.properties")));
        assertEquals("일반", new ScoreStore().load(Difficulty.NORMAL).entries().get(0).name());
        assertEquals("아이템", new ScoreStore(true).load(Difficulty.NORMAL).entries().get(0).name());
    }

    @Test
    void 저장_파일_자리에_폴더가_있으면_저장과_삭제가_실패를_알린다() throws Exception {
        Path blocked = tempHome.resolve("blocked");
        Files.createDirectories(blocked.resolve("inside"));   // 비어 있지 않은 폴더라 지울 수도 없다
        ScoreStore store = new ScoreStore(blocked);

        ScoreBoard board = new ScoreBoard();
        board.add(new ScoreEntry("a", 1));
        assertThrows(IllegalStateException.class, () -> store.save(Difficulty.EASY, board));
        assertThrows(IllegalStateException.class, store::delete);
        assertTrue(store.load(Difficulty.EASY).entries().isEmpty());
    }
}
