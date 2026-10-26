package com.tetr11s.app;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.app.controller.NameInputController;
import com.tetr11s.app.controller.ScoreBoardController;
import com.tetr11s.app.controller.StartMenuController;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameScreen;
import com.tetr11s.game.view.GameView;
import com.tetr11s.score.ScoreBoard;
import com.tetr11s.score.ScoreEntry;
import com.tetr11s.score.ScoreStore;
import com.tetr11s.settings.DefaultPalette;
import com.tetr11s.settings.KeyBindings;
import com.tetr11s.settings.SettingsStore;
import com.tetr11s.settings.SettingsView;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * ScreenManager 를 통해 게임 화면을 전환
 * stage 는 앱을 한번 실행하면 완전 종료할 때까지 살아있기에 private final 로 둠
 * currentScreen 은 게임을 실행할 때마다 생성되므로, 앱 실행 후 여러 번 만들 수 있게 둬야 함
 */

public class ScreenManager {
    public static final double MENU_BASE_WIDTH = 480;
    public static final double MENU_BASE_HEIGHT = 640;

    private final Stage stage;
    private final ScoreStore scoreStore = new ScoreStore();
    private GameScreen currentScreen;
    private ChangeListener<Boolean> focusListener; 

    // 현재 화면의 루트와 전달받는 게임뷰로 group과 stackpane를 만들어, 화면 크기 조절 시 scale 변환을 적용할 수 있게 할거임
    private Parent currentContent;
    private StackPane currentWrapper;

    // 현재 화면의 기준 해상도 저장
    private double currentBaseWidth = MENU_BASE_WIDTH;
    private double currentBaseHeight = MENU_BASE_HEIGHT;

    public ScreenManager(Stage stage){
        this.stage=stage;
        this.stage.setResizable(false);
    }

/**
     * 현재 모니터의 가용 해상도(작업표시줄 제외한 논리 픽셀 크기)를 가져와서
     * 3단계 프리셋(0.75, 1.0, 1.25)이 모니터 크기에 맞도록 실제 배율을 계산한다.
     * - 기본(1.0)일 때 모니터 세로 높이의 약 70%를 차지하도록 기준 배율을 잡음 (일반 FHD~4K 모두 대응)
     */
    public static double computeMonitorFittedScale(double presetScale, double baseWidth, double baseHeight) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double screenW = visualBounds.getWidth();
        double screenH = visualBounds.getHeight();

        // 모니터 높이의 70%를 1.0배율 기준으로 삼되, 너무 작아지거나 커지지 않게 보정
        double baseFitRatio = (screenH * 0.70) / MENU_BASE_HEIGHT;
        // 만약 일반 모니터(1080p 등)에서 원래 픽셀(1.0) 그대로 쓰고 싶다면 기본값을 최소 1.0 이상으로 유지
        if (baseFitRatio < 1.0 && screenH >= 800) {
            baseFitRatio = 1.0;
        }

        double actualScale = baseFitRatio * presetScale;

