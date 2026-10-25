package com.tetr11s.game.controller;

import java.util.function.IntConsumer;

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
    // 줄 삭제 연출: FLASH_INTERVAL_MS마다 흰색 ↔ 원래 색을 바꾸고, CLEAR_DURATION_MS가 지나면 실제로 지운다
    private static final long FLASH_INTERVAL_MS = 80;
    private static final long CLEAR_DURATION_MS = 400;

    private final GameManager game;
    private final GameView view;
    private final KeyBindings bindings;
    private final IntConsumer onQuit;

    private final KeyRepeater repeater = new KeyRepeater();
    private final AnimationTimer gameLoop;

    private long lastDropMs;
    private long lastFrameMs;      // 직전 프레임 시각 (프레임 사이에 흐른 시간 계산용)
    private long clearElapsedMs;   // 지금 줄 삭제 연출이 진행된 시간 (일시정지 동안은 늘지 않음)

    /**
     * @param onQuit 게임을 종료할 때 실행할 일 (지금은 프로그램 종료, 나중에 시작 메뉴로 이동)
     */
    public GameController(GameManager game, GameView view, KeyBindings bindings, IntConsumer onQuit) {
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
        lastFrameMs = lastDropMs;
        clearElapsedMs = 0;
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
        long elapsed = now - lastFrameMs;   // 직전 프레임부터 흐른 시간
        lastFrameMs = now;

        // 꾹 누른 키 반복
        for (GameAction action : repeater.update(now)) {
            perform(action);
        }

        if (game.isPaused() || game.isGameOver()) {
            lastDropMs = now;   // 멈춘 동안 시간이 쌓이지 않게
            return;
        }

        // 줄 삭제 연출 중: 자동 낙하 대신 연출 시간을 진행한다
        if (game.isClearing()) {
            lastDropMs = now;   // 연출 동안에도 낙하 시간이 쌓이지 않게
            clearElapsedMs += elapsed;
            if (clearElapsedMs >= CLEAR_DURATION_MS) {
                game.finishLineClear();
                clearElapsedMs = 0;
            }
            update();
            return;
        }

        // 자동 낙하
        boolean dropped = false;

        while (now - lastDropMs >= game.getDropIntervalMillis()) {
            lastDropMs += game.getDropIntervalMillis();
            game.tick();
            dropped = true;

            if (game.isGameOver() || game.isClearing()) {
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
            onQuit.accept(game.getScore()); //stop 은 ScreenManager 로 관리
        } else {
            togglePause();
        }
    }

    private void update() {
        view.setLineClearFlash(isFlashOn());
        view.refresh();
        if (game.isGameOver()) {
            repeater.cancelRepeats();
        }
    }

    // 줄 삭제 연출 중 지금이 흰색 차례인가 (0~80ms 흰색, 80~160ms 원래 색, 160~240ms 흰색 ...)
    private boolean isFlashOn() {
        return game.isClearing() && (clearElapsedMs / FLASH_INTERVAL_MS) % 2 == 0;
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }
}