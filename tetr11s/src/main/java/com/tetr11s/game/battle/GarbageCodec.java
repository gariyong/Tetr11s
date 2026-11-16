package com.tetr11s.game.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 공격 줄 목록 <-> 글자 변환 (네트워크로 보낼 때 사용)
 *
 * 한 줄은 "XX.XXXXXXX" 형태 (X: 블럭, .: 빈 칸) 줄 사이는 줄바꿈으로 나누고, 순서는 위에서 아래
 * 줄이 하나도 없으면 빈 문자열
 */
public final class GarbageCodec {

    private static final char FILLED = 'X';
    private static final char EMPTY = '.';
    private static final String SEPARATOR = "\n";

    private GarbageCodec() {
    }

    /*  줄 목록 -> 메시지 형태로 변환 */
    public static String encode(List<GarbageLine> lines) {
        Objects.requireNonNull(lines, "lines");
        StringBuilder text = new StringBuilder();
        for (GarbageLine line : lines) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            for (int col = 0; col < line.width(); col++) {
                text.append(line.isFilled(col) ? FILLED : EMPTY);
            }
        }
        return text.toString();
    }

    /**
     * @throws IllegalArgumentException 형식이 잘못됐을 때 (다른 글자, 빈 줄, 줄마다 다른 너비)
     */
    public static List<GarbageLine> decode(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return List.of();
        }

        List<GarbageLine> lines = new ArrayList<>();
        for (String row : text.split(SEPARATOR, -1)) {   // -1: 끝에 붙은 빈 줄도 버리지 않고 검사한다
            if (row.isEmpty() || (!lines.isEmpty() && row.length() != lines.get(0).width())) {
                throw new IllegalArgumentException("공격 줄의 너비가 잘못됨: " + text);
            }
            boolean[] filled = new boolean[row.length()];
            for (int col = 0; col < row.length(); col++) {
                char ch = row.charAt(col);
                if (ch != FILLED && ch != EMPTY) {
                    throw new IllegalArgumentException("공격 줄에 쓸 수 없는 글자: " + ch);
                }
                filled[col] = (ch == FILLED);
            }
            lines.add(new GarbageLine(filled));
        }
        return List.copyOf(lines);
    }
}