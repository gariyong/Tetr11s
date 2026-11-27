package com.tetr11s.settings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

import com.tetr11s.app.ScreenManager;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.controller.BattleKeyBindings;
import com.tetr11s.game.controller.GameAction;
import com.tetr11s.game.controller.PlayerInput;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.score.ScoreStore;

import javafx.application.Platform;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * 설정 화면 (기준 크기 740x660).
 *
 * 왼쪽 고정폭 탭(디스플레이 · 게임 · 키 설정 · 기타)과 오른쪽 스크롤 영역으로 구성되며,
 * 싱글 플레이 키뿐 아니라 로컬 2인 대전(P1/P2) 키 설정, 기록 초기화, 전체 초기화를 모두 지원한다.
 */
public class SettingsView extends BorderPane {

    public static final double BASE_WIDTH = 740;
    public static final double BASE_HEIGHT = 660;

    private static final double SAMPLE_CELL_SIZE = 16;
    private static final double TAB_COLUMN_WIDTH = 132;
    private static final String[] TAB_NAMES = {"디스플레이", "게임", "키 설정", "기타"};
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private final DefaultPalette palette;
    private final KeyBindings bindings;
    private final BattleKeyBindings battleBindings;
    private final Consumer<Double> onScaleChanged;

    private final Canvas samples = new Canvas(680, 84);
    private final VBox settingsContent = new VBox(14);
    private final VBox tabs = new VBox(4);
    private final Map<GameAction, Button> keyButtons = new EnumMap<>(GameAction.class);
    private final Map<GameAction, Button> p1BattleButtons = new EnumMap<>(GameAction.class);
    private final Map<GameAction, Button> p2BattleButtons = new EnumMap<>(GameAction.class);
    private final Map<GameAction, String> actionLabels = Map.of(
            GameAction.MOVE_LEFT, "← 왼쪽 이동",
            GameAction.MOVE_RIGHT, "→ 오른쪽 이동",
            GameAction.SOFT_DROP, "↓ 소프트드롭",
            GameAction.HARD_DROP, "하드드롭",
            GameAction.ROTATE_CW, "시계 회전",
            GameAction.PAUSE, "일시정지",
            GameAction.QUIT, "종료");

    private VBox displaySection;
    private VBox gameSection;
    private VBox keySection;
    private VBox otherSection;
    private List<VBox> sections = List.of();
    private ScrollPane settingsScroll;

    // UI 즉시 동기화(refreshUi)용 컨트롤 참조
    private CheckBox colorblindCheck;
    private ComboBox<String> colorPresetCombo;
    private ComboBox<String> scaleCombo;
    private ComboBox<String> difficultyCombo;
    private CheckBox bugModeCheck;
    private ComboBox<String> keyPresetCombo;
    private CheckBox keyResetConfirmCheck;
    private CheckBox scoreResetConfirmCheck;
    private CheckBox allResetConfirmCheck;
    private Label footerStatusLabel;

    private GameAction capturingAction;
    private PlayerInput capturingBattleInput;
    private boolean suppressNextKeyRelease;
    private boolean syncingUi;

    private int rowNumber;
    private int selectedIndex = -1;
    private double lastX;
    private double lastY;

    public SettingsView(DefaultPalette palette, KeyBindings bindings, Runnable onBack) {
        this(palette, bindings, new BattleKeyBindings(), onBack, ignored -> { });
    }

    public SettingsView(DefaultPalette palette, KeyBindings bindings, Runnable onBack,
                        Consumer<Double> onScaleChanged) {
        this(palette, bindings, new BattleKeyBindings(), onBack, onScaleChanged);
    }

    public SettingsView(DefaultPalette palette, KeyBindings bindings, BattleKeyBindings battleBindings,
                        Runnable onBack, Consumer<Double> onScaleChanged) {
        this.palette = Objects.requireNonNull(palette, "palette");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.battleBindings = Objects.requireNonNull(battleBindings, "battleBindings");
        this.onScaleChanged = onScaleChanged;

        getStylesheets().add(getClass().getResource("/com/tetr11s/css/settings.css").toExternalForm());
        getStyleClass().add("settings-view");
        setPrefSize(BASE_WIDTH, BASE_HEIGHT);
        setMinSize(BASE_WIDTH, BASE_HEIGHT);
        setMaxSize(BASE_WIDTH, BASE_HEIGHT);
        setPadding(new Insets(10, 14, 10, 14));
        setTop(createPreview());
        setCenter(createMainArea());
        setBottom(createFooter(onBack));

        // 키 입력 캡처는 캡처링 단계(EventFilter)에서 가로채어 ScrollPane 방향키/Button Space·Enter 충돌을 막는다.
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleCapturingKeyPressed);
        addEventFilter(KeyEvent.KEY_RELEASED, this::handleCapturingKeyReleased);

