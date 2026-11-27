package com.tetr11s.game;

import java.util.Objects;

import com.tetr11s.game.controller.BattleController;
import com.tetr11s.game.view.BattleView;

import javafx.scene.Scene;

/**
 * 로컬 2인 대전의 화면(BattleView)과 컨트롤러(BattleController)를 묶어
 * ScreenManager가 ActiveScreen 인터페이스로 관리할 수 있게 한다.
 */
public class BattleScreen implements ActiveScreen {

    private final BattleController controller;
    private final BattleView view;

    public BattleScreen(BattleController controller, BattleView view) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.view = Objects.requireNonNull(view, "view");
    }

    public BattleView view() {
        return view;
    }

    @Override
    public void attachToScene(Scene targetScene) {
        Objects.requireNonNull(targetScene, "targetScene");
        targetScene.setOnKeyPressed(controller::onKeyPressed);
        targetScene.setOnKeyReleased(controller::onKeyReleased);
    }

    @Override
    public void start() {
        controller.start();
    }

    @Override
    public void stop() {
        controller.stop();
    }

    @Override
    public void releaseAllKeys() {
        controller.releaseAllKeys();
    }
}
