package com.tetr11s.game.controller;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.view.GameView;
import com.tetr11s.settings.KeyBindings;

import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyEvent;

/**
 * 시간과 키 입력을 GameManager 호출로 바꿔 주고, 바뀔 때마다 화면을 다시 그린다.
 * gameLoop(매 프레임)에서 꾹 누른 키 반복과 자동 낙하(현재 레벨의 낙하 간격마다 tick)를 처리한다.
 */
public class GameController {

    private final GameManager game;
    private final GameView view;
    private final KeyBindings bindings;
    private final Runnable onQuit;

    private final KeyRepeater repeater = new KeyRepeater();
    private final AnimationTimer gameLoop;

    private long lastDropMs;

    /**
     * @param onQuit 게임을 종료할 때 실행할 일 (지금은 프로그램 종료, 나중에 시작 메뉴로 이동)
     */
    public GameController(GameManager game, GameView view, KeyBindings bindings, Runnable onQuit) {
        this.game = game;
        this.view = view;
        this.bindings = bindings;
        this.onQuit = onQuit;

        this.gameLoop = new AnimationTimer() {
            @Override 
            public void handle(long now){
                onFrame(nowMillis());
            }
        };
    }

    public void start() {
        game.start();
        lastDropMs = nowMillis();
        view.refresh();
        gameLoop.start();
    }

    public void stop() {
        gameLoop.stop();
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
            case HARD_DROP -> {
                game.hardDrop();
                lastDropMs = nowMillis();
            }
            case PAUSE -> togglePause();
            case QUIT -> quit();
        }
        update();
    }

    // 매 프레임 호출
    private void onFrame(long now) {
        // 꾹 누른 키 반복
        for (GameAction action : repeater.update(now)) {
            perform(action);
        }

        // 자동 낙하
        if (game.isPaused() || game.isGameOver()) {
            lastDropMs = now;   // 멈춘 동안 시간이 쌓이지 않게
            return;
        }

        boolean dropped = false;
        
        while (now - lastDropMs >= game.getDropIntervalMillis()) {
            lastDropMs += game.getDropIntervalMillis();
            game.tick();
            dropped = true;

            if (game.isGameOver()) {
                break;
            }
        }

        if (dropped) {
            update();
        }
    }

    private void togglePause() {
        game.togglePause();
        if (game.isPaused()) {
            repeater.cancelRepeats();
        }
    }

    /**
     * 게임 중에 누르면 먼저 일시정지하고 안내를 보여준다.
     * 일시정지나 게임오버 상태에서 한 번 더 누르면 종료한다.
     */
    private void quit() {
        if (game.isPaused() || game.isGameOver()) {
            onQuit.run(); //stop 은 ScreenManager 로 관리
        } else {
            togglePause();
        }
    }

    private void update() {
        view.refresh();
        if (game.isGameOver()) {
            repeater.cancelRepeats();
        }
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }
}