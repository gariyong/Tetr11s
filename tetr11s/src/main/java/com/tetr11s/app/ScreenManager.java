package com.tetr11s.app;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import com.tetr11s.app.controller.NameInputController;
import com.tetr11s.app.controller.ScoreBoardController;
import com.tetr11s.app.controller.StartMenuController;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameScreen;
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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * ScreenManager 를 통해 게임 화면을 전환
 * stage 는 앱을 한번 실행하면 완전 종료할 때까지 살아있기에 private final 로 둠
 * currentScreen 은 게임을 실행할 때마다 생성되므로, 앱 실행 후 여러 번 만들 수 있게 둬야 함
 */

public class ScreenManager {
    private final Stage stage;
    private final ScoreStore scoreStore = new ScoreStore();
    private GameScreen currentScreen;
    private ChangeListener<Boolean> focusListener; 

    public ScreenManager(Stage stage){
        this.stage=stage;
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
        stage.setScene(new Scene(root));

        stage.show();
    }

    /**
     * 시작화면에서 '시작' 버튼을 누르면 게임을 실행하는 함수
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
        stage.setScene(currentScreen.scene());
        stage.setResizable(false);
        stage.show();

        currentScreen.start();
    }

    public void showSettings() {
        stopCurrentGame();
        SettingsView view = new SettingsView(
                new DefaultPalette(), new KeyBindings(), () -> {
            try {
                showStartMenu();
            } catch (IOException e) {
                throw new RuntimeException("시작 메뉴를 열 수 없습니다.", e);
            }
                });
        stage.setTitle("Tetr11s - 설정");
        stage.setScene(new Scene(view));
        stage.setResizable(false);
        stage.show();
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
        stage.setScene(new Scene(root));
        stage.show();
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
        stage.setScene(new Scene(root));
        stage.show();
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
    
}
