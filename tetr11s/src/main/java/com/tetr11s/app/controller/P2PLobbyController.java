package com.tetr11s.app.controller;

import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.net.flow.LobbyView;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * P2P 로비 (p2p_lobby.fxml): 연결 직후와 매 판이 끝난 뒤의 대기 화면
 *
 * 조작: ←→ 모드 고르기(서버만), Enter '게임 시작' / '준비 취소', Esc 나가기, 마우스 클릭
 * 화면은 받은 상태를 그리고 누른 것을 알리기만 한다. 모드나 준비 상태를 실제로 바꾸는 건 P2P 흐름 담당이다
 * 이전 화면에서 누른 Enter(접속)가 여기서 '게임 시작'으로 처리되지 않도록, 키를 한 번 뗄 때까지는 Enter·Esc를 받지 않는다
 */
public class P2PLobbyController implements LobbyView {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass READY = PseudoClass.getPseudoClass("ready");
    private static final PseudoClass LAG = PseudoClass.getPseudoClass("lag");

    private static final BattleMode[] MODES = BattleMode.values();

    @FXML
    private BorderPane root;

    @FXML
    private Label connectionLabel;

    @FXML
    private Label modeCaptionLabel;

    @FXML
    private HBox modeBox;

    @FXML
    private Label modeDescriptionLabel;

    @FXML
    private Label myReadyLabel;

    @FXML
    private Label peerReadyLabel;

    @FXML
    private Label readyButton;

    @FXML
    private Label countdownLabel;

    @FXML
    private Label resultLabel;

    @FXML
    private Label networkLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Label keyHintLabel;

    private Listener listener;
    private boolean host;
    private BattleMode mode = BattleMode.NORMAL;
    private boolean countingDown = false;
    private Timeline countdown;
    private boolean ready = false;   // 이 화면에서 키를 한 번이라도 뗐는가

    @FXML
    private void initialize() {
        for (BattleMode each : MODES) {
            Label label = new Label(each.displayName());
            label.getStyleClass().add("p2p-mode");
            label.setOnMouseClicked(event -> selectMode(each));
            modeBox.getChildren().add(label);
        }
        connectionLabel.setText("");
        countdownLabel.setText("");
        resultLabel.setText("");
        networkLabel.setText("");
        messageLabel.setText("");
        showLobby(BattleMode.NORMAL, false, false);

        // 포커스를 가진 노드가 없으면 키 입력이 루트까지 오지 않아서, 화면에 올라갈 때 포커스를 가져온다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void showConnection(boolean host, String peerAddress, String difficulty) {
        this.host = host;
        connectionLabel.setText((host ? "서버" : "클라이언트") + "  ·  상대 " + peerAddress + "  ·  " + difficulty);
        modeCaptionLabel.setText(host ? "대전 모드 (←→로 선택)" : "대전 모드 (서버가 고릅니다)");
        updateKeyHint();
    }

    @Override
    public void showLobby(BattleMode mode, boolean myReady, boolean peerReady) {
        stopCountdown();
        this.mode = mode;
        for (int i = 0; i < MODES.length; i++) {
            modeBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, MODES[i] == mode);
        }
        modeDescriptionLabel.setText(mode.description());

        showReady(myReadyLabel, myReady);
        showReady(peerReadyLabel, peerReady);
        readyButton.setText(myReady ? "준비 취소" : "게임 시작");
        readyButton.pseudoClassStateChanged(SELECTED, true);
        updateKeyHint();
    }

    @Override
    public void showCountdown(long millis) {
        stopCountdown();
        countingDown = true;
        readyButton.pseudoClassStateChanged(SELECTED, false);
        updateKeyHint();

        // 남은 초를 1초마다 줄여서 보여 준다 (3 → 2 → 1 → 시작!)
        int seconds = (int) Math.max(1, (millis + 999) / 1000);
        countdown = new Timeline();
        for (int i = 0; i <= seconds; i++) {
            int left = seconds - i;
            countdown.getKeyFrames().add(new KeyFrame(Duration.seconds(i),
                    event -> countdownLabel.setText(left > 0 ? String.valueOf(left) : "시작!")));
        }
        countdown.play();
    }

    @Override
    public void showResult(String text) {
        resultLabel.setText(text);
    }

    @Override
    public void showNetwork(boolean lagging, long rttMillis) {
        String rtt = rttMillis < 0 ? "측정 중" : rttMillis + "ms";
        networkLabel.setText((lagging ? "랙 발생" : "연결 좋음") + " (" + rtt + ")");
        networkLabel.pseudoClassStateChanged(LAG, lagging);
    }

    @Override
    public void showMessage(String text) {
        messageLabel.setText(text == null ? "" : text);
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case LEFT -> selectMode(MODES[Math.floorMod(mode.ordinal() - 1, MODES.length)]);
            case RIGHT -> selectMode(MODES[Math.floorMod(mode.ordinal() + 1, MODES.length)]);
            case ENTER -> {
                if (ready && !countingDown && listener != null) {
                    listener.onToggleReady();
                }
            }
            case ESCAPE -> {
                if (ready && listener != null) {
                    listener.onLeave();
                }
            }
            default -> {
                return;
            }
        }
        event.consume();
    }

    @FXML
    private void onKeyReleased(KeyEvent event) {
        ready = true;
    }

    // '게임 시작' 버튼 클릭 (FXML의 onMouseClicked)
    @FXML
    private void onReadyClicked(MouseEvent event) {
        if (!countingDown && listener != null) {
            listener.onToggleReady();
        }
    }

    // 서버만 모드를 바꿀 수 있다. 화면은 바로 바꾸지 않고, 바뀐 로비 상태가 오면 showLobby로 그린다
    private void selectMode(BattleMode newMode) {
        if (host && !countingDown && newMode != mode && listener != null) {
            listener.onModeSelected(newMode);
        }
    }

    private static void showReady(Label label, boolean isReady) {
        label.setText(isReady ? "준비 완료" : "대기 중");
        label.pseudoClassStateChanged(READY, isReady);
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
        countingDown = false;
        countdownLabel.setText("");
    }

    private void updateKeyHint() {
        if (countingDown) {
            keyHintLabel.setText("곧 시작합니다     Esc 나가기");
        } else if (host) {
            keyHintLabel.setText("←→ 모드     Enter 게임 시작/취소     Esc 나가기");
        } else {
            keyHintLabel.setText("Enter 게임 시작/취소     Esc 나가기");
        }
    }
}