package com.tetr11s.app.controller;

import java.util.List;

import com.tetr11s.score.ScoreBoard;
import com.tetr11s.score.ScoreEntry;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * 스코어보드 화면
 *
 * 받은 ScoreBoard를 1~10위 표로 보여주기만 하고, 기록을 바꾸지 않는다
 * Enter나 Esc를 누르면 onBack을 실행한다 (어디로 갈지는 ScreenManager가 정함)
 */
public class ScoreBoardController {
    private static final String EMPTY_NAME = "---";
    private static final String EMPTY_SCORE = "-";

    @FXML
    private BorderPane root;

    @FXML
    private VBox rowBox;

    private Runnable onBack = () -> { };

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
     * 보여줄 기록과, 화면을 떠날 때 할 일을 받는다
     *
     * @param board  보여줄 순위표
     * @param onBack Enter / Esc를 눌렀을 때 실행할 일
     */
    public void show(ScoreBoard board, Runnable onBack) {
        this.onBack = onBack;

        List<ScoreEntry> entries = board.entries();
        rowBox.getChildren().clear();
        for (int rank = 0; rank < ScoreBoard.CAPACITY; rank++) {
            ScoreEntry entry = rank < entries.size() ? entries.get(rank) : null;
            rowBox.getChildren().add(createRow(rank, entry));
        }
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case ENTER, ESCAPE -> onBack.run();
            default -> {
                return;
            }
        }
        event.consume();
    }

    // 한 줄: 순위 | 이름 | 점수. 기록이 없는 자리는 흐리게 표시
    private HBox createRow(int rank, ScoreEntry entry) {
        Label rankLabel = cell(Integer.toString(rank + 1), "rank");
        Label nameLabel = cell(entry == null ? EMPTY_NAME : entry.name(), "name");
        Label scoreLabel = cell(entry == null ? EMPTY_SCORE : String.format("%,d", entry.score()), "score");

        HBox row = new HBox(rankLabel, nameLabel, scoreLabel);
        row.getStyleClass().add("score-row");
        if (entry == null) {
            row.getStyleClass().add("empty-row");
        }
        return row;
    }

    private static Label cell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

}
