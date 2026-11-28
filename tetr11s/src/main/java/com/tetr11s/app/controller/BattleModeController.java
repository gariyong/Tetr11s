package com.tetr11s.app.controller;

import java.io.IOException;
import java.util.List;

import com.tetr11s.app.ScreenManager;
import com.tetr11s.app.menu.MenuEntry;
import com.tetr11s.app.menu.MenuModel;
import com.tetr11s.game.battle.BattleMode;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * 로컬 2인 대전 모드 선택 화면 (일반 / 아이템 / 시간제한 / 돌아가기).
 *
 * 기존 MenuModel과 MenuEntry를 재사용하며, ScoreBoardController와 동일하게
 * 이전 화면의 Enter 키 잔상이 바로 실행되는 것을 막는 ready 가드를 적용한다.
 */
public class BattleModeController {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private ScreenManager screenManager;
    private boolean ready = false;

    private final MenuModel menu = new MenuModel(List.of(
            new MenuEntry(BattleMode.NORMAL.displayName() + " 대전", () -> startBattle(BattleMode.NORMAL)),
            new MenuEntry(BattleMode.ITEM.displayName() + " 대전", () -> startBattle(BattleMode.ITEM)),
            new MenuEntry(BattleMode.TIME_LIMIT.displayName() + " 대전", () -> startBattle(BattleMode.TIME_LIMIT)),
            new MenuEntry("돌아가기", this::goBack)
    ));

    @FXML
    private BorderPane root;

    @FXML
    private VBox menuBox;

    @FXML
    private Label descriptionLabel;

    public void setScreenManager(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    @FXML
    private void initialize() {
        List<MenuEntry> entries = menu.entries();
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Label label = new Label(entries.get(i).label());
            label.getStyleClass().add("menu-item");
            label.setOnMouseClicked(event -> {
                while (menu.selectedIndex() != index) {
                    menu.moveDown();
                }
                showSelection();
                menu.activate();
            });
            menuBox.getChildren().add(label);
        }
        showSelection();

        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (!ready) {
            event.consume();
            return;
        }
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
            case ESCAPE -> goBack();
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

    private void showSelection() {
        for (int i = 0; i < menuBox.getChildren().size(); i++) {
            menuBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == menu.selectedIndex());
        }
        descriptionLabel.setText(descriptionFor(menu.selectedIndex()));
    }

    private static String descriptionFor(int index) {
        return switch (index) {
            case 0 -> BattleMode.NORMAL.description();
            case 1 -> BattleMode.ITEM.description();
            case 2 -> BattleMode.TIME_LIMIT.description();
            default -> "시작 메뉴로 돌아갑니다.";
        };
    }

    private void startBattle(BattleMode mode) {
        screenManager.showBattle(mode);
    }

    private void goBack() {
        try {
            screenManager.showStartMenu();
        } catch (IOException e) {
            throw new RuntimeException("시작 메뉴를 열 수 없습니다.", e);
        }
    }
}
