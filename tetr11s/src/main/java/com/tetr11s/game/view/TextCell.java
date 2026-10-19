package com.tetr11s.game.view;

import java.util.HashMap;
import java.util.Map;

import javafx.geometry.Bounds;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

/**
 * 글자 하나를 그리는 정사각형 칸
 * 글자의 "실제로 칠해진 부분"의 크기를 재고, 칸에 맞게 가로와 세로를 따로 늘려서 그린다.
 */
class TextCell extends StackPane {

    // 칸을 얼마나 채울지
    private static final double FILL = 0.9;

    /** 글자별 실제 크기를 한 번만 재서 기억한다 */
    private static final Map<String, Bounds> GLYPH_BOUNDS = new HashMap<>();

    private final double size;
    private final Font font;
    private final Text text = new Text();

    TextCell(double size, Font font) {
        this.size = size;
        this.font = font;

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);

        text.setFont(font);
        // 글자 주변 여백을 빼고, 실제로 칠해진 부분을 기준으로 배치한다
        text.setBoundsType(TextBoundsType.VISUAL);
        getChildren().add(text);
    }

    /**
     * 칸에 글자를 그린다.
     *
     * @param fill true면 칸 크기에 맞게 늘려서 그린다 (블럭, 고스트). false면 원래 크기 (빈 칸의 점)
     */
    void show(String symbol, Color color, boolean fill) {
        text.setText(symbol);
        text.setFill(color);

        if (fill && !symbol.isEmpty()) {
            Bounds b = glyphBounds(symbol);
            text.setScaleX(FILL * size / b.getWidth());
            text.setScaleY(FILL * size / b.getHeight());
        } else {
            text.setScaleX(1);
            text.setScaleY(1);
        }
    }

    void clear() {
        show("", Color.TRANSPARENT, false);
    }

    private Bounds glyphBounds(String symbol) {
        return GLYPH_BOUNDS.computeIfAbsent(symbol + "|" + font, key -> {
            Text probe = new Text(symbol);
            probe.setFont(font);
            probe.setBoundsType(TextBoundsType.VISUAL);
            return probe.getLayoutBounds();
        });
    }
}