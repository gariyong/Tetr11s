package com.tetr11s.app.controller;

import java.util.List;

import com.tetr11s.net.flow.JoinView;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * P2P 클라이언트 접속 화면 (p2p_join.fxml)
 *
 * 조작: 입력칸에 IP를 쓰고 Enter, ↑↓로 최근 주소 고르기(입력칸에 채워짐), 최근 주소 클릭, Esc
 * 입력칸이 화살표와 Esc를 먼저 가져가지 않도록 키는 루트에서 먼저(이벤트 필터로) 받는다
 * 이전 화면에서 누른 Enter·Esc가 바로 실행되지 않도록, 키를 한 번 뗄 때까지는 둘 다 받지 않는다
 */
public class P2PJoinController implements JoinView {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private static final String IDLE_HINT = "Enter 접속     ↑↓ 최근 주소     Esc 돌아가기";
    private static final String CONNECTING_HINT = "Esc 접속 취소";

    @FXML
    private BorderPane root;

    @FXML
    private TextField addressField;

    @FXML
    private Label statusLabel;

    @FXML
    private VBox recentBox;

    @FXML
    private Label keyHintLabel;

    private Listener listener;
    private List<String> recent = List.of();
    private int selectedRecent = -1;   // ↑↓로 고른 최근 주소 (없으면 -1)
    private boolean connecting = false;
    private boolean ready = false;     // 이 화면에서 키를 한 번이라도 뗐는가

    @FXML
    private void initialize() {
        statusLabel.setText("");
        keyHintLabel.setText(IDLE_HINT);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        root.addEventFilter(KeyEvent.KEY_RELEASED, event -> ready = true);

        // 화면에 올라가면 바로 입력할 수 있게 입력칸에 포커스를 준다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                addressField.requestFocus();
            }
        });
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void showRecent(List<String> addresses) {
        recent = List.copyOf(addresses);
        selectedRecent = -1;
        recentBox.getChildren().clear();
        if (recent.isEmpty()) {
            Label none = new Label("아직 없습니다");
            none.getStyleClass().add("p2p-hint");
            recentBox.getChildren().add(none);
            return;
        }
        for (int i = 0; i < recent.size(); i++) {
            int index = i;
            Label label = new Label(recent.get(i));
            label.getStyleClass().add("p2p-recent");
            label.setOnMouseClicked(event -> {
                if (!connecting) {
                    selectRecent(index);
                    addressField.requestFocus();
                }
            });
            recentBox.getChildren().add(label);
        }
    }

    @Override
    public void showStatus(String text, boolean error) {
        statusLabel.setText(error ? "! " + text : text);   // 색만으로 구분하지 않도록 앞에 표시를 붙인다
        statusLabel.pseudoClassStateChanged(ERROR, error);
    }

    @Override
    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
        addressField.setDisable(connecting);
        keyHintLabel.setText(connecting ? CONNECTING_HINT : IDLE_HINT);
        if (connecting) {
            root.requestFocus();   // 입력칸이 꺼지면 Esc를 받을 곳이 필요하다
        } else {
            addressField.requestFocus();
        }
    }

    // 입력칸의 Enter (FXML의 onAction)
    @FXML
    private void onSubmit(ActionEvent event) {
        if (ready && !connecting && listener != null) {
            listener.onConnect(addressField.getText());
        }
    }

    // 루트가 입력칸보다 먼저 받는 키: ↑↓ 최근 주소, Esc
    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case UP -> {
                if (!connecting && !recent.isEmpty()) {
                    selectRecent(selectedRecent <= 0 ? recent.size() - 1 : selectedRecent - 1);
                }
                event.consume();
            }
            case DOWN -> {
                if (!connecting && !recent.isEmpty()) {
                    selectRecent(selectedRecent < 0 || selectedRecent == recent.size() - 1 ? 0 : selectedRecent + 1);
                }
                event.consume();
            }
            case ESCAPE -> {
                if (ready && listener != null) {
                    listener.onCancel();
                }
                event.consume();
            }
            default -> {
                // 나머지 키는 입력칸이 받는다 (Enter는 onSubmit으로 온다)
            }
        }
    }

    // 최근 주소 하나를 강조하고 입력칸에 채운다
    private void selectRecent(int index) {
        selectedRecent = index;
        for (int i = 0; i < recentBox.getChildren().size(); i++) {
            recentBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == index);
        }
        addressField.setText(recent.get(index));
        addressField.positionCaret(addressField.getText().length());
    }
}