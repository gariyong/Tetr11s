package com.tetr11s.game.tetromino;

import java.util.*;

/**
 * 7가지 테트로미노와 SRS 회전 상태. 회전은 시계 방향 기준.
 * 좌표는 {x, y}로 적는다. 모양 상자의 왼쪽 아래가 (0, 0)이고 y는 위로 갈수록 커진다.
 * toCells()에서 보드용 (row, col)로 바꾼다. row는 위에서 아래로 커진다.
 */
public enum Tetromino {
    // 회전은 시계방향 기준
    T(3, new int[][][]{
        {{1,1}, {0,1}, {2,1}, {1,2}},   // 0˚
        {{1,1}, {1,0}, {1,2}, {2,1}},   // 90˚
        {{1,1}, {0,1}, {2,1}, {1,0}},   // 180˚
        {{1,1}, {1,0}, {1,2}, {0,1}}    // 270˚
    }),

    O(2, new int[][][]{
        {{0,0}, {0,1}, {1,0}, {1,1}},   // 0˚   
        {{0,0}, {0,1}, {1,0}, {1,1}},   // 90˚
        {{0,0}, {0,1}, {1,0}, {1,1}},   // 180˚
        {{0,0}, {0,1}, {1,0}, {1,1}}    // 270˚
    }),

    I(4, new int[][][]{
        {{0,2}, {1,2}, {2,2}, {3,2}},   // 0˚
        {{2,0}, {2,1}, {2,2}, {2,3}},   // 90˚
        {{0,1}, {1,1}, {2,1}, {3,1}},   // 180˚
        {{1,0}, {1,1}, {1,2}, {1,3}}    // 270˚
    }),

    S(3, new int[][][]{
        {{1,1}, {0,1}, {1,2}, {2,2}},   // 0˚
        {{1,1}, {1,2}, {2,1}, {2,0}},   // 90˚
        {{1,1}, {2,1}, {0,0}, {1,0}},   // 180˚
        {{1,1}, {1,0}, {0,1}, {0,2}}    // 270˚
    }),

    Z(3, new int[][][]{
        {{1,1}, {1,2}, {0,2}, {2,1}},
        {{1,1}, {1,0}, {2,1}, {2,2}},
        {{1,1}, {0,1}, {1,0}, {2,0}},
        {{1,1}, {0,1}, {0,0}, {1,2}}
    }),

    L(3, new int[][][]{
        {{1,1}, {0,1}, {2,1}, {2,2}},
        {{1,1}, {1,2}, {1,0}, {2,0}},
        {{1,1}, {0,1}, {2,1}, {0,0}},
        {{1,1}, {1,2}, {1,0}, {0,2}}
    }),

    J(3, new int[][][]{
        {{1,1}, {0,1}, {2,1}, {0,2}},
        {{1,1}, {1,0}, {1,2}, {2,2}},
        {{1,1}, {0,1}, {2,1}, {2,0}},
        {{1,1}, {1,0}, {1,2}, {0,0}}
    })
    ;

    // 회전 상태 개수
    public static final int ROTATION_COUNT = 4;

    // 테트로미노 칸 수
    public static final int CELL_COUNT = 4;

    private final int size;
    private final List<List<Cell>> rotations;

    // 테트로미노 생성자
    Tetromino(int size, int[][][] states){
        // 회전 상태 개수 검사
        if(states.length != ROTATION_COUNT){
            throw new IllegalArgumentException(name() + ": 회전 상태는 4개여야 함");
        }

        this.size = size;
        List<List<Cell>> list = new ArrayList<>();

        for(int[][] state : states){
            list.add(toCells(size, state));
        }

        this.rotations = List.copyOf(list);
    }

    // 모양을 담는 정사각형의 한 변의 길이
    public int size(){
        return size;
    }

    // 블럭 회전 상태에 맞는 칸들의 좌표 반환
    public List<Cell> cells(int rotation){
        return rotations.get(Math.floorMod(rotation, ROTATION_COUNT));
    }

    // 좌표 숫자 배열을 cell list로 바꾸는 과정 + 변환 검사
    private List<Cell> toCells(int size, int[][] coords){
            if(coords.length != CELL_COUNT){
                throw new IllegalArgumentException(name() + ": tetromino는 4칸이여야 함");
            }

            List<Cell> cells = new ArrayList<>();
            for(int[] p : coords){
                Cell cell = new Cell(size - 1 - p[1], p[0]);
                
                if(cell.row() < 0 || cell.row() >= size || cell.col() < 0 || cell.col() >= size){
                    throw new IllegalArgumentException(name() + ": cell이 size를 벗어남");
                }

                cells.add(cell);
            }

            if(new HashSet<>(cells).size() != CELL_COUNT){
                throw new IllegalArgumentException(name() + ": 중복된 좌표가 존재함" + cells);
            }

            return List.copyOf(cells);
        }
}