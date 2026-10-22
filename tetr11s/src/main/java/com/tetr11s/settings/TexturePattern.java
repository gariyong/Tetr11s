package com.tetr11s.settings;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public enum TexturePattern {
    DIAGONAL,
    DIAGONAL_REVERSE,
    DOTS,
    ZIGZAG,
    WAVE,
    CROSSHATCH,
    NONE;

    public void draw(GraphicsContext graphics, double x, double y, double size, Color color) {
        graphics.save();
        graphics.beginPath();
        graphics.rect(x, y, size, size);
        graphics.closePath();
        graphics.clip();
        graphics.setStroke(color.deriveColor(0, 1, 1, 0.8));
        graphics.setFill(color.deriveColor(0, 1, 1, 0.8));
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
            case NONE -> {
            }
        }
        graphics.restore();
    }
}
