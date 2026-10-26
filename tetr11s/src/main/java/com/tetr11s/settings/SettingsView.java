package com.tetr11s.settings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import com.tetr11s.app.ScreenManager;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.score.ScoreStore;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * 설정 화면의 고정 기준 크기는 시작 메뉴와 같은 480x640이다.
 * 내용은 하나의 스크롤 영역에 두고, 왼쪽 탭은 해당 설정 줄로 이동한다.
 */
public class SettingsView extends BorderPane {
    public static final double BASE_WIDTH = 480;
    public static final double BASE_HEIGHT = 640;

    private static final double SAMPLE_CELL_SIZE = 17;
    private static final double SAMPLE_GAP = 3;
    private static final String[] TAB_NAMES = {"화면", "게임", "기타"};
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private final DefaultPalette palette;
    private final KeyBindings bindings;
    private final Consumer<Double> onScaleChanged;
    private final Canvas samples = new Canvas(440, 116);
    private final VBox settingsContent = new VBox(14);
    private final VBox tabs = new VBox(4);
    private final Map<GameAction, Button> keyButtons = new EnumMap<>(GameAction.class);
    private final Map<GameAction, String> actionLabels = Map.of(
            GameAction.MOVE_LEFT, "←",
            GameAction.MOVE_RIGHT, "→",
            GameAction.SOFT_DROP, "↓",
            GameAction.HARD_DROP, "하드드롭",
            GameAction.ROTATE_CW, "회전",
            GameAction.PAUSE, "pause",
            GameAction.QUIT, "quit");

    private VBox displaySection;
    private VBox gameSection;
    private VBox otherSection;
    private ScrollPane settingsScroll;
    private int rowNumber;
    private int selectedIndex = -1;
    private double lastX;
    private double lastY;

    public SettingsView(DefaultPalette palette, KeyBindings bindings, Runnable onBack) {
        this(palette, bindings, onBack, ignored -> { });
    }

    public SettingsView(DefaultPalette palette, KeyBindings bindings, Runnable onBack,
                        Consumer<Double> onScaleChanged) {
        this.palette = palette;
        this.bindings = bindings;
        this.onScaleChanged = onScaleChanged;

        getStylesheets().add(getClass().getResource("/com/tetr11s/css/settings.css").toExternalForm());
        getStyleClass().add("settings-view");
        setPrefSize(BASE_WIDTH, BASE_HEIGHT);
        setMinSize(BASE_WIDTH, BASE_HEIGHT);
        setMaxSize(BASE_WIDTH, BASE_HEIGHT);
        setPadding(new Insets(8, 12, 8, 12));
        setTop(createPreview());
        setCenter(createMainArea());
        createFooter(onBack);
        drawSamples();
    }

    // borderpane top: 테트로미노 미리보기 영역
    private VBox createPreview() {
        samples.getStyleClass().add("tetromino-preview");
        samples.setOnMousePressed(this::selectSample);
        samples.setOnMouseDragged(this::adjustSelectedColor);
        samples.setOnScroll(event -> {
            if (selectedIndex < 0) {
                return;
            }
            Tetromino type = Tetromino.values()[selectedIndex];
            Color color = palette.getColor(type);
            double saturation = clamp(color.getSaturation() + event.getDeltaY() / 500.0);
            palette.setColor(type, Color.hsb(color.getHue(), saturation, color.getBrightness()));
            drawSamples();
        });

        Label guideLabel = new Label("블럭을 누르고 좌우 드래그: 색조, 상하 드래그: 명도, 스크롤: 채도");
        guideLabel.getStyleClass().add("preview-guide-label");

        VBox previewBox = new VBox(4, samples, guideLabel);
        previewBox.setAlignment(Pos.CENTER);
        BorderPane.setAlignment(previewBox, Pos.CENTER);
        return previewBox;
    }
    
