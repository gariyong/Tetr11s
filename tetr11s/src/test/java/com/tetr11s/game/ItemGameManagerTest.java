package com.tetr11s.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.item.Item;
import com.tetr11s.game.tetromino.Block;
import com.tetr11s.game.tetromino.Cell;
import com.tetr11s.game.tetromino.Tetromino;

class ItemGameManagerTest {

    private static final int BOTTOM = Board.HEIGHT - 1; // 21

    private static Supplier<Tetromino> always(Tetromino type) {
        return () -> type;
    }

    /** 다음에 나올 아이템을 강제로 지정하고, 현재 일반 블록을 고정시켜 아이템 블록을 스폰한다. */
    private static ItemGameManager startWithItem(Item item, Tetromino blockType, long seed) {
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, false, always(blockType));
        game.setRandomSeed(seed);
        game.start();
        game.forceNextItem(item);
        game.hardDrop();
        if (game.isClearing()) {
            game.finishLineClear();
        }
        // 테스트 보드를 깔끔히 비운 상태에서 방금 스폰된 아이템 블록만 테스트
        game.getBoard().clear();
        return game;
    }

    // ---------- 1. 무게추(W) ----------

    @Test
    void 무게추는_지나가는_4열의_모든_블록을_점수없이_밀고_바닥에_6칸_그대로_남는다() {
        ItemGameManager game = startWithItem(Item.W, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 무게추 기본 생성 위치는 col 3~6 (너비 4). 중간~바닥까지 블록을 빽빽하게 쌓음
        for (int row = 10; row <= BOTTOM; row++) {
            for (int col = 3; col <= 6; col++) {
                board.fillCell(row, col, Tetromino.T, null);
            }
        }
        // 무게추 경로 밖(col 2, col 7) 블록은 건드리지 않아야 함
        board.fillCell(15, 2, Tetromino.Z, null);
        board.fillCell(15, 7, Tetromino.S, null);

        int scoreBefore = game.getScore();
        game.hardDrop();

        // 낙하/관통 점수 없음
        assertEquals(scoreBefore, game.getScore());

        // 경로 밖 블록은 그대로 유지
        assertEquals(Tetromino.Z, board.get(15, 2));
        assertEquals(Tetromino.S, board.get(15, 7));

        // 10~19행의 3~6열은 모두 깨끗하게 밀렸어야 함
        for (int row = 10; row <= BOTTOM - 2; row++) {
            for (int col = 3; col <= 6; col++) {
                assertNull(board.get(row, col), "밀려야 할 칸이 남아 있음: (" + row + "," + col + ")");
            }
        }

        // 바닥 20~21행에는 6칸 무게추 모양과 Item.W 표시가 그대로 잔류
        Set<Cell> expectedWeightCells = Set.of(
                new Cell(BOTTOM - 1, 4), new Cell(BOTTOM - 1, 5),
                new Cell(BOTTOM, 3), new Cell(BOTTOM, 4), new Cell(BOTTOM, 5), new Cell(BOTTOM, 6));
        for (Cell c : expectedWeightCells) {
            assertNotNull(board.get(c.row(), c.col()), "바닥 무게추 칸이 비어 있음: " + c);
            assertEquals(Item.W, board.getItem(c.row(), c.col()), "무게추 W 표시 누락: " + c);
        }
        // 무게추 상단 양쪽 빈 모서리(20행 3열, 6열)도 비어 있어야 함
        assertNull(board.get(BOTTOM - 1, 3));
        assertNull(board.get(BOTTOM - 1, 6));
    }

    @Test
    void 무게추는_블록에_닿기_전엔_좌우이동_가능하고_닿은_뒤엔_좌우이동이_잠긴다() {
        ItemGameManager game = startWithItem(Item.W, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 닿기 전에는 좌우 이동 가능
        assertTrue(game.moveRight());
        assertTrue(game.moveLeft());

        // 무게추 바로 아래(2행 4열)에 블록 배치 후 tick으로 관통 시작
        board.fillCell(2, 4, Tetromino.J, null);
        int scoreBefore = game.getScore();
        assertTrue(game.tick());

        // 블록을 부수며 내려갔으므로 점수 변화 없고, 좌우 이동 잠금
        assertEquals(scoreBefore, game.getScore());
        assertNull(board.get(2, 4));
        assertFalse(game.moveLeft());
        assertFalse(game.moveRight());

        // 고스트는 바닥(BOTTOM)까지 표시
        Block ghost = game.getGhost();
        assertNotNull(ghost);
        assertEquals(BOTTOM, ghost.cells().stream().mapToInt(Cell::row).max().orElse(-1));
    }

    @Test
    void 메테오와_무게추는_스폰시_미리보기의_next_테트로미노를_버리지_않는다() {
        List<Tetromino> sequence = List.of(Tetromino.I, Tetromino.T, Tetromino.L, Tetromino.J);
        int[] idx = {0};
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, false,
                () -> sequence.get(idx[0]++ % sequence.size()));
        game.start(); // current=I, next=T

        assertEquals(Tetromino.T, game.getNext());
        game.forceNextItem(Item.W);
        game.hardDrop(); // W 스폰 -> next는 여전히 T여야 함

        assertEquals(Item.W, game.getCurrentItem());
        assertEquals(Tetromino.T, game.getNext());
    }

    // ---------- 2. 메테오(M) ----------

    @Test
    void 메테오는_3x3을_파괴하고_옆_라인과_파괴존의_HO를_무조건_살려_비산시키며_화면을_흔든다() {
        ItemGameManager game = startWithItem(Item.M, Tetromino.O, 42L);
        Board board = game.getBoard();

        // 메테오는 기본 col=4에 스폰되어 (BOTTOM-1, 4)에 착지 -> 3x3 파괴존: cols 3..5, 옆 비산존: col 2, 6
        board.fillCell(BOTTOM, 4, Tetromino.T, null);       // 3x3 파괴존 일반 셀 -> 파괴(비산 안 됨)
        board.fillCell(BOTTOM, 3, Tetromino.S, null);       // 3x3 파괴존 일반 셀 -> 파괴(비산 안 됨)
        board.fillCell(BOTTOM - 1, 5, Tetromino.Z, Item.O); // 3x3 파괴존 안의 O -> 무조건 살린 채 비산!
        board.fillCell(BOTTOM, 2, Tetromino.L, null);       // 왼쪽 옆 라인 일반 셀 -> 왼쪽 대각선 비산
        board.fillCell(BOTTOM, 6, Tetromino.J, null);       // 오른쪽 옆 라인 일반 셀 -> 오른쪽 대각선 비산
        board.fillCell(BOTTOM - 1, 2, Tetromino.I, Item.H); // 왼쪽 옆 라인 H -> 무조건 살린 채 비산!

        game.hardDrop();

        // 1. 파괴존의 O와 비산존의 H 모두 보드 어딘가에 살아남아 있어야 함
        int survivedH = 0;
        int survivedO = 0;
        for (int r = 0; r < board.height(); r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.getItem(r, c) == Item.H) {
                    survivedH++;
                }
                if (board.getItem(r, c) == Item.O) {
                    survivedO++;
                }
            }
        }
        assertEquals(1, survivedH, "비산존의 H는 무조건 살린 채 비산되어야 함");
        assertEquals(1, survivedO, "파괴존 안의 O도 무조건 살린 채 비산되어야 함");

        // 2. 3x3 파괴존과 옆 라인 원래 자리는 모두 비워졌어야 함 (비산된 셀이 다른 열로 이동)
        for (int r = BOTTOM - 1; r <= BOTTOM; r++) {
            for (int c = 2; c <= 6; c++) {
                assertNull(board.get(r, c), "충돌 3x3 및 옆 라인 원래 자리가 비어있지 않음: (" + r + "," + c + ")");
            }
        }

        // 3. 비산 파편은 총 4개(파괴존 O 1개 + 옆 라인 일반 2개 + 옆 라인 H 1개)
        long now = System.currentTimeMillis();
        List<ItemGameManager.MeteorFragment> fragments = game.getActiveMeteorFragments(now);
        assertEquals(4, fragments.size(), "파괴존 O 1개 + 비산존 3개 = 총 4개가 비산되어야 함");
        for (ItemGameManager.MeteorFragment f : fragments) {
            assertNotEquals(f.from().col(), f.to().col(), "원래 열 제자리가 아닌 좌우로 비산해야 함");
            assertEquals(f.type(), board.get(f.to().row(), f.to().col()), "원래 블록 색상이 유지되어야 함");
            assertEquals(f.item(), board.getItem(f.to().row(), f.to().col()), "H/O 아이템 표시도 유지되어야 함");
        }

        // 4. 화면 흔들림 발동 및 종료 후 (0, 0) 리셋
        double[] shaking = game.getShakeOffset(now + 20L);
        assertTrue(Math.abs(shaking[0]) > 0.0 || Math.abs(shaking[1]) > 0.0, "메테오 충돌 시 화면이 흔들려야 함");
        double[] settled = game.getShakeOffset(now + ItemGameManager.SCREEN_SHAKE_DURATION_MS + 10L);
        assertEquals(0.0, settled[0]);
        assertEquals(0.0, settled[1]);
    }

    @Test
    void 메테오는_하드드롭과_소프트드롭_없이_자연낙하_tick으로_안착해도_예외없이_다음블록으로_진행된다() {
        ItemGameManager game = startWithItem(Item.M, Tetromino.O, 7L);
        Board board = game.getBoard();

        // row=14에 장애물 배치 -> 메테오가 tick으로 내려와 row=13에서 자연 안착
        board.fillCell(14, 4, Tetromino.T, null);
        board.fillCell(14, 2, Tetromino.L, Item.H);
        board.fillCell(14, 6, Tetromino.J, Item.O);

        int guard = 0;
        while (game.getCurrentItem() == Item.M && guard++ < 30) {
            game.tick();
        }

        assertNotEquals(Item.M, game.getCurrentItem(), "메테오 자연 안착 후 다음 블록으로 정상 진행되어야 함");
        assertFalse(game.isGameOver());
        assertNotNull(game.getCurrent());
    }

    // ---------- 3. 수소(H) ----------

    @Test
    void 수소는_한_블록에_여러개_붙어도_빈_바닥에서는_자폭하지_않는다() {
        // 여러 시드 반복 테스트 (15% 확률로 2개 이상 H가 붙는 경우 포함)
        for (long seed = 0; seed < 40; seed++) {
            ItemGameManager game = startWithItem(Item.H, Tetromino.O, seed);
            game.hardDrop();
            Board board = game.getBoard();

            int filled = 0;
            for (int r = BOTTOM - 1; r <= BOTTOM; r++) {
                for (int c = 4; c <= 5; c++) {
                    if (board.get(r, c) != null) {
                        filled++;
                    }
                }
            }
            assertEquals(4, filled, "seed=" + seed + "에서 빈 바닥인데 H가 자폭함");
        }
    }

    @Test
    void 수소는_기존_다른_수소와_대각선으로_접하면_3x3_폭발한다() {
        ItemGameManager game = startWithItem(Item.H, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 현재 O 블록의 H 셀 위치를 조회해 대각선 아래 칸에 기존 H 배치
        Cell hCell = game.getCurrentItemCells().get(0);
        int landingRow = BOTTOM - (1 - hCell.row()); // O 블록(0~1행)이 20~21행에 착지
        int existingRow = landingRow - 1;
        int existingCol = hCell.col() + 1;
        // 대각선 위치가 O 블록 내부와 겹치지 않도록 O 블록 오른쪽(col 6)으로 배치
        existingCol = 6;
        existingRow = (landingRow == BOTTOM) ? BOTTOM - 1 : BOTTOM;

        // O 블록의 H 셀이 col 5가 되도록 이동 조정
        if (hCell.col() == 4) {
            game.moveRight();
            hCell = game.getCurrentItemCells().get(0);
            landingRow = BOTTOM - (1 - hCell.row());
            existingCol = hCell.col() + 1;
            existingRow = (landingRow == BOTTOM) ? BOTTOM - 1 : BOTTOM;
        }

        board.fillCell(existingRow, existingCol, Tetromino.T, Item.H);
        // 폭발 범위 안의 주변 블록도 함께 사라지는지 확인
        board.fillCell(BOTTOM, existingCol, Tetromino.Z, null);

        game.hardDrop();

        assertNull(board.get(existingRow, existingCol), "대각선 기존 H가 폭발로 사라져야 함");
        assertNull(board.get(landingRow, hCell.col()), "새 H 셀도 폭발로 사라져야 함");
    }

    // ---------- 4. 산소(O) ----------

    @Test
    void 산소는_수소_2개와_대각선_인접시_아래쪽_바닥부터_찰랑이는_물로_채우고_원소를_소모한다() {
        ItemGameManager game = startWithItem(Item.O, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 바닥 위에 다리를 놓고(18행) 그 아래(19~21행)에 공백을 둠
        for (int c = 3; c <= 6; c++) {
            board.fillCell(18, c, Tetromino.T, null);
        }
        // O 블록이 16~17행(col 4~5)에 착지할 예정. O 셀 위치 기준으로 대각선/인접 H 2개 배치
        Cell oCell = game.getCurrentItemCells().get(0);
        int landedORow = 16 + oCell.row();
        int landedOCol = oCell.col();

        // O 셀의 좌상/우상 대각선(블록과 겹치지 않는 col 3, col 6)에 H 2개 배치
        board.fillCell(landedORow - 1, 3, Tetromino.L, Item.H);
        board.fillCell(landedORow - 1, 6, Tetromino.J, Item.H);
        // 만약 landedOCol이 4면 col 6은 거리 2이므로, landedOCol 양옆 대각선으로 정확히 배치
        board.clearCell(landedORow - 1, 3);
        board.clearCell(landedORow - 1, 6);
        int leftHCol = landedOCol - 1;
        int rightHCol = landedOCol + 1;
        board.fillCell(landedORow - 1, leftHCol, Tetromino.L, Item.H);
        board.fillCell(landedORow - 1, rightHCol, Tetromino.J, Item.H);

        game.hardDrop();

        // 1. 반응한 O와 H 2개의 아이템 표시가 모두 소모(null)되었어야 함
        assertNull(board.getItem(landedORow, landedOCol), "반응한 O 아이템이 소모되어야 함");
        assertNull(board.getItem(landedORow - 1, leftHCol), "반응한 H1 아이템이 소모되어야 함");
        assertNull(board.getItem(landedORow - 1, rightHCol), "반응한 H2 아이템이 소모되어야 함");

        // 2. O 위쪽(row < landedORow)에는 물이 생기면 안 됨
        for (int r = 0; r < landedORow; r++) {
            for (int c = 0; c < board.width(); c++) {
                assertFalse(board.isWater(r, c), "물이 O보다 위쪽에 생성됨: (" + r + "," + c + ")");
            }
        }

        // 3. 아래쪽 바닥(BOTTOM 행)부터 5칸이 물 블록(isWater=true, item=null)으로 채워져야 함
        int waterCount = 0;
        int bottomWaterCount = 0;
        for (int r = landedORow; r <= BOTTOM; r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.isWater(r, c)) {
                    waterCount++;
                    assertNull(board.getItem(r, c), "물 칸에 Item.O가 복제되면 안 됨");
                    if (r == BOTTOM) {
                        bottomWaterCount++;
                    }
                }
            }
        }
        assertEquals(5, waterCount, "물이 5칸 채워져야 함");
        assertEquals(5, bottomWaterCount, "물은 가장 깊은 바닥 행(21행)부터 채워져야 함");
    }

    @Test
    void 기존_산소_옆에_두번째_수소가_떨어져도_양방향으로_물_반응이_발동한다() {
        ItemGameManager game = startWithItem(Item.H, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 현재 H 셀이 착지할 위치 파악
        Cell hCell = game.getCurrentItemCells().get(0);
        int landedHRow = BOTTOM - (1 - hCell.row());
        int landedHCol = hCell.col();

        // O 블록(col 4~5) 오른쪽(col 6)에 기존 O 배치, 그 오른쪽(col 7)에 기존 H 배치
        // (기존 H는 col 7이라 새로 떨어지는 H(col 4~5)와 거리 2 이상 -> 수소폭탄은 안 터짐!)
        if (landedHCol == 4) {
            game.moveRight();
            hCell = game.getCurrentItemCells().get(0);
            landedHRow = BOTTOM - (1 - hCell.row());
            landedHCol = hCell.col();
        }
        int oCol = landedHCol + 1;
        int otherHCol = oCol + 1;
        board.fillCell(landedHRow, oCol, Tetromino.T, Item.O);
        board.fillCell(landedHRow, otherHCol, Tetromino.L, Item.H);

        game.hardDrop();

        assertTrue(board.hasWater(), "H가 나중에 떨어져도 기존 O가 H 2개와 인접하면 물이 생성되어야 함");
        assertNull(board.getItem(landedHRow, oCol), "기존 O가 소모되어야 함");
    }

    // ---------- 5. 줄 삭제(L) ----------

    @Test
    void 줄삭제_L은_가로줄을_반짝임_연출과_함께_삭제하고_꽉찬줄과_합산점수_및_공격을_보낸다() {
        ItemGameManager game = startWithItem(Item.L, Tetromino.O, 1L);
        List<List<GarbageLine>> sentAttacks = new ArrayList<>();
        game.setOnAttack(sentAttacks::add);

        Board board = game.getBoard();
        Cell lCell = game.getCurrentItemCells().get(0);
        int lRow = BOTTOM - (1 - lCell.row());
        int otherRow = (lRow == BOTTOM) ? BOTTOM - 1 : BOTTOM;

        // L이 없는 쪽 줄(otherRow)은 O블록이 착지하면 꽉 차도록 양쪽 8칸을 채움
        for (int c = 0; c < board.width(); c++) {
            if (c != 4 && c != 5) {
                board.fillCell(otherRow, c, Tetromino.T, null);
            }
        }
        // L이 있는 쪽 줄(lRow)은 1칸만 채워 꽉 차지 않은 상태로 둠
        board.fillCell(lRow, 0, Tetromino.Z, null);

        int scoreBefore = game.getScore();
        game.hardDrop();

        // 1. 반짝임 연출 상태에 들어가고, 두 줄(20, 21행) 모두 clearingRows에 포함
        assertTrue(game.isClearing(), "L 줄도 반짝임 연출을 거쳐야 함");
        assertEquals(List.of(BOTTOM - 1, BOTTOM), game.getClearingRows());

        // 2. 2줄 동시 삭제 보너스(300점) + 하드드롭 20점 = 320점 증가 (100+100 분리 계산이 아님!)
        assertEquals(scoreBefore + 20 + 300, game.getScore());

        // 3. 대전 모드 공격 2줄 전송 확인
        assertEquals(1, sentAttacks.size());
        assertEquals(2, sentAttacks.get(0).size());

        // 4. 연출 종료 후 두 줄 모두 실제로 지워짐
        game.finishLineClear();
        assertFalse(game.isClearing());
        for (int c = 0; c < board.width(); c++) {
            assertNull(board.get(BOTTOM, c));
            assertNull(board.get(BOTTOM - 1, c));
        }
    }

    // ---------- 6. 메테오 즉시 가시 스폰 & 아이템 우선 적용/연쇄 반응 ----------

    @Test
    void 메테오는_스폰_직후_보이는_최상단_행에서_시작한다() {
        ItemGameManager game = startWithItem(Item.M, Tetromino.O, 1L);

        assertEquals(Item.M, game.getCurrentItem());
        assertEquals(Board.BUFFER_ROWS, game.getCurrent().cells().get(0).row(),
                "1x1 메테오는 숨겨진 버퍼 행(0~1)이 아니라 보이는 최상단 행(2)에서 바로 시작해야 함");
    }

    @Test
    void 수소폭탄_H2가_완성되면서_줄이_꽉차면_폭발이_먼저_적용된다() {
        ItemGameManager game = startWithItem(Item.H, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 현재 O 블록의 H 셀이 BOTTOM 행 col 4 또는 5에 착지하도록 확인
        Cell hCell = game.getCurrentItemCells().get(0);
        int landedHRow = BOTTOM - (1 - hCell.row());
        int adjacentCol = (hCell.col() == 4) ? 3 : 6;

        // landedHRow 행을 O 블록 자리(4, 5열)만 비워두고 전부 채움 + 바로 옆에 기존 H 배치
        for (int c = 0; c < board.width(); c++) {
            if (c != 4 && c != 5) {
                board.fillCell(landedHRow, c, Tetromino.T, c == adjacentCol ? Item.H : null);
            }
        }

        game.hardDrop();

        // H2 폭발이 줄 삭제보다 먼저 적용되어 3x3을 부숴버리므로 해당 줄은 줄 삭제로 터지지 않음
        assertFalse(game.isClearing(), "H2 폭발이 줄 삭제보다 먼저 적용되어야 함");
        assertNull(board.get(landedHRow, adjacentCol), "기존 H가 폭발로 사라져야 함");
        assertNull(board.get(landedHRow, hCell.col()), "새 H도 폭발로 사라져야 함");
        assertFalse(game.getActiveExplosions(System.currentTimeMillis()).isEmpty(), "폭발 연출이 등록되어야 함");
    }

    @Test
    void 산소_H2O가_물로_줄을_완성하면_물_차오름_연출_시간동안_줄삭제를_대기한다() {
        ItemGameManager game = startWithItem(Item.O, Tetromino.O, 1L);
        Board board = game.getBoard();

        // 바닥(BOTTOM) 행에 5칸만 채워두고 5칸을 비워둠 -> 물 5칸이 채워지면 바닥 줄이 완성됨
        for (int c = 0; c < 5; c++) {
            board.fillCell(BOTTOM, c, Tetromino.T, null);
        }
        // 위층(BOTTOM - 2 = 19행)에 다리를 놓고 O 블록이 17~18행에 착지하도록 구성
        for (int c = 3; c <= 6; c++) {
            board.fillCell(BOTTOM - 2, c, Tetromino.T, null);
        }
        Cell oCell = game.getCurrentItemCells().get(0);
        int landedORow = (BOTTOM - 4) + oCell.row();
        int landedOCol = oCell.col();
        board.fillCell(landedORow - 1, landedOCol - 1, Tetromino.L, Item.H);
        board.fillCell(landedORow - 1, landedOCol + 1, Tetromino.J, Item.H);

        game.hardDrop();

        // 1. 물이 먼저 채워져 바닥 줄이 완성됨 -> clearingRows에 BOTTOM 포함
        assertTrue(game.isClearing(), "물로 완성된 바닥 줄이 삭제 대상이 되어야 함");
        assertTrue(game.getClearingRows().contains(BOTTOM));
        // 2. 물 차오름 연출 시간 동안은 isItemEffectAnimating이 true여야 함 (줄 반짝임보다 물 연출 우선)
        assertTrue(game.isItemEffectAnimating(System.currentTimeMillis()),
                "물 차오름 연출이 끝나기 전에는 줄 삭제 반짝임을 대기해야 함");
        // 3. 연출 시간이 지나면 false로 바뀌어 줄 삭제가 진행됨
        assertFalse(game.isItemEffectAnimating(System.currentTimeMillis() + 2000L));
    }

    @Test
    void 줄이_터진_뒤_위에서_내려온_블록으로_새로_완성된_H2가_연쇄_폭발한다() {
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, false, always(Tetromino.O));
        game.start();
        Board board = game.getBoard();

        // 19행 0열에 H, 21행 0열에 H 배치 (20행이 가운데를 막고 있음)
        board.fillCell(BOTTOM - 2, 0, Tetromino.T, Item.H);
        board.fillCell(BOTTOM, 0, Tetromino.T, Item.H);
        // 20행은 4,5열만 비워두고 채워서 O 블록(20~21행)이 20행을 완성하도록 구성
        for (int c = 0; c < board.width(); c++) {
            if (c != 4 && c != 5) {
                board.fillCell(BOTTOM - 1, c, Tetromino.Z, null);
            }
        }
        // 21행 4,5열 아래는 비어있으므로 O 블록은 20~21행에 착지 -> 20행만 꽉 참
        game.hardDrop();
        assertEquals(List.of(BOTTOM - 1), game.getClearingRows());

        // 20행이 터지면 19행의 H가 20행으로 내려와 21행의 H와 접하면서 연쇄 폭발!
        game.finishLineClear();

        assertNull(board.get(BOTTOM - 1, 0), "내려온 H가 연쇄 폭발로 사라져야 함");
        assertNull(board.get(BOTTOM, 0), "기존 바닥 H도 연쇄 폭발로 사라져야 함");
    }

    @Test
    void 같은_블록에_H가_2개_있어도_위쪽_L줄삭제_후_아래_H가_자폭하지_않는다() {
        // 같은 블록에 H 2개 이상이 붙는 시드를 찾아 바닥에 고정시킨 뒤, 위쪽 줄을 L 아이템으로 삭제
        ItemGameManager game = null;
        for (long seed = 0; seed < 100; seed++) {
            ItemGameManager candidate = startWithItem(Item.H, Tetromino.O, seed);
            if (candidate.getCurrentItemCells().size() >= 2) {
                game = candidate;
                break;
            }
        }
        assertNotNull(game, "H 셀 2개 이상이 생성되는 시드를 찾지 못함");

        // 1. 같은 블록에 H가 2개 붙은 O 블록을 바닥(20~21행)에 고정 -> 자폭하지 않아야 함
        game.forceNextItem(Item.L);
        game.hardDrop();
        Board board = game.getBoard();
        assertEquals(Item.L, game.getCurrentItem());

        int hCountBefore = 0;
        for (int r = BOTTOM - 1; r <= BOTTOM; r++) {
            for (int c = 4; c <= 5; c++) {
                if (board.getItem(r, c) == Item.H) {
                    hCountBefore++;
                }
            }
        }
        assertTrue(hCountBefore >= 2, "바닥에 같은 블록 소속 H 2개 이상이 남아 있어야 함");

        // 2. 위쪽(18~19행)에 L 아이템 블록을 떨어뜨려 줄 삭제 수행
        game.hardDrop();
        assertTrue(game.isClearing());
        game.finishLineClear();

        // 3. L 줄 삭제 이후에도 아래 있던 같은 블록 소속 H들은 자폭하지 않고 그대로 유지되어야 함
        int hCountAfter = 0;
        for (int r = 0; r < board.height(); r++) {
            for (int c = 0; c < board.width(); c++) {
                if (board.getItem(r, c) == Item.H) {
                    hCountAfter++;
                }
            }
        }
        assertEquals(hCountBefore, hCountAfter, "L 줄 삭제 후 아래에 있던 같은 블록 H가 자폭하면 안 됨");
    }

    @Test
    void 버그판에서_아이템이_나오기_직전_턴에_NEXT_블록란에_대기중인_아이템이_표시된다() {
        ItemGameManager game = new ItemGameManager(Difficulty.NORMAL, true, always(Tetromino.O));
        game.setRandomSeed(3L);
        game.start();

        // 1~3번째 고정: 아직 NEXT에 아이템 없음
        for (int i = 0; i < 3; i++) {
            game.hardDrop();
            assertNull(game.getNextItemType());
        }

        // 4번째 고정 직후: 5번째 블록이 떨어지는 동안 NEXT에 대기 중인 아이템이 표시되어야 함!
        game.hardDrop();
        Item previewed = game.getNextItemType();
        assertNotNull(previewed, "아이템이 나오기 직전 턴에는 NEXT 블록란에 대기 아이템이 보여야 함");
        assertNull(game.getCurrentItem(), "현재 조작 중인 5번째 블록은 아직 일반 블록이어야 함");

        // 5번째 고정 직후: NEXT에 보였던 바로 그 아이템이 현재 블록으로 스폰됨
        game.hardDrop();
        assertEquals(previewed, game.getCurrentItem(), "NEXT에 대기 중이던 아이템이 그대로 스폰되어야 함");
        assertNull(game.getNextItemType(), "스폰 직후에는 NEXT 대기 아이템이 비워져야 함");
    }
}
