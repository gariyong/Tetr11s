package com.tetr11s.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.RouletteWheelGenerator;

/**
 * 일반 낙하 규칙을 공유하면서, 블록이 고정되는 순간 아이템을 셀에 주입한다.
 */
public class ItemGameManager extends GameManager {
    private final Random random = new Random();
    private Item pendingItem;
    private Item currentItem;
    private List<Integer> currentItemIndices = List.of();
    private int linesAtLastItem;
    private final boolean bugMode;
    private int locksSinceItem;

    public ItemGameManager() {
        this(Difficulty.DEFAULT);
    }

    public ItemGameManager(Difficulty difficulty) {
        this(difficulty, false);
    }

    public ItemGameManager(Difficulty difficulty, boolean bugMode) {
        super(new Board(),
                new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(difficulty.iBlockWeight())),
                difficulty, true);
        this.bugMode = bugMode;
    }

    @Override
    public void start() {
        pendingItem = null;
        currentItem = null;
        currentItemIndices = List.of();
        linesAtLastItem = 0;
        locksSinceItem = 0;
        super.start();
    }

    @Override
    protected void spawnNext() {
        if (pendingItem == Item.M) {
            current = Block.special(List.of(new Cell(0, 0)), board.width());
            next = nextType.get();
            if (!board.canPlace(current)) {
                gameOver = true;
            }
        } else if (pendingItem == Item.W) {
            current = Block.special(List.of(
                    new Cell(0, 1), new Cell(0, 2),
                    new Cell(1, 0), new Cell(1, 1),
                    new Cell(1, 2), new Cell(1, 3)), board.width());
            next = nextType.get();
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
    }

    @Override
    protected void onBlockLocked(Block locked) {
        if (currentItem == Item.L) {
            List<Integer> rows = new ArrayList<>();
            for (Cell cell : itemCells(locked, Item.L).stream().map(locked.cells()::get).toList()) {
                if (!rows.contains(cell.row())) {
                    rows.add(cell.row());
                }
            }
            board.clearRows(rows);
            score.addLineClear(rows.size());
            scheduleItemIfNeeded();
        } else if (currentItem == Item.H) {
            for (Cell cell : locked.cells()) {
                if (adjacentHydrogen(cell.row(), cell.col())) {
                    explode(cell.row(), cell.col());
                    break;
                }
            }
        } else if (currentItem == Item.M) {
        Map<Cell, Item> scattering = new LinkedHashMap<>();
        
        // 1. 메테오 충돌 지점 주변 3x3 범위 탐색
        for (Cell meteor : itemCells(locked, Item.M).stream().map(locked.cells()::get).toList()) {
            for (int row = meteor.row() - 1; row <= meteor.row() + 1; row++) {
                for (int col = meteor.col() - 1; col <= meteor.col() + 1; col++) {
                    Cell target = new Cell(row, col);
                    if (board.isInside(target)) {
                        // 주변에 H나 O 아이템이 있다면 비산시키기 위해 따로 백업
                        Item itemOnBoard = board.getItem(row, col); // 보드판에서 아이템 정보 가져오는 메서드 가정
                        if (itemOnBoard == Item.H || itemOnBoard == Item.O) {
                            scattering.put(target, itemOnBoard);
                        }
                        // 메테오 칸이 아니면 주변 3x3 칸을 싹 부갬(삭제)
                        if (!(row == meteor.row() && col == meteor.col())) {
                            board.clearCell(row, col);
                        }
                    }
                }
            }
            board.clearCell(meteor.row(), meteor.col()); // 메테오 본체도 삭제
        }
        
        // 2. 비산 및 아래로 낙하 처리 실행
        scatterAndFallItems(scattering);
    } else if (currentItem == Item.W) {
        // 1. 현재 떨어져서 고정되려던 무게추 블록의 가로 폭(최소 열 ~ 최대 열)을 파악합니다.
        int minCol = locked.cells().stream().mapToInt(Cell::col).min().orElse(0);
        int maxCol = locked.cells().stream().mapToInt(Cell::col).max().orElse(0);
        int widthSpan = maxCol - minCol;

        // 2. 허공에 멈춰 있던 기존 블록 흔적을 깨끗하게 지웁니다.
        for (Cell c : locked.cells()) {
            if (board.isInside(c)) {
                board.clearCell(c.row(), c.col());
            }
        }

        // 3. [눈속임 핵심] 보드의 맨 밑바닥 행(Visible Height 기준 최하단)에 무게추를 즉시 생성합니다.
        // Board의 가장 마지막 행 번호는 board.height() - 1 입니다.
        int bottomRow = board.height() - 1;

        // 기존 무게추가 가진 블록 모양(Tetromino 타입)을 그대로 유지한 채 바닥 행으로 위치만 강제 이동시킵니다.
        Block floorWeightBlock = new Block(
            locked.type(), 
            locked.rotation(), 
            bottomRow - 1, // 바닥에 닿게 하기 위해 행 조절 (블록 크기에 맞춰 bottomRow 조정 가능)
            minCol
        );

        // 만약 바닥 자리가 다른 블록 때문에 차있다면 그 자리만 싹 비워서 자리를 만들어 줍니다.
        for (Cell c : floorWeightBlock.cells()) {
            if (board.isInside(c)) {
                board.clearCell(c.row(), c.col());
            }
        }

        // 4. 바닥에 강제로 생성한 무게추 블록을 최종 락(Lock)시킵니다!
        if (board.canPlace(floorWeightBlock)) {
            board.lock(floorWeightBlock);
        } else {
            // 혹시라도 자리가 안 나면 최하단 한 줄만 완전히 새로 잡아서 고정
            Block emergencyBlock = new Block(locked.type(), locked.rotation(), board.height() - 2, minCol);
            if (board.canPlace(emergencyBlock)) {
                board.lock(emergencyBlock);
            }
        }
        
    } else if (currentItem == Item.O && touchesTwoHydrogens(locked)) {
            fillWater(locked.cells().get(0));
        }
    }

    public Item getCurrentItem() {
        return currentItem;
    }

    public List<Cell> getCurrentItemCells() {
        if (current == null) {
            return List.of();
        }
        List<Cell> cells = current.cells();
        List<Cell> selected = new ArrayList<>();
        for (int index : currentItemIndices) {
            selected.add(cells.get(index));
        }
        return List.copyOf(selected);
    }

    @Override
    public Item getNextItemType() {
        return pendingItem;
    }

    private boolean adjacentHydrogen(int row, int col) {
        int count = 0;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (board.isInside(new Cell(r, c)) && board.getItem(r, c) == Item.H) {
                    count++;
                }
            }
        }
        return count >= 2;
    }

    private void explode(int row, int col) {
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                board.clearCell(r, c);
            }
        }
    }

    private boolean touchesTwoHydrogens(Block locked) {
        for (Cell source : locked.cells()) {
            int count = 0;
            for (int row = source.row() - 1; row <= source.row() + 1; row++) {
                for (int col = source.col() - 1; col <= source.col() + 1; col++) {
                    if (board.isInside(new Cell(row, col))
                            && board.getItem(row, col) == Item.H) {
                        count++;
                    }
                }
            }
            if (count >= 2) {
                return true;
            }
        }
        return false;
    }

    private void fillWater(Cell origin) {
        List<Cell> empty = new ArrayList<>();
        for (int row = board.height() - 1; row >= 0; row--) {
            for (int col = 0; col < board.width(); col++) {
                if (board.get(row, col) == null) {
                    empty.add(new Cell(row, col));
                }

            }
        }
        empty.sort((left, right) -> {
            int leftDistance = Math.abs(left.row() - origin.row()) + Math.abs(left.col() - origin.col());
            int rightDistance = Math.abs(right.row() - origin.row()) + Math.abs(right.col() - origin.col());
            if (leftDistance != rightDistance) {
                return Integer.compare(leftDistance, rightDistance);
            }
            return Integer.compare(left.col(), right.col());
        });
        for (int i = 0; i < Math.min(5, empty.size()); i++) {
            Cell cell = empty.get(i);
            board.fillCell(cell.row(), cell.col(), current.type(), Item.O);
        }
    }

    private void scatterAndFallItems(Map<Cell, Item> items) {
    for (Map.Entry<Cell, Item> entry : items.entrySet()) {
        Cell source = entry.getKey();
        Item itemType = entry.getValue();
        
        boolean placed = false;
        // 3교대 거리(반경 2~4칸) 바깥쪽으로 튕겨낼 빈 공간을 찾음
        for (int radius = 2; radius <= 4 && !placed; radius++) {
            for (int row = source.row() - radius; row <= source.row() + radius; row++) {
                for (int col = source.col() - radius; col <= source.col() + radius; col++) {
                    Cell scatterDest = new Cell(row, col);
                    // 맨땅(빈 공간)인 곳으로 튕겨냄
                    if (Math.abs(row - source.row()) + Math.abs(col - source.col()) == radius
                            && board.isInside(scatterDest)
                            && board.get(row, col) == null) {
                        
                        // [핵심] 튕겨 나간 자리가 공중이라면, 아래 칸이 빌 때까지 쭉 미끄러져 내려오게 함 (중력 적용)
                        int landingRow = row;
                        while (landingRow + 1 < board.height() && board.get(landingRow + 1, col) == null) {
                            landingRow++; // 아래가 비어있으면 계속 한 칸씩 밑으로 하강
                        }
                        
                        // 최종 낙하한 바닥 위치에 아이템 생성 및 안착
                        board.fillCell(landingRow, col, com.tetr11s.game.tetromino.Tetromino.O, itemType);
                        placed = true;
                        break;
                    }
                }
                if (placed) break;
            }
        }
    }
}

    private void inject(Block block, Item item) {
        for (Cell target : itemCells(block, item).stream().map(block.cells()::get).toList()) {
            board.setItem(target.row(), target.col(), item);
        }
    }

    private List<Integer> itemCells(Block block, Item item) {
        return currentItemIndices;
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
