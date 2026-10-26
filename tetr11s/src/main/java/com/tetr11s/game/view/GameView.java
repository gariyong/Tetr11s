package com.tetr11s.game.view;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.controller.GameAction;
import com.tetr11s.settings.DefaultPalette;
import com.tetr11s.settings.KeyBindings;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 게임 화면 전체: 왼쪽에 보드, 오른쪽에 다음 블럭, 점수, 상태, 조작 안내.
 * 상태를 바꾸지 않고, refresh()가 호출되면 GameManager를 읽어서 다시 그리기만 한다.
 */
public class GameView extends HBox {

    // 게임 화면의 고정 기준 해상도
    public static final double BASE_WIDTH = 540;
    public static final double BASE_HEIGHT = 644;

    private static final Font INFO_FONT = Font.font("Monospaced", FontWeight.BOLD, 18);
    private static final Font HELP_FONT = Font.font("Monospaced", 13);
    private static final Color PAUSED_COLOR = Color.rgb(240, 200, 80);
    private static final Color GAME_OVER_COLOR = Color.rgb(240, 80, 80);

    private final GameManager game;
    private final KeyBindings bindings;
    private final BoardView boardView;
    private final NextBlockView nextView;
    private final Label scoreValue = infoLabel("0");
    private final Label levelValue = infoLabel("1");
    private final Label linesValue = infoLabel("0");
    private final Label statusLabel = infoLabel("");

    private boolean lineClearFlash;   // 지울 줄을 흰색으로 그릴 차례인가 (GameController가 정해 줌)
    

    public GameView(GameManager game, DefaultPalette palette, KeyBindings bindings) {
        this.game = game;
        this.bindings = bindings;

        Board board = game.getBoard();
        this.boardView = new BoardView(palette, board.width(), board.height() - Board.BUFFER_ROWS);
        this.nextView = new NextBlockView(palette);

        VBox side = new VBox(10,
                infoLabel("NEXT"), nextView,
                infoLabel("SCORE"), scoreValue,
                infoLabel("LEVEL"), levelValue,
                infoLabel("LINES"), linesValue,
                infoLabel("MODE"), infoLabel(game.getDifficulty().displayName()),
                statusLabel,
                helpLabel());
        side.setAlignment(Pos.TOP_LEFT);

        setSpacing(24);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #1e1e1e;");
        getChildren().addAll(boardView, side);

        // 내부 레이아웃 고정
        setPrefSize(BASE_WIDTH, BASE_HEIGHT);
        setMinSize(BASE_WIDTH, BASE_HEIGHT);
        setMaxSize(BASE_WIDTH, BASE_HEIGHT);
    }

    /** 줄 삭제 연출에서 지금 흰색으로 그릴 차례인지 알려 준다. 다음 refresh()부터 반영된다. */
    public void setLineClearFlash(boolean on) {
        this.lineClearFlash = on;
    }

    /** 게임 상태를 읽어서 화면 전체를 다시 그린다. */
    public void refresh() {
        boardView.render(game, lineClearFlash);
        nextView.render(game.getNext());

        scoreValue.setText(String.valueOf(game.getScore()));
        levelValue.setText(String.valueOf(game.getLevel()));
        linesValue.setText(String.valueOf(game.getLines()));
        
        if (game.isGameOver()) {
            statusLabel.setText("GAME OVER\n" + key(GameAction.QUIT) + ": 종료");
            statusLabel.setTextFill(GAME_OVER_COLOR);
        } else if (game.isPaused()) {
            statusLabel.setText("PAUSED\n"
                    + key(GameAction.PAUSE) + ": 계속\n"
                    + key(GameAction.QUIT) + ": 종료");
            statusLabel.setTextFill(PAUSED_COLOR);
        } else {
            statusLabel.setText("");
        }
    }

    /** 현재 키 설정으로 조작 안내를 만든다. */
    private Label helpLabel() {
        String text = String.join("\n",
                key(GameAction.MOVE_LEFT) + " / " + key(GameAction.MOVE_RIGHT) + " : 이동",
                key(GameAction.SOFT_DROP) + " : 아래로",
                key(GameAction.ROTATE_CW) + " : 회전",
                key(GameAction.HARD_DROP) + " : 바로 떨어뜨리기",
                key(GameAction.PAUSE) + " : 일시정지",
                key(GameAction.QUIT) + " : 종료");
        Label help = new Label(text);
        help.setFont(HELP_FONT);
        help.setTextFill(Color.rgb(160, 160, 160));
        return help;
    }

    private String key(GameAction action) {
        return bindings.keyFor(action).getName();
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setFont(INFO_FONT);
        label.setTextFill(Color.WHITE);
        return label;
    }
}