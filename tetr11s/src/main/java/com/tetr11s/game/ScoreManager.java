package com.tetr11s.game;

/**
 * 점수, 레벨(속도 단계), 지운 줄 수를 관리함
 * 
 * 점수 규칙
 * 블럭이 한 칸 떨어질 때마다 (자동/수동 무관) 1점 × 레벨
 * 레벨 1(초기 속도)에서는 1점, 속도가 빨라질수록 칸당 점수가 늘어난다
 *
 *  줄을 지우면 한 번에 지운 줄 수에 따라 보너스 × 레벨
 * (1줄 100, 2줄 300, 3줄 500, 4줄 800)
 *
 * 속도 규칙
 * 10줄을 지울 때마다 레벨이 1 오른다 (최대 15)
*  레벨에 따라 자동 낙하 간격이 짧아진다
 */
public class ScoreManager {

    public static final int LINES_PER_LEVEL = 10;
    public static final int MAX_LEVEL = 15;

    /** 한 번에 지운 줄 수(0~4)별 기본 보너스 */
    private static final int[] LINE_CLEAR_SCORES = {0, 100, 300, 500, 800};

    private int score;
    private int level;
    private int lines;

    public ScoreManager() {
        reset();
    }

    // 새 게임: 점수 0, 레벨 1, 줄 0
    public void reset() {
        score = 0;
        level = 1;
        lines = 0;
    }

    // 블럭이 떨어진 칸 수만큼 점수를 더한다.
    public void addDrop(int cells) {
        if (cells <= 0) {
            return;
        }
        
        score += cells * level;
    }

    /**
     * 줄을 지웠을 때 보너스를 더하고, 지운 줄 수에 따라 레벨을 올린다.
     * 보너스는 지우기 전 레벨로 계산한다.
     *
     * @param cleared 한 번에 지운 줄 수 (0~4)
     */
    public void addLineClear(int cleared) {
        if (cleared <= 0) {
            return;
        }
        int index = Math.min(cleared, LINE_CLEAR_SCORES.length - 1);
        score += LINE_CLEAR_SCORES[index] * level;

        lines += cleared;
        level = Math.min(MAX_LEVEL, 1 + lines / LINES_PER_LEVEL);
    }

    public int score() {
        return score;
    }

    public int level() {
        return level;
    }

    public int lines() {
        return lines;
    }

    // 현재 레벨의 자동 낙하 간격(ms)
    public long dropIntervalMillis() {
        return dropIntervalMillis(level);
    }

    /**
     * 레벨별 자동 낙하 간격(ms). 테트리스 가이드라인 공식:
     * (0.8 - (레벨 - 1) × 0.007) ^ (레벨 - 1) 초
     * 레벨 1 = 1000ms, 2 = 793ms, 3 = 618ms, 5 = 355ms, 10 = 64ms, 15 = 7ms
     */
    public static long dropIntervalMillis(int level) {
        int lv = Math.max(1, Math.min(MAX_LEVEL, level));
        double seconds = Math.pow(0.8 - (lv - 1) * 0.007, lv - 1);
        
        return Math.max(1, Math.round(seconds * 1000));
    }
}