package com.tetr11s.game;

import java.util.Locale;

/**
 * 난이도
 *
 * 레벨 1의 속도는 모두 같고, 레벨이 오를 때 빨라지는 정도가 다르다
 * speedUpRate: 레벨이 오를 때 늘어나는 속도에 곱하는 값 (Normal 기준 1.0, Easy는 20% 느리게, Hard는 20% 빠르게)
 * iBlockWeight: RouletteWheelGenerator에서 I 블럭의 가중치 (다른 블럭은 DEFAULT_WEIGHT = 10)
 *               Easy는 I 블럭이 20% 더 자주, Hard는 20% 덜 나온다
 */
public enum Difficulty {
    EASY("Easy", 0.8, 12),
    NORMAL("Normal", 1.0, 10),
    HARD("Hard", 1.2, 8);

    /** 설정을 저장한 적이 없거나 저장된 값을 읽을 수 없을 때 쓰는 난이도 */
    public static final Difficulty DEFAULT = NORMAL;

    private final String displayName;
    private final double speedUpRate;
    private final int iBlockWeight;

    Difficulty(String displayName, double speedUpRate, int iBlockWeight) {
        this.displayName = displayName;
        this.speedUpRate = speedUpRate;
        this.iBlockWeight = iBlockWeight;
    }

    /** 화면에 보여줄 이름 */
    public String displayName() {
        return displayName;
    }

    /** 레벨이 오를 때 늘어나는 속도에 곱하는 값 */
    public double speedUpRate() {
        return speedUpRate;
    }

    /** 블럭 생성기(RouletteWheelGenerator)에서 I 블럭의 가중치 */
    public int iBlockWeight() {
        return iBlockWeight;
    }

    /** 다음 난이도 (Hard 다음은 Easy로 돌아간다). 스코어보드에서 → 키 */
    public Difficulty next() {
        Difficulty[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 이전 난이도 (Easy 이전은 Hard로 돌아간다). 스코어보드에서 ← 키 */
    public Difficulty previous() {
        Difficulty[] all = values();
        return all[Math.floorMod(ordinal() - 1, all.length)];
    }

    /**
     * 파일에 저장된 문자열을 난이도로 바꾼다
     * 저장된 값이 없거나 알 수 없는 값이면 DEFAULT
     */
    public static Difficulty fromSaved(String value) {
        if (value == null) {
            return DEFAULT;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}