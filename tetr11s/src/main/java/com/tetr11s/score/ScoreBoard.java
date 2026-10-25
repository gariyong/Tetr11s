package com.tetr11s.score;

import java.util.ArrayList;
import java.util.List;

/**
 * 점수 높은 순으로 정렬된 상위 기록 목록
 *
 * 최대 CAPACITY개까지 보존하고, 넘치면 가장 낮은 기록이 빠짐
 * 동점이면 먼저 세운 기록이 위에 옴
 */
public class ScoreBoard {

    public static final int CAPACITY = 10;

    private final List<ScoreEntry> entries = new ArrayList<>();

    public ScoreBoard() {
    }

    /**
     * 저장된 기록으로 만든다
     * 파일 내용이 정렬돼 있지 않거나 CAPACITY개를 넘어도 add()를 거치면서 다시 정리된다
     *
     * @param saved 순위 순서대로 저장돼 있던 기록
     */
    public ScoreBoard(List<ScoreEntry> saved) {
        for (ScoreEntry entry : saved) {
            add(entry);
        }
    }

    /** 이 점수가 순위표에 들어갈 수 있는지를 확인 */
    public boolean qualifies(int score) {
        if (entries.size() < CAPACITY) {
            return true;
        }
        return score > entries.get(entries.size() - 1).score();
    }

    /**
     * 기록을 순위에 맞는 자리에 넣는다
     *
     * @return 들어간 자리 (0 = 1위). 순위권 밖이면 -1
     */
    public int add(ScoreEntry entry) {
        if (!qualifies(entry.score())) {
            return -1;
        }

        // 나보다 점수가 높거나 같은 기록 뒤에 넣는다
        int index = 0;
        while (index < entries.size() && entries.get(index).score() >= entry.score()) {
            index++;
        }
        entries.add(index, entry);

        if (entries.size() > CAPACITY) {
            entries.remove(entries.size() - 1);
        }
        return index;
    }

    /** 순위 순서대로 된 기록 (읽기 전용) */
    public List<ScoreEntry> entries() {
        return List.copyOf(entries);
    }
}