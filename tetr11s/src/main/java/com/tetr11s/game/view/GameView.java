package com.tetr11s.game.view;

import com.tetr11s.game.Board;
import com.tetr11s.game.GameManager;
import com.tetr11s.settings.DefaultPalette;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 게임 화면 전체: 왼쪽에 보드, 오른쪽에 다음 블럭과 점수.
 * 상태를 바꾸지 않고, refresh()가 호출되면 GameManager를 읽어서 다시 그리기만 한다.
 */
public class GameView extends HBox {

    private static final Font INFO_FONT = Font.font("Monospaced", FontWeight.BOLD, 18);

    private final GameManager game;
    private final BoardView boardView;
    private final NextBlockView nextView;
    private final Label scoreValue = infoLabel("0");
    private final Label gameOverLabel = infoLabel("GAME OVER");

    public GameView(GameManager game, DefaultPalette palette) {
        this.game = game;

        Board board = game.getBoard();
        this.boardView = new BoardView(palette, board.width(), board.height() - Board.BUFFER_ROWS);
        this.nextView = new NextBlockView(palette);

        gameOverLabel.setTextFill(Color.rgb(240, 80, 80));
        gameOverLabel.setVisible(false);

        VBox side = new VBox(10,
                infoLabel("NEXT"), nextView,
                infoLabel("SCORE"), scoreValue,
                gameOverLabel);
        side.setAlignment(Pos.TOP_LEFT);

        setSpacing(24);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #1e1e1e;");
        getChildren().addAll(boardView, side);
    }

    /** 게임 상태를 읽어서 화면 전체를 다시 그린다. */
    public void refresh() {
        boardView.render(game);
        nextView.render(game.getNext());
        gameOverLabel.setVisible(game.isGameOver());
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setFont(INFO_FONT);
        label.setTextFill(Color.WHITE);
        return label;
    }
}