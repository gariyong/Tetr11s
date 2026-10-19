package com.tetr11s;

import com.tetr11s.app.ScreenManager;

import javafx.application.Application;
import javafx.stage.Stage;
/**
 * TetrisApp 애서는 시작화면만 실행
 * 따라서 ScreenManager 로 showStartMenu 만 불러옴
 */
public class TetrisApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        ScreenManager screen = new ScreenManager(stage);
        screen.showStartMenu();
    }

    public static void main(String[] args) {
        launch(args);
    }
}