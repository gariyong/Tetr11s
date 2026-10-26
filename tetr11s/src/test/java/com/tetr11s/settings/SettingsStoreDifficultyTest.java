package com.tetr11s.settings;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.Difficulty;

/**
 * SettingsStore는 사용자 폴더(user.home)의 save.properties를 쓴다
 * 실제 설정 파일을 건드리지 않도록, 테스트 동안만 user.home을 임시 폴더로 바꿔 둔다
 */
class SettingsStoreDifficultyTest {

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
    void 저장한_적이_없으면_Normal() {
        assertEquals(Difficulty.NORMAL, SettingsStore.loadDifficulty());
    }

    @Test
    void 저장한_난이도를_다시_읽는다() {
        for (Difficulty difficulty : Difficulty.values()) {
            SettingsStore.saveDifficulty(difficulty);
            assertEquals(difficulty, SettingsStore.loadDifficulty());
        }
    }

    @Test
    void 난이도를_저장해도_다른_설정은_남아_있다() {
        SettingsStore.saveScale(1.25);
        SettingsStore.saveDifficulty(Difficulty.HARD);

        assertEquals(1.25, SettingsStore.loadScale());
    }

    @Test
    void 설정_파일을_지우면_다시_Normal() {
        SettingsStore.saveDifficulty(Difficulty.EASY);
        SettingsStore.delete();

        assertEquals(Difficulty.NORMAL, SettingsStore.loadDifficulty());
    }

    @Test
    void 화면_배율_프리셋을_저장하고_읽는다() {
        SettingsStore.saveResolutionPreset("노트북", 1.25);

        assertEquals(1.25, SettingsStore.loadResolutionPreset("노트북"));
    }

    @Test
    void 화면_배율_프리셋은_활성_배율과_분리되어_저장된다() {
        SettingsStore.saveScale(0.75);
        SettingsStore.saveResolutionPreset("기본", 1.0);

        assertEquals(0.75, SettingsStore.loadScale());
        assertEquals(1.0, SettingsStore.loadResolutionPreset("기본"));
    }
}