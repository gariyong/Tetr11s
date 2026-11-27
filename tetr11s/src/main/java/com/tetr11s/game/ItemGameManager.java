package com.tetr11s.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.RouletteWheelGenerator;
import com.tetr11s.game.tetromino.Tetromino;

/**
 * 일반 낙하 규칙을 공유하면서, 아이템(L, W, H, O, M) 생성·효과를 처리한다.
 */
public class ItemGameManager extends GameManager {
    private static final List<Cell> METEOR_SHAPE = List.of(new Cell(0, 0));
    private static final List<Cell> WEIGHT_SHAPE = List.of(
            new Cell(0, 1), new Cell(0, 2),
            new Cell(1, 0), new Cell(1, 1),
            new Cell(1, 2), new Cell(1, 3));
    private static final int WATER_FILL_COUNT = 5;
    private static final long WATER_STEP_DELAY_MS = 90L;
    public static final long WATER_RISE_DURATION_MS = 260L;
    public static final long METEOR_SCATTER_DURATION_MS = 320L;
    public static final long EXPLOSION_DURATION_MS = 280L;
    public static final long SCREEN_SHAKE_DURATION_MS = 360L;

    /** 메테오 충돌 후 대각선으로 비산하는 파편 연출용 정보 (H/O는 아이템 표시까지 유지) */
    public record MeteorFragment(Cell from, Cell peak, Cell to, Tetromino type, Item item, long startMs) {
    }

    /** 수소폭탄(H2) 3x3 폭발 연출용 정보 */
    public record ExplosionEffect(int row, int col, long startMs) {
    }

    private final Random random = new Random();
    private Item pendingItem;
    private List<Integer> pendingItemIndices = List.of();
    private Item currentItem;
    private List<Integer> currentItemIndices = List.of();
    // 홀드 칸 블럭에 붙은 아이템과 그 칸 번호 (아이템이 없으면 null / 빈 목록)
    private Item heldItem;
    private List<Integer> heldItemIndices = List.of();
    // hold() 도중 홀드 칸에서 꺼내는 블럭의 아이템 (spawnFromHold에서 쓴다)
    private Item itemFromHold;
    private List<Integer> itemIndicesFromHold = List.of();
    private int linesAtLastItem;
    private final boolean bugMode;
    private int locksSinceItem;
    private boolean weightCrushing;
    private final List<MeteorFragment> meteorFragments = new ArrayList<>();
    private final List<ExplosionEffect> explosions = new ArrayList<>();
    private long itemEffectUntilMs;
    private long shakeStartMs;
    private double shakeIntensity;
    private int nextItemGroupId = 1;

    public ItemGameManager() {
        this(Difficulty.DEFAULT);
    }

    public ItemGameManager(Difficulty difficulty) {
        this(difficulty, false);
    }