    // borderpane center: 탭과 스크롤 영역
    private HBox createMainArea() {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tabIndex = i;
            Button tab = new Button(TAB_NAMES[i]);
            tab.getStyleClass().add("settings-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            tab.setOnAction(event -> jumpTo(tabIndex));
            tabs.getChildren().add(tab);
        }
        tabs.getStyleClass().add("settings-tabs");
        tabs.setPrefWidth(104);
        tabs.setFillWidth(true);

        rowNumber = 0;
        displaySection = createDisplaySection();
        gameSection = createGameSection();
        otherSection = createOtherSection();
        settingsContent.getChildren().setAll(displaySection, gameSection, otherSection);
        settingsContent.getStyleClass().add("settings-content");

        settingsScroll = new ScrollPane(settingsContent);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        settingsScroll.getStyleClass().add("settings-scroll");
        HBox.setHgrow(settingsScroll, Priority.ALWAYS);

        HBox area = new HBox(10, tabs, settingsScroll);
        area.getStyleClass().add("settings-main");
        jumpTo(0);
        return area;
    }

    // 설정 스크롤 점프하기
    private void jumpTo(int index) {
        for (int i = 0; i < tabs.getChildren().size(); i++) {
            tabs.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == index);
        }
        Node section = index == 0 ? displaySection : index == 1 ? gameSection : otherSection;
        if (section == null || settingsScroll == null) {
            return;
        }
        double target = section.getBoundsInParent().getMinY();
        double range = settingsScroll.getContent().getBoundsInLocal().getHeight()
                - settingsScroll.getViewportBounds().getHeight();
        settingsScroll.setVvalue(range <= 0 ? 0 : target / range);
    }

    // 화면 섹션 생성
    private VBox createDisplaySection() {
        VBox section = section("화면");
        section.getChildren().add(checkRow("◆  블럭 설정색상 커스텀 사용",
                palette.isColorblindMode(), palette::setColorblindMode));

        ComboBox<String> colors = presetCombo(false);
        Button load = accentButton("불러오기");
        Button overwrite = accentButton("덮어쓰기");
        load.setOnAction(event -> loadColorPreset(colors.getValue()));
        overwrite.setOnAction(event -> {
            if (saveColorPreset(colors.getValue())) {
                addPresetItem(colors, colors.getValue());
                loadColorPreset(colors.getValue());
            }
        });

        ComboBox<String> scales = new ComboBox<>();
        scales.setEditable(false);
        scales.getItems().addAll(
                scaleLabel(0.75),
                scaleLabel(1.0),
                scaleLabel(1.25)
        );
        scales.getItems().addAll(resolutionPresetNames());
        scales.setValue(scaleLabel(SettingsStore.loadScale()));

        // 콤보박스에서 프리셋을 고르면 바로 적용되게 연결
        scales.setOnAction(event -> {
            if (scales.getValue() != null) {
                loadScale(scales.getValue());
            }
        });

        Button scaleLoad = accentButton("적용");
        scaleLoad.setOnAction(event -> loadScale(scales.getValue()));
        section.getChildren().add(settingRow("화면 해상도", scales,
                new HBox(6, scaleLoad)));
        return section;
    }

    // 게임 섹션 생성
    private VBox createGameSection() {
        VBox section = section("게임");
        ComboBox<String> difficulty = new ComboBox<>();
        for (Difficulty value : Difficulty.values()) {
            difficulty.getItems().add(value.displayName());
        }
        difficulty.setValue(SettingsStore.loadDifficulty().displayName());
        difficulty.setOnAction(event -> {
            for (Difficulty value : Difficulty.values()) {
                if (value.displayName().equals(difficulty.getValue())) {
                    SettingsStore.saveDifficulty(value);
                    break;
                }
            }
        });
        section.getChildren().add(settingRow("◆  난이도", difficulty, new Label("")));
        CheckBox bugMode = checkBox("◇ 템전 버그판 (5번 lock마다 아이템 블록)");
        bugMode.setSelected(SettingsStore.loadItemBugMode());
        bugMode.setOnAction(event -> SettingsStore.saveItemBugMode(bugMode.isSelected()));
        section.getChildren().add(settingRow("", bugMode, new Label("테스트용")));
        section.getChildren().add(createKeySettings());
        return section;
    }

    // 키 설정 섹션 생성    
    private VBox createKeySettings() {
        VBox section = new VBox(6);
        section.getStyleClass().add("subsection");
        section.getChildren().add(sectionTitle("◆  키 설정"));

        ComboBox<String> presets = presetCombo(true);
        Button load = accentButton("불러오기");
        Button overwrite = accentButton("덮어쓰기");
        load.setOnAction(event -> loadKeyPreset(presets.getValue()));
        overwrite.setOnAction(event -> {
            if (saveKeyPreset(presets.getValue())) {
                addPresetItem(presets, presets.getValue());
            }
        });
        section.getChildren().add(settingRow("", presets, new HBox(6, load, overwrite)));

        VBox keyRows = new VBox(0);
        for (GameAction action : GameAction.values()) {
            Button key = keyButton(action);
            keyRows.getChildren().add(settingRow("- " + actionLabels.get(action),
                    key, new Label("")));
        }
        section.getChildren().add(keyRows);

        CheckBox confirm = checkBox("체크한 상태에서 초기화하면 만든 키 프리셋도 삭제");
        Button reset = dangerButton("키 설정 초기화");
        reset.setOnAction(event -> {
            if (!confirm.isSelected()) {
                showMessage("키 설정 초기화", "초기화 체크박스를 먼저 선택하세요.");
                return;
            }
            bindings.resetToDefault();
            bindings.saveActive();
            bindings.deletePresets();
            updateKeyButtons();
            showMessage("키 설정 초기화", "키와 키 프리셋을 초기화했습니다.");
        });
        section.getChildren().add(new HBox(6, reset, confirm));
        return section;
    }

    // 기타 섹션 생성
    private VBox createOtherSection() {
        VBox section = section("기타");
        CheckBox confirm = checkBox("체크한 상태에서 초기화");
        Button reset = dangerButton("초기화");
        reset.setOnAction(event -> {
            if (!confirm.isSelected()) {
                showMessage("전체 초기화", "초기화 체크박스를 먼저 선택하세요.");
                return;
            }
            SettingsStore.delete();
            palette.resetAll();
            bindings.resetToDefault();
            bindings.saveActive();
            try {
                new ScoreStore().delete();
            } catch (IllegalStateException e) {
                showMessage("전체 초기화 실패", e.getMessage());
                return;
            }
            drawSamples();
            showMessage("전체 초기화", "스코어보드, 프리셋, 팔레트를 초기화했습니다.");
            onScaleChanged.accept(1.0);
        });
        section.getChildren().add(settingRow("◆  초기화", reset, confirm));
        section.getChildren().add(settingRow("◆  소리", new Label("추가예정"), new Label("")));
        return section;
    }

    // VBox 타입 섹션 정의
    private VBox section(String title) {
        VBox section = new VBox(0);
        section.getStyleClass().add("settings-section");
        section.getChildren().add(sectionTitle(title));
        return section;
    }

    // Label 타입 섹션 타이틀 정의
    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

