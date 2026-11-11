package com.tetr11s.game.battle;

import java.util.Arrays;
import java.util.Objects;

public final class GarbageLine {
  private final boolean[] filled;

  /**
     * @param filled 칸마다 블럭이 있으면 true (왼쪽 열부터)
     */
    public GarbageLine(boolean[] filled) {
        Objects.requireNonNull(filled, "filled");
        if (filled.length == 0) {
            throw new IllegalArgumentException("줄의 너비는 1 이상이어야 함");
        }
        this.filled = filled.clone();
    }
 
    /** 줄의 너비(칸 수). */
    public int width() {
        return filled.length;
    }
 
    /** col 열에 블럭이 있는지. */
    public boolean isFilled(int col) {
        Objects.checkIndex(col, filled.length);
        return filled[col];
    }
 
    /** 모양이 같으면 같은 줄이다. */
    @Override
    public boolean equals(Object o) {
        return o instanceof GarbageLine other && Arrays.equals(filled, other.filled);
    }
 
    @Override
    public int hashCode() {
        return Arrays.hashCode(filled);
    }
 
    /** "XX.XXXXXXX" 형태 (X: 블럭, .: 빈칸). 디버깅과 테스트 실패 메시지용. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(filled.length);
        for (boolean f : filled) {
            sb.append(f ? 'X' : '.');
        }
        return sb.toString();
    }

}
