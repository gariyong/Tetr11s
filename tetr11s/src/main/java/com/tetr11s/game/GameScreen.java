package com.tetr11s.game;

import com.tetr11s.game.controller.GameController;
import com.tetr11s.game.view.GameView;
import com.tetr11s.settings.DefaultPalette;
import com.tetr11s.settings.KeyBindings;

import javafx.scene.Scene;
/**
 * GameScreen 을 만들어, ScreenManager 애서 호출할 수 있도록 한다
 * 기존 TetrisApp에서 만들던 객체들을 여기서 생성한다
 */
public class GameScreen {

    private final GameController controller;
    private final Scene scene;

    public GameScreen(GameController controller, Scene scene){
        this.controller=controller;
        this.scene=scene;
    }

    public static GameScreen create(Runnable onQuit){
        GameManager game = new GameManager();
        KeyBindings bindings = new KeyBindings();
        GameView view = new GameView(game, new DefaultPalette(), bindings);
        GameController controller = new GameController(game, view, bindings, onQuit);

        Scene scene = new Scene(view);
        scene.setOnKeyPressed(controller::onKeyPressed);
        scene.setOnKeyReleased(controller::onKeyReleased);

        return new GameScreen(controller, scene);
    }

    /**
     * scene, start, releaseAllKeys, stop 과 같은 메서드들은 ScreenManager 에서 직접 참조 못함
     * 따라서 GameScreen 에서 불러올 수 있게 한다
    */
    public Scene scene(){
        return scene;
    }

    public void start(){
        controller.start();
    }

    public void releaseAllKeys(){
        controller.releaseAllKeys();
    }

    public void stop(){
        controller.stop();
    }
}
