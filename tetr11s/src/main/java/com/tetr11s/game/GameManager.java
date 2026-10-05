package com.tetr11s.game;

import java.util.function.Supplier;

import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.SevenBagGenerator;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.tetromino.WallKick;

/**
 * 테트리스 한 판의 규칙을 담당
 * 이동, 회전(SRS 벽 킥), 한 칸 낙하, 하드 드롭, 고정, 다음 블럭 생성, 게임오버 판정을 함
 */
public class GameManager {

    private final Board board;
    private final Supplier<Tetromino> nextType;

    private Block current;
    private Tetromino next;
    private boolean gameOver;

    // 기본 게임: 10x22 보드(버퍼 행 2개 포함), 7-bag 생성
    public GameManager() {
        this(new Board(), new SevenBagGenerator());
    }

    /**
     * @param board    블럭이 쌓일 보드
     * @param nextType 다음 블럭 종류를 하나씩 꺼내 주는 함수 (게임에서는 SevenBagGenerator, 테스트에서는 정해진 순서)
     */
    public GameManager(Board board, Supplier<Tetromino> nextType) {
        this.board = board;
        this.nextType = nextType;
    }

    // 새 게임 시작: 보드를 비우고 첫 블럭을 생성
    public void start() {
        board.clear();
        gameOver = false;
        next = nextType.get();
        spawnNext();
    }

    // ----- 조작 -----

    public boolean moveLeft() {
        return tryMove(0, -1);
    }

    public boolean moveRight() {
        return tryMove(0, 1);
    }

    /** 한 칸 아래로. 막혀 있으면 아무 일도 하지 않는다 (고정은 tick이 담당). */
    public boolean softDrop() {
        return tryMove(1, 0);
    }

    /** 시계 방향으로 90도 회전. 막혀 있으면 SRS 벽 킥을 차례로 시도한다. */
    public boolean rotateClockwise() {
        if (gameOver) {
            return false;
        }

        Block rotated = current.rotate();
        for (Cell kick : WallKick.offsets(current.type(), current.rotation())) {
            Block candidate = rotated.move(kick.row(), kick.col());
            if (board.canPlace(candidate)) {
                current = candidate;
                return true;
            }
        }
        return false;
    }

    /**
     * 바닥까지 한 번에 떨어뜨리고 고정한다.
     *
     * @return 떨어진 칸 수 (게임오버 상태면 0)
     */
    public int hardDrop() {
        if (gameOver) {
            return 0;
        }
        int distance = 0;
        while (tryMove(1, 0)) {
            distance++;
        }
        lockAndSpawn();
        return distance;
    }

    /**
     * 시간이 한 칸만큼 흘렀을 때 호출한다 (자동 낙하).
     * 내려갈 수 있으면 한 칸 내리고, 막혀 있으면 고정 후 다음 블럭을 생성한다.
     *
     * @return 한 칸 내려갔으면 true, 고정됐으면 false
     */
    public boolean tick() {
        if (gameOver) {
            return false;
        }
        if (tryMove(1, 0)) {
            return true;
        }
        lockAndSpawn();
        return false;
    }

    // ----- 상태 조회 (화면, 테스트용) -----

    public Board getBoard() {
        return board;
    }

    public Block getCurrent() {
        return current;
    }

    public Tetromino getNext() {
        return next;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    // 현재 블럭이 떨어질 위치 (고스트 블럭)
    public Block getGhost() {
        Block ghost = current;
        while (board.canPlace(ghost.move(1, 0))) {
            ghost = ghost.move(1, 0);
        }
        return ghost;
    }

    // ----- 내부 처리 -----

    private boolean tryMove(int dRow, int dCol) {
        if (gameOver) {
            return false;
        }
        Block candidate = current.move(dRow, dCol);
        if (board.canPlace(candidate)) {
            current = candidate;
            return true;
        }
        return false;
    }

    private void lockAndSpawn() {
        board.lock(current);

        // 락 아웃: 블럭 전체가 보이지 않는 버퍼 영역에 고정되면 게임오버
        boolean allInBuffer = current.cells().stream()
                .allMatch(c -> c.row() < Board.BUFFER_ROWS);
        if (allInBuffer) {
            gameOver = true;
            return;
        }

        board.clearFullLines();
        spawnNext();
    }

    private void spawnNext() {
        current = Block.spawn(next, board.width());
        next = nextType.get();

        // 블럭 아웃: 생성 위치가 이미 막혀 있으면 게임오버
        if (!board.canPlace(current)) {
            gameOver = true;
        }
    }
}