package com.tetr11s.game.view;

import java.util.Objects;

import com.tetr11s.game.battle.BattleManager;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.game.controller.BattleKeyBindings;
import com.tetr11s.settings.DefaultPalette;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 로컬 2인 대전 화면 전체(1윈도우 2보드).
 *
 * 상단 바(모드명 · 남은 시간 타이머 · ESC 안내), 중앙 대칭 2인 보드(P1 | P2),
 * 그리고 승패가 결정됐을 때 덮어 올라오는 결과 오버레이로 구성된다.
 */
public class BattleView extends StackPane {

    public static final double BASE_WIDTH = 1040;
    public static final double BASE_HEIGHT = 690;

    private static final long TIMER_URGENT_MS = 10_000L;

    private static final Color P1_ACCENT = Color.rgb(90, 210, 255);
    private static final Color P2_ACCENT = Color.rgb(255, 145, 95);
    private static final Color DRAW_COLOR = Color.rgb(245, 220, 90);
    private static final Color URGENT_COLOR = Color.rgb(255, 90, 90);

    private final BattleManager battle;
    private final BattlePlayerView p1View;
    private final BattlePlayerView p2View;
    private final Label timerLabel;
    private final VBox resultOverlay;
    private final Label winnerLabel;
    private final Label resultDetailLabel;
    private final Label actionHintLabel;
    private boolean paused;

