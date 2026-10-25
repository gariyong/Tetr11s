package com.tetr11s.app.controller;

import java.util.function.Consumer;

import com.tetr11s.score.ScoreEntry;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * 이름 입력 화면 (순위권 점수일 때만 열린다)
 *
 * 이름을 검사하고, 올바르면 onConfirm에 이름을 넘긴다
 * 기록을 저장하는 일은 하지 않는다 (ScreenManager가 담당)
 */
public class NameInputController {

    private static final PseudoClass TOO_LONG = PseudoClass.getPseudoClass("too-long");

    @FXML
    private Label scoreLabel;

    @FXML
    private TextField nameField;

    @FXML
    private Label countLabel;

    @FXML
    private Label messageLabel;

    private Consumer<String> onConfirm = name -> { };

    @FXML
    private void initialize() {
        // 글자를 입력할 때마다 글자 수 표시를 갱신하고, 이전 안내 문구는 지운다
        nameField.textProperty().addListener((observable, oldText, newText) -> {
            updateCount(newText);
            messageLabel.setText("");
        });
        updateCount("");

        // 화면에 올라가자마자 바로 입력할 수 있게 입력 칸에 포커스를 준다
        nameField.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                nameField.requestFocus();
            }
        });
    }

    /**
     * @param score     이번 판 점수 (화면에 보여주기만 함)
     * @param onConfirm 올바른 이름으로 Enter를 눌렀을 때 실행할 일
     */
    public void show(int score, Consumer<String> onConfirm) {
        this.onConfirm = onConfirm;
        scoreLabel.setText(String.format("%,d", score));
    }

    // 입력 칸에서 Enter
    @FXML
    private void onConfirm() {
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            messageLabel.setText("이름을 입력하세요.");
            return;
        }
        if (length(name) > ScoreEntry.MAX_NAME_LENGTH) {
            messageLabel.setText(ScoreEntry.MAX_NAME_LENGTH + "자 이하로 입력하세요.");
            return;
        }
        onConfirm.accept(name);
    }

    private void updateCount(String text) {
        int length = length(text.strip());
        countLabel.setText(length + " / " + ScoreEntry.MAX_NAME_LENGTH);
        countLabel.pseudoClassStateChanged(TOO_LONG, length > ScoreEntry.MAX_NAME_LENGTH);
    }

    // 글자 수 (이모지처럼 두 칸을 쓰는 문자도 한 글자로 센다)
    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}