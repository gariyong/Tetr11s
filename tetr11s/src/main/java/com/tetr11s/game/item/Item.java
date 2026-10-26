package com.tetr11s.game.item;


/**
 * 아이템 종류 5가지 (L, W, H, O, M)
 * tetrominoRiding: true면 기존 테트로미노(4칸 블록)에 업혀서 나오고, 
 *                   false면 단독 셀(메테오 등) 혹은 특수 형태로 나옴.
 */
public enum Item {
    L("줄삭제", true),
    W("무게추", false),
    H("수소", true),
    O("산소", true),
    M("메테오", false);

    private final String itemName;
    private final boolean tetrominoRiding;

    Item(String itemName, boolean tetrominoRiding) {
        this.itemName = itemName;
        this.tetrominoRiding = tetrominoRiding;
    }

    public String getItemName() {
        return itemName;
    }

    public boolean isTetrominoRiding() {
        return tetrominoRiding;
    }
}