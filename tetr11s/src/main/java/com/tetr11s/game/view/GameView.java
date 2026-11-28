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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 게임 화면 전체: 왼쪽 위에 홀드, 가운데 보드, 오른쪽에 다음 블럭, 점수, 상태, 조작 안내.
 * 일시정지하거나 게임오버가 되면 대전 화면과 같은 모양의 안내 카드가 화면 전체를 덮는다.
 * 상태를 바꾸지 않고, refresh()가 호출되면 GameManager를 읽어서 다시 그리기만 한다.
 */
public class GameView extends StackPane {

    // 게임 화면의 고정 기준 해상도
    // 홀드 칸(126) + 간격(24)이 들어가서 540에서 150 늘었다
    public static final double BASE_WIDTH = 690;
    public static final double BASE_HEIGHT = 644 + BoardView.BUFFER_HEIGHT;

    private static final Font INFO_FONT = Font.font("Monospaced", FontWeight.BOLD, 18);
    private static final Font HELP_FONT = Font.font("Monospaced", 13);
    // 일시정지 안내 카드는 BattleView의 일시정지 화면과 같은 글꼴과 색을 쓴다
    private static final Color PAUSED_COLOR = Color.rgb(245, 220, 90);
    private static final Color GAME_OVER_COLOR = Color.rgb(240, 80, 80);

    private final GameManager game;
    private final KeyBindings bindings;
    private final BoardView boardView;
    private final NextBlockView nextView;
    private final HoldView holdView;
    private final Label scoreValue = infoLabel("0");
    private final Label levelValue = infoLabel("1");
    private final Label linesValue = infoLabel("0");
    private final Label overlayTitle = new Label();
    private final Label overlayDetail = new Label();
    private final Label overlayHint = new Label();
    private final VBox overlay;

    private boolean restartAvailable;   // 게임오버 팝업에 Enter 다시 하기를 보여 줄지 (GameController가 정해 줌)
    private boolean lineClearFlash;   // 지울 줄을 흰색으로 그릴 차례인가 (GameController가 정해 줌)
    

    public GameView(GameManager game, DefaultPalette palette, KeyBindings bindings) {
        this.game = game;
        this.bindings = bindings;

        Board board = game.getBoard();
        this.boardView = new BoardView(palette, board.width(), board.height() - Board.BUFFER_ROWS);
        this.nextView = new NextBlockView(palette);
        this.holdView = new HoldView(palette, INFO_FONT, Color.WHITE);

        VBox side = new VBox(10,
                infoLabel("NEXT"), nextView,
                infoLabel("SCORE"), scoreValue,
                infoLabel("LEVEL"), levelValue,
                infoLabel("LINES"), linesValue,
                infoLabel("MODE"), infoLabel(game.getDifficulty().displayName()),
                helpLabel());
        side.setAlignment(Pos.TOP_LEFT);

        HBox content = new HBox(24, holdView, boardView, side);
        // 보드 캔버스 위쪽은 버퍼 줄 자리라서, 홀드·옆 정보는 그만큼 내려 보드 테두리와 맞춘다
        HBox.setMargin(holdView, new Insets(BoardView.BUFFER_HEIGHT, 0, 0, 0));
        HBox.setMargin(side, new Insets(BoardView.BUFFER_HEIGHT, 0, 0, 0));
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1e1e1e;");

        this.overlay = overlay();
        getChildren().addAll(content, overlay);

        // 내부 레이아웃 고정
        setPrefSize(BASE_WIDTH, BASE_HEIGHT);
        setMinSize(BASE_WIDTH, BASE_HEIGHT);
        setMaxSize(BASE_WIDTH, BASE_HEIGHT);
    }

    /** 게임오버 뒤 Enter로 다시 시작할 수 있는지 알려 준다. 게임오버 팝업 안내 문구에 쓰인다. */
    public void setRestartAvailable(boolean available) {
        this.restartAvailable = available;
    }

    /** 줄 삭제 연출에서 지금 흰색으로 그릴 차례인지 알려 준다. 다음 refresh()부터 반영된다. */
    public void setLineClearFlash(boolean on) {
        this.lineClearFlash = on;
    }

