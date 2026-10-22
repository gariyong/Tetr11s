package com.tetr11s.settings;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Canvas 셀 위에 그릴 고정 텍스처 패턴이다.
 *
 * 색상 면을 먼저 그린 뒤 호출되며, 각 패턴은 하나의 셀 영역 안에서만
 * 그려지도록 클리핑된다. 따라서 패턴 선이 인접 셀로 넘어가지 않는다.
 */
public enum TexturePattern {
    /** 왼쪽 위에서 오른쪽 아래 방향으로 반복되는 빗금. */
    DIAGONAL,
    /** 오른쪽 위에서 왼쪽 아래 방향으로 반복되는 빗금. */
    DIAGONAL_REVERSE,
    /** 일정 간격의 점 무늬. */
    DOTS,
    /** 지그재그 선 무늬. */
    ZIGZAG,
    /** 반복되는 물결 무늬. */
    WAVE,
    /** 양방향 빗금이 겹친 X 무늬. */
    CROSSHATCH,
    /** 추가 무늬 없이 기본 색상 면만 표시. */
    NONE;

    /**
     * 지정한 셀에 이 패턴을 그린다.
     *
     * @param graphics 패턴을 그릴 Canvas 그래픽 컨텍스트
     * @param x 셀의 왼쪽 픽셀 좌표
     * @param y 셀의 위쪽 픽셀 좌표
     * @param size 셀의 그리기 크기
     * @param color 셀의 기본 블럭 색상
     */
    public void draw(GraphicsContext graphics, double x, double y, double size, Color color) {
        // 패턴을 셀 안으로 제한하고, 메서드 종료 시 이전 Canvas 상태를 복원한다.
        graphics.save();
        graphics.beginPath();
        graphics.rect(x, y, size, size);
        graphics.closePath();
        graphics.clip();

        // 같은 색상을 다시 그리면 패턴이 묻히므로, 밝기에 따라 흑백 대비색을 사용한다.
        Color textureColor = getContrastColor(color);
        graphics.setStroke(textureColor);
        graphics.setFill(textureColor);
        graphics.setLineWidth(1.5);

        switch (this) {
            case DIAGONAL -> {
                for (double offset = -size; offset < size * 2; offset += 7) {
                    graphics.strokeLine(x + offset, y, x + offset - size, y + size);
                }
            }
            case DIAGONAL_REVERSE -> {
                for (double offset = 0; offset < size * 2; offset += 7) {
                    graphics.strokeLine(x + offset, y, x + offset + size, y + size);
                }
            }
            case DOTS -> {
                for (double dotY = y + 4; dotY < y + size; dotY += 8) {
                    for (double dotX = x + 4; dotX < x + size; dotX += 8) {
                        graphics.fillOval(dotX - 1.5, dotY - 1.5, 3, 3);
                    }
                }
            }
            case ZIGZAG -> {
                for (double start = x - size; start < x + size * 2; start += 10) {
                    graphics.strokeLine(start, y + size * 0.25, start + 5, y + size * 0.75);
                    graphics.strokeLine(start + 5, y + size * 0.75, start + 10, y + size * 0.25);
                }
            }
            case WAVE -> {
                for (double start = x - size; start < x + size * 2; start += 12) {
                    graphics.strokeArc(start, y + size * 0.15, 12, size * 0.7, 0, 180, javafx.scene.shape.ArcType.OPEN);
                    graphics.strokeArc(start + 6, y + size * 0.15, 12, size * 0.7, 180, 180, javafx.scene.shape.ArcType.OPEN);
                }
            }
            case CROSSHATCH -> {
                for (double offset = -size; offset < size * 2; offset += 8) {
                    graphics.strokeLine(x + offset, y, x + offset - size, y + size);
                    graphics.strokeLine(x + offset, y, x + offset + size, y + size);
                }
            }
            // 기본 색상 사각형은 호출한 View가 이미 그렸으므로 아무것도 덧그리지 않는다.
            case NONE -> { }
        }
        graphics.restore();
    }

    private static Color getContrastColor(Color color) {
        double luminance = color.getRed() * 0.299
                + color.getGreen() * 0.587
                + color.getBlue() * 0.114;
        return luminance > 0.55 ? Color.rgb(0, 0, 0, 0.75) : Color.rgb(255, 255, 255, 0.85);
    }
}
