package com.tetr11s.app;

import java.io.IOException;

import com.tetr11s.app.controller.StartMenuController;
import com.tetr11s.game.GameScreen;

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

    public ScreenManager(Stage stage){
        this.stage=stage;
    }

    /**
     * 이전 currentScreen 이 남아, 계속 게임이 돌아가는 경우를 막기 위해 null 로 초기화
     */
    private void stopCurrentGame(){
        if(currentScreen != null){
            currentScreen.stop();
            currentScreen=null;
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

        currentScreen = GameScreen.create(() -> {
            try {
                showStartMenu();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // 다른 창으로 전환하면 키를 뗀 신호가 오지 않아서, 블럭이 계속 움직이는 문제를 막는다
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                currentScreen.releaseAllKeys();
            }
        });

        stage.setTitle("Tetr11s");
        stage.setScene(currentScreen.scene());
        stage.setResizable(false);
        stage.show();

        currentScreen.start();
    }
    
}