        // 화면 밖으로 절대 넘치지 않도록 최대 크기 제한 (모니터 가용 영역의 90% 이내)
        double maxScaleW = (screenW * 0.90) / baseWidth;
        double maxScaleH = (screenH * 0.90) / baseHeight;
        return Math.min(actualScale, Math.min(maxScaleW, maxScaleH));
    }

    /**
     * 현재 프리셋 배율(0.75 / 1.0 / 1.25)에 따른 실제 창 해상도(가로x세로) 문자열을 반환한다.
     */
    public static String getResolutionText(double presetScale) {
        double s = computeMonitorFittedScale(presetScale, MENU_BASE_WIDTH, MENU_BASE_HEIGHT);
        int w = (int) Math.round(MENU_BASE_WIDTH * s);
        int h = (int) Math.round(MENU_BASE_HEIGHT * s);
        return w + "×" + h;
    }
    /**
     * 이전 currentScreen 이 남아, 계속 게임이 돌아가는 경우를 막기 위해 null 로 초기화
     * listener 가 게임이 끝나도 남아있어, 이를 없애는 기능 추가
     */
    private void stopCurrentGame(){
        if(currentScreen != null){
            stage.focusedProperty().removeListener(focusListener);
            currentScreen.stop();
            currentScreen=null;
            focusListener=null; 
            
        }
    }

    /**
     * start 함수에서 시작화면을 불러오는 함수
     */
    public void showStartMenu() throws IOException{
        stopCurrentGame();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tetr11s/fxml/start_menu.fxml"));
        Parent root = loader.load();

        StartMenuController controller = loader.getController();
        controller.setScreenManager(this);

        stage.setTitle("Tetr11s");
        mountScene(root, MENU_BASE_WIDTH, MENU_BASE_HEIGHT);
    }

    /**
     * 시작화면에서 '시작' 버튼을 누르면 게임을 실행하는 함수
     * 게임뷰의 씬을 게임스크린의 씬에 연결한다
     */
    public void showGame(){
        stopCurrentGame();

        Difficulty difficulty = SettingsStore.loadDifficulty();

        GameScreen screen = GameScreen.create(difficulty, score -> {
            try {
                endGame(difficulty, score);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // 다른 창으로 전환하면 키를 뗀 신호가 오지 않아서, 블럭이 계속 움직이는 문제를 막는다
        // 이 판의 screen 만 가리키고, 판이 끝나면 stopCurrentGame 에서 제거된다
        focusListener = (obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                screen.releaseAllKeys();
            }
        };
        stage.focusedProperty().addListener(focusListener);
        currentScreen = screen;

        stage.setTitle("Tetr11s");
        
        // 게임뷰를 게임스크린과 씬을 연결
        Scene gameScene = mountScene(screen.view(), GameView.BASE_WIDTH, GameView.BASE_HEIGHT);
        screen.attachToScene(gameScene);
        currentScreen.start();
    }

    // 설정화면뷰를 씬에 연결한다
    public void showSettings() {
        stopCurrentGame();

        SettingsView view = new SettingsView(
                new DefaultPalette(),
                new KeyBindings(),
                () -> {
                    try {
                        showStartMenu();
                    } catch (IOException e) {
                        throw new RuntimeException("시작 메뉴를 열 수 없습니다.", e);
                    }
                },
                scale -> {
                    // 해상도 프리셋이 바뀌면 창 크기를 저장한 뒤 설정 화면을 곧바로 리프레시(재생성)함
                    SettingsStore.saveScale(scale);
                    showSettings(); 
                });

        stage.setTitle("Tetr11s - 설정");
        mountScene(view, SettingsView.BASE_WIDTH, SettingsView.BASE_HEIGHT);
    }
    /**
     * 스코어보드 화면 (시작 메뉴의 '스코어보드'에서 들어올 때). 강조할 기록 없이 저장된 순위표를 보여준다
     */
    public void showScoreboard() throws IOException {
        showScoreboard(loadAllBoards(), SettingsStore.loadDifficulty(), ScoreBoardController.NO_HIGHLIGHT);
    }

    /**
     * 스코어보드 화면. 아래쪽에서 '시작 메뉴로' 또는 '게임 종료'를 고른다
     *
     * @param boards 난이도별 순위표
     * @param difficulty 처음 보여줄 난이도
     * @param highlightRank 방금 추가된 기록의 순위 (0 = 1위). 없으면 NO_HIGHLIGHT
     */
    private void showScoreboard(Map<Difficulty, ScoreBoard> boards, Difficulty difficulty, int highlightRank) throws IOException {
        stopCurrentGame();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tetr11s/fxml/score_board.fxml"));
        Parent root = loader.load();

        ScoreBoardController controller = loader.getController();
        controller.show(boards, difficulty, highlightRank, () -> {
            try {
                showStartMenu();
            } catch (IOException e) {
                throw new RuntimeException("시작 메뉴를 열 수 없습니다.", e);
            }
        }, Platform::exit);

        stage.setTitle("Tetr11s - 스코어보드");
        mountScene(root, MENU_BASE_WIDTH, MENU_BASE_HEIGHT);
    }

/**
     * 모든 화면(메뉴, 설정, 게임, 스코어보드, 이름입력)을 Group + StackPane으로 감싸서
     * 확대/축소 시 내용물과 윈도우 창 크기가 완벽히 일치하도록 마운트한다.
     */
    private Scene mountScene(Parent content, double baseWidth, double baseHeight) {
        this.currentContent = content;
        this.currentBaseWidth = baseWidth;
        this.currentBaseHeight = baseHeight;

        if (content instanceof Region region) {
            region.setPrefSize(baseWidth, baseHeight);
            region.setMinSize(baseWidth, baseHeight);
            region.setMaxSize(baseWidth, baseHeight);
        }

        Group scaledGroup = new Group(content);
        StackPane wrapper = new StackPane(scaledGroup);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setStyle("-fx-background-color: #090a16;");
        this.currentWrapper = wrapper;

        double presetScale = SettingsStore.loadScale();
        double actualScale = computeMonitorFittedScale(presetScale, baseWidth, baseHeight);
        double targetW = Math.round(baseWidth * actualScale);
        double targetH = Math.round(baseHeight * actualScale);

        content.setScaleX(actualScale);
        content.setScaleY(actualScale);

        // 래퍼의 크기도 명시적으로 고정
        wrapper.setPrefSize(targetW, targetH);
        wrapper.setMinSize(targetW, targetH);
        wrapper.setMaxSize(targetW, targetH);

        Scene scene = new Scene(wrapper, targetW, targetH);
        stage.setScene(scene);
        
        // 창 크기를 강제로 대상 해상도에 맞게 재설정
        applyStageSize(targetW, targetH);
        stage.show();

        Platform.runLater(content::requestFocus);
        return scene;
    }
    
    public void applyScale(double presetScale) {
        if (currentContent == null || currentWrapper == null) {
            return;
        }

        double actualScale = computeMonitorFittedScale(presetScale, currentBaseWidth, currentBaseHeight);
        double targetW = Math.round(currentBaseWidth * actualScale);
        double targetH = Math.round(currentBaseHeight * actualScale);

        // 1. 내부 컨텐츠 스케일 적용
        currentContent.setScaleX(actualScale);
        currentContent.setScaleY(actualScale);

        // 2. 래퍼(StackPane) 크기를 실제 스케일된 목표 크기로 지정
        currentWrapper.setPrefSize(targetW, targetH);
        currentWrapper.setMinSize(targetW, targetH);
        currentWrapper.setMaxSize(targetW, targetH);
        currentWrapper.requestLayout();

        // 3. 정확한 스케일 적용 크기(targetW, targetH)를 전달
        applyStageSize(targetW, targetH);
    }

    private void applyStageSize(double scaledWidth, double scaledHeight) {
        Platform.runLater(() -> {
            stage.setResizable(true);
            
            Scene scene = stage.getScene();
            if (scene != null) {
                scene.setRoot(currentWrapper);
            }
            
            // 래퍼 크기에 맞춰 Scene 크기 우선 갱신
            currentWrapper.setPrefSize(scaledWidth, scaledHeight);
            stage.sizeToScene();
            
            // 이미 계산된 실제 스케일 크기(scaledWidth, scaledHeight)를 윈도우 창 크기로 직접 주입
            stage.setWidth(scaledWidth);
            stage.setHeight(scaledHeight);
            
            stage.sizeToScene();
            stage.setResizable(false);
            stage.centerOnScreen();
        });
    }

    private void resizeStageToWrapper() {
        // Windows HiDPI(4K)에서 setResizable(false) 상태일 때 sizeToScene() 여백 오차를 막기 위해
        // 잠깐 제약을 풀고 sizeToScene() 수행 후 다시 고정하고 화면 중앙에 배치
        stage.setResizable(true);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.centerOnScreen();
    }
    /**
     * 게임이 끝났을 때 (게임오버 후 Esc, 일시정지 후 Esc)
     * 순위권 점수면 이름 입력 화면, 아니면 바로 스코어보드로 간다
     */
    private void endGame(Difficulty difficulty, int score) throws IOException {
        ScoreBoard board = scoreStore.load(difficulty);
        if (score > 0 && board.qualifies(score)) {
            showNameInput(difficulty, score);
        } else {
            showScoreboard(loadAllBoards(), difficulty, ScoreBoardController.NO_HIGHLIGHT);
        }
    }

    /**
     * 이름 입력 화면. 이름이 확정되면 기록을 저장하고 스코어보드로 간다
     */
    private void showNameInput(Difficulty difficulty, int score) throws IOException {
        stopCurrentGame();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tetr11s/fxml/name_input.fxml"));
        Parent root = loader.load();

        NameInputController controller = loader.getController();
        controller.show(score, name -> {
            ScoreBoard board = scoreStore.load(difficulty);
            int rank = board.add(new ScoreEntry(name, score));
            save(difficulty, board);

            // 다른 난이도는 파일에서, 이 난이도는 방금 기록을 넣은 순위표 그대로 (저장에 실패해도 화면에는 보이도록)
            Map<Difficulty, ScoreBoard> boards = loadAllBoards();
            boards.put(difficulty, board);
            try {
                showScoreboard(boards, difficulty, rank);
            } catch (IOException e) {
                throw new RuntimeException("스코어보드 화면을 열 수 없습니다.", e);
            }
        });

        stage.setTitle("Tetr11s - 기록");
        mountScene(root, MENU_BASE_WIDTH, MENU_BASE_HEIGHT);
    }

    // 모든 난이도의 순위표를 파일에서 읽는다
    private Map<Difficulty, ScoreBoard> loadAllBoards() {
        Map<Difficulty, ScoreBoard> boards = new EnumMap<>(Difficulty.class);
        for (Difficulty difficulty : Difficulty.values()) {
            boards.put(difficulty, scoreStore.load(difficulty));
        }
        return boards;
    }

    // 기록을 순위표에 넣고 파일에 저장한다. 저장에 실패해도 게임은 계속된다
    private void save(Difficulty difficulty, ScoreBoard board) {
        try {
            scoreStore.save(difficulty, board);
        } catch (IllegalStateException e) {
            System.err.println("기록을 저장하지 못했습니다: " + e.getMessage());
        }
    }

    // 리프레시
    // 설정 화면에서 해상도를 바꿨을 때 창을 리프레시(재마운트)하는 메서드
    public void refreshSettings() {
        showSettings();
    }
    
}
