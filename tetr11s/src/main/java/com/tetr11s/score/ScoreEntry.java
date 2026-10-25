package com.tetr11s.score;

/**
 * 스코어보드 기록 한 줄 (이름, 점수)
 * 추후 레벨, 날짜 같은 항목이 필요해지면 여기에 추가
 */
public record ScoreEntry(String name, int score) {

    public static final int MAX_NAME_LENGTH = 8;

    public ScoreEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 비어 있을 수 없음");
        }
        name = name.strip();
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("이름은 " + MAX_NAME_LENGTH + "자 이하여야 함: " + name);
        }
        if (score < 0) {
            throw new IllegalArgumentException("점수는 0 이상이어야 함: " + score);
        }
    }
}