    public BattleView(BattleManager battle, DefaultPalette palette, BattleKeyBindings bindings) {
        this.battle = Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(bindings, "bindings");

        this.p1View = new BattlePlayerView(
                "PLAYER 1", P1_ACCENT, battle.getPlayer1(), palette, bindings.helpTextFor(1), true);
        this.p2View = new BattlePlayerView(
                "PLAYER 2", P2_ACCENT, battle.getPlayer2(), palette, bindings.helpTextFor(2), false);

        // ----- 상단 바 -----
        String modeText = "로컬 대전 · " + battle.getMode().displayName()
                + " (" + battle.getPlayer1().getDifficulty().displayName() + ")";
        Label modeLabel = new Label(modeText);
        modeLabel.setFont(Font.font("Malgun Gothic", FontWeight.BOLD, 16));
        modeLabel.setTextFill(Color.rgb(210, 215, 230));

        this.timerLabel = new Label(battle.getMode() == BattleMode.TIME_LIMIT
                ? formatRemainingTime(battle.getRemainingMillis())
                : "VS");
        timerLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 24));
        timerLabel.setTextFill(Color.WHITE);

        Label exitHint = new Label("ESC : 일시정지");
        exitHint.setFont(Font.font("Malgun Gothic", 13));
        exitHint.setTextFill(Color.rgb(150, 155, 175));

        BorderPane topBar = new BorderPane();
        topBar.setLeft(modeLabel);
        topBar.setCenter(timerLabel);
        topBar.setRight(exitHint);
        BorderPane.setAlignment(modeLabel, Pos.CENTER_LEFT);
        BorderPane.setAlignment(timerLabel, Pos.CENTER);
        BorderPane.setAlignment(exitHint, Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(0, 8, 8, 8));

        // ----- 중앙 2인 대칭 보드 -----
        Region divider = new Region();
        divider.setPrefWidth(2);
        divider.setMinWidth(2);
        divider.setMaxWidth(2);
        divider.setStyle("-fx-background-color: #2e3248;");

        HBox arenas = new HBox(16, p1View, divider, p2View);
        arenas.setAlignment(Pos.CENTER);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(topBar);
        mainLayout.setCenter(arenas);
        mainLayout.setPadding(new Insets(16, 20, 16, 20));
        mainLayout.setStyle("-fx-background-color: #161824;");

        // ----- 승패 결과 오버레이 -----
        this.winnerLabel = new Label("");
        winnerLabel.setFont(Font.font("Malgun Gothic", FontWeight.BOLD, 38));

        this.resultDetailLabel = new Label("");
        resultDetailLabel.setFont(Font.font("Malgun Gothic", FontWeight.BOLD, 18));
        resultDetailLabel.setTextFill(Color.rgb(220, 224, 240));

        this.actionHintLabel = new Label("Enter : 다시 대전     |     Esc : 시작 메뉴");
        actionHintLabel.setFont(Font.font("Malgun Gothic", 15));
        actionHintLabel.setTextFill(Color.rgb(175, 180, 200));

        VBox card = new VBox(14, winnerLabel, resultDetailLabel, actionHintLabel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 48, 32, 48));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #1d2033; -fx-background-radius: 14; "
                + "-fx-border-color: #4a5178; -fx-border-width: 2; -fx-border-radius: 14;");

        this.resultOverlay = new VBox(card);
        resultOverlay.setAlignment(Pos.CENTER);
        resultOverlay.setStyle("-fx-background-color: rgba(9, 10, 22, 0.78);");
        resultOverlay.setVisible(false);
        resultOverlay.setMouseTransparent(true);

        getChildren().addAll(mainLayout, resultOverlay);

        setPrefSize(BASE_WIDTH, BASE_HEIGHT);
        setMinSize(BASE_WIDTH, BASE_HEIGHT);
        setMaxSize(BASE_WIDTH, BASE_HEIGHT);
    }

    public BattlePlayerView player1View() {
        return p1View;
    }

    public BattlePlayerView player2View() {
        return p2View;
    }

    /** 대전 일시정지 표시 여부를 설정한다. 다음 refresh()부터 반영된다. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** 양쪽 플레이어 보드, 제한 시간 타이머, 일시정지/승패 오버레이를 현재 상태에 맞춰 갱신한다. */
    public void refresh() {
        p1View.refresh();
        p2View.refresh();

        if (battle.getMode() == BattleMode.TIME_LIMIT) {
            long rem = battle.getRemainingMillis();
            timerLabel.setText(formatRemainingTime(rem));
            timerLabel.setTextFill(rem <= TIMER_URGENT_MS ? URGENT_COLOR : Color.WHITE);
        }

        if (battle.isFinished()) {
            showResult(battle.getResult());
        } else if (paused) {
            showPaused();
        } else {
            resultOverlay.setVisible(false);
        }
    }

    private void showPaused() {
        winnerLabel.setText("PAUSED");
        winnerLabel.setTextFill(DRAW_COLOR);
        resultDetailLabel.setText("대전이 일시정지되었습니다");
        actionHintLabel.setText("Enter / P : 계속하기     |     Esc : 시작 메뉴로 나가기");
        resultOverlay.setVisible(true);
    }

    private void showResult(BattleResult result) {
        switch (result) {
            case PLAYER1_WIN -> {
                winnerLabel.setText("PLAYER 1 WIN!");
                winnerLabel.setTextFill(P1_ACCENT);
            }
            case PLAYER2_WIN -> {
                winnerLabel.setText("PLAYER 2 WIN!");
                winnerLabel.setTextFill(P2_ACCENT);
            }
            case DRAW -> {
                winnerLabel.setText("DRAW!");
                winnerLabel.setTextFill(DRAW_COLOR);
            }
        }
        resultDetailLabel.setText(String.format("P1 %,d점   vs   P2 %,d점",
                battle.getPlayer1().getScore(), battle.getPlayer2().getScore()));
        actionHintLabel.setText("Enter : 다시 대전     |     Esc : 시작 메뉴");
        resultOverlay.setVisible(true);
    }

    /**
     * 남은 시간(ms)을 MM:SS 문자열로 변환한다.
     * 올림 처리((rem + 999) / 1000)로 0.1초 남았을 때 00:00으로 보이는 어색함을 막는다.
     */
    public static String formatRemainingTime(long remainingMs) {
        long clamped = Math.max(0L, remainingMs);
        long totalSeconds = (clamped + 999L) / 1000L;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