private HBox checkRow(String text, boolean selected, Consumer<Boolean> action) {
        CheckBox check = checkBox(text);
        check.setSelected(selected);
        check.setOnAction(event -> action.accept(check.isSelected()));
        return settingRow("", check, new Label(""));
    }

    private CheckBox checkBox(String text) {
        CheckBox check = new CheckBox(text);
        check.getStyleClass().add("settings-check");
        return check;
    }

    private HBox settingRow(String title, Node control, Node actions) {
        Label label = new Label(title);
        label.getStyleClass().add("setting-label");
        HBox row = new HBox(8, label, control, actions);
        row.getStyleClass().add(rowNumber++ % 2 == 0 ? "stripe-light" : "stripe-dark");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private Button keyButton(GameAction action) {
        Button key = new Button(bindings.keyFor(action).getName());
        key.getStyleClass().add("key-button");
        key.setOnAction(event -> {
            key.setText("...");
            key.setOnKeyPressed(keyEvent -> captureKey(action, key, keyEvent));
            key.requestFocus();
        });
        keyButtons.put(action, key);
        return key;
    }

    private void captureKey(GameAction action, Button button, KeyEvent event) {
        if (!(event.getCode() == KeyCode.ESCAPE && event.isShiftDown())) {
            bindings.bind(action, event.getCode());
            button.setText(bindings.keyFor(action).getName());
        } else {
            button.setText(bindings.keyFor(action).getName());
        }
        button.setOnKeyPressed(null);
        event.consume();
    }

    private ComboBox<String> presetCombo(boolean keyPreset) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.getItems().addAll(keyPreset ? keyPresetNames() : colorPresetNames());
        combo.setPromptText("프리셋 선택");
        combo.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                combo.getEditor().requestFocus();
                combo.getEditor().selectAll();
            }
        });
        combo.setOnShown(event -> {
            if (!combo.getItems().contains("추가")) {
                combo.getItems().add("추가");
            }
        });
        combo.setOnAction(event -> {
            if ("추가".equals(combo.getValue())) {
                combo.getItems().remove("추가");
                combo.setValue("새 프리셋");
                combo.getEditor().selectAll();
            }
        });
        return combo;
    }



    private boolean saveColorPreset(String selected) {
        try {
            palette.savePreset(presetValue(selected));
            showMessage("컬러 프리셋", "현재 팔레트를 덮어썼습니다.");
            return true;
        } catch (IllegalArgumentException e) {
            showMessage("컬러 프리셋 저장 실패", e.getMessage());
            return false;
        }
    }

    private void loadColorPreset(String selected) {
        try {
            String name = presetValue(selected);
            ColorblindPreset preset = colorblindPreset(name);
            if (preset != null) {
                palette.applyPreset(preset);
            } else {
                palette.loadPreset(name);
            }
            drawSamples();
        } catch (IllegalArgumentException e) {
            showMessage("컬러 프리셋 불러오기 실패", e.getMessage());
        }
    }

    // 키바인딩 저장과 불러오기
    private boolean saveKeyPreset(String selected) {
        try {
            bindings.savePreset(presetValue(selected));
            showMessage("키 프리셋", "현재 키 설정을 덮어썼습니다.");
            return true;
        } catch (IllegalArgumentException e) {
            showMessage("키 프리셋 저장 실패", e.getMessage());
            return false;
        }
    }

    private void addPresetItem(ComboBox<String> combo, String value) {
        String name = value == null ? "" : value.trim();
        if (!name.isBlank() && !"추가".equals(name) && !combo.getItems().contains(name)) {
            combo.getItems().add(name);
        }
        if (!name.isBlank()) {
            combo.setValue(name);
        }
    }

    private void loadKeyPreset(String selected) {
        try {
            bindings.loadPreset(presetValue(selected));
            updateKeyButtons();
        } catch (IllegalArgumentException e) {
            showMessage("키 프리셋 불러오기 실패", e.getMessage());
        }
    }
