package com.tetr11s.settings;

import com.tetr11s.game.tetromino.Tetromino;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

// 색맹모드 설정 뷰 

public class ColorblindSettingsView extends BorderPane {
    private static final double SAMPLE_CELL_SIZE = 54;
    private static final double SAMPLE_GAP = 8;

    private final DefaultPalette palette;
    private final Canvas samples;
    private final Label selectedLabel = new Label();
    private final Label valueLabel = new Label();
    private final TextField presetName = new TextField();
    private int selectedIndex = -1;
    private double lastX;
    private double lastY;

    public ColorblindSettingsView(DefaultPalette palette, Runnable onBack) {
        this.palette = palette;
        this.samples = new Canvas(
                Tetromino.values().length * (SAMPLE_CELL_SIZE + SAMPLE_GAP) + SAMPLE_GAP,
                SAMPLE_CELL_SIZE + 34);

        setPadding(new Insets(24));
        setStyle("-fx-background-color: #1e1e1e;");
        setTop(title("색맹 모드 설정"));
        setCenter(createEditor());
        setBottom(createFooter(onBack));
        drawSamples();
    }

    private VBox createEditor() {
        Label help = new Label(
                "블럭을 클릭한 뒤 좌우로 움직여 색조, 위아래로 움직여 명도를 조절하세요.\n"
                        + "블럭 위에서 스크롤하면 채도를 조절합니다.");
        help.setTextFill(Color.LIGHTGRAY);

        samples.setOnMousePressed(this::selectSample);
        samples.setOnMouseDragged(this::adjustSelectedColor);
        samples.setOnScroll(event -> {
            if (selectedIndex >= 0) {
                Tetromino type = Tetromino.values()[selectedIndex];
                Color color = palette.getColor(type);
                double saturation = clamp(color.getSaturation() + event.getDeltaY() / 500.0);
                palette.setColor(type, Color.hsb(color.getHue(), saturation, color.getBrightness()));
                drawSamples();
            }
        });

        HBox presetButtons = new HBox(8,
                presetButton(ColorblindPreset.RED_GREEN),
                presetButton(ColorblindPreset.PROTAN),
                presetButton(ColorblindPreset.DEUTAN),
                presetButton(ColorblindPreset.TRITAN));
        presetButtons.setAlignment(Pos.CENTER);

        VBox editor = new VBox(14, help, samples, selectedLabel, valueLabel,
                new Label("내 프리셋 이름"), presetName, presetButtons);
        editor.setAlignment(Pos.TOP_CENTER);
        return editor;
    }

    private HBox createFooter(Runnable onBack) {
        Button save = new Button("프리셋 저장");
        save.setOnAction(event -> savePreset());
        Button load = new Button("프리셋 불러오기");
        load.setOnAction(event -> loadPreset());
        Button normal = new Button("기본 색상");
        normal.setOnAction(event -> {
            palette.restoreDefaultColors();
            palette.setColorblindMode(false);
            drawSamples();
        });
        Button back = new Button("돌아가기");
        back.setOnAction(event -> onBack.run());

        HBox footer = new HBox(8, save, load, normal, back);
        footer.setAlignment(Pos.CENTER);
        return footer;
    }

    private Button presetButton(ColorblindPreset preset) {
        Button button = new Button(preset.displayName());
        button.setOnAction(event -> {
            palette.applyPreset(preset);
            drawSamples();
        });
        return button;
    }

    private void selectSample(MouseEvent event) {
        int index = (int) (event.getX() / (SAMPLE_CELL_SIZE + SAMPLE_GAP));
        if (index < 0 || index >= Tetromino.values().length) {
            return;
        }
        selectedIndex = index;
        lastX = event.getX();
        lastY = event.getY();
        updateSelection();
    }

    private void adjustSelectedColor(MouseEvent event) {
        if (selectedIndex < 0) {
            return;
        }
        Tetromino type = Tetromino.values()[selectedIndex];
        Color color = palette.getColor(type);
        double hue = (color.getHue() + (event.getX() - lastX) * 0.8) % 360;
        if (hue < 0) {
            hue += 360;
        }
        double brightness = clamp(color.getBrightness() - (event.getY() - lastY) / 160.0);
        palette.setColor(type, Color.hsb(hue, color.getSaturation(), brightness));
        lastX = event.getX();
        lastY = event.getY();
        drawSamples();
    }

    private void drawSamples() {
        var graphics = samples.getGraphicsContext2D();
        graphics.clearRect(0, 0, samples.getWidth(), samples.getHeight());
        Tetromino[] types = Tetromino.values();
        for (int i = 0; i < types.length; i++) {
            double x = SAMPLE_GAP + i * (SAMPLE_CELL_SIZE + SAMPLE_GAP);
            Color color = palette.getColor(types[i]);
            graphics.setFill(color);
            graphics.fillRect(x, 2, SAMPLE_CELL_SIZE, SAMPLE_CELL_SIZE);
            graphics.setStroke(color.brighter());
            graphics.strokeRect(x + 0.5, 2.5, SAMPLE_CELL_SIZE - 1, SAMPLE_CELL_SIZE - 1);
            palette.getPattern(types[i]).draw(graphics, x, 2, SAMPLE_CELL_SIZE, color);
            graphics.setFill(Color.WHITE);
            graphics.fillText(types[i].name(), x + SAMPLE_CELL_SIZE / 2 - 4, SAMPLE_CELL_SIZE + 20);
        }
        updateSelection();
    }

    private void updateSelection() {
        if (selectedIndex < 0) {
            selectedLabel.setText("블럭을 선택하세요.");
            valueLabel.setText("");
            return;
        }
        Tetromino type = Tetromino.values()[selectedIndex];
        Color color = palette.getColor(type);
        selectedLabel.setText("선택: " + type.name());
        valueLabel.setText(String.format("H %.0f°  S %.0f%%  B %.0f%%",
                color.getHue(), color.getSaturation() * 100, color.getBrightness() * 100));
    }

    private void savePreset() {
        try {
            palette.savePreset(presetName.getText());
            showMessage("프리셋 저장", "프리셋을 저장했습니다.");
        } catch (IllegalArgumentException e) {
            showMessage("프리셋 저장 실패", e.getMessage());
        }
    }

    private void loadPreset() {
        try {
            palette.loadPreset(presetName.getText());
            drawSamples();
            showMessage("프리셋 불러오기", "프리셋을 불러왔습니다.");
        } catch (IllegalArgumentException e) {
            showMessage("프리셋 불러오기 실패", e.getMessage());
        }
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        BorderPane.setAlignment(label, Pos.CENTER);
        return label;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
