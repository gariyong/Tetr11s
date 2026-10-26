package com.tetr11s.app.controller;

import java.io.IOException;
import java.util.List;

import com.tetr11s.app.ScreenManager;
import com.tetr11s.app.menu.MenuEntry;
import com.tetr11s.app.menu.MenuModel;
import com.tetr11s.settings.SettingsStore;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
* MenuModel 을 생성하고, 안에 MenuEntry 들을 추가한다
*/
public class StartMenuController {

    //selected 상태 생성
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private ScreenManager screenManager;

    private final MenuModel menu = new MenuModel(List.of(
            new MenuEntry("게임 시작", () -> screenManager.showGame(false)),
            new MenuEntry("아이템전 시작", () -> screenManager.showGame(true)),
            new MenuEntry("설정", this::openSettings),
            new MenuEntry("스코어보드", this::openScoreboard),
            new MenuEntry("게임 종료", this::exitGame)
    ));

    @FXML
    private BorderPane root;

    @FXML
    private VBox menuBox;
    
    @FXML
    private Label messageLabel;

    public void setScreenManager(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    @FXML
    private void initialize() {
        for (MenuEntry entry : menu.entries()) {
            Label label = new Label(entry.label());
            label.getStyleClass().add("menu-item");
            menuBox.getChildren().add(label);
        }
        showSelection();

        // 포커스를 가진 노드가 없으면 키 입력이 루트까지 오지 않아서, 화면에 올라갈 때 포커스를 가져온다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });

    }

    //UP, DOWN, ENTER 로 이벤트를 나눔
    @FXML
    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case UP -> {
                menu.moveUp();
                showSelection();
            }
            case DOWN -> {
                menu.moveDown();
                showSelection();
            }
            case ENTER -> menu.activate();
            default -> {
                return;
            }
        }
        event.consume();
    }

    // 선택된 항목만 강조 표시
    private void showSelection() {
        for (int i = 0; i < menuBox.getChildren().size(); i++) {
            menuBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == menu.selectedIndex());
        }
        messageLabel.setText("");
    }

    private void openSettings() {
        screenManager.showSettings();

    }

    private void openScoreboard() {
        try {
            screenManager.showScoreboard();
        } catch (IOException e) {
            throw new RuntimeException("스코어보드 화면을 열 수 없습니다.", e);
        }
    }

    private void exitGame() {
        Platform.exit();
    }

    @FXML
    private void cycleSize() {
        double current = SettingsStore.loadScale();
        double next = current == 0.75 ? 1.0 : current == 1.0 ? 1.25 : 0.75;
        SettingsStore.saveScale(next);
        screenManager.applyScale(next);
        messageLabel.setText("게임 크기: " + next + "배");
    }

    @FXML
    private void resetSettings() {
        SettingsStore.delete();
        messageLabel.setText("모든 설정을 초기화했습니다.");
    }

}
