package com.tetr11s;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.controller.GameController;
import com.tetr11s.game.view.GameView;
import com.tetr11s.settings.DefaultPalette;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TetrisApp extends Application {

    @Override
    public void start(Stage stage) {
        // 시작 메뉴가 생기면 ScreenManager를 통해 게임 화면으로 전환
        GameManager game = new GameManager();
        GameView view = new GameView(game, new DefaultPalette());
        GameController controller = new GameController(game, view);

        Scene scene = new Scene(view);
        scene.setOnKeyPressed(controller::handleKey);

        stage.setTitle("Tetr11s");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        controller.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}