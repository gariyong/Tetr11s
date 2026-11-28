package com.tetr11s.app.controller;

import java.util.List;

import com.tetr11s.net.flow.HostWaitView;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

/**
 * P2P 서버 대기 화면 (p2p_host.fxml)
 *
 * 내 IP와 진행 상황을 보여 주고, Esc를 누르면 알린다. 무엇을 보여 줄지는 P2P 흐름 담당이 정한다
 * 이전 화면에서 누른 키가 바로 실행되지 않도록, 키를 한 번 뗄 때까지는 Esc를 받지 않는다
 */
public class P2PHostController implements HostWaitView {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    @FXML
    private BorderPane root;

    @FXML
    private Label mainAddressLabel;

    @FXML
    private Label otherAddressesLabel;

    @FXML
    private Label statusLabel;

    private Runnable onCancel = () -> {
    };
    private boolean ready = false;   // 이 화면에서 키를 한 번이라도 뗐는가

    @FXML
    private void initialize() {
        mainAddressLabel.setText("찾는 중...");
        otherAddressesLabel.setText("");
        statusLabel.setText("");

        // 포커스를 가진 노드가 없으면 키 입력이 루트까지 오지 않아서, 화면에 올라갈 때 포커스를 가져온다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });
    }

    @Override
    public void showAddresses(List<String> addresses) {
        if (addresses.isEmpty()) {
            mainAddressLabel.setText("주소를 찾지 못했습니다");
            otherAddressesLabel.setText("네트워크(와이파이)에 연결되어 있는지 확인하세요.");
            return;
        }
        mainAddressLabel.setText(addresses.get(0));
        otherAddressesLabel.setText(addresses.size() == 1
                ? ""
                : "안 되면 다른 주소: " + String.join(", ", addresses.subList(1, addresses.size())));
    }

    @Override
    public void showStatus(String text, boolean error) {
        statusLabel.setText(error ? "! " + text : text);   // 색만으로 구분하지 않도록 앞에 표시를 붙인다
        statusLabel.pseudoClassStateChanged(ERROR, error);
    }

    @Override
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            if (ready) {
                onCancel.run();
            }
            event.consume();
        }
    }

    @FXML
    private void onKeyReleased(KeyEvent event) {
        ready = true;
    }
}