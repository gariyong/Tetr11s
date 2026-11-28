package com.tetr11s.app.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tetr11s.app.menu.MenuEntry;
import com.tetr11s.app.menu.MenuModel;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * 여러 하위 메뉴(대전 방식 선택, P2P 첫 화면 등)가 같이 쓰는 메뉴 화면 (menu_screen.fxml)
 *
 * 제목, 안내 문구, 항목, 돌아가기 동작은 ScreenManager가 화면을 열 때 show로 넘겨준다
 * 조작: ↑↓ 이동, Enter 선택, Esc 돌아가기, 마우스 클릭
 *
 * 이전 화면에서 누른 Enter·Esc가 이 화면에서 한 번 더 눌린 것처럼 처리되지 않도록,
 * 키를 한 번 뗄 때까지는 Enter와 Esc를 받지 않는다 (BattleModeController의 ready와 같은 이유)
 */
public class MenuScreenController {

    /** 항목을 골랐을 때 할 일. 화면 열기처럼 IOException을 던져도 된다 */
    @FunctionalInterface
    public interface Action {
        void run() throws IOException;
    }

    /**
     * 메뉴 항목 하나
     *
     * @param label       메뉴에 보이는 글자
     * @param description 이 항목을 고른 상태일 때 아래에 보여 줄 설명
     * @param action      Enter나 클릭으로 골랐을 때 할 일
     */
    public record Item(String label, String description, Action action) {
        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(action, "action");
        }
    }

    //selected 상태 생성
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    @FXML
    private BorderPane root;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private VBox menuBox;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label messageLabel;

    private List<Item> items = List.of();
    private MenuModel menu;           // show를 부르기 전에는 null
    private Action onBack;
    private boolean ready = false;    // 이 화면에서 키를 한 번이라도 뗐는가

    @FXML
    private void initialize() {
        // 포커스를 가진 노드가 없으면 키 입력이 루트까지 오지 않아서, 화면에 올라갈 때 포커스를 가져온다
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                root.requestFocus();
            }
        });
    }

    /**
     * 화면 내용을 채운다 (FXML을 읽은 직후 한 번 부른다)
     *
     * @param onBack Esc를 눌렀을 때 할 일 (보통 이전 화면 열기)
     */
    public void show(String title, String subtitle, List<Item> items, Action onBack) {
        this.items = List.copyOf(items);
        this.onBack = Objects.requireNonNull(onBack, "onBack");

        List<MenuEntry> entries = new ArrayList<>();
        for (Item item : this.items) {
            entries.add(new MenuEntry(item.label(), () -> run(item.action())));
        }
        this.menu = new MenuModel(entries);

        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);

        menuBox.getChildren().clear();
        for (int i = 0; i < this.items.size(); i++) {
            int index = i;
            Label label = new Label(this.items.get(i).label());
            label.getStyleClass().add("menu-item");
            label.setOnMouseClicked(event -> {
                select(index);
                menu.activate();
            });
            menuBox.getChildren().add(label);
        }
        showSelection();
    }

    //UP, DOWN, ENTER 로 이벤트를 나눔
    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (menu == null) {
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
            case ENTER -> {
                if(ready) menu.activate();
            }
            case ESCAPE -> {
                if(ready) run(onBack);
            }
            default -> {
                return;
            }
        }

        event.consume();
    }

    /** 아래쪽에 안내나 에러 메시지를 띄운다 (선택을 옮기면 지워진다) */
    public void showMessage(String message) {
        messageLabel.setText(message == null ? "" : message);
    }

    @FXML
    private void onKeyReleased(KeyEvent event) {
        ready = true;
    }

    // 마우스로 누른 항목을 선택 상태로 만든다
    private void select(int index) {
        while (menu.selectedIndex() != index) {
            menu.moveDown();
        }
        showSelection();
    }

    // 선택된 항목만 강조 표시
    private void showSelection() {
        for (int i = 0; i < menuBox.getChildren().size(); i++) {
            menuBox.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == menu.selectedIndex());
        }

        descriptionLabel.setText(items.get(menu.selectedIndex()).description());
        messageLabel.setText("");
    }

    // 화면을 열다 실패하면 (FXML 파일이 없는 등) 프로그램 오류로 알린다
    private static void run(Action action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new UncheckedIOException("화면을 열 수 없습니다.", e);
        }
    }

    /**private  void openBattleModeMenu(){
        try {
            screenManager.showBattleModeMenu();
        } catch (Exception e) {
            throw new RuntimeException("로컬 대전모드를 실행할 수 없습니다.", e);
        }
    }

    private void openStartMenu(){
        try {
            screenManager.showStartMenu();
        } catch (Exception e) {
            throw new RuntimeException("시작화면으로 돌아갈 수 없습니다.", e);
        }
    }**/
}
