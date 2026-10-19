package com.tetr11s.settings;

import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.paint.Color;


// 일반 모드 색상 (테트리스 가이드라인 표준 색)
public class DefaultPalette {

    private static final String SYMBOL = "■";

    private final Map<Tetromino, Color> colors = new EnumMap<>(Tetromino.class);

    public DefaultPalette() {
        colors.put(Tetromino.I, Color.rgb(0, 240, 240));   // 하늘색
        colors.put(Tetromino.O, Color.rgb(240, 240, 0));   // 노랑
        colors.put(Tetromino.T, Color.rgb(160, 0, 240));   // 보라
        colors.put(Tetromino.S, Color.rgb(0, 240, 0));     // 초록
        colors.put(Tetromino.Z, Color.rgb(240, 0, 0));     // 빨강
        colors.put(Tetromino.J, Color.rgb(0, 0, 240));     // 파랑
        colors.put(Tetromino.L, Color.rgb(240, 160, 0));   // 주황
    }

    // 블럭 종류별 색
    public Color getColor(Tetromino type) {
        return colors.get(type);
    }

    // 블럭 한 칸을 그릴 글자
    public String getSymbol(Tetromino type) {
        return SYMBOL;
    }
}