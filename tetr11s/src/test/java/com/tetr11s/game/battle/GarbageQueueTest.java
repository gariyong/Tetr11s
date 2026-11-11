package com.tetr11s.game.battle;

import static com.tetr11s.game.battle.TestLines.hole;
import static com.tetr11s.game.battle.TestLines.holes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GarbageQueueTest {

    private static final int W = 10;

    /** 빈 칸 위치가 from, from+1, ... 인 줄 n개 (위에서 아래 순서) */
    private static List<GarbageLine> attack(int from, int n) {
        return holes(W, from, n);
    }

    // ---------- 기본 ----------

    @Test
    void 처음에는_비어있다() {
        GarbageQueue q = new GarbageQueue(W);
        assertEquals(0, q.size());
        assertEquals(List.of(), q.lines());
    }

    @Test
    void 받은_줄은_모양과_순서_그대로_보관된다() {
        GarbageQueue q = new GarbageQueue(W);
        assertEquals(3, q.receiveAttack(attack(0, 3)));
        assertEquals(attack(0, 3), q.lines());
        assertEquals(3, q.size());
    }

    @Test
    void lines는_보기만_하고_대기열을_비우지_않는다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 2));
        q.lines();
        q.lines();
        assertEquals(2, q.size());
    }

    // ---------- 요구사항: 아래쪽으로 추가 ----------

    @Test
    void 여러_번_공격받으면_새_줄은_기존_줄의_아래쪽에_추가된다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 2));   // 빈 칸 0, 1
        q.receiveAttack(attack(5, 3));   // 빈 칸 5, 6, 7

        List<GarbageLine> expected = new ArrayList<>(attack(0, 2));
        expected.addAll(attack(5, 3));
        assertEquals(expected, q.lines());
    }

    // ---------- 요구사항: 최대 10줄 ----------

    @Test
    void 최대_줄_수는_10줄이다() {
        assertEquals(10, GarbageQueue.MAX_GARBAGE_LINES);
    }

    @Test
    void 정확히_10줄까지_받을_수_있다() {
        GarbageQueue q = new GarbageQueue(W);
        assertEquals(4, q.receiveAttack(attack(0, 4)));
        assertEquals(6, q.receiveAttack(attack(4, 6)));
        assertEquals(10, q.size());
    }

    @Test
    void 이미_10줄이_차_있으면_새_공격은_무시된다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 10));
        List<GarbageLine> before = q.lines();

        assertEquals(0, q.receiveAttack(attack(3, 4)));
        assertEquals(before, q.lines());
    }

    @Test
    void 합쳐서_10줄이_넘으면_새_공격의_아래쪽을_잘라내고_추가한다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 7));                    // 7줄, 남은 자리 3
        assertEquals(3, q.receiveAttack(attack(7, 4)));   // 4줄 중 위 3줄만

        List<GarbageLine> expected = new ArrayList<>(attack(0, 7));
        expected.addAll(attack(7, 3));                    // 마지막(가장 아래) 1줄은 잘림
        assertEquals(expected, q.lines());
    }

    @Test
    void 비어_있을_때_10줄이_넘는_공격도_아래쪽을_잘라낸다() {
        GarbageQueue q = new GarbageQueue(W);
        List<GarbageLine> big = attack(0, 12);
        assertEquals(10, q.receiveAttack(big));
        assertEquals(big.subList(0, 10), q.lines());
    }

    @Test
    void 빈_공격은_아무것도_바꾸지_않는다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 2));
        assertEquals(0, q.receiveAttack(List.of()));
        assertEquals(2, q.size());
    }

    // ---------- 꺼내기 ----------

    @Test
    void takeAll은_모든_줄을_순서대로_꺼내고_비운다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 2));
        q.receiveAttack(attack(4, 1));

        assertEquals(List.of(hole(W, 0), hole(W, 1), hole(W, 4)), q.takeAll());
        assertEquals(0, q.size());
    }

    @Test
    void 비어_있을_때_takeAll은_빈_리스트다() {
        assertEquals(List.of(), new GarbageQueue(W).takeAll());
    }

    @Test
    void 꺼낸_뒤에는_다시_10줄까지_받을_수_있다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 10));
        q.takeAll();
        assertEquals(10, q.receiveAttack(attack(0, 10)));
    }

    @Test
    void reset하면_비워진다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 5));
        q.reset();
        assertEquals(0, q.size());
    }

    // ---------- 외부에서 바꿀 수 없음 ----------

    @Test
    void lines로_받은_리스트는_수정할_수_없다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 1));
        assertThrows(UnsupportedOperationException.class, () -> q.lines().add(hole(W, 3)));
    }

    @Test
    void takeAll로_받은_리스트는_수정할_수_없다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 1));
        assertThrows(UnsupportedOperationException.class, () -> q.takeAll().add(hole(W, 3)));
    }

    @Test
    void 넘겨준_공격_리스트를_나중에_바꿔도_영향이_없다() {
        GarbageQueue q = new GarbageQueue(W);
        List<GarbageLine> a = new ArrayList<>(attack(0, 2));
        q.receiveAttack(a);
        a.clear();
        assertEquals(2, q.size());
    }

    // ---------- 잘못된 입력 ----------

    @Test
    void 너비가_다른_줄이_섞여_있으면_예외이고_대기열은_그대로다() {
        GarbageQueue q = new GarbageQueue(W);
        q.receiveAttack(attack(0, 1));
        List<GarbageLine> bad = List.of(hole(W, 2), TestLines.of("XXX."));
        assertThrows(IllegalArgumentException.class, () -> q.receiveAttack(bad));
        assertEquals(1, q.size());
    }

    @Test
    void null_입력은_예외() {
        GarbageQueue q = new GarbageQueue(W);
        assertThrows(NullPointerException.class, () -> q.receiveAttack(null));
        List<GarbageLine> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> q.receiveAttack(withNull));
    }

    @Test
    void 너비가_0_이하면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new GarbageQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new GarbageQueue(-1));
    }
}
