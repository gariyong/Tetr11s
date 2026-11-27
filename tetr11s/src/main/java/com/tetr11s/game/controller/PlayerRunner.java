package com.tetr11s.game.controller;

import java.util.Objects;
import java.util.function.LongSupplier;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.ItemGameManager;

/**
 * 대전 화면에서 한 플레이어의 프레임 진행 상태(키 반복, 줄 삭제 연출, 자동 낙하)를 캡슐화한다.
 *
 * JavaFX에 의존하지 않는 순수 Java 클래스이므로 JUnit으로 2인 동시 시뮬레이션을 직접 검증할 수 있다.
 */
public class PlayerRunner {

    static final long FLASH_INTERVAL_MS = 80;
    static final long CLEAR_DURATION_MS = 400;

    private final GameManager game;
    private final KeyRepeater repeater = new KeyRepeater();
    private final LongSupplier wallClock;

    private long lastDropMs;
    private long clearElapsedMs;

    public PlayerRunner(GameManager game) {
        this(game, System::currentTimeMillis);
    }

    PlayerRunner(GameManager game, LongSupplier wallClock) {
        this.game = Objects.requireNonNull(game, "game");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    }

    public GameManager game() {
        return game;
    }

    /** 대전 시작 또는 재시작 시 시간·키 상태를 초기화한다. */
    public void reset(long nowMs) {
        lastDropMs = nowMs;
        clearElapsedMs = 0;
        repeater.clear();
    }

    /** 키를 눌렀을 때 호출한다. 상태가 바뀌었으면 true. */
    public boolean onPress(GameAction action, long nowMs) {
        if (action == null || action == GameAction.PAUSE || action == GameAction.QUIT) {
            return false;
        }
        if (repeater.press(action, nowMs)) {
            return perform(action, nowMs);
        }
        return false;
    }

    /** 키를 뗐을 때 호출한다. */
    public void onRelease(GameAction action, long nowMs) {
        if (action != null && action != GameAction.PAUSE && action != GameAction.QUIT) {
            repeater.release(action, nowMs);
        }
    }

    /**
     * 한 프레임만큼 이 플레이어를 진행시킨다.
     *
     * @param nowMs     현재 단조 시각(ms)
     * @param elapsedMs 직전 프레임부터 흐른 시간(ms)
     * @return 화면을 다시 그릴 필요가 있으면 true
     */
    public boolean step(long nowMs, long elapsedMs) {
        boolean changed = false;

        for (GameAction action : repeater.update(nowMs)) {
            if (perform(action, nowMs)) {
                changed = true;
            }
        }

        if (game.isGameOver()) {
            lastDropMs = nowMs;
            repeater.cancelRepeats();
            return changed;
        }

        // 줄 삭제 연출 중: 아이템 효과 애니메이션이 끝난 뒤 400ms 반짝임을 진행한다
        if (game.isClearing()) {
            lastDropMs = nowMs;
            if (isWaitingForItemEffect()) {
                return true;
            }
            if (elapsedMs > 0) {
                clearElapsedMs += elapsedMs;
            }
            if (clearElapsedMs >= CLEAR_DURATION_MS) {
                game.finishLineClear();
                clearElapsedMs = 0;
            }
            return true;
        }

        // 자동 낙하 (창 멈춤 등으로 시간이 크게 튄 경우 한 프레임에 폭주하지 않게 보정)
        long dropInterval = game.getDropIntervalMillis();
        if (nowMs - lastDropMs > dropInterval * 3) {
            lastDropMs = nowMs - dropInterval;
        }

        while (nowMs - lastDropMs >= dropInterval) {
            lastDropMs += dropInterval;
            game.tick();
            changed = true;

            if (game.isGameOver() || game.isClearing()) {
                break;
            }
            dropInterval = game.getDropIntervalMillis();
        }

        if (game.isGameOver()) {
            repeater.cancelRepeats();
        }

        boolean hasEffects = (game instanceof ItemGameManager itemGame) && itemGame.hasActiveVisualEffects();
        return changed || hasEffects;
    }

    /** 지금 프레임에서 지울 줄을 흰색으로 반짝일 차례인가. */
    public boolean isFlashOn() {
        return game.isClearing() && !isWaitingForItemEffect()
                && (clearElapsedMs / FLASH_INTERVAL_MS) % 2 == 0;
    }

    public void cancelRepeats() {
        repeater.cancelRepeats();
    }

    public void clearKeys() {
        repeater.clear();
    }

    /** 일시정지 동안 낙하 시간이 쌓이지 않도록 기준 시각을 현재 시각으로 맞춘다. */
    public void syncDropClock(long nowMs) {
        lastDropMs = nowMs;
    }

    private boolean perform(GameAction action, long nowMs) {
        if (game.isGameOver()) {
            return false;
        }
        return switch (action) {
            case MOVE_LEFT -> game.moveLeft();
            case MOVE_RIGHT -> game.moveRight();
            case SOFT_DROP -> game.softDrop();
            case ROTATE_CW -> game.rotateClockwise();
            case HARD_DROP -> {
                game.hardDrop();
                lastDropMs = nowMs;
                yield true;
            }
            case PAUSE, QUIT -> false;
        };
    }

    private boolean isWaitingForItemEffect() {
        return game instanceof ItemGameManager itemGame
                && itemGame.isItemEffectAnimating(wallClock.getAsLong());
    }
}
