package com.tetr11s.game;

import java.util.function.IntConsumer;

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

    // 겜뷰를 게임스크린에서 받음 
    private final GameController controller;
    private final GameView view;
    private Scene scene;

    public GameScreen(GameController controller, GameView view, Scene scene) {
        this.controller = controller;
        this.view = view;
        this.scene = scene;
    }

    /**
     * @param difficulty 이번 판의 난이도 (ScreenManager가 게임을 시작할 때 설정에서 읽어 넘긴다)
     * @param onQuit     게임을 끝낼 때 실행할 일. 끝난 시점의 점수를 받는다
     */
    public static GameScreen create(Difficulty difficulty, IntConsumer onQuit){
        GameManager game = new GameManager(difficulty);
        KeyBindings bindings = new KeyBindings();
        GameView view = new GameView(game, new DefaultPalette(), bindings);

        GameController controller = new GameController(game, view, bindings, onQuit);

        Scene scene = new Scene(view);
        scene.setOnKeyPressed(controller::onKeyPressed);
        scene.setOnKeyReleased(controller::onKeyReleased);

        return new GameScreen(controller, view, scene);
    }

    // GameScreen의 현재 씬에 타겟 씬을 연결해줌
    public void attachToScene(Scene targetScene) {
        this.scene = targetScene;
        targetScene.setOnKeyPressed(controller::onKeyPressed);
        targetScene.setOnKeyReleased(controller::onKeyReleased);
    }

    // 게임뷰를 반환
    public GameView view() {
        return view;
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
