package com.tetr11s.score;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.tetr11s.game.Difficulty;

/**
 * 스코어보드를 파일에 저장하고 불러온다 (난이도마다 따로)
 *
 * 설정(save.properties)과 다른 파일에 저장해서, 설정을 초기화해도 기록은 남는다
 * 기록을 지우는 건 delete()뿐이다 (설정 화면의 '기록 초기화', 모든 난이도)
 *
 * 난이도 구분이 없던 예전 형식(0.name=...)은 읽지 않고, 다음에 저장할 때 지워진다
 */
public class ScoreStore {

    private static final String FILE_NAME = "scores.properties";

    private final Path path;

    private final boolean isItemMode;
    public ScoreStore() {
        this(false);
    }

    public ScoreStore(boolean isItemMode) {
        this(Paths.get(System.getProperty("user.home"), ".tetr11s", "scores.properties"), isItemMode);
    }

    public ScoreStore(Path path) {
        this(path, false);
    }

    public ScoreStore(Path path, boolean isItemMode) {
        this.path = path;
        this.isItemMode = isItemMode;
    }

    public ScoreBoard load(Difficulty difficulty) {
        Properties properties = read();
        String prefix = prefix(difficulty, isItemMode); // 모드별 키 적용

        List<ScoreEntry> entries = new ArrayList<>();
        for (int rank = 0; rank < ScoreBoard.CAPACITY; rank++) {
            ScoreEntry entry = parseEntry(properties, prefix + rank);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new ScoreBoard(entries);
    }

    public void save(Difficulty difficulty, ScoreBoard board) {
        Properties saved = read();
        Properties properties = new Properties();
        String prefix = prefix(difficulty, isItemMode);

        for (String key : saved.stringPropertyNames()) {
            boolean knownScoreKey = false;
            for (Difficulty other : Difficulty.values()) {
                if (key.startsWith(prefix(other, false)) || key.startsWith(prefix(other, true))) {
                    knownScoreKey = true;
                    break;
                }
            }
            if (knownScoreKey && !key.startsWith(prefix)) {
                properties.setProperty(key, saved.getProperty(key));
            }
        }

        List<ScoreEntry> entries = board.entries();
        for (int rank = 0; rank < entries.size(); rank++) {
            properties.setProperty(prefix + rank + ".name", entries.get(rank).name());
            properties.setProperty(prefix + rank + ".score", Integer.toString(entries.get(rank).score()));
        }
        write(properties);
    }

    /** 기록 초기화: 파일을 지운다 (모든 난이도. 없으면 아무 일도 하지 않음) */
    public void delete() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("스코어보드 파일을 삭제할 수 없습니다: " + path, e);
        }
    }

    // 파일 전체를 읽는다. 없거나 읽을 수 없으면 비어 있는 Properties
    private Properties read() {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException e) {   // 읽기 실패, 또는 유니코드 이스케이프가 깨진 파일
            System.err.println("스코어보드 파일을 읽을 수 없습니다: " + e.getMessage());
            return new Properties();
        }
        return properties;
    }

    private void write(Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Tetr11s scoreboard");
            }
        } catch (IOException e) {
            throw new IllegalStateException("스코어보드를 저장할 수 없습니다: " + path, e);
        }
    }

    // 난이도와 모드 별 키의 앞부분 (예: "item_normal.")
    private static String prefix(Difficulty difficulty, boolean isItemMode) {
        String base = difficulty.name().toLowerCase(Locale.ROOT) + ".";
        return isItemMode ? "item_" + base : base;
    }

    // 한 순위의 기록을 읽는다 (key 예: "normal.0"). 없거나 망가졌으면 null
    private static ScoreEntry parseEntry(Properties properties, String key) {
        String name = properties.getProperty(key + ".name");
        String score = properties.getProperty(key + ".score");
        if (name == null || score == null) {
            return null;
        }
        try {
            return new ScoreEntry(name, Integer.parseInt(score.strip()));
        } catch (IllegalArgumentException e) {   // 숫자가 아님(NumberFormatException 포함), 빈 이름, 8자 초과, 음수 점수
            return null;
        }
    }
}