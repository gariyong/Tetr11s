package com.tetr11s.game.controller;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.view.GameView;
import com.tetr11s.settings.KeyBindings;

import javafx.animation.AnimationTimer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/**
 * 시간과 키 입력을 GameManager 호출로 바꿔 주고, 바뀔 때마다 화면을 다시 그린다.
 * gravity: 1초마다 한 칸 자동 낙하
 * inputLoop: 매 프레임 KeyRepeater를 확인해서 꾹 누른 키를 반복 실행
 */
public class GameController {

    private final GameManager game;
    private final GameView view;
    private final KeyBindings bindings;
    private final Runnable onQuit;

    private final KeyRepeater repeater = new KeyRepeater();
    private final Timeline gravity;
    private final AnimationTimer inputLoop;

    /**
     * @param onQuit 게임을 종료할 때 실행할 일 (지금은 프로그램 종료, 나중에 시작 메뉴로 이동)
     */
    public GameController(GameManager game, GameView view, KeyBindings bindings, Runnable onQuit) {
        this.game = game;
        this.view = view;
        this.bindings = bindings;
        this.onQuit = onQuit;

        this.gravity = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            game.tick();
            update();
        }));
        this.gravity.setCycleCount(Animation.INDEFINITE);

        this.inputLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                for (GameAction action : repeater.update(nowMillis())) {
                    perform(action);
                }
            }
        };
    }

    public void start() {
        game.start();
        view.refresh();
        gravity.play();
        inputLoop.start();
    }

    public void stop() {
        gravity.stop();
        inputLoop.stop();
        repeater.clear();
    }

    public void onKeyPressed(KeyEvent event) {
        GameAction action = bindings.actionFor(event.getCode());
        if (action == null) {
            return;
        }
        event.consume();
        if (repeater.press(action, nowMillis())) {
            perform(action);
        }
    }

    public void onKeyReleased(KeyEvent event) {
        GameAction action = bindings.actionFor(event.getCode());
        if (action != null) {
            repeater.release(action, nowMillis());
        }
    }

    /** 창이 포커스를 잃으면 키를 뗀 신호가 오지 않으니, 누른 키를 모두 잊는다. */
    public void releaseAllKeys() {
        repeater.clear();
    }

    private void perform(GameAction action) {
        switch (action) {
            case MOVE_LEFT -> game.moveLeft();
            case MOVE_RIGHT -> game.moveRight();
            case SOFT_DROP -> game.softDrop();
            case ROTATE_CW -> game.rotateClockwise();
            case HARD_DROP -> game.hardDrop();
            case PAUSE -> togglePause();
            case QUIT -> quit();
        }
        update();
    }

    private void togglePause() {
        game.togglePause();
        if (game.isPaused()) {
            gravity.pause();
            repeater.cancelRepeats();
        } else {
            gravity.play();
        }
    }

    /**
     * 게임 중에 누르면 먼저 일시정지하고 안내를 보여준다.
     * 일시정지나 게임오버 상태에서 한 번 더 누르면 종료한다.
     */
    private void quit() {
        if (game.isPaused() || game.isGameOver()) {
            stop();
            onQuit.run();
        } else {
            togglePause();
        }
    }

    private void update() {
        view.refresh();
        if (game.isGameOver()) {
            gravity.stop();
            repeater.cancelRepeats();
        }
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }
}