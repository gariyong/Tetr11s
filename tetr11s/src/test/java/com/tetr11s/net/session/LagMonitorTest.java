package com.tetr11s.net.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tetr11s.net.session.LagMonitor.State;

/** 기본 기준값: 평균 200ms 이상 또는 1초 무응답이면 랙, 150ms 아래로 내려와야 해제, 5초 무응답이면 끊김 */
class LagMonitorTest {

    private final LagMonitor monitor = new LagMonitor(SessionSettings.DEFAULT);

    private void addAll(long... rtts) {
        for (long rtt : rtts) {
            monitor.addRtt(rtt);
        }
    }

    @Test
    void 처음에는_정상이고_왕복_시간은_모른다() {
        assertEquals(State.NORMAL, monitor.update(0));
        assertEquals(-1, monitor.averageRttMillis());
    }

    @Test
    void 아직_못_잰_값은_무시한다() {
        addAll(-1, 10, -1, 20);

        assertEquals(15, monitor.averageRttMillis());
    }

    @Test
    void 최근_4개만_평균낸다() {
        addAll(1_000, 10, 20, 30, 40);   // 1000은 밀려난다

        assertEquals(25, monitor.averageRttMillis());
    }

    @Test
    void 평균이_200ms_이상이면_랙이고_아래면_정상이다() {
        addAll(190, 190, 200, 210);   // 평균 197
        assertEquals(State.NORMAL, monitor.update(0));

        addAll(230);                  // 190, 200, 210, 230 → 평균 207
        assertEquals(State.LAGGING, monitor.update(0));
    }

    @Test
    void 일초_동안_아무것도_못_받으면_랙이다() {
        addAll(5);

        assertEquals(State.NORMAL, monitor.update(999));
        assertEquals(State.LAGGING, monitor.update(1_000));
    }

    @Test
    void 랙은_평균이_150ms_아래로_내려와야_풀린다() {
        addAll(300, 300, 300, 300);
        assertEquals(State.LAGGING, monitor.update(0));

        addAll(170, 170, 170, 170);   // 200 아래지만 150 이상
        assertEquals(State.LAGGING, monitor.update(0));

        addAll(140, 140, 140, 140);
        assertEquals(State.NORMAL, monitor.update(0));
    }

    @Test
    void 무응답으로_생긴_랙은_다시_받아야_풀린다() {
        addAll(5);
        assertEquals(State.LAGGING, monitor.update(1_500));

        assertEquals(State.LAGGING, monitor.update(1_200));   // 아직 못 받음
        assertEquals(State.NORMAL, monitor.update(50));       // 다시 받기 시작함
    }

    @Test
    void 오초_동안_아무것도_못_받으면_끊김이고_돌아오지_않는다() {
        assertEquals(State.DISCONNECTED, monitor.update(5_000));

        assertEquals(State.DISCONNECTED, monitor.update(0));
    }
}
