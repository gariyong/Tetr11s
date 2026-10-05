package com.tetr11s.game.controller;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.view.GameView;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/**
 * 시간(1초 자동 낙하)과 키 입력을 GameManager 호출로 바꿔 주고, 바뀔 때마다 화면을 다시 그린다.
 * 최소 버전: 키는 고정되어 있고, 일시정지/종료/속도 증가는 다음 단계에서 추가한다.
 */
public class GameController {

    private final GameManager game;
    private final GameView view;
    private final Timeline gravity;

    public GameController(GameManager game, GameView view) {
        this.game = game;
        this.view = view;
        this.gravity = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            game.tick();
            update();
        }));
        this.gravity.setCycleCount(Animation.INDEFINITE);
    }

    public void start() {
        game.start();
        view.refresh();
        gravity.play();
    }

    /** 키를 누르면 바로 처리한다. */
    public void handleKey(KeyEvent event) {
        switch (event.getCode()) {
            case LEFT -> game.moveLeft();
            case RIGHT -> game.moveRight();
            case DOWN -> game.softDrop();
            case UP -> game.rotateClockwise();
            case SPACE -> game.hardDrop();
            default -> {
                return;
            }
        }
        event.consume();
        update();
    }

    private void update() {
        view.refresh();
        if (game.isGameOver()) {
            gravity.stop();
        }
    }
}