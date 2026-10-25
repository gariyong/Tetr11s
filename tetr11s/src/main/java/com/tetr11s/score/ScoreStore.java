package com.tetr11s.score;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 스코어보드를 파일에 저장하고 불러온다
 *
 * 설정(save.properties)과 다른 파일에 저장해서, 설정을 초기화해도 기록은 남는다
 * 기록을 지우는 건 delete()뿐이다 (설정 화면의 '기록 초기화')
 *
 */
public class ScoreStore {

    private static final String FILE_NAME = "scores.properties";

    private final Path path;

    /** 게임에서 쓰는 기본 위치: ~/.tetr11s/scores.properties (save.properties와 같은 폴더) */
    public ScoreStore() {
        this(Paths.get(System.getProperty("user.home"), ".tetr11s", FILE_NAME));
    }

    /** 테스트에서는 임시 폴더 경로를 넘겨서 실제 기록 파일을 건드리지 않는다 */
    public ScoreStore(Path path) {
        this.path = path;
    }

    /**
     * 저장된 기록을 읽는다
     * 파일이 없거나 읽을 수 없으면 빈 스코어보드, 망가진 줄은 건너뛴다
     */
    public ScoreBoard load() {
        if (!Files.exists(path)) {
            return new ScoreBoard();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException e) {   // 읽기 실패, 또는 유니코드 이스케이프가 깨진 파일
            System.err.println("스코어보드 파일을 읽을 수 없습니다: " + e.getMessage());
            return new ScoreBoard();
        }

        List<ScoreEntry> entries = new ArrayList<>();
        for (int rank = 0; rank < ScoreBoard.CAPACITY; rank++) {
            ScoreEntry entry = parseEntry(properties, rank);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new ScoreBoard(entries);
    }

    /** 스코어보드 전체를 파일에 덮어쓴다 */
    public void save(ScoreBoard board) {
        Properties properties = new Properties();
        List<ScoreEntry> entries = board.entries();
        for (int rank = 0; rank < entries.size(); rank++) {
            properties.setProperty(rank + ".name", entries.get(rank).name());
            properties.setProperty(rank + ".score", Integer.toString(entries.get(rank).score()));
        }

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Tetr11s scoreboard");
            }
        } catch (IOException e) {
            throw new IllegalStateException("스코어보드를 저장할 수 없습니다: " + path, e);
        }
    }

    /** 기록 초기화: 파일을 지운다 (없으면 아무 일도 하지 않음) */
    public void delete() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("스코어보드 파일을 삭제할 수 없습니다: " + path, e);
        }
    }

    // 한 순위의 기록을 읽는다. 없거나 망가졌으면 null
    private static ScoreEntry parseEntry(Properties properties, int rank) {
        String name = properties.getProperty(rank + ".name");
        String score = properties.getProperty(rank + ".score");
        if (name == null || score == null) {
            return null;
        }
        try {
            return new ScoreEntry(name, Integer.parseInt(score.strip()));
        } catch (IllegalArgumentException e) {   // 숫자가 아님(NumberFormatException 포함), 빈 이름, 음수 점수
            return null;
        }
    }
}