    public ItemGameManager(Difficulty difficulty, boolean bugMode) {
        this(difficulty, bugMode,
                new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(difficulty.iBlockWeight())));
    }

    /**
     * 블럭 생성기를 직접 정한다.
     * 대전 모드에서 두 플레이어에게 같은 블럭 순서를 주려고 쓴다 (같은 seed의 생성기).
     */
    public ItemGameManager(Difficulty difficulty, boolean bugMode, Supplier<Tetromino> nextType) {
        super(new Board(), nextType, difficulty, true);
        this.bugMode = bugMode;
    }

    @Override
    public void start() {
        pendingItem = null;
        pendingItemIndices = List.of();
        currentItem = null;
        currentItemIndices = List.of();
        heldItem = null;
        heldItemIndices = List.of();
        itemFromHold = null;
        itemIndicesFromHold = List.of();
        linesAtLastItem = 0;
        locksSinceItem = 0;
        weightCrushing = false;
        meteorFragments.clear();
        explosions.clear();
        itemEffectUntilMs = 0L;
        shakeStartMs = 0L;
        shakeIntensity = 0.0;
        nextItemGroupId = 1;
        super.start();
    }

    // ----- 무게추(W) 전용 조작 오버라이드 -----

    @Override
    public boolean moveLeft() {
        if (currentItem == Item.W && weightCrushing) {
            return false;
        }
        return super.moveLeft();
    }

    @Override
    public boolean moveRight() {
        if (currentItem == Item.W && weightCrushing) {
            return false;
        }
        return super.moveRight();
    }

    @Override
    public boolean softDrop() {
        if (currentItem == Item.W) {
            if (!canAct()) {
                return false;
            }
            return stepWeightDown(); // 무게추는 점수 없이 하강
        }
        return super.softDrop();
    }

    @Override
    public int hardDrop() {
        if (currentItem == Item.W) {
            if (!canAct()) {
                return 0;
            }
            int distance = 0;
            while (stepWeightDown()) {
                distance++;
            }
            // 무게추는 지나가는 행을 점수 없이 밀어버림 (score.addDrop 호출 안 함)
            lockAndSpawn();
            return distance;
        }
        return super.hardDrop();
    }

    @Override
    public boolean tick() {
        if (currentItem == Item.W) {
            if (!canAct()) {
                return false;
            }
            if (stepWeightDown()) {
                return true; // 무게추는 점수 없이 하강
            }
            lockAndSpawn();
            return false;
        }
        return super.tick();
    }

    @Override
    public Block getGhost() {
        if (current == null) {
            return null;
        }
        if (currentItem == Item.W) {
            Block ghost = current;
            while (isWithinBottom(ghost.move(1, 0))) {
                ghost = ghost.move(1, 0);
            }
            return ghost;
        }
        return super.getGhost();
    }

    // ----- 홀드: 아이템도 블럭과 함께 홀드 칸에 들어갔다가 그대로 다시 나온다 -----

    @Override
    public boolean hold() {
        if (!canHold()) {
            return false;
        }
        // 꺼낼 블럭의 아이템과 넣을 블럭의 아이템을 맞바꾼다
        itemFromHold = heldItem;
        itemIndicesFromHold = heldItemIndices;
        heldItem = currentItem;
        heldItemIndices = currentItemIndices;
        return super.hold();
    }

    /** 무게추가 이미 블럭을 부수기 시작했으면 홀드할 수 없다 (부순 칸을 되돌릴 수 없으므로) */
    @Override
    public boolean canHold() {
        if (currentItem == Item.W && weightCrushing) {
            return false;
        }
        return super.canHold();
    }

    @Override
    protected void spawnFromHold(Tetromino type) {
        weightCrushing = false;
        if (itemFromHold == Item.M) {
            current = spawnMeteor();
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else if (itemFromHold == Item.W) {
            current = Block.special(WEIGHT_SHAPE, board.width());
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else {
            super.spawnFromHold(type);
        }
        // 같은 블럭 모양이 회전 0으로 다시 나오므로 아이템이 붙은 칸 번호도 그대로 쓸 수 있다
        currentItem = itemFromHold;
        currentItemIndices = itemIndicesFromHold;
        itemFromHold = null;
        itemIndicesFromHold = List.of();
    }

    private boolean stepWeightDown() {
        Block candidate = current.move(1, 0);
        if (!isWithinBottom(candidate)) {
            return false;
        }
        for (Cell c : candidate.cells()) {
            if (board.isInside(c) && board.get(c.row(), c.col()) != null) {
                weightCrushing = true;
                board.clearCell(c.row(), c.col());
            }
        }
        current = candidate;
        return true;
    }

    private boolean isWithinBottom(Block block) {
        return block.cells().stream().allMatch(c -> c.row() < board.height());
    }

    // ----- 스폰 / 고정 / 아이템 효과 -----

    @Override
    protected void spawnNext() {
        weightCrushing = false;
        Item itemToSpawn = pendingItem;
        List<Integer> indicesToSpawn = pendingItemIndices;
        pendingItem = null;
        pendingItemIndices = List.of();

        if (itemToSpawn == Item.M) {
            // 1x1 단일 셀 메테오는 버퍼 행(0~1)에서 시작하면 2틱 동안 숨어 있으므로,
            // 보이는 최상단 행(Board.BUFFER_ROWS)부터 배치해 스폰 즉시 화면에 나타나게 한다.
            current = spawnMeteor();
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else if (itemToSpawn == Item.W) {
            current = Block.special(WEIGHT_SHAPE, board.width());
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else {
            super.spawnNext();
        }
        currentItem = itemToSpawn;
        if (currentItem == null) {
            currentItemIndices = List.of();
        } else if (currentItem == Item.M || currentItem == Item.W || indicesToSpawn.isEmpty()) {
            currentItemIndices = itemIndices(current, currentItem);
        } else {
            currentItemIndices = indicesToSpawn;
        }
        if (itemToSpawn != null) {
            locksSinceItem = 0;
        }

        // 다음 턴(NEXT 미리보기)에 대기할 아이템을 스폰 직후 예약해
        // 현재 블록을 조작하는 동안 NEXT 창에 대기 중인 아이템이 표시되도록 한다.
        scheduleNextPreviewItem();
    }

    private Block spawnMeteor() {
        Block base = Block.special(METEOR_SHAPE, board.width());
        for (int row = Board.BUFFER_ROWS; row >= 0; row--) {
            Block candidate = base.move(row, 0);
            if (board.canPlace(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    @Override
    public void finishLineClear() {
        if (!isClearing()) {
            return;
        }
        super.finishLineClear();
    }

    @Override
    protected boolean handlePostLineClear() {
        List<Cell> filledWater = resolveBoardChemistry();
        List<Integer> full = board.fullRows();
        if (full.isEmpty()) {
            return false;
        }
        startAttackRecord(filledWater);
        recordClearedRows(full);
        sendAttack();
        score.addLineClear(full.size());
        clearingRows = full;
        current = null;
        return true;
    }

    /**
     * 줄 삭제 후 위 블록이 내려오면서 보드 위에서 새로 완성된 H2 / H2O 반응을 처리한다.
     * 반응으로 채워진 물 셀 목록을 반환한다.
     */
    private List<Cell> resolveBoardChemistry() {
        List<Cell> filledWater = new ArrayList<>();
        // 1) 새로 인접하게 된 서로 다른 블록 소속 H2 폭발 검사 (8방향)
        List<Cell> exploding = new ArrayList<>();
        for (int r = 0; r < board.height(); r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.getItem(r, c) == Item.H && touchesOtherHydrogen(new Cell(r, c))) {
                    exploding.add(new Cell(r, c));
                }
            }
        }
        for (Cell center : exploding) {
            if (board.getItem(center.row(), center.col()) == Item.H) {
                explode(center.row(), center.col());
            }
        }

        // 2) 새로 H 2개와 인접하게 된 O (H2O) 물 반응 검사
        for (int r = 0; r < board.height(); r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.getItem(r, c) == Item.O) {
                    filledWater.addAll(tryReactOxygen(new Cell(r, c)));
                }
            }
        }
        return filledWater;
    }

    @Override
    protected void lockAndSpawn() {
        if (current == null) {
            return;
        }
        if (bugMode) {
            locksSinceItem++;
        }
        if (currentItem == Item.M) {
            // 메테오는 보드에 쌓이는 블록이 아니라 충돌 즉시 파괴·비산 후 소멸하므로
            // board.lock(current)을 거치지 않고 전용 충돌 경로로 처리한다.
            Block meteor = current;
            List<Cell> scattered = handleMeteorLocked(meteor);
            List<Integer> full = board.fullRows();
            startAttackRecord(scattered);
            recordClearedRows(full);
            sendAttack();
            if (full.isEmpty()) {
                addGarbageThenSpawn();
            } else {
                score.addLineClear(full.size());
                clearingRows = full;
                current = null;
            }
            return;
        }
        if (currentItem != null) {
            inject(current, currentItem);
        }
        super.lockAndSpawn();
    }

    @Override
    protected List<Integer> extraRowsToClear(Block locked) {
        if (currentItem != Item.L) {
            return List.of(); // L 아이템이 아니면 추가로 지울 줄 없음 (빈 리스트 반환)
        }
        List<Integer> rows = new ArrayList<>();
        for (Cell cell : getItemCells(locked)) {
            if (!rows.contains(cell.row())) {
                rows.add(cell.row());
            }
        }
        return rows;
    }

    @Override
    protected void onBlockLocked(Block locked) {
        // Item.W는 stepWeightDown()으로 바닥까지 밀어낸 뒤,
        // lockAndSpawn()의 inject + board.lock(current)로 6칸 무게추 모양과 W 표시가 바닥에 그대로 남는다.
        // Item.M은 lockAndSpawn()에서 board.lock 없이 전용 충돌 경로로 처리된다.
        if (currentItem == Item.H) {
            handleHydrogenLocked(locked);
        } else if (currentItem == Item.O) {
            handleOxygenLocked(locked);
        }
    }

    // ----- 3. 수소(H) 처리 (대각선 포함 8방향 접촉) -----

    private void handleHydrogenLocked(Block locked) {
        Set<Cell> newHCells = new LinkedHashSet<>(getItemCells(locked));
        List<Cell> exploding = new ArrayList<>();

        for (Cell hCell : newHCells) {
            if (touchesOtherHydrogen(hCell)) {
                exploding.add(hCell);
            }
        }
        if (!exploding.isEmpty()) {
            for (Cell center : exploding) {
                explode(center.row(), center.col());
            }
            return;
        }

        // 폭발하지 않았을 때, 새로 놓인 H 주변의 기존 O 셀이 H 2개와 인접하게 되었으면 물 반응 발동
        Set<Cell> checkedO = new HashSet<>();
        for (Cell hCell : newHCells) {
            for (int r = hCell.row() - 1; r <= hCell.row() + 1; r++) {
                for (int c = hCell.col() - 1; c <= hCell.col() + 1; c++) {
                    Cell neighbor = new Cell(r, c);
                    if (board.isInside(neighbor)
                            && board.getItem(r, c) == Item.O
                            && checkedO.add(neighbor)) {
                        tryReactOxygen(neighbor);
                    }
                }
            }
        }
    }

    /** 대각선 포함 8방향에 같은 블록(itemGroup)이 아닌 다른 H 셀이 있는지 검사 */
    private boolean touchesOtherHydrogen(Cell hCell) {
        int myGroup = board.getItemGroup(hCell.row(), hCell.col());
        for (int r = hCell.row() - 1; r <= hCell.row() + 1; r++) {
            for (int c = hCell.col() - 1; c <= hCell.col() + 1; c++) {
                if (r == hCell.row() && c == hCell.col()) {
                    continue;
                }
                Cell neighbor = new Cell(r, c);
                if (board.isInside(neighbor) && board.getItem(r, c) == Item.H) {
                    int otherGroup = board.getItemGroup(r, c);
                    if (myGroup == 0 || otherGroup == 0 || myGroup != otherGroup) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void explode(int row, int col) {
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                board.clearCell(r, c);
            }
        }
        long now = System.currentTimeMillis();
        explosions.add(new ExplosionEffect(row, col, now));
        extendItemEffect(now + EXPLOSION_DURATION_MS);
    }

    // ----- 4. 산소(O) 처리 (물 차오름) -----

    private void handleOxygenLocked(Block locked) {
        for (Cell oCell : getItemCells(locked)) {
            if (board.isInside(oCell) && board.getItem(oCell.row(), oCell.col()) == Item.O) {
                tryReactOxygen(oCell);
            }
        }
    }

    private List<Cell> tryReactOxygen(Cell oCell) {
        List<Cell> adjacentHydrogens = findAdjacentHydrogens(oCell);
        if (adjacentHydrogens.size() < 2) {
            return List.of();
        }
        // 반응에 사용된 O와 H 2개의 아이템 속성을 소모 (무한 재반응 방지)
        board.setItem(oCell.row(), oCell.col(), null);
        for (int i = 0; i < 2; i++) {
            Cell h = adjacentHydrogens.get(i);
            board.setItem(h.row(), h.col(), null);
        }
        return fillWaterDownward(oCell);
    }

    private List<Cell> findAdjacentHydrogens(Cell origin) {
        List<Cell> hydrogens = new ArrayList<>();
        for (int r = origin.row() - 1; r <= origin.row() + 1; r++) {
            for (int c = origin.col() - 1; c <= origin.col() + 1; c++) {
                if (r == origin.row() && c == origin.col()) {
                    continue;
                }
                Cell target = new Cell(r, c);
                if (board.isInside(target) && board.getItem(r, c) == Item.H) {
                    hydrogens.add(target);
                }
            }
        }
        return hydrogens;
    }

    private List<Cell> fillWaterDownward(Cell origin) {
        List<Cell> candidates = findFlowReachableEmptyCells(origin);
        if (candidates.isEmpty()) {
            for (int r = origin.row(); r < board.height(); r++) {
                for (int c = 0; c < board.width(); c++) {
                    if (board.get(r, c) == null) {
                        candidates.add(new Cell(r, c));
                    }
                }
            }
        }

        candidates.sort((left, right) -> {
            int rowCmp = Integer.compare(right.row(), left.row()); // 아래 행부터 차오름
            if (rowCmp != 0) {
                return rowCmp;
            }
            int distCmp = Integer.compare(Math.abs(left.col() - origin.col()), Math.abs(right.col() - origin.col()));
            if (distCmp != 0) {
                return distCmp;
            }
            return Integer.compare(left.col(), right.col());
        });

        List<Cell> filled = new ArrayList<>();
        long now = System.currentTimeMillis();
        int count = Math.min(WATER_FILL_COUNT, candidates.size());
        for (int i = 0; i < count; i++) {
            Cell cell = candidates.get(i);
            board.fillWaterCell(cell.row(), cell.col(), Tetromino.I, now + i * WATER_STEP_DELAY_MS);
            filled.add(cell);
        }
        if (count > 0) {
            extendItemEffect(now + (count - 1) * WATER_STEP_DELAY_MS + WATER_RISE_DURATION_MS);
        }
        return filled;
    }

    private List<Cell> findFlowReachableEmptyCells(Cell origin) {
        int[][] flowDirs = {{1, 0}, {0, -1}, {0, 1}}; // 아래, 좌, 우로만 흐름
        Set<Cell> visited = new HashSet<>();
        Deque<Cell> queue = new ArrayDeque<>();
        List<Cell> reachable = new ArrayList<>();

        for (int[] d : flowDirs) {
            Cell start = new Cell(origin.row() + d[0], origin.col() + d[1]);
            if (board.isInside(start) && board.get(start.row(), start.col()) == null && visited.add(start)) {
                queue.add(start);
            }
        }

        while (!queue.isEmpty()) {
            Cell cur = queue.poll();
            reachable.add(cur);
            for (int[] d : flowDirs) {
                Cell next = new Cell(cur.row() + d[0], cur.col() + d[1]);
                if (board.isInside(next) && board.get(next.row(), next.col()) == null && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reachable;
    }

    // ----- 5. 메테오(M) 처리 (3x3 파괴 + 좌우 옆 라인 비산 + H/O 무조건 생존 비산 + 화면 흔들림) -----

    private record ScatteredCell(Cell source, Tetromino type, Item item, int itemGroup, int pushDir) {
    }

    private List<Cell> handleMeteorLocked(Block locked) {
        List<Cell> meteorCells = getItemCells(locked);
        Cell center = meteorCells.isEmpty() ? locked.cells().get(0) : meteorCells.get(0);
        long now = System.currentTimeMillis();
        List<ScatteredCell> cellsToScatter = new ArrayList<>();

        // 1. 파괴존 (중심 3x3 박스): 일반 셀은 완전 파괴, H/O 셀은 무조건 살려서 비산 목록에 보존
        for (Cell meteor : meteorCells) {
            for (int r = meteor.row() - 1; r <= meteor.row() + 1; r++) {
                for (int c = meteor.col() - 1; c <= meteor.col() + 1; c++) {
                    Cell target = new Cell(r, c);
                    if (!board.isInside(target)) {
                        continue;
                    }
                    if (!(r == meteor.row() && c == meteor.col()) && board.get(r, c) != null) {
                        Item itemOnBoard = board.getItem(r, c);
                        if (itemOnBoard == Item.H || itemOnBoard == Item.O) {
                            int pushDir = c < center.col() ? -1 : (c > center.col() ? 1 : (random.nextBoolean() ? -1 : 1));
                            cellsToScatter.add(new ScatteredCell(
                                    target, board.get(r, c), itemOnBoard, board.getItemGroup(r, c), pushDir));
                        }
                    }
                    board.clearCell(r, c);
                }
            }
        }

        // 2. 비산존 (3x3 박스의 바로 좌우 옆 라인): 일반 셀과 H/O 셀 모두 바깥쪽 대각선 비산 대상에 추가
        int[] sideCols = {center.col() - 2, center.col() + 2};
        for (int sideCol : sideCols) {
            int pushDir = sideCol < center.col() ? -1 : 1;
            for (int r = center.row() - 1; r <= center.row() + 1; r++) {
                Cell target = new Cell(r, sideCol);
                if (!board.isInside(target) || board.get(r, sideCol) == null) {
                    continue;
                }
                Item itemOnBoard = board.getItem(r, sideCol);
                Tetromino blockType = board.get(r, sideCol);
                int group = board.getItemGroup(r, sideCol);
                board.clearCell(r, sideCol);
                cellsToScatter.add(new ScatteredCell(target, blockType, itemOnBoard, group, pushDir));
            }
        }

        // 3. 비산 대상 셀들을 좌/우 대각선으로 날려 보내고 중력 낙하 (H/O는 빈 칸이 있으면 무조건 생존 안착)
        List<Cell> landedCells = new ArrayList<>();
        for (ScatteredCell cell : cellsToScatter) {
            Cell dest = scatterCellAndFall(cell, now);
            if (dest != null) {
                landedCells.add(dest);
            }
        }

        // 4. 비산되어 착지한 H/O가 다른 H/O와 새로 접했으면 연쇄 화학 반응 처리
        landedCells.addAll(resolveBoardChemistry());

        // 5. 메테오 충격 화면 흔들림 발동 (강도 11px)
        triggerScreenShake(11.0, now);
        return landedCells;
    }

    /** 셀을 바깥쪽 대각선으로 튕겨낸 뒤 중력으로 바닥까지 낙하시킨다. H/O는 무조건 생존하도록 전체 빈칸 폴백을 가진다. */
    private Cell scatterCellAndFall(ScatteredCell cell, long nowMs) {
        int[] colDirs = {cell.pushDir(), -cell.pushDir()};
        int[] rowDirs = {-1, 0, 1}; // 대각선 위 우선 -> 수평 -> 대각선 아래
        int[] dists = {2, 1, 3};

        for (int colDir : colDirs) {
            for (int rowDir : rowDirs) {
                for (int dist : dists) {
                    int peakRow = Math.max(Board.BUFFER_ROWS, cell.source().row() + rowDir * dist);
                    int destCol = cell.source().col() + colDir * dist;
                    Cell peak = new Cell(peakRow, destCol);
                    if (!board.isInside(peak) || board.get(peakRow, destCol) != null) {
                        continue;
                    }
                    return landScatteredCell(cell, peak, nowMs);
                }
            }
        }

        // H, O는 무조건 살린 채 비산: 기본 대각선 자리가 모두 찼다면 보드 내 가장 가까운 빈 칸으로 대피 착지
        if (cell.item() == Item.H || cell.item() == Item.O) {
            Cell fallbackPeak = findNearestEmptyCell(cell.source());
            if (fallbackPeak != null) {
                return landScatteredCell(cell, fallbackPeak, nowMs);
            }
        }
        return null;
    }

    private Cell landScatteredCell(ScatteredCell cell, Cell peak, long nowMs) {
        int landingRow = peak.row();
        while (landingRow + 1 < board.height() && board.get(landingRow + 1, peak.col()) == null) {
            landingRow++;
        }
        Cell dest = new Cell(landingRow, peak.col());
        board.fillCell(landingRow, peak.col(), cell.type(), null);
        if (cell.item() != null) {
            board.setItem(landingRow, peak.col(), cell.item(), cell.itemGroup());
        }
        meteorFragments.add(new MeteorFragment(cell.source(), peak, dest, cell.type(), cell.item(), nowMs));
        extendItemEffect(nowMs + METEOR_SCATTER_DURATION_MS);
        return dest;
    }

    private Cell findNearestEmptyCell(Cell origin) {
        Cell best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int r = Board.BUFFER_ROWS; r < board.height(); r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.get(r, c) == null) {
                    int dist = Math.abs(r - origin.row()) + Math.abs(c - origin.col());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new Cell(r, c);
                    }
                }
            }
        }
        return best;
    }

    private void triggerScreenShake(double intensity, long nowMs) {
        this.shakeStartMs = nowMs;
        this.shakeIntensity = Math.max(this.shakeIntensity, intensity);
    }

    /** 현재 시각 기준 화면 흔들림 오프셋(dx, dy)을 반환한다. 종료 시 (0, 0)으로 리셋된다. */
    public double[] getShakeOffset(long nowMs) {
        if (shakeStartMs == 0L) {
            return new double[] {0.0, 0.0};
        }
        long elapsed = nowMs - shakeStartMs;
        if (elapsed >= SCREEN_SHAKE_DURATION_MS) {
            shakeStartMs = 0L;
            shakeIntensity = 0.0;
            return new double[] {0.0, 0.0};
        }
        double decay = 1.0 - (double) elapsed / SCREEN_SHAKE_DURATION_MS;
        double dx = Math.sin(elapsed * 0.095) * shakeIntensity * decay;
        double dy = Math.cos(elapsed * 0.125) * shakeIntensity * decay * 0.75;
        return new double[] {dx, dy};
    }

    private void extendItemEffect(long untilMs) {
        if (untilMs > itemEffectUntilMs) {
            itemEffectUntilMs = untilMs;
        }
    }

    /** 아이템 효과(물 차오름, 수소폭탄 폭발, 메테오 비산)가 아직 화면에 재생 중인지 여부 */
    public boolean isItemEffectAnimating(long nowMs) {
        return nowMs < itemEffectUntilMs;
    }

    /** 현재 진행 중인 메테오 비산 연출 파편 목록 반환 (끝난 항목은 자동 정리) */
    public List<MeteorFragment> getActiveMeteorFragments(long nowMs) {
        meteorFragments.removeIf(f -> nowMs - f.startMs() >= METEOR_SCATTER_DURATION_MS);
        return List.copyOf(meteorFragments);
    }

    /** 현재 진행 중인 수소폭탄 폭발 연출 목록 반환 (끝난 항목은 자동 정리) */
    public List<ExplosionEffect> getActiveExplosions(long nowMs) {
        explosions.removeIf(e -> nowMs - e.startMs() >= EXPLOSION_DURATION_MS);
        return List.copyOf(explosions);
    }

    /** 물 블록이나 아이템 연출/화면 흔들림이 있어 화면을 계속 갱신해야 하는지 여부 */
    public boolean hasActiveVisualEffects() {
        long now = System.currentTimeMillis();
        meteorFragments.removeIf(f -> now - f.startMs() >= METEOR_SCATTER_DURATION_MS);
        explosions.removeIf(e -> now - e.startMs() >= EXPLOSION_DURATION_MS);
        return !meteorFragments.isEmpty() || !explosions.isEmpty()
                || shakeStartMs > 0L || now < itemEffectUntilMs || board.hasWater();
    }

    // ----- 상태 조회 및 유틸 -----

    public Item getCurrentItem() {
        return currentItem;
    }

    public List<Cell> getCurrentItemCells() {
        if (current == null) {
            return List.of();
        }
        return getItemCells(current);
    }

    @Override
    public Item getNextItemType() {
        return pendingItem;
    }

    @Override
    public Item getHeldItemType() {
        return heldItem;
    }

    /** 홀드 칸 블럭에서 아이템 표시가 붙을 셀 인덱스 목록 */
    public List<Integer> getHeldItemIndices() {
        return heldItemIndices;
    }

    /** NEXT 미리보기 블록에서 아이템 표시가 붙을 셀 인덱스 목록 */
    public List<Integer> getNextItemIndices() {
        return pendingItemIndices;
    }

    /** 테스트용: 난수 시드 고정 및 다음 아이템 지정 */
    void setRandomSeed(long seed) {
        random.setSeed(seed);
    }

    void forceNextItem(Item item) {
        queuePendingItem(item);
    }

    private List<Cell> getItemCells(Block block) {
        List<Cell> cells = block.cells();
        List<Cell> selected = new ArrayList<>();
        for (int index : currentItemIndices) {
            if (index >= 0 && index < cells.size()) {
                selected.add(cells.get(index));
            }
        }
        return List.copyOf(selected);
    }

    private void inject(Block block, Item item) {
        int groupId = nextItemGroupId++;
        for (Cell target : getItemCells(block)) {
            if (board.isInside(target)) {
                board.setItem(target.row(), target.col(), item, groupId);
            }
        }
    }

    private List<Integer> itemIndices(Block block, Item item) {
        return itemIndicesForSize(block.cells().size(), item);
    }

    private List<Integer> itemIndicesForSize(int size, Item item) {
        List<Integer> selected = new ArrayList<>();
        selected.add(random.nextInt(size));
        if (item == Item.W) {
            selected.clear();
            for (int index = 0; index < size; index++) {
                selected.add(index);
            }
        } else if (item == Item.H || item == Item.O) {
            for (int index = 0; index < size; index++) {
                if (!selected.contains(index) && random.nextDouble() < 0.15) {
                    selected.add(index);
                }
            }
        }
        return List.copyOf(selected);
    }

    private Item randomItem() {
        Item[] values = Item.values();
        return values[random.nextInt(values.length)];
    }

    private void queuePendingItem(Item item) {
        pendingItem = item;
        if (item == null || item == Item.M || item == Item.W || next == null) {
            pendingItemIndices = List.of();
        } else {
            pendingItemIndices = itemIndicesForSize(next.cells(0).size(), item);
        }
    }

    private void scheduleNextPreviewItem() {
        if (pendingItem != null) {
            return;
        }
        if (bugMode) {
            if (locksSinceItem >= 4) {
                queuePendingItem(randomItem());
                locksSinceItem = 0;
            }
        } else if (getLines() / 10 > linesAtLastItem / 10) {
            queuePendingItem(randomItem());
            linesAtLastItem = getLines();
        }
    }
}
