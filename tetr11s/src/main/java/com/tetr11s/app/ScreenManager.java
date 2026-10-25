package com.tetr11s.app;

import java.io.IOException;

import com.tetr11s.app.controller.StartMenuController;
import com.tetr11s.game.GameScreen;
import com.tetr11s.settings.DefaultPalette;
import com.tetr11s.settings.KeyBindings;
import com.tetr11s.settings.SettingsView;

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
        stage.setScene(new Scene(root));

        stage.show();
    }

    /**
     * 시작화면에서 '시작' 버튼을 누르면 게임을 실행하는 함수
     */
    public void showGame(){
        stopCurrentGame();

        GameScreen screen = GameScreen.create(() -> {
            try {
                showStartMenu();
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
    
}
