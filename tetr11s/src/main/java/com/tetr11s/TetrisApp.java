package com.tetr11s;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.controller.GameController;
import com.tetr11s.game.view.GameView;
import com.tetr11s.settings.DefaultPalette;
import com.tetr11s.settings.KeyBindings;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TetrisApp extends Application {

    @Override
    public void start(Stage stage) {
        GameManager game = new GameManager();
        KeyBindings bindings = new KeyBindings();
        GameView view = new GameView(game, new DefaultPalette(), bindings);
        GameController controller = new GameController(game, view, bindings, Platform::exit);

        Scene scene = new Scene(view);
        scene.setOnKeyPressed(controller::onKeyPressed);
        scene.setOnKeyReleased(controller::onKeyReleased);

        // 다른 창으로 전환하면 키를 뗀 신호가 오지 않아서, 블럭이 계속 움직이는 문제를 막는다
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                controller.releaseAllKeys();
            }
        });

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