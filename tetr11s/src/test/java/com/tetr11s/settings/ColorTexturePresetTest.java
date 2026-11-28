package com.tetr11s.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

class ColorTexturePresetTest {

    @ParameterizedTest
    @EnumSource(ColorblindPreset.class)
    void 색맹_프리셋은_7가지_블럭에_서로_다른_색을_준다(ColorblindPreset preset) {
        Map<Tetromino, Color> colors = preset.colors();

        assertEquals(Tetromino.values().length, colors.size());
        Set<Color> distinct = new HashSet<>(colors.values());
        assertEquals(Tetromino.values().length, distinct.size());
        assertFalse(preset.displayName().isBlank());
    }

    @Test
    void 색맹_프리셋은_적록과_청황을_모두_포함한다() {
        Set<String> names = new HashSet<>();
        for (ColorblindPreset preset : ColorblindPreset.values()) {
            names.add(preset.displayName());
        }
        assertEquals(Set.of("적록색맹", "적색약", "녹색약", "청황색맹"), names);
    }

    @ParameterizedTest
    @EnumSource(TexturePattern.class)
    void 무늬를_그린_뒤_그래픽_상태가_원래대로_돌아온다(TexturePattern pattern) {
        GraphicsContext graphics = new Canvas(60, 60).getGraphicsContext2D();
        graphics.setLineWidth(4);
        graphics.setStroke(Color.RED);

        // 밝은 색과 어두운 색 둘 다 그려서 대비색 두 갈래를 모두 지난다
        pattern.draw(graphics, 10, 10, 30, Color.YELLOW);
        pattern.draw(graphics, 10, 10, 30, Color.NAVY);

        assertEquals(4, graphics.getLineWidth());
        assertEquals(Color.RED, graphics.getStroke());
    }
}
