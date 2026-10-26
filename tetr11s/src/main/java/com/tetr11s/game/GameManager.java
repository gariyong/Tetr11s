package com.tetr11s.game;

import java.util.List;
import java.util.function.Supplier;

import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.RouletteWheelGenerator;
import com.tetr11s.game.tetromino.Tetromino;
import com.tetr11s.game.tetromino.WallKick;
import com.tetr11s.game.item.Item;

/**
 * 테트리스 한 판의 규칙을 담당
 * 이동, 회전(SRS 벽 킥), 한 칸 낙하, 하드 드롭, 고정, 다음 블럭 생성, 게임오버 판정을 함
 */
public class GameManager {

    protected final boolean mode;
    protected final Board board;
    protected final Supplier<Tetromino> nextType;
    protected final Difficulty difficulty;
    protected final ScoreManager score;

    protected Block current;
    protected Tetromino next;
    protected boolean gameOver;
    protected boolean paused;
    // 지금 지우는 중인 줄 (비어 있으면 줄 삭제 중이 아님)
    protected List<Integer> clearingRows = List.of();

    // 기본 게임: Normal 난이도
    public GameManager() {
        this(Difficulty.DEFAULT);
    }

    /**
     * 난이도에 맞춘 게임: 10x22 보드(버퍼 행 2개 포함), 룰렛 휠 블럭 생성
     * 난이도가 정하는 것: I 블럭 가중치(블럭 생성), 레벨이 오를 때 빨라지는 정도(ScoreManager)
     */
    public GameManager(Difficulty difficulty) {
        this(new Board(),
                new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(difficulty.iBlockWeight())),
                difficulty, false);
    }

    /**
     * @param board    블럭이 쌓일 보드
     * @param nextType 다음 블럭 종류를 하나씩 꺼내 주는 함수 (게임에서는 RouletteWheelGenerator, 테스트에서는 정해진 순서)
     */
    public GameManager(Board board, Supplier<Tetromino> nextType) {
        this(board, nextType, Difficulty.DEFAULT);
    }

    public GameManager(Board board, Supplier<Tetromino> nextType, Difficulty difficulty) {
        this(board, nextType, difficulty, false);
    }

    protected GameManager(Board board, Supplier<Tetromino> nextType, Difficulty difficulty, boolean itemMode) {
        this.board = board;
        this.nextType = nextType;
        this.difficulty = difficulty;
        this.mode = itemMode;
        this.score = new ScoreManager(difficulty, itemMode);
    }

    // 새 게임 시작: 보드를 비우고 첫 블럭을 생성
    public void start() {
        board.clear();
        score.reset();
        gameOver = false;
        paused = false;
        clearingRows = List.of();
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
        if(tryMove(1, 0)){
            score.addDrop(1);

            return true;
        }

        return false;
    }

    /** 시계 방향으로 90도 회전. 막혀 있으면 SRS 벽 킥을 차례로 시도한다. */
    public boolean rotateClockwise() {
        if (!canAct()) {
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
        if (!canAct()) {
            return 0;
        }
        int distance = 0;
        while (tryMove(1, 0)) {
            distance++;
        }

        score.addDrop(distance);
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
        if (!canAct()) {
            return false;
        }
        if (tryMove(1, 0)) {
            score.addDrop(1);
            return true;
        }
        
        lockAndSpawn();
        return false;
    }

    /**
     * 줄 삭제 연출이 끝났을 때 호출한다.
     * 꽉 찬 줄을 실제로 지우고 다음 블럭을 생성한다.
     * 줄 삭제 중이 아니면 아무 일도 하지 않는다.
     */
    public void finishLineClear() {
        if (!isClearing()) {
            return;
        }
        board.clearFullLines();
        clearingRows = List.of();
        spawnNext();
    }

    // 일시정지 ↔ 재개. 게임오버 상태에서는 아무 일도 하지 않는다.
    public void togglePause() {
        if (gameOver) {
            return;
        }
        paused = !paused;
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

    public Item getNextItemType() {
        return null;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isPaused() {
        return paused;
    }

    // 꽉 찬 줄을 보여 주는 중이라 아직 지우지 않은 상태인가
    public boolean isClearing() {
        return !clearingRows.isEmpty();
    }

    // 지우는 중인 줄의 행 번호 (위에서 아래 순서, 보드 기준 = 버퍼 행 포함)
    public List<Integer> getClearingRows() {
        return clearingRows;
    }

    public int getScore(){
        return score.score();
    }

    public Difficulty getDifficulty(){
        return difficulty;
    }

    public int getLevel(){
        return score.level();
    }

    public int getLines(){
        return score.lines();
    }

    // 현재 레벨의 자동 낙하 속도
    public long getDropIntervalMillis(){
        return score.dropIntervalMillis();
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

    // 게임오버도 일시정지도 아닐 때만 조작할 수 있다.
    private boolean canAct() {
        return !gameOver && !paused && !isClearing();
    }

    private boolean tryMove(int dRow, int dCol) {
        if (!canAct()) {
            return false;
        }

        Block candidate = current.move(dRow, dCol);
        if (board.canPlace(candidate)) {
            current = candidate;
            return true;
        }

        return false;
    }

    protected void lockAndSpawn() {
        board.lock(current);
        onBlockLocked(current);

        // 락 아웃: 블럭 전체가 보이지 않는 버퍼 영역에 고정되면 게임오버
        boolean allInBuffer = current.cells().stream()
                .allMatch(c -> c.row() < Board.BUFFER_ROWS);
        if (allInBuffer) {
            gameOver = true;
            return;
        }

        List<Integer> full = board.fullRows();
        if (full.isEmpty()) {
            spawnNext();
            return;
        }

        // 꽉 찬 줄이 있으면 바로 지우지 않고 '줄 삭제 중' 상태로 멈춘다.
        // 점수는 지금 올리고, 실제 삭제와 다음 블럭 생성은 finishLineClear()에서 한다.
        score.addLineClear(full.size());
        clearingRows = full;
        current = null;   // 방금 블럭은 보드에 고정됐으니 따로 그리지 않는다
    }

    protected void onBlockLocked(Block locked) {
    }

    protected void spawnNext() {
        current = Block.spawn(next, board.width());
        next = nextType.get();

        // 블럭 아웃: 생성 위치가 이미 막혀 있으면 게임오버
        if (!board.canPlace(current)) {
            gameOver = true;
        }
    }
}