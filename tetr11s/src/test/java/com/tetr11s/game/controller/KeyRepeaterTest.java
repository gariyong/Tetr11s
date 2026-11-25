package com.tetr11s.game.controller;

import static com.tetr11s.game.controller.GameAction.HARD_DROP;
import static com.tetr11s.game.controller.GameAction.MOVE_LEFT;
import static com.tetr11s.game.controller.GameAction.MOVE_RIGHT;
import static com.tetr11s.game.controller.GameAction.PAUSE;
import static com.tetr11s.game.controller.GameAction.QUIT;
import static com.tetr11s.game.controller.GameAction.ROTATE_CW;
import static com.tetr11s.game.controller.GameAction.SOFT_DROP;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 키를 꾹 눌렀을 때의 반복 입력 (DAS / ARR).
 * 계산을 알아보기 쉽게 DAS = 100ms, ARR = 20ms로 만든다.
 */
class KeyRepeaterTest {

    private static final long DAS = 100;
    private static final long ARR = 20;

    private final KeyRepeater repeater = new KeyRepeater(DAS, ARR);

    // ---------- 생성 ----------

    @Test
    void 기본값은_DAS_170ms_ARR_50ms() {
        KeyRepeater defaults = new KeyRepeater();
        defaults.press(MOVE_LEFT, 0);

        assertEquals(List.of(), defaults.update(KeyRepeater.DEFAULT_DAS_MS - 1));
        assertEquals(List.of(MOVE_LEFT), defaults.update(KeyRepeater.DEFAULT_DAS_MS));
        assertEquals(List.of(), defaults.update(KeyRepeater.DEFAULT_DAS_MS + KeyRepeater.DEFAULT_ARR_MS - 1));
        assertEquals(List.of(MOVE_LEFT), defaults.update(KeyRepeater.DEFAULT_DAS_MS + KeyRepeater.DEFAULT_ARR_MS));
    }