    /** 게임 상태를 읽어서 화면 전체를 다시 그린다. */
    public void refresh() {
        boardView.render(game, lineClearFlash);
        holdView.render(game);
        javafx.scene.Node shakeTarget = getParent() != null ? getParent() : this;
        if (game instanceof com.tetr11s.game.ItemGameManager itemGame) {
            nextView.render(game.getNext(), game.getNextItemType(), itemGame.getNextItemIndices());
            double[] shake = itemGame.getShakeOffset(System.currentTimeMillis());
            shakeTarget.setTranslateX(shake[0]);
            shakeTarget.setTranslateY(shake[1]);
        } else {
            nextView.render(game.getNext(), game.getNextItemType());
            shakeTarget.setTranslateX(0);
            shakeTarget.setTranslateY(0);
        }

        scoreValue.setText(String.valueOf(game.getScore()));
        levelValue.setText(String.valueOf(game.getLevel()));
        linesValue.setText(String.valueOf(game.getLines()));

        // 게임오버 중에는 일시정지가 안 되므로 게임오버 카드가 우선이다
        if (game.isGameOver()) {
            showGameOver();
        } else if (game.isPaused()) {
            showPaused();
        } else {
            overlay.setVisible(false);
        }
    }

    private void showPaused() {
        overlayTitle.setText("PAUSED");
        overlayTitle.setTextFill(PAUSED_COLOR);
        overlayDetail.setText("게임이 일시정지되었습니다");
        // 일시정지 중에 종료 키를 누르면 게임을 끝내고 점수 화면으로 간다
        overlayHint.setText(key(GameAction.PAUSE) + " : 계속하기     |     "
                + key(GameAction.QUIT) + " : 게임 종료");
        overlay.setVisible(true);
    }

    private void showGameOver() {
        overlayTitle.setText("GAME OVER");
        overlayTitle.setTextFill(GAME_OVER_COLOR);
        overlayDetail.setText(String.format("점수 %,d점   |   레벨 %d   |   %d줄",
                game.getScore(), game.getLevel(), game.getLines()));
        // Enter: 같은 모드로 다시 시작 (기록은 남지 않음) / 종료 키: 이름 입력(순위권) 또는 스코어보드
        String record = key(GameAction.QUIT) + " : 점수 기록으로";
        overlayHint.setText(restartAvailable ? "Enter : 다시 하기     |     " + record : record);
        overlay.setVisible(true);
    }

    /** 대전 화면의 결과 오버레이와 같은 모양: 어둡게 덮은 바탕 위 가운데 카드. 글은 상태에 따라 바뀐다. */
    private VBox overlay() {
        overlayTitle.setFont(Font.font("Malgun Gothic", FontWeight.BOLD, 38));

        overlayDetail.setFont(Font.font("Malgun Gothic", FontWeight.BOLD, 18));
        overlayDetail.setTextFill(Color.rgb(220, 224, 240));

        overlayHint.setFont(Font.font("Malgun Gothic", 15));
        overlayHint.setTextFill(Color.rgb(175, 180, 200));

        VBox card = new VBox(14, overlayTitle, overlayDetail, overlayHint);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 48, 32, 48));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #1d2033; -fx-background-radius: 14; "
                + "-fx-border-color: #4a5178; -fx-border-width: 2; -fx-border-radius: 14;");

        VBox box = new VBox(card);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: rgba(9, 10, 22, 0.78);");
        box.setVisible(false);
        box.setMouseTransparent(true);
        return box;
    }

    /** 현재 키 설정으로 조작 안내를 만든다. */
    private Label helpLabel() {
        String text = String.join("\n",
                key(GameAction.MOVE_LEFT) + " / " + key(GameAction.MOVE_RIGHT) + " : 이동",
                key(GameAction.SOFT_DROP) + " : 아래로",
                key(GameAction.ROTATE_CW) + " : 회전",
                key(GameAction.HARD_DROP) + " : 바로 떨어뜨리기",
                key(GameAction.HOLD) + " : 홀드",
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