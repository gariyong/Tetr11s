package com.tetr11s.game.controller;

import java.util.Objects;

import com.tetr11s.game.battle.BattleManager;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.view.BattleView;

import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * 로컬 2인 대전의 게임 루프(AnimationTimer 1개)와 2인 키 입력 라우팅을 담당한다.
 *
 * 싱글 모드와 동일하게 대전 중 ESC를 누르면 일시정지(PAUSED)되고,
 * 일시정지 상태에서 Enter를 누르면 재개, ESC를 한 번 더 누르면 시작 메뉴로 종료한다.
 */
public class BattleController {

    private final BattleManager battle;
    private final BattleView view;
    private final BattleKeyBindings bindings;
    private final Runnable onExit;
    private final Runnable onRestart;

    private final PlayerRunner player1;
    private final PlayerRunner player2;
    private final AnimationTimer gameLoop;

    private long lastFrameMs;
    private boolean paused;

    public BattleController(BattleManager battle, BattleView view, BattleKeyBindings bindings,
                            Runnable onExit, Runnable onRestart) {
        this.battle = Objects.requireNonNull(battle, "battle");
        this.view = Objects.requireNonNull(view, "view");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.onExit = Objects.requireNonNull(onExit, "onExit");
        this.onRestart = Objects.requireNonNull(onRestart, "onRestart");

        this.player1 = new PlayerRunner(battle.getPlayer1());
        this.player2 = new PlayerRunner(battle.getPlayer2());

        this.gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(nowMillis());
            }
        };
    }

    public void start() {
        battle.start();
        paused = false;
        long now = nowMillis();
        lastFrameMs = now;
        player1.reset(now);
        player2.reset(now);
        syncAndRefresh();
        gameLoop.start();
    }

    public void stop() {
        gameLoop.stop();
        paused = false;
        player1.clearKeys();
        player2.clearKeys();
    }

    public void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        // 1. 대전 종료 상태: ESC → 시작 메뉴, ENTER → 재대전
        if (battle.isFinished()) {
            if (code == KeyCode.ESCAPE) {
                event.consume();
                onExit.run();
            } else if (code == KeyCode.ENTER) {
                event.consume();
                onRestart.run();
            }
            return;
        }

        // 2. 일시정지 상태: ESC 한 번 더 → 시작 메뉴로 종료, ENTER 또는 P → 계속하기
        if (paused) {
            event.consume();
            if (code == KeyCode.ESCAPE) {
                onExit.run();
            } else if (code == KeyCode.ENTER || code == KeyCode.P) {
                resumeBattle();
            }
            return;
        }

        // 3. 진행 중: ESC → 일시정지 (싱글 모드와 동일한 2단계 종료 흐름)
        if (code == KeyCode.ESCAPE) {
            event.consume();
            pauseBattle();
            return;
        }

        bindings.inputFor(code).ifPresent(input -> {
            event.consume();
            PlayerRunner runner = runnerFor(input.player());
            if (runner.onPress(input.action(), nowMillis())) {
                battle.update(0);
                if (battle.isFinished()) {
                    player1.cancelRepeats();
                    player2.cancelRepeats();
                }
                syncAndRefresh();
            }
        });
    }

    public void onKeyReleased(KeyEvent event) {
        bindings.inputFor(event.getCode()).ifPresent(input ->
                runnerFor(input.player()).onRelease(input.action(), nowMillis()));
    }

    /** 창 포커스를 잃으면 키를 뗀 신호가 오지 않으므로 양쪽 플레이어 키를 모두 초기화한다. */
    public void releaseAllKeys() {
        player1.clearKeys();
        player2.clearKeys();
    }

    private void pauseBattle() {
        paused = true;
        player1.cancelRepeats();
        player2.cancelRepeats();
        syncAndRefresh();
    }

    private void resumeBattle() {
        long now = nowMillis();
        paused = false;
        lastFrameMs = now;
        player1.syncDropClock(now);
        player2.syncDropClock(now);
        player1.clearKeys();
        player2.clearKeys();
        syncAndRefresh();
    }

    private void onFrame(long now) {
        if (battle.isFinished() || paused) {
            lastFrameMs = now;
            player1.syncDropClock(now);
            player2.syncDropClock(now);
            return;
        }

        long elapsed = Math.max(0L, now - lastFrameMs);
        lastFrameMs = now;

        boolean changed1 = player1.step(now, elapsed);
        boolean changed2 = player2.step(now, elapsed);

        // 두 플레이어를 모두 진행시킨 직후 승패·제한시간을 판정한다 (동시 게임오버 → 무승부)
        battle.update(elapsed);

        if (battle.isFinished()) {
            player1.cancelRepeats();
            player2.cancelRepeats();
            syncAndRefresh();
            return;
        }

        if (changed1 || changed2 || battle.getMode() == BattleMode.TIME_LIMIT) {
            syncAndRefresh();
        }
    }

    private PlayerRunner runnerFor(int player) {
        return player == 1 ? player1 : player2;
    }

    private void syncAndRefresh() {
        view.setPaused(paused);
        view.player1View().setLineClearFlash(player1.isFlashOn());
        view.player2View().setLineClearFlash(player2.isFlashOn());
        view.refresh();
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }
}