private void loadScale(String selected) {
        try {
            double scale = isBuiltInScale(selected)
                    ? parseScale(selected)
                    : SettingsStore.loadResolutionPreset(presetValue(selected));
            SettingsStore.saveScale(scale);
            
            // 크기만 대충 바꾸는 것이 아니라, 화면을 완전히 새로 고침(재생성)하여 반영
            if (onScaleChanged != null) {
                onScaleChanged.accept(scale);
            }
        } catch (IllegalArgumentException e) {
            showMessage("화면 해상도", e.getMessage());
        }
    }
    
    private String presetValue(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank() || "추가".equals(name)) {
            throw new IllegalArgumentException("프리셋 이름을 입력하세요.");
        }
        return name;
    }

    private List<String> colorPresetNames() {
        List<String> names = new ArrayList<>(List.of("적록", "적색", "녹색", "녹황"));
        Properties properties = SettingsStore.load();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("preset.") && key.contains(".color.")) {
                String name = key.substring(7, key.indexOf(".color."));
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private List<String> keyPresetNames() {
        List<String> names = new ArrayList<>();
        Properties properties = SettingsStore.load();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("preset.") && key.contains(".key.")) {
                String name = key.substring(7, key.indexOf(".key."));
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private ColorblindPreset colorblindPreset(String name) {
        return switch (name) {
            case "적록" -> ColorblindPreset.RED_GREEN;
            case "적색" -> ColorblindPreset.PROTAN;
            case "녹색" -> ColorblindPreset.DEUTAN;
            case "녹황" -> ColorblindPreset.TRITAN;
            default -> null;
        };
    }

    private double parseScale(String value) {
        if (value == null) {
            throw new IllegalArgumentException("화면 해상도를 선택하세요.");
        }
        if (value.contains("0.75")) {
            return 0.75;
        }
        if (value.contains("1.25")) {
            return 1.25;
        }
        if (value.contains("1.0") || value.contains("기본")) {
            return 1.0;
        }
        throw new IllegalArgumentException("지원하지 않는 화면 해상도입니다.");
    }

    private boolean isBuiltInScale(String value) {
        return value != null && (value.contains("0.75") || value.contains("1.0")
                || value.contains("1.25") || value.contains("작게") || value.contains("기본") || value.contains("크게"));
    }

    private List<String> resolutionPresetNames() {
        List<String> names = new ArrayList<>();
        for (String key : SettingsStore.load().stringPropertyNames()) {
            if (key.startsWith("preset.resolution.")) {
                names.add(key.substring("preset.resolution.".length()));
            }
        }
        return names;
    }

    private String scaleLabel(double scale) {
        String res = ScreenManager.getResolutionText(scale);
        if (scale == 0.75) {
            return "작게 (" + res + ") · 0.75";
        } else if (scale == 1.25) {
            return "크게 (" + res + ") · 1.25";
        }
        return "기본 (" + res + ") · 1.0";
    }

    private HBox createFooter(Runnable onBack) {
        Button back = accentButton("돌아가기");
        back.setOnAction(event -> onBack.run());
        HBox footer = new HBox(back);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(6, 0, 0, 0));
        setBottom(footer);
        return footer;
    }

    private Button accentButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("accent-button");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        return button;
    }

    private void selectSample(MouseEvent event) {
        int index = sampleIndexAt(event.getX(), event.getY());
        if (index >= 0) {
            selectedIndex = index;
            lastX = event.getX();
            lastY = event.getY();
            drawSamples();
        }
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

    private int sampleIndexAt(double mouseX, double mouseY) {
        Tetromino[] types = Tetromino.values();
        for (int i = 0; i < types.length; i++) {
            double[] origin = sampleOrigin(i);
            for (Cell cell : types[i].cells(0)) {
                double x = origin[0] + cell.col() * SAMPLE_CELL_SIZE;
                double y = origin[1] + cell.row() * SAMPLE_CELL_SIZE;
                if (mouseX >= x && mouseX <= x + SAMPLE_CELL_SIZE
                        && mouseY >= y && mouseY <= y + SAMPLE_CELL_SIZE) {
                    return i;
                }
            }
        }
        return -1;
    }

    private double[] sampleOrigin(int index) {
        int column = index % 4;
        int row = index / 4;
        return new double[] {18 + column * 106, 8 + row * 53};
    }

    private void drawSamples() {
        GraphicsContext graphics = samples.getGraphicsContext2D();
        graphics.clearRect(0, 0, samples.getWidth(), samples.getHeight());
        Tetromino[] types = Tetromino.values();
        for (int i = 0; i < types.length; i++) {
            double[] origin = sampleOrigin(i);
            Color color = palette.getColor(types[i]);
            for (Cell cell : types[i].cells(0)) {
                double x = origin[0] + cell.col() * SAMPLE_CELL_SIZE;
                double y = origin[1] + cell.row() * SAMPLE_CELL_SIZE;
                graphics.setFill(color);
                graphics.fillRect(x, y, SAMPLE_CELL_SIZE - 1, SAMPLE_CELL_SIZE - 1);
                palette.getPattern(types[i]).draw(graphics, x, y, SAMPLE_CELL_SIZE - 1, color);
                graphics.setStroke(color.brighter());
                graphics.strokeRect(x + 0.5, y + 0.5, SAMPLE_CELL_SIZE - 2, SAMPLE_CELL_SIZE - 2);
            }
            if (selectedIndex == i) {
                graphics.setStroke(Color.WHITE);
                graphics.setLineWidth(2);
                graphics.strokeRect(origin[0] - 3, origin[1] - 3,
                        4 * SAMPLE_CELL_SIZE + 5, 4 * SAMPLE_CELL_SIZE + 5);
            }
        }
    }

    private void updateKeyButtons() {
        keyButtons.forEach((action, button) -> button.setText(bindings.keyFor(action).getName()));
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
