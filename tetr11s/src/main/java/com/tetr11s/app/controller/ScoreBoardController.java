package com.tetr11s.app.controller;

import java.util.List;
import java.util.Map;

import com.tetr11s.app.menu.MenuEntry;
import com.tetr11s.app.menu.MenuModel;
import com.tetr11s.game.Difficulty;
import com.tetr11s.score.ScoreBoard;
import com.tetr11s.score.ScoreEntry;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * 스코어보드 화면
 *
 * 난이도별 순위표를 받아서 1~10위 표로 보여주기만 하고, 기록을 바꾸지 않는다
 * ←→로 보고 싶은 난이도의 순위표로 넘긴다
 * 방금 추가된 기록이 있으면, 그 기록이 들어간 난이도의 순위표에서만 그 줄을 강조한다
 * 아래쪽에서 '시작 메뉴로 / 게임 종료'를 ↑↓로 고른다 (실제로 무엇을 할지는 ScreenManager가 정함)
 */
public class ScoreBoardController {

    /** 강조할 줄이 없을 때 show()에 넘기는 값 */
    public static final int NO_HIGHLIGHT = -1;

    private static final String EMPTY_NAME = "---";
    private static final String EMPTY_SCORE = "-";
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    @FXML
    private BorderPane root;

    @FXML
    private Label difficultyLabel;

    @FXML
    private VBox rowBox;

    @FXML
    private VBox menuBox;

    private MenuModel menu;
    private Runnable onMenu = () -> { };

    private Map<Difficulty, ScoreBoard> boards;
    private Difficulty shown;               // 지금 보고 있는 난이도
    private Difficulty highlightDifficulty; // 방금 기록이 들어간 난이도
    private int highlightRank = NO_HIGHLIGHT;

    // 이전 화면에서 누르고 있던 키가 이 화면에 들어오는 것을 막는다 (아래 onKeyReleased 참고)
    private boolean ready = false;

    @FXML
    private void initialize() {
        // 시작 메뉴와 같은 이유: 포커스를 가진 노드가 없으면 키 입력이 루트까지 오지 않는다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });
    }

    /**
     * @param boards         난이도별 순위표
     * @param difficulty     처음 보여줄 난이도 (방금 기록이 있으면 그 기록의 난이도)
     * @param highlightRank  처음 보여주는 난이도에서 강조할 줄 (0 = 1위). 없으면 NO_HIGHLIGHT
     * @param onMenu         '시작 메뉴로' 또는 Esc
     * @param onExit         '게임 종료'
     */
    public void show(Map<Difficulty, ScoreBoard> boards, Difficulty difficulty, int highlightRank,
                     Runnable onMenu, Runnable onExit) {
        this.boards = boards;
        this.highlightDifficulty = difficulty;
        this.highlightRank = highlightRank;
        this.onMenu = onMenu;

        menu = new MenuModel(List.of(
                new MenuEntry("시작 메뉴로", onMenu),
                new MenuEntry("게임 종료", onExit)));
        menuBox.getChildren().clear();
        for (MenuEntry entry : menu.entries()) {
            Label label = new Label(entry.label());
            label.getStyleClass().add("menu-item");
            menuBox.getChildren().add(label);
        }
        showSelection();

        showBoard(difficulty);
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (!ready) {
            event.consume();   // 아직 떼지 않은 이전 화면의 키 (자동 반복 포함)
            return;
        }
        switch (event.getCode()) {
            case LEFT -> showBoard(shown.previous());
            case RIGHT -> showBoard(shown.next());
            case UP -> {
                menu.moveUp();
                showSelection();
            }
            case DOWN -> {
                menu.moveDown();
                showSelection();
            }
            case ENTER -> menu.activate();
            case ESCAPE -> onMenu.run();
            default -> {
                return;
            }
        }
        event.consume();
    }

    /**
     * 이 화면은 항상 키를 눌러서 들어온다 (메뉴의 Enter, 게임의 Esc, 이름 입력의 Enter)
     * 그 키를 꾹 누르고 있으면 자동 반복으로 같은 키가 이 화면에도 계속 들어와서, 보자마자 넘어가 버린다
     * 그래서 키를 한 번 뗀 뒤부터 입력을 받는다
     */
    @FXML
    private void onKeyReleased(KeyEvent event) {
        ready = true;
    }

    // 이 난이도의 순위표로 표를 다시 채운다
    private void showBoard(Difficulty difficulty) {
        shown = difficulty;
        difficultyLabel.setText("◀  " + difficulty.displayName() + "  ▶");

        List<ScoreEntry> entries = boards.getOrDefault(difficulty, new ScoreBoard()).entries();
        int highlight = (difficulty == highlightDifficulty) ? highlightRank : NO_HIGHLIGHT;

        rowBox.getChildren().clear();
        for (int rank = 0; rank < ScoreBoard.CAPACITY; rank++) {
            ScoreEntry entry = rank < entries.size() ? entries.get(rank) : null;
            rowBox.getChildren().add(createRow(rank, entry, rank == highlight));
        }
    }

    // 선택된 항목만 강조 표시 (시작 메뉴와 같은 방식)
    private void showSelection() {
        for (int i = 0; i < menuBox.getChildren().size(); i++) {
            menuBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == menu.selectedIndex());
        }
    }

    // 한 줄: 순위 | 이름 | 점수. 기록이 없는 자리는 흐리게, 방금 기록은 강조
    private HBox createRow(int rank, ScoreEntry entry, boolean highlight) {
        Label rankLabel = cell(Integer.toString(rank + 1), "rank");
        Label nameLabel = cell(entry == null ? EMPTY_NAME : entry.name(), "name");
        Label scoreLabel = cell(entry == null ? EMPTY_SCORE : String.format("%,d", entry.score()), "score");

        HBox row = new HBox(rankLabel, nameLabel, scoreLabel);
        row.getStyleClass().add("score-row");
        if (entry == null) {
            row.getStyleClass().add("empty-row");
        }
        if (highlight) {
            row.getStyleClass().add("highlight-row");
        }
        return row;
    }

    private static Label cell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}