    @Test
    void DAS가_음수거나_ARR이_0_이하이면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new KeyRepeater(-1, ARR));
        assertThrows(IllegalArgumentException.class, () -> new KeyRepeater(DAS, 0));
        assertThrows(IllegalArgumentException.class, () -> new KeyRepeater(DAS, -5));
    }

    @Test
    void DAS가_0이면_바로_반복을_시작할_수_있다() {
        KeyRepeater noDelay = assertDoesNotThrow(() -> new KeyRepeater(0, ARR));
        noDelay.press(MOVE_RIGHT, 0);

        assertEquals(List.of(MOVE_RIGHT), noDelay.update(0));
    }

    // ---------- 반복하는 동작 ----------

    @Test
    void 좌우_이동과_소프트_드롭만_반복한다() {
        assertTrue(KeyRepeater.isRepeatable(MOVE_LEFT));
        assertTrue(KeyRepeater.isRepeatable(MOVE_RIGHT));
        assertTrue(KeyRepeater.isRepeatable(SOFT_DROP));

        assertFalse(KeyRepeater.isRepeatable(ROTATE_CW));
        assertFalse(KeyRepeater.isRepeatable(HARD_DROP));
        assertFalse(KeyRepeater.isRepeatable(PAUSE));
        assertFalse(KeyRepeater.isRepeatable(QUIT));
    }

    // ---------- 처음 누른 순간 ----------

    @Test
    void 처음_누르면_true를_돌려줘서_바로_1번_실행된다() {
        assertTrue(repeater.press(MOVE_LEFT, 0));
        assertTrue(repeater.press(ROTATE_CW, 0));
    }

    @Test
    void 누르고_있는_키를_다시_누르면_운영체제_자동_반복으로_보고_무시한다() {
        repeater.press(MOVE_LEFT, 0);

        assertFalse(repeater.press(MOVE_LEFT, 30));
        assertFalse(repeater.press(MOVE_LEFT, 60));
    }

    @Test
    void 키를_뗐다가_다시_누르면_다시_바로_실행된다() {
        repeater.press(ROTATE_CW, 0);
        repeater.release(ROTATE_CW, 10);

        assertTrue(repeater.press(ROTATE_CW, 20));
    }

    // ---------- 좌우 이동: DAS 후 ARR 간격 ----------

    @Test
    void 좌우_이동은_DAS가_지나기_전에는_반복하지_않는다() {
        repeater.press(MOVE_LEFT, 0);

        assertEquals(List.of(), repeater.update(DAS - 1));
    }

    @Test
    void 좌우_이동은_DAS가_지나면_ARR_간격으로_반복한다() {
        repeater.press(MOVE_LEFT, 0);

        assertEquals(List.of(MOVE_LEFT), repeater.update(DAS));
        assertEquals(List.of(), repeater.update(DAS + ARR - 1));
        assertEquals(List.of(MOVE_LEFT), repeater.update(DAS + ARR));
        assertEquals(List.of(MOVE_LEFT), repeater.update(DAS + 2 * ARR));
    }

    @Test
    void 프레임이_늦게_와도_밀린_반복을_빠짐없이_실행한다() {
        repeater.press(MOVE_RIGHT, 0);

        // DAS, DAS+ARR, DAS+2ARR, DAS+3ARR 네 번이 한 프레임에 몰려 있다
        List<GameAction> fired = repeater.update(DAS + 3 * ARR);
        assertEquals(Collections.nCopies(4, MOVE_RIGHT), fired);

        // 이미 실행한 반복은 다시 나오지 않는다
        assertEquals(List.of(), repeater.update(DAS + 3 * ARR));
        assertEquals(List.of(MOVE_RIGHT), repeater.update(DAS + 4 * ARR));
    }

    // ---------- 소프트 드롭: 기다리지 않고 ARR 간격 ----------

    @Test
    void 소프트_드롭은_DAS를_기다리지_않고_ARR_간격으로_반복한다() {
        repeater.press(SOFT_DROP, 0);

        assertEquals(List.of(), repeater.update(ARR - 1));
        assertEquals(List.of(SOFT_DROP), repeater.update(ARR));
        assertEquals(List.of(SOFT_DROP), repeater.update(2 * ARR));
    }

    // ---------- 반복하지 않는 동작 ----------

    @Test
    void 회전_하드드롭_일시정지_종료는_꾹_눌러도_반복하지_않는다() {
        repeater.press(ROTATE_CW, 0);
        repeater.press(HARD_DROP, 0);
        repeater.press(PAUSE, 0);
        repeater.press(QUIT, 0);

        assertEquals(List.of(), repeater.update(10_000));
    }

    // ---------- 떼기 ----------

    @Test
    void 키를_떼면_반복이_멈춘다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.update(DAS);
        repeater.release(MOVE_LEFT, DAS + 5);

        assertEquals(List.of(), repeater.update(DAS + 10 * ARR));
    }

    @Test
    void 누르지_않은_키를_떼도_아무_일도_없다() {
        assertDoesNotThrow(() -> repeater.release(MOVE_LEFT, 0));
        assertEquals(List.of(), repeater.update(DAS * 10));
    }

    // ---------- 좌우 동시 입력 ----------

    @Test
    void 좌우를_동시에_누르면_나중에_누른_방향만_반복한다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.press(MOVE_RIGHT, 50);

        // 왼쪽은 DAS(100)에 반복될 예정이었지만 취소, 오른쪽은 50 + DAS부터
        assertEquals(List.of(), repeater.update(DAS));
        assertEquals(List.of(MOVE_RIGHT), repeater.update(50 + DAS));
    }

    @Test
    void 나중에_누른_방향을_떼면_아직_누르고_있는_반대_방향이_DAS부터_다시_반복한다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.press(MOVE_RIGHT, 50);
        repeater.release(MOVE_RIGHT, 200);

        assertEquals(List.of(), repeater.update(200 + DAS - 1));
        assertEquals(List.of(MOVE_LEFT), repeater.update(200 + DAS));
    }

    @Test
    void 반대_방향을_누르고_있지_않으면_떼도_다시_반복하지_않는다() {
        repeater.press(MOVE_RIGHT, 0);
        repeater.release(MOVE_RIGHT, 10);

        assertEquals(List.of(), repeater.update(10 + DAS * 5));
    }

    @Test
    void 이동과_소프트_드롭은_함께_반복할_수_있다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.press(SOFT_DROP, 0);

        // 소프트 드롭은 ARR마다, 이동은 DAS가 지나야 시작
        assertEquals(List.of(SOFT_DROP), repeater.update(ARR));
        assertEquals(List.of(SOFT_DROP), repeater.update(2 * ARR));

        // DAS(100) 시점: 이동 첫 반복 + 소프트 드롭(60, 80, 100) — GameAction 선언 순서대로 나온다
        assertEquals(List.of(MOVE_LEFT, SOFT_DROP, SOFT_DROP, SOFT_DROP), repeater.update(DAS));
    }

    // ---------- 일시정지 / 포커스 잃음 ----------

    @Test
    void cancelRepeats는_반복만_멈추고_누르고_있는_키는_기억한다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.cancelRepeats();

        assertEquals(List.of(), repeater.update(DAS * 10));
        // 계속 누르고 있는 상태라 다시 눌러도(운영체제 자동 반복) 실행되지 않는다
        assertFalse(repeater.press(MOVE_LEFT, DAS * 10));
    }

    @Test
    void cancelRepeats_뒤에는_뗐다가_다시_눌러야_실행된다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.cancelRepeats();
        repeater.release(MOVE_LEFT, 500);

        assertTrue(repeater.press(MOVE_LEFT, 600));
        assertEquals(List.of(MOVE_LEFT), repeater.update(600 + DAS));
    }

    @Test
    void clear는_누르고_있던_키도_모두_잊는다() {
        repeater.press(MOVE_LEFT, 0);
        repeater.press(SOFT_DROP, 0);
        repeater.clear();

        assertEquals(List.of(), repeater.update(DAS * 10));
        // 뗀 신호 없이도 다시 누르면 처음 누른 것으로 본다
        assertTrue(repeater.press(MOVE_LEFT, DAS * 10));
        assertTrue(repeater.press(SOFT_DROP, DAS * 10));
    }
}
