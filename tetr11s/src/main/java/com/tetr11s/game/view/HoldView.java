package com.tetr11s.game.view;

import java.util.List;

import com.tetr11s.game.GameManager;
import com.tetr11s.game.ItemGameManager;
import com.tetr11s.settings.DefaultPalette;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * 보드 왼쪽 위의 홀드 칸: "HOLD" 제목과 홀드한 블럭(아이템 포함).
 * 지금 블럭으로 이미 홀드했으면 다시 쓸 수 없다는 뜻으로 흐리게 그린다.
 */
public class HoldView extends VBox {

    private static final double USED_OPACITY = 0.35;

    private final NextBlockView blockView;

    public HoldView(DefaultPalette palette, Font titleFont, Color titleColor) {
        Label title = new Label("HOLD");
        title.setFont(titleFont);
        title.setTextFill(titleColor);

        // 블럭 그리기는 다음 블럭 칸과 같다 (4x4 칸, 아이템 표시 포함)
        this.blockView = new NextBlockView(palette);
        blockView.setCentered(true);
        // 빈 홀드 칸도 자리가 보이도록 어두운 바탕과 테두리를 깐다
        StackPane box = new StackPane(blockView);
        box.setStyle("-fx-background-color: #111111; -fx-border-color: #888888; -fx-border-width: 1;");
        box.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);

        setSpacing(6);
        setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(title, box);
    }

    /** 게임의 홀드 상태를 읽어 다시 그린다. */
    public void render(GameManager game) {
        List<Integer> itemIndices = (game instanceof ItemGameManager itemGame)
                ? itemGame.getHeldItemIndices()
                : List.of();
        blockView.render(game.getHeld(), game.getHeldItemType(), itemIndices);
        blockView.setOpacity(game.isHoldUsed() ? USED_OPACITY : 1.0);
    }
}
