package com.tetr11s.score;

/**
 * 스코어보드 기록 한 줄 (이름, 점수)
 * 추후 레벨, 날짜 같은 항목이 필요해지면 여기에 추가
 */
public record ScoreEntry(String name, int score) {

    public ScoreEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 비어 있을 수 없음");
        }
        if (score < 0) {
            throw new IllegalArgumentException("점수는 0 이상이어야 함: " + score);
        }
        name = name.strip();
    }
}