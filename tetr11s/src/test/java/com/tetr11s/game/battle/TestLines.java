package com.tetr11s.game.battle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 테스트 전용: 공격 줄을 쉽게 만드는 도구.
 * 게임 코드에서는 쓰지 않으므로 src/test에만 둔다.
 */
public final class TestLines {

    private TestLines() {
    }

    /** 문자열로 줄을 만든다. 'X'는 블럭, '.'은 빈 칸. 예: TestLines.of("XX.X") */
    public static GarbageLine of(String pattern) {
        boolean[] cells = new boolean[pattern.length()];
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != 'X' && c != '.') {
                throw new IllegalArgumentException("'X' 또는 '.'만 사용: " + pattern);
            }
            cells[i] = (c == 'X');
        }
        return new GarbageLine(cells);
    }

    /** col 열만 비어 있고 나머지는 모두 찬 줄. 예: hole(10, 3) → "XXX.XXXXXX" */
    public static GarbageLine hole(int width, int col) {
        char[] c = new char[width];
        Arrays.fill(c, 'X');
        c[col] = '.';
        return of(new String(c));
    }

    /**
     * 빈 칸 위치가 from, from+1, ... 인 줄 n개 (위에서 아래 순서).
     * 줄마다 빈 칸 위치가 달라서 순서가 바뀌었는지 테스트로 확인할 수 있다.
     */
    public static List<GarbageLine> holes(int width, int from, int n) {
        List<GarbageLine> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(hole(width, (from + i) % width));
        }
        return list;
    }
}
