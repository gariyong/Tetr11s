package com.tetr11s.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    // 메테오, 무게추 아이템 모양 정의. 아이템 처리중 사용하는 상수 정의.
    private static final List<Cell> METEOR_SHAPE = List.of(new Cell(0, 0));
    private static final List<Cell> WEIGHT_SHAPE = List.of(
            new Cell(0, 1), new Cell(0, 2),
            new Cell(1, 0), new Cell(1, 1),
            new Cell(1, 2), new Cell(1, 3));
    private static final int WATER_FILL_COUNT = 5;
    private static final int MAX_METEOR_SCATTER_CELLS = 3;


    private final Random random;
    private Item pendingItem;
    private Item currentItem;
    private List<Integer> currentItemIndices = List.of();
    private int linesAtLastItem;
    private final boolean bugMode;
    private int locksSinceItem;
    private boolean weightCrushing;

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
        this(new Board(), nextType, difficulty, bugMode, new Random());
    }

     /** 테스트 등에서 보드와 난수를 직접 주입할 수 있는 생성자 */
    ItemGameManager(Board board, Supplier<Tetromino> nextType, Difficulty difficulty, boolean bugMode, Random random) {
        super(board, nextType, difficulty, true);
        this.bugMode = bugMode;
        this.random = random;
    }

    @Override
    public void start() {
        pendingItem = null;
        currentItem = null;
        currentItemIndices = List.of();
        linesAtLastItem = 0;
        locksSinceItem = 0;
        weightCrushing = false;
        super.start();
    }

    // ------ 무게 추 전용 조작 오버라이드 ------
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
    /**
     * 무게추를 한 칸 아래로 내리며 지나가는 자리의 모든 블록·아이템을 점수 없이 지운다.
     * 한 번이라도 다른 블록에 닿으면 weightCrushing이 true가 되어 이후 좌우 이동이 잠긴다.
     * 바닥에 도달하면 false를 반환하고, 이후 lockAndSpawn()에서 6칸 무게추 모양 그대로 바닥에 고정된다.
     */
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
        if (pendingItem == Item.M) {
            current = Block.special(METEOR_SHAPE, board.width());
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else if (pendingItem == Item.W) {
            current = Block.special(WEIGHT_SHAPE, board.width());
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else {
            super.spawnNext();
        }
        currentItem = pendingItem;
        currentItemIndices = currentItem == null ? List.of() : itemIndices(current, currentItem);
        pendingItem = null;
    }

    @Override
    public void finishLineClear() {
        if (!isClearing()) {
            return;
        }
        scheduleItemIfNeeded();
        super.finishLineClear();
    }

    @Override
    protected void lockAndSpawn() {
        if (current != null && currentItem != null) {
            inject(current, currentItem);
        }
        if (bugMode) {
            locksSinceItem++;
            if (locksSinceItem >= 5 && pendingItem == null) {
                pendingItem = randomItem();
                locksSinceItem = 0;
            }
        }
        super.lockAndSpawn();
        scheduleItemIfNeeded();
    }
    @Override
    protected List<Integer> extraRowsToClear(Block locked) {
        if (currentItem != Item.L) {
            return List.of();
        }
        List<Integer> rows = new ArrayList<>();
        for (Cell cell : getItemCells(locked)) {
            if (!rows.contains(cell.row())) {
                rows.add(cell.row());
            }
        }
        return rows;
    }

    // 블록 별 아이템 효과 처리
    @Override
    protected void onBlockLocked(Block locked) {
        // Item.W는 stepWeightDown()으로 바닥까지 밀어낸 뒤,
        // lockAndSpawn()의 inject + board.lock(current)로 6칸 무게추 모양과 W 표시가 바닥에 그대로 남는다.
        if (currentItem == Item.H) {
            handleHydrogenLocked(locked);
        } else if (currentItem == Item.O) {
            handleOxygenLocked(locked);
        } else if (currentItem == Item.M) {
            handleMeteorLocked(locked);
        }
    }
     // ----- 3. 수소(H) 처리 (대각선 포함 8방향 접촉) -----
    private void handleHydrogenLocked(Block locked) {
        Set<Cell> newHCells = new LinkedHashSet<>(getItemCells(locked));
        List<Cell> exploding = new ArrayList<>();
        // 새로 고정된 H 셀이 "보드에 이미 있던 다른 H 셀"과 8방향(대각선 포함)으로 접하면 폭발 대상 등록
        for (Cell hCell : newHCells) {
            if (touchesOtherHydrogen(hCell, newHCells)) {
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
    /** 대각선 포함 8방향에 같은 블록 소속이 아닌 다른 H 셀이 있는지 검사 */
    private boolean touchesOtherHydrogen(Cell hCell, Set<Cell> sameBlockHCells) {
        for (int r = hCell.row() - 1; r <= hCell.row() + 1; r++) {
            for (int c = hCell.col() - 1; c <= hCell.col() + 1; c++) {
                Cell neighbor = new Cell(r, c);
                if (board.isInside(neighbor)
                        && !sameBlockHCells.contains(neighbor)
                        && board.getItem(r, c) == Item.H) {
                    return true;
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
    }
    
    // ----- 4. 산소(O) 처리 -----
    private void handleOxygenLocked(Block locked) {
        for (Cell oCell : getItemCells(locked)) {
            if (board.isInside(oCell) && board.getItem(oCell.row(), oCell.col()) == Item.O) {
                tryReactOxygen(oCell);
            }
        }
    }
    private void tryReactOxygen(Cell oCell) {
        List<Cell> adjacentHydrogens = findAdjacentHydrogens(oCell);
        if (adjacentHydrogens.size() < 2) {
            return;
        }
        // 반응에 사용된 O와 H 2개의 아이템 속성을 소모 (무한 재반응 방지)
        board.setItem(oCell.row(), oCell.col(), null);
        for (int i = 0; i < 2; i++) {
            Cell h = adjacentHydrogens.get(i);
            board.setItem(h.row(), h.col(), null);
        }
        fillWaterDownward(oCell);
    }
    /** 산소 셀 기준 8방향(대각선 허용) 인접한 수소 셀 목록 반환 */
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
    /**
     * 물이 아래로 흘러내리며 공백을 메운다.
     * 1) origin에서 아래/좌/우로 흘러 도달 가능한 빈칸을 우선 수집
     * 2) 직접 연결된 빈칸이 없으면 origin 행 이하(row >= origin.row())의 빈칸을 수집
     * 3) 가장 깊은 행(row 내림차순) -> origin 열에 가까운 순으로 최대 5칸을 일반 블록(item=null)으로 채움
     */
    private void fillWaterDownward(Cell origin) {
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
            int rowCmp = Integer.compare(right.row(), left.row()); // 아래 행 우선
            if (rowCmp != 0) {
                return rowCmp;
            }
            int distCmp = Integer.compare(Math.abs(left.col() - origin.col()), Math.abs(right.col() - origin.col()));
            if (distCmp != 0) {
                return distCmp;
            }
            return Integer.compare(left.col(), right.col());
        });
        for (int i = 0; i < Math.min(WATER_FILL_COUNT, candidates.size()); i++) {
            Cell cell = candidates.get(i);
            board.fillCell(cell.row(), cell.col(), Tetromino.I, null); // Item.O가 아닌 null로 채움
        }
    }
    private List<Cell> findFlowReachableEmptyCells(Cell origin) {
        int[][] flowDirs = {{1, 0}, {0, -1}, {0, 1}}; // 아래, 좌, 우로만 흐름 (위로 역류 금지)
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
    
    // ----- 5. 메테오(M) 처리 -----
    private record ScatteredCell(Cell source, Tetromino type) {
    }
    private void handleMeteorLocked(Block locked) {
        List<ScatteredCell> normalCells = new ArrayList<>();
        List<Cell> meteorCells = getItemCells(locked);
        Cell center = meteorCells.isEmpty() ? locked.cells().get(0) : meteorCells.get(0);
        // 1. 메테오 충돌 지점 주변 3x3 셀 단위 삭제
        //    - O, H 아이템 셀은 명세대로 비산하지 않고 그대로 소멸
        //    - 일반 블록 셀은 삭제하되 비산 후보에 추가
        for (Cell meteor : meteorCells) {
            for (int r = meteor.row() - 1; r <= meteor.row() + 1; r++) {
                for (int c = meteor.col() - 1; c <= meteor.col() + 1; c++) {
                    if (r == meteor.row() && c == meteor.col()) {
                        continue;
                    }
                    Cell target = new Cell(r, c);
                    if (!board.isInside(target) || board.get(r, c) == null) {
                        continue;
                    }
                    Item itemOnBoard = board.getItem(r, c);
                    Tetromino blockType = board.get(r, c);
                    board.clearCell(r, c);
                    if (itemOnBoard != Item.H && itemOnBoard != Item.O) {
                        normalCells.add(new ScatteredCell(target, blockType));
                    }
                }
            }
            board.clearCell(meteor.row(), meteor.col()); // 메테오 본체 삭제
        }
        // 2. 일반 셀 중 일부(최대 3칸)만 좌우로 날려 보내고 중력 적용
        Collections.shuffle(normalCells, random);
        int scatterCount = Math.min(MAX_METEOR_SCATTER_CELLS, normalCells.size());
        for (int i = 0; i < scatterCount; i++) {
            scatterLeftRightAndFall(center, normalCells.get(i));
        }
    }
    private void scatterLeftRightAndFall(Cell meteorCenter, ScatteredCell cell) {
        int primaryDir;
        if (cell.source().col() < meteorCenter.col()) {
            primaryDir = -1;
        } else if (cell.source().col() > meteorCenter.col()) {
            primaryDir = 1;
        } else {
            primaryDir = random.nextBoolean() ? -1 : 1;
        }
        int[] dirs = {primaryDir, -primaryDir};
        for (int dir : dirs) {
            for (int dist = 2; dist <= 4; dist++) {
                int destCol = cell.source().col() + dir * dist;
                int startRow = Math.max(0, cell.source().row() - 1);
                Cell dest = new Cell(startRow, destCol);
                if (!board.isInside(dest) || board.get(startRow, destCol) != null) {
                    continue;
                }
                int landingRow = startRow;
                while (landingRow + 1 < board.height() && board.get(landingRow + 1, destCol) == null) {
                    landingRow++;
                }
                board.fillCell(landingRow, destCol, cell.type(), null);
                return;
            }
        }
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
    /** 테스트용: 다음에 나올 아이템을 강제로 지정한다. */
    void forceNextItem(Item item) {
        this.pendingItem = item;
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
        for (Cell target : getItemCells(block)) {
            if (board.isInside(target)) {
                board.setItem(target.row(), target.col(), item);
            }
        }
    }
    private List<Integer> itemIndices(Block block, Item item) {
        List<Integer> selected = new ArrayList<>();
        selected.add(random.nextInt(block.cells().size()));
        if (item == Item.W) {
            selected.clear();
            for (int index = 0; index < block.cells().size(); index++) {
                selected.add(index);
            }
        } else if (item == Item.H || item == Item.O) {
            for (int index = 0; index < block.cells().size(); index++) {
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
    private void scheduleItemIfNeeded() {
        if (!bugMode && getLines() / 10 > linesAtLastItem / 10 && pendingItem == null) {
            pendingItem = randomItem();
            linesAtLastItem = getLines();
        }
    }
}