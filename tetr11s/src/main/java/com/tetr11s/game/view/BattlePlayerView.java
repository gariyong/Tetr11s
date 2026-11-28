package com.tetr11s.game.view;

import java.util.Objects;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.ItemGameManager;
import com.tetr11s.settings.DefaultPalette;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 로컬 대전 화면에서 플레이어 1명분의 UI(홀드 + 보드 + 사이드바)를 담당하는 재사용 컴포넌트.
 *
 * boardOnLeft 값에 따라 P1은 [홀드 | 보드 | 사이드바], P2는 [사이드바 | 홀드 | 보드]로 배치된다.
 */
public class BattlePlayerView extends HBox {

    private static final double SIDE_WIDTH = 152;
    private static final Font HEADER_FONT = Font.font("Malgun Gothic", FontWeight.BOLD, 18);
    private static final Font SECTION_FONT = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font VALUE_FONT = Font.font("Monospaced", FontWeight.BOLD, 16);
    private static final Font HELP_FONT = Font.font("Malgun Gothic", 11);
    private static final Color GAME_OVER_COLOR = Color.rgb(240, 80, 80);

    private final GameManager game;
    private final BoardView boardView;
    private final NextBlockView nextView;
    private final HoldView holdView;
    private final GarbagePreviewView garbageView;
    private final Label scoreValue = valueLabel("0");
    private final Label levelValue = valueLabel("1");
    private final Label linesValue = valueLabel("0");
    private final Label statusLabel = valueLabel("");

    private boolean lineClearFlash;

    public BattlePlayerView(String title, Color accentColor, GameManager game,
                            DefaultPalette palette, String helpText, boolean boardOnLeft) {
        this.game = Objects.requireNonNull(game, "game");
        Objects.requireNonNull(palette, "palette");

        Board board = game.getBoard();
        this.boardView = new BoardView(palette, board.width(), board.height() - Board.BUFFER_ROWS);
        this.nextView = new NextBlockView(palette);
        this.garbageView = new GarbagePreviewView();
        this.holdView = new HoldView(palette, SECTION_FONT, Color.rgb(190, 195, 215));

        Label header = new Label(title);
        header.setFont(HEADER_FONT);
        header.setTextFill(accentColor);

        HBox levelLinesRow = new HBox(18,
                new VBox(2, sectionLabel("LEVEL"), levelValue),
                new VBox(2, sectionLabel("LINES"), linesValue));

        statusLabel.setTextFill(GAME_OVER_COLOR);

        Label helpLabel = new Label(helpText);
        helpLabel.setFont(HELP_FONT);
        helpLabel.setTextFill(Color.rgb(165, 165, 180));

        VBox side = new VBox(6,
                header,
                sectionLabel("NEXT"), nextView,
                sectionLabel("INCOMING"), garbageView,
                sectionLabel("SCORE"), scoreValue,
                levelLinesRow,
                statusLabel,
                helpLabel);
        side.setAlignment(Pos.TOP_LEFT);
        side.setPrefWidth(SIDE_WIDTH);
        side.setMinWidth(SIDE_WIDTH);
        side.setMaxWidth(SIDE_WIDTH);

        setSpacing(14);
        setAlignment(Pos.TOP_CENTER);
        // 보드 캔버스 위쪽은 버퍼 줄 자리라서, 홀드·옆 정보는 그만큼 내려 보드 테두리와 맞춘다
        HBox.setMargin(holdView, new Insets(BoardView.BUFFER_HEIGHT, 0, 0, 0));
        HBox.setMargin(side, new Insets(BoardView.BUFFER_HEIGHT, 0, 0, 0));
        // 홀드 칸은 두 플레이어 모두 보드 왼쪽 위에 둔다
        if (boardOnLeft) {
            getChildren().addAll(holdView, boardView, side);
        } else {
            getChildren().addAll(side, holdView, boardView);
        }
    }

    /** 줄 삭제 연출에서 흰색 반짝임 차례인지 설정한다. 다음 refresh()부터 반영된다. */
    public void setLineClearFlash(boolean on) {
        this.lineClearFlash = on;
    }

    /** GameManager 상태를 읽어 보드·미리보기·공격 대기열·점수를 다시 그린다. */
    public void refresh() {
        boardView.render(game, lineClearFlash);
        holdView.render(game);
        if (game instanceof ItemGameManager itemGame) {
            nextView.render(game.getNext(), game.getNextItemType(), itemGame.getNextItemIndices());
            double[] shake = itemGame.getShakeOffset(System.currentTimeMillis());
            // 해당 플레이어의 보드만 흔들리게 하여 상대 보드에 영향을 주지 않는다
            boardView.setTranslateX(shake[0]);
            boardView.setTranslateY(shake[1]);
        } else {
            nextView.render(game.getNext(), game.getNextItemType());
            boardView.setTranslateX(0);
            boardView.setTranslateY(0);
        }

        garbageView.render(game.getIncomingGarbage());
        scoreValue.setText(String.format("%,d", game.getScore()));
        levelValue.setText(String.valueOf(game.getLevel()));
        linesValue.setText(String.valueOf(game.getLines()));
        statusLabel.setText(game.isGameOver() ? "GAME OVER" : "");
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setFont(SECTION_FONT);
        label.setTextFill(Color.rgb(190, 195, 215));
        return label;
    }

    private static Label valueLabel(String text) {
        Label label = new Label(text);
        label.setFont(VALUE_FONT);
        label.setTextFill(Color.WHITE);
        return label;
    }
}