        refreshUi();
    }

    // borderpane top: 테트로미노 7종 1줄 미리보기 영역
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
            refreshUi();
        });

        Label guideLabel = new Label("블럭을 클릭 후 좌우 드래그: 색조  ·  상하 드래그: 명도  ·  휠 스크롤: 채도");
        guideLabel.getStyleClass().add("preview-guide-label");

        VBox previewBox = new VBox(4, samples, guideLabel);
        previewBox.setAlignment(Pos.CENTER);
        BorderPane.setAlignment(previewBox, Pos.CENTER);
        return previewBox;
    }

    // borderpane center: 고정폭 왼쪽 탭과 오른쪽 스크롤 영역
    private HBox createMainArea() {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tabIndex = i;
            Button tab = new Button(TAB_NAMES[i]);
            tab.getStyleClass().add("settings-tab");
            tab.setMinWidth(TAB_COLUMN_WIDTH);
            tab.setPrefWidth(TAB_COLUMN_WIDTH);
            tab.setMaxWidth(Double.MAX_VALUE);
            tab.setMinHeight(44);
            tab.setOnAction(event -> jumpTo(tabIndex));
            tabs.getChildren().add(tab);
        }
        tabs.getStyleClass().add("settings-tabs");
        tabs.setMinWidth(TAB_COLUMN_WIDTH);
        tabs.setPrefWidth(TAB_COLUMN_WIDTH);
        tabs.setMaxWidth(TAB_COLUMN_WIDTH);
        tabs.setFillWidth(true);

        rowNumber = 0;
        displaySection = createDisplaySection();
        gameSection = createGameSection();
        keySection = createKeySection();
        otherSection = createOtherSection();
        sections = List.of(displaySection, gameSection, keySection, otherSection);

        settingsContent.getChildren().setAll(sections);
        settingsContent.getStyleClass().add("settings-content");
        settingsContent.setFillWidth(true);
        settingsContent.setMaxWidth(Double.MAX_VALUE);

        settingsScroll = new ScrollPane(settingsContent);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        settingsScroll.getStyleClass().add("settings-scroll");
        settingsScroll.setMinWidth(0);
        HBox.setHgrow(settingsScroll, Priority.ALWAYS);

        // 스크롤 이동 시 현재 위치에 해당하는 탭을 자동으로 강조한다
        settingsScroll.vvalueProperty().addListener((obs, oldVal, newVal) -> syncActiveTabFromScroll());

        HBox area = new HBox(12, tabs, settingsScroll);
        area.getStyleClass().add("settings-main");
        selectTab(0);
        return area;
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabs.getChildren().size(); i++) {
            tabs.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == index);
        }
    }

    private void syncActiveTabFromScroll() {
        if (settingsScroll == null || sections.isEmpty()) {
            return;
        }
        double contentH = settingsScroll.getContent().getBoundsInLocal().getHeight();
        double viewportH = settingsScroll.getViewportBounds().getHeight();
        double range = contentH - viewportH;
        if (range <= 0) {
            selectTab(0);
            return;
        }
        double topY = settingsScroll.getVvalue() * range + 24;
        int active = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).getBoundsInParent().getMinY() <= topY) {
                active = i;
            }
        }
        if (settingsScroll.getVvalue() >= 0.99) {
            active = sections.size() - 1;
        }
        selectTab(active);
    }

    // 설정 스크롤 점프하기
    private void jumpTo(int index) {
        selectTab(index);
        if (settingsScroll == null || index < 0 || index >= sections.size()) {
            return;
        }
        Node section = sections.get(index);
        Platform.runLater(() -> {
            double target = section.getBoundsInParent().getMinY();
            double range = settingsScroll.getContent().getBoundsInLocal().getHeight()
                    - settingsScroll.getViewportBounds().getHeight();
            double v = range <= 0 ? 0.0 : clamp(target / range);
            settingsScroll.setVvalue(v);
            selectTab(index);
        });
    }

    // 1. 디스플레이 섹션
    private VBox createDisplaySection() {
        VBox section = section("디스플레이");

        colorblindCheck = checkBox("◆  블럭 설정색상 커스텀 사용");
        colorblindCheck.setSelected(palette.isColorblindMode());
        colorblindCheck.setOnAction(event -> {
            if (syncingUi) {
                return;
            }
            palette.setColorblindMode(colorblindCheck.isSelected());
            refreshUi();
            setFooterStatus(colorblindCheck.isSelected() ? "커스텀 블록 색상을 사용합니다." : "기본 표준 색상을 사용합니다.");
        });
        section.getChildren().add(fullWidthRow(colorblindCheck, null));

        colorPresetCombo = presetCombo(false);
        colorPresetCombo.setOnAction(event -> {
            if (syncingUi) {
                return;
            }
            if ("추가".equals(colorPresetCombo.getValue())) {
                colorPresetCombo.getItems().remove("추가");
                colorPresetCombo.setValue("새 프리셋");
                colorPresetCombo.getEditor().selectAll();
                return;
            }
            String selected = currentComboText(colorPresetCombo);
            if (colorblindPreset(selected) != null) {
                loadColorPreset(selected);
            }
        });
        Button load = accentButton("불러오기");
        Button overwrite = accentButton("덮어쓰기");
        load.setOnAction(event -> loadColorPreset(currentComboText(colorPresetCombo)));
        overwrite.setOnAction(event -> {
            String target = currentComboText(colorPresetCombo);
            if (saveColorPreset(target)) {
                String normalized = normalizePresetName(target);
                addPresetItem(colorPresetCombo, normalized);
                loadColorPreset(normalized);
            }
        });
        section.getChildren().add(settingRow("◆  색맹/컬러 프리셋", colorPresetCombo,
                buttonGroup(load, overwrite)));

        scaleCombo = new ComboBox<>();
        scaleCombo.setEditable(false);
        scaleCombo.setMaxWidth(Double.MAX_VALUE);
        scaleCombo.getItems().addAll(
                scaleLabel(0.75),
                scaleLabel(1.0),
                scaleLabel(1.25)
        );
        scaleCombo.getItems().addAll(resolutionPresetNames());
        scaleCombo.setValue(scaleLabel(SettingsStore.loadScale()));
        scaleCombo.setOnAction(event -> {
            if (syncingUi || scaleCombo.getValue() == null) {
                return;
            }
            loadScale(scaleCombo.getValue());
        });

        Button scaleLoad = accentButton("적용");
        scaleLoad.setOnAction(event -> loadScale(scaleCombo.getValue()));
        section.getChildren().add(settingRow("◆  화면 해상도", scaleCombo, buttonGroup(scaleLoad)));
        return section;
    }

    // 2. 게임 섹션
    private VBox createGameSection() {
        VBox section = section("게임");

        difficultyCombo = new ComboBox<>();
        difficultyCombo.setMaxWidth(Double.MAX_VALUE);
        for (Difficulty value : Difficulty.values()) {
            difficultyCombo.getItems().add(value.displayName());
        }
        difficultyCombo.setValue(SettingsStore.loadDifficulty().displayName());
        difficultyCombo.setOnAction(event -> {
            if (syncingUi) {
                return;
            }
            for (Difficulty value : Difficulty.values()) {
                if (value.displayName().equals(difficultyCombo.getValue())) {
                    SettingsStore.saveDifficulty(value);
                    setFooterStatus("난이도를 " + value.displayName() + "로 저장했습니다.");
                    break;
                }
            }
            refreshUi();
        });
        section.getChildren().add(settingRow("◆  난이도", difficultyCombo, null));

        bugModeCheck = checkBox("◇  템전 버그판 (4회 고정 후 다음 블록 아이템 등장)");
        bugModeCheck.setSelected(SettingsStore.loadItemBugMode());
        bugModeCheck.setOnAction(event -> {
            if (syncingUi) {
                return;
            }
            SettingsStore.saveItemBugMode(bugModeCheck.isSelected());
            refreshUi();
            setFooterStatus(bugModeCheck.isSelected() ? "아이템 버그판 모드를 켰습니다." : "아이템 버그판 모드를 껐습니다.");
        });
        Label badge = new Label("테스트용");
        badge.getStyleClass().add("hint-badge");
        section.getChildren().add(fullWidthRow(bugModeCheck, badge));
        return section;
    }

    // 3. 키 설정 섹션 (싱글 + 로컬 2인 대전)
    private VBox createKeySection() {
        VBox section = section("키 설정");

        // ----- 3-1. 싱글 플레이 키 설정 -----
        section.getChildren().add(subsectionTitle("싱글 플레이 키 설정"));

        keyPresetCombo = presetCombo(true);
        Button load = accentButton("불러오기");
        Button overwrite = accentButton("덮어쓰기");
        load.setOnAction(event -> loadKeyPreset(currentComboText(keyPresetCombo)));
        overwrite.setOnAction(event -> {
            String target = currentComboText(keyPresetCombo);
            if (saveKeyPreset(target)) {
                String normalized = normalizePresetName(target);
                addPresetItem(keyPresetCombo, normalized);
                refreshUi();
            }
        });
        section.getChildren().add(settingRow("◆  키 프리셋", keyPresetCombo, buttonGroup(load, overwrite)));

        for (GameAction action : GameAction.values()) {
            Button key = singleKeyButton(action);
            section.getChildren().add(settingRow("  " + actionLabels.get(action), key, null));
        }

        keyResetConfirmCheck = checkBox("체크 후 초기화하면 저장된 키 프리셋도 함께 삭제");
        Button resetSingle = dangerButton("싱글 키 초기화");
        resetSingle.setOnAction(event -> {
            if (!keyResetConfirmCheck.isSelected()) {
                showMessage("싱글 키 초기화", "초기화 체크박스를 먼저 선택하세요.");
                return;
            }
            clearCaptureState();
            bindings.resetToDefault();
            bindings.saveActive();
            bindings.deletePresets();
            keyResetConfirmCheck.setSelected(false);
            keyPresetCombo.setValue(null);
            refreshUi();
            setFooterStatus("싱글 키와 키 프리셋을 기본값으로 초기화했습니다.");
            showMessage("싱글 키 초기화", "싱글 키와 키 프리셋을 초기화했습니다.");
        });
        section.getChildren().add(fullWidthRow(keyResetConfirmCheck, buttonGroup(resetSingle)));

        // ----- 3-2. 로컬 2인 대전 키 설정 -----
        section.getChildren().add(subsectionTitle("로컬 대전 키 설정 (중복 입력 시 자동 교환 · ESC는 일시정지/종료 전용)"));

        Label actionHead = new Label("동작");
        actionHead.getStyleClass().add("setting-label");
        Label p1Head = new Label("PLAYER 1");
        p1Head.getStyleClass().addAll("battle-column-header", "p1-header");
        p1Head.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(p1Head, Priority.ALWAYS);
        Label p2Head = new Label("PLAYER 2");
        p2Head.getStyleClass().addAll("battle-column-header", "p2-header");
        p2Head.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(p2Head, Priority.ALWAYS);

        HBox headerRow = new HBox(10, actionHead, p1Head, p2Head);
        headerRow.getStyleClass().addAll("setting-row", "battle-header-row");
        headerRow.setAlignment(Pos.CENTER_LEFT);
        section.getChildren().add(headerRow);

        for (GameAction action : BattleKeyBindings.BATTLE_ACTIONS) {
            Label label = new Label("  " + actionLabels.get(action));
            label.getStyleClass().add("setting-label");

            Button p1Btn = battleKeyButton(1, action);
            Button p2Btn = battleKeyButton(2, action);
            HBox.setHgrow(p1Btn, Priority.ALWAYS);
            HBox.setHgrow(p2Btn, Priority.ALWAYS);

            HBox row = new HBox(10, label, p1Btn, p2Btn);
            row.getStyleClass().addAll("setting-row", rowNumber++ % 2 == 0 ? "stripe-light" : "stripe-dark");
            row.setAlignment(Pos.CENTER_LEFT);
            section.getChildren().add(row);
        }

        Label battleResetHint = new Label("기본값: P1 (W/A/S/D + Space)  ·  P2 (방향키 + Enter)");
        battleResetHint.getStyleClass().add("inline-hint");
        battleResetHint.setWrapText(true);
        Button resetBattle = dangerButton("대전 키 초기화");
        resetBattle.setOnAction(event -> {
            clearCaptureState();
            battleBindings.resetToDefault();
            battleBindings.saveActive();
            refreshUi();
            setFooterStatus("로컬 대전 키(P1/P2)를 기본값으로 초기화했습니다.");
        });
        section.getChildren().add(fullWidthRow(battleResetHint, buttonGroup(resetBattle)));

        return section;
    }

    // 4. 기타 섹션 (기록 초기화 / 전체 초기화 / 소리)
    private VBox createOtherSection() {
        VBox section = section("기타");

        scoreResetConfirmCheck = checkBox("일반·아이템 모든 난이도의 스코어보드 기록 삭제");
        Button resetScores = dangerButton("기록 초기화");
        resetScores.setOnAction(event -> {
            if (!scoreResetConfirmCheck.isSelected()) {
                showMessage("기록 초기화", "기록 삭제 확인 체크박스를 먼저 선택하세요.");
                return;
            }
            try {
                new ScoreStore(false).delete();
                new ScoreStore(true).delete();
            } catch (IllegalStateException e) {
                showMessage("기록 초기화 실패", e.getMessage());
                return;
            }
            scoreResetConfirmCheck.setSelected(false);
            setFooterStatus("모든 난이도의 스코어보드 기록을 초기화했습니다.");
            showMessage("기록 초기화", "일반 및 아이템 모드의 스코어보드 기록을 모두 초기화했습니다.");
        });
        section.getChildren().add(settingRow("◆  기록 초기화", scoreResetConfirmCheck, buttonGroup(resetScores)));

        allResetConfirmCheck = checkBox("설정·프리셋·키(싱글/대전)·팔레트·기록 모두 초기화");
        Button resetAll = dangerButton("전체 초기화");
        resetAll.setOnAction(event -> {
            if (!allResetConfirmCheck.isSelected()) {
                showMessage("전체 초기화", "전체 초기화 체크박스를 먼저 선택하세요.");
                return;
            }
            clearCaptureState();
            SettingsStore.delete();
            palette.resetAll();
            bindings.resetToDefault();
            bindings.saveActive();
            battleBindings.resetToDefault();
            battleBindings.saveActive();
            try {
                new ScoreStore(false).delete();
                new ScoreStore(true).delete();
            } catch (IllegalStateException e) {
                showMessage("전체 초기화 실패", e.getMessage());
                return;
            }
            allResetConfirmCheck.setSelected(false);
            keyResetConfirmCheck.setSelected(false);
            scoreResetConfirmCheck.setSelected(false);
            colorPresetCombo.setValue(null);
            keyPresetCombo.setValue(null);
            refreshUi();
            showMessage("전체 초기화", "설정, 프리셋, 키, 팔레트, 스코어보드를 모두 초기화했습니다.");
            if (onScaleChanged != null) {
                onScaleChanged.accept(1.0);
            }
        });
        section.getChildren().add(settingRow("◆  전체 초기화", allResetConfirmCheck, buttonGroup(resetAll)));

        Label soundHint = new Label("추가 예정");
        soundHint.getStyleClass().add("inline-hint");
        section.getChildren().add(settingRow("◆  소리", soundHint, null));
        return section;
    }

    /**
     * 설정창의 모든 컨트롤 표시를 현재 저장된 실제 상태로 즉시 동기화한다. 스크롤 위치는 유지된다.
     */
    public void refreshUi() {
        syncingUi = true;
        try {
            if (colorblindCheck != null) {
                colorblindCheck.setSelected(palette.isColorblindMode());
            }
            if (colorPresetCombo != null) {
                refreshPresetCombo(colorPresetCombo, colorPresetNames());
            }
            if (scaleCombo != null) {
                scaleCombo.setValue(scaleLabel(SettingsStore.loadScale()));
            }
            if (difficultyCombo != null) {
                difficultyCombo.setValue(SettingsStore.loadDifficulty().displayName());
            }
            if (bugModeCheck != null) {
                bugModeCheck.setSelected(SettingsStore.loadItemBugMode());
            }
            if (keyPresetCombo != null) {
                refreshPresetCombo(keyPresetCombo, keyPresetNames());
            }
            updateKeyButtons();
            drawSamples();
        } finally {
            syncingUi = false;
        }
    }

    private void refreshPresetCombo(ComboBox<String> combo, List<String> latestNames) {
        String current = currentComboText(combo);
        combo.getItems().setAll(latestNames);
        if (current != null && !current.isBlank() && latestNames.contains(current)) {
            combo.setValue(current);
            combo.getEditor().setText(current);
        } else {
            combo.setValue(null);
            combo.getEditor().clear();
        }
    }

    private String currentComboText(ComboBox<String> combo) {
        if (combo.isEditable() && combo.getEditor() != null) {
            String text = combo.getEditor().getText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return combo.getValue();
    }

    private VBox section(String title) {
        VBox section = new VBox(0);
        section.getStyleClass().add("settings-section");
        section.setFillWidth(true);
        section.getChildren().add(sectionTitle(title));
        return section;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label subsectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subsection-title");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        return label;
    }

    private CheckBox checkBox(String text) {
        CheckBox check = new CheckBox(text);
        check.getStyleClass().add("settings-check");
        check.setWrapText(true);
        check.setMinHeight(Region.USE_PREF_SIZE);
        check.setMaxWidth(Double.MAX_VALUE);
        return check;
    }

    private HBox settingRow(String title, Node control, Node actions) {
        Label label = new Label(title);
        label.getStyleClass().add("setting-label");

        HBox row = new HBox(10);
        row.getStyleClass().addAll("setting-row", rowNumber++ % 2 == 0 ? "stripe-light" : "stripe-dark");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(label, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        if (actions != null) {
            row.getChildren().add(actions);
        }
        return row;
    }

    private HBox fullWidthRow(Node mainControl, Node trailing) {
        HBox row = new HBox(10);
        row.getStyleClass().addAll("setting-row", rowNumber++ % 2 == 0 ? "stripe-light" : "stripe-dark");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(mainControl);
        HBox.setHgrow(mainControl, Priority.ALWAYS);
        if (trailing != null) {
            row.getChildren().add(trailing);
        }
        return row;
    }

    private static HBox buttonGroup(Button... buttons) {
        HBox box = new HBox(6, buttons);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private Button singleKeyButton(GameAction action) {
        Button key = new Button(bindings.keyFor(action).getName());
        key.getStyleClass().add("key-button");
        key.setMinWidth(110);
        key.setMaxWidth(Double.MAX_VALUE);
        key.setOnAction(event -> {
            capturingBattleInput = null;
            capturingAction = action;
            updateKeyButtons();
            key.setText("입력 대기...");
            setFooterStatus("싱글 [" + actionLabels.get(action) + "] 키를 누르세요 (Shift+Esc 취소)");
            key.requestFocus();
        });
        keyButtons.put(action, key);
        return key;
    }

    private Button battleKeyButton(int player, GameAction action) {
        Button key = new Button(battleBindings.keyFor(player, action).getName());
        key.getStyleClass().add("key-button");
        key.setMinWidth(110);
        key.setMaxWidth(Double.MAX_VALUE);
        key.setOnAction(event -> {
            capturingAction = null;
            capturingBattleInput = new PlayerInput(player, action);
            updateKeyButtons();
            key.setText("입력 대기...");
            setFooterStatus("P" + player + " [" + actionLabels.get(action) + "] 키를 누르세요 (Shift+Esc 취소)");
            key.requestFocus();
        });
        (player == 1 ? p1BattleButtons : p2BattleButtons).put(action, key);
        return key;
    }

    private void clearCaptureState() {
        capturingAction = null;
        capturingBattleInput = null;
    }

    private void handleCapturingKeyPressed(KeyEvent event) {
        if (capturingAction == null && capturingBattleInput == null) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.UNDEFINED) {
            event.consume();
            return;
        }

        boolean cancelled = (code == KeyCode.ESCAPE && event.isShiftDown());

        if (capturingAction != null) {
            GameAction target = capturingAction;
            capturingAction = null;
            suppressNextKeyRelease = true;
            if (!cancelled) {
                bindings.bind(target, code);
                setFooterStatus("싱글 [" + actionLabels.get(target) + "] 키를 " + code.getName() + "로 저장했습니다.");
            } else {
                setFooterStatus("키 변경을 취소했습니다.");
            }
            refreshUi();
            event.consume();
            return;
        }

        PlayerInput target = capturingBattleInput;
        capturingBattleInput = null;
        suppressNextKeyRelease = true;
        if (cancelled) {
            setFooterStatus("키 변경을 취소했습니다.");
        } else if (code == KeyCode.ESCAPE) {
            setFooterStatus("ESC는 대전 일시정지/종료 전용 키라 지정할 수 없습니다.");
        } else {
            battleBindings.bind(target.player(), target.action(), code);
            setFooterStatus("P" + target.player() + " [" + actionLabels.get(target.action())
                    + "] 키를 " + code.getName() + "로 저장했습니다.");
        }
        refreshUi();
        event.consume();
    }

    private void handleCapturingKeyReleased(KeyEvent event) {
        if (suppressNextKeyRelease) {
            suppressNextKeyRelease = false;
            event.consume();
        }
    }

    private ComboBox<String> presetCombo(boolean keyPreset) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setMinWidth(120);
        combo.getItems().addAll(keyPreset ? keyPresetNames() : colorPresetNames());
        combo.setPromptText("프리셋 선택 또는 입력");
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
            if (syncingUi) {
                return;
            }
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
            setFooterStatus("컬러 프리셋을 저장했습니다.");
            showMessage("컬러 프리셋", "현재 팔레트를 저장했습니다.");
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
            refreshUi();
            setFooterStatus("컬러 프리셋 [" + name + "]을 적용했습니다.");
        } catch (IllegalArgumentException e) {
            showMessage("컬러 프리셋 불러오기 실패", e.getMessage());
        }
    }

    private boolean saveKeyPreset(String selected) {
        try {
            bindings.savePreset(presetValue(selected));
            setFooterStatus("키 프리셋을 저장했습니다.");
            showMessage("키 프리셋", "현재 싱글 키 설정을 저장했습니다.");
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
            if (combo.isEditable() && combo.getEditor() != null) {
                combo.getEditor().setText(name);
            }
        }
    }

    private void loadKeyPreset(String selected) {
        try {
            clearCaptureState();
            String name = presetValue(selected);
            bindings.loadPreset(name);
            refreshUi();
            setFooterStatus("키 프리셋 [" + name + "]을 불러왔습니다.");
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

    private static String normalizePresetName(String name) {
        return name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N}_-]", "_");
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
        if (name == null) {
            return null;
        }
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
        footerStatusLabel = new Label("모든 설정 변경 사항은 자동으로 저장됩니다.");
        footerStatusLabel.getStyleClass().add("footer-status");
        footerStatusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(footerStatusLabel, Priority.ALWAYS);

        Button back = accentButton("돌아가기");
        back.setOnAction(event -> onBack.run());

        HBox footer = new HBox(12, footerStatusLabel, back);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 0, 0, 0));
        return footer;
    }

    private void setFooterStatus(String text) {
        if (footerStatusLabel != null) {
            footerStatusLabel.setText(text == null ? "" : text);
        }
    }

    private Button accentButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("accent-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
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
        refreshUi();
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
        return new double[] {18 + index * 94, 10};
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
                graphics.setLineWidth(1);
                graphics.strokeRect(x + 0.5, y + 0.5, SAMPLE_CELL_SIZE - 2, SAMPLE_CELL_SIZE - 2);
            }
            if (selectedIndex == i) {
                graphics.setStroke(Color.WHITE);
                graphics.setLineWidth(2);
                graphics.strokeRect(origin[0] - 4, origin[1] - 4,
                        4 * SAMPLE_CELL_SIZE + 8, 4 * SAMPLE_CELL_SIZE + 8);
            }
        }
    }

    private void updateKeyButtons() {
        keyButtons.forEach((action, button) -> button.setText(bindings.keyFor(action).getName()));
        p1BattleButtons.forEach((action, button) -> button.setText(battleBindings.keyFor(1, action).getName()));
        p2BattleButtons.forEach((action, button) -> button.setText(battleBindings.keyFor(2, action).getName()));
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
