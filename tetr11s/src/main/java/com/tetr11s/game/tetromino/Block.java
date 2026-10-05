package com.tetr11s.game.tetromino; 

import java.util.*;

/**
 * type: 블럭 종류
 * rotation: 회전 상태(0 = 시작, 1 = 90˚, 2 = 180˚, 3 = 270˚)
 * row, col: 모양 상자 왼쪽 위 칸의 보드 위치 (row는 아래로 갈수록 커짐)
 */
public record Block(Tetromino type, int rotation, int row, int col) {
    
    public Block{
        if(type == null){
            throw new IllegalArgumentException("type은 null일 수 없음");
        }

        rotation = Math.floorMod(rotation, Tetromino.ROTATION_COUNT);
    }

    // 보드 가운데 위쪽에 새로운 블럭 생성
    // 너비 10 기준으로 I는 3~6열, O는 4~5열, 나머지는 3~5열에 놓임
    public static Block spawn(Tetromino type, int boardWidth){
        int col = (boardWidth - type.size()) / 2;
        
        return new Block(type, 0, 0, col);
    }

    // 블럭 이동
    public Block move(int dRow, int dCol){
        return new Block(type, rotation, row + dRow, col + dCol);
    }

    // 블럭 회전(시계 방향)
    public Block rotate(){
        return new Block(type, rotation + 1, row, col);
    }

    // 이 블럭이 보드에서 차지하는 칸들(보드 기준 절대 좌표)
    public List<Cell> cells(){
        List<Cell> result = new ArrayList<>();
        for(Cell c : type.cells(rotation)){
            result.add(new Cell(row + c.row(), col +c.col()));
        }

        return List.copyOf(result);
    }
}


