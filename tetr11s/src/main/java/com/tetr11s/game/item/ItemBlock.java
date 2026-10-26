package com.tetr11s.game.item;

import com.tetr11s.game.tetromino.Tetromino;

public class ItemBlock {
    private final Tetromino tetromino; // 일반 블록 모양 (메테오 M인 경우 null일 수 있음)
    private final Item itemType;       // L, W, H, O, M 중 하나
    private final int targetCellIndex; // 4칸 중 알파벳이 박힐 셀 인덱스 (0~3)

    public ItemBlock(Tetromino tetromino, Item itemType, int targetCellIndex) {
        this.tetromino = tetromino;
        this.itemType = itemType;
        this.targetCellIndex = targetCellIndex;
    }

    public Tetromino getTetromino() {
        return tetromino;
    }

    public Item getItemType() {
        return itemType;
    }

    public int getTargetCellIndex() {
        return targetCellIndex;
    }
}