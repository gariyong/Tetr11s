package com.tetr11s.settings;

import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.game.Difficulty;
import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.score.ScoreStore;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

// 설정 화면. 색상, 키 바인딩, 프리셋을 관리한다.
// 스코어보드 기록은 설정과 다른 파일(ScoreStore)에 있어서, '기록 초기화'로만 지운다

public class SettingsView extends BorderPane {
    private static final double SAMPLE_CELL_SIZE = 54;
    private static final double SAMPLE_GAP = 8;

    private final DefaultPalette palette;
    private final KeyBindings bindings;
    private final Canvas samples;
    private final Label selectedLabel = new Label();
    private final Label valueLabel = new Label();
    private final TextField presetName = new TextField();
    private final ToggleGroup difficultyGroup = new ToggleGroup();
    private final Map<GameAction, Button> keyButtons = new EnumMap<>(GameAction.class);
    private int selectedIndex = -1;
    private double lastX;
    private double lastY;

    public SettingsView(DefaultPalette palette, KeyBindings bindings, Runnable onBack) {
        this.palette = palette;
        this.bindings = bindings;
        this.samples = new Canvas(
                Tetromino.values().length * (SAMPLE_CELL_SIZE + SAMPLE_GAP) + SAMPLE_GAP,
                SAMPLE_CELL_SIZE + 34);

        setPadding(new Insets(24));
        setStyle("-fx-background-color: #1e1e1e;");
        setTop(createTopControls());
        setCenter(createEditor());
        setBottom(createFooter(onBack));
        drawSamples();
    }

    private VBox createTopControls() {
        
        Button colorblind = new Button("색맹 모드");
        colorblind.setOnAction(event -> drawSamples());
        CheckBox enabled = new CheckBox("사용");
        enabled.setTextFill(Color.WHITE);
        enabled.setSelected(palette.isColorblindMode());
        enabled.setOnAction(event -> palette.setColorblindMode(enabled.isSelected()));

        HBox controls = new HBox(10, colorblind, enabled);
        controls.setAlignment(Pos.CENTER);
        VBox top = new VBox(10, title("설정"), controls, createDifficultySelector());
        top.setAlignment(Pos.CENTER);
        return top;
    }

    // 난이도 선택. 고르는 즉시 저장하고, 다음 게임부터 적용된다
    private HBox createDifficultySelector() {
        Label label = new Label("난이도");
        label.setTextFill(Color.WHITE);

        HBox selector = new HBox(12, label);
        for (Difficulty difficulty : Difficulty.values()) {
            RadioButton option = new RadioButton(difficulty.displayName());
            option.setUserData(difficulty);
            option.setToggleGroup(difficultyGroup);
            option.setTextFill(Color.WHITE);
            selector.getChildren().add(option);
        }

        // 저장된 난이도를 먼저 표시한 뒤에 저장 리스너를 붙인다 (처음 표시할 때는 저장하지 않도록)
        selectDifficulty(SettingsStore.loadDifficulty());
        difficultyGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle != null) {
                SettingsStore.saveDifficulty((Difficulty) newToggle.getUserData());
            }
        });

        selector.setAlignment(Pos.CENTER);
        return selector;
    }

    // 화면의 난이도 선택 표시를 바꾼다
    private void selectDifficulty(Difficulty difficulty) {
        for (Toggle toggle : difficultyGroup.getToggles()) {
            if (toggle.getUserData() == difficulty) {
                difficultyGroup.selectToggle(toggle);
            }
        }
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
                new Label("색상 프리셋 이름"), presetName, presetButtons,
                createKeyBindingEditor());
        editor.setAlignment(Pos.TOP_CENTER);
        return editor;
    }

    private VBox createKeyBindingEditor() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        int row = 0;
        for (GameAction action : GameAction.values()) {
            Label label = new Label(action.name());
            Button key = new Button(bindings.keyFor(action).getName());
            keyButtons.put(action, key);
            key.setOnAction(event -> {
                key.setText("키를 누르세요");
                key.setOnKeyPressed(keyEvent -> {
                    bindings.bind(action, keyEvent.getCode());
                    key.setText(bindings.keyFor(action).getName());
                    keyEvent.consume();
                });
                key.requestFocus();
            });
            grid.addRow(row++, label, key);
        }

        Button defaultKeys = new Button("기본 키");
        defaultKeys.setOnAction(event -> {
            bindings.resetToDefault();
            bindings.saveActive();
            updateKeyButtons();
            showMessage("키 바인딩", "기본 키로 복원했습니다.");
        });
        Button saveKeys = new Button("키 프리셋 저장");
        saveKeys.setOnAction(event -> saveKeyPreset());
        Button loadKeys = new Button("키 프리셋 불러오기");
        loadKeys.setOnAction(event -> loadKeyPreset());
        return new VBox(8, new Label("키 바인딩"), grid,
                new HBox(8, defaultKeys, saveKeys, loadKeys));
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
        Button reset = new Button("모든 설정 초기화");
        reset.setTextFill(Color.ORANGE);
        reset.setOnAction(event -> resetAll());
        Button delete = new Button("save 파일 삭제");
        delete.setOnAction(event -> {
            SettingsStore.delete();
            showMessage("설정 파일 삭제", "save.properties를 삭제했습니다. 다음 실행부터 기본 설정을 사용합니다.");
        });
        Button resetScores = new Button("기록 초기화");          // ← 추가
        resetScores.setTextFill(Color.ORANGE);
        resetScores.setOnAction(event -> resetScoreboard());

        HBox footer = new HBox(8, save, load, normal, reset, resetScores, delete, back);
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

    private void saveKeyPreset() {
            try {
                bindings.savePreset(presetName.getText());
                showMessage("키 프리셋 저장", "키 프리셋을 저장했습니다.");
            } catch (IllegalArgumentException e) {
                showMessage("키 프리셋 저장 실패", e.getMessage());
            }
    }

    private void loadKeyPreset() {
            try {
                bindings.loadPreset(presetName.getText());
                updateKeyButtons();
                showMessage("키 프리셋 불러오기", "키 프리셋을 불러왔습니다.");
            } catch (IllegalArgumentException e) {
                showMessage("키 프리셋 불러오기 실패", e.getMessage());
            }
    }


    private void resetAll() {
            SettingsStore.delete();
            palette.resetAll();
            bindings.resetToDefault();
            bindings.saveActive();
            updateKeyButtons();
            SettingsStore.saveScale(1.0);
            selectDifficulty(Difficulty.DEFAULT);
            drawSamples();
            showMessage("설정 초기화", "모든 설정을 기본값으로 되돌렸습니다.");
    }

    // 스코어보드 기록만 지운다 (설정은 그대로). 되돌릴 수 없으므로 한 번 더 묻는다
    private void resetScoreboard() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "스코어보드 기록을 모두 지울까요?\n지운 기록은 되돌릴 수 없습니다.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("기록 초기화");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            new ScoreStore().delete();
            showMessage("기록 초기화", "스코어보드 기록을 모두 지웠습니다.");
        } catch (IllegalStateException e) {
            showMessage("기록 초기화 실패", e.getMessage());
        }
    }

    private void updateKeyButtons() {
        keyButtons.forEach((action, button) ->
                button.setText(bindings.keyFor(action).getName()));
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
