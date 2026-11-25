package com.tetr11s.net.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 연결 상태 판정: 정상 / 랙 / 끊김 (네트워크도 시간도 모르고, 넣어 준 값으로만 판단한다)
 *
 * - 최근 왕복 시간 몇 개의 평균이 lagRttMs 이상이거나, lagSilenceMs 이상 아무것도 받지 못하면 랙
 * - 랙은 평균이 recoverRttMs 아래로 내려오고 다시 받기 시작해야 풀린다 (기준을 낮게 둬서 표시가 깜빡이지 않게)
 * - disconnectSilenceMs 이상 아무것도 받지 못하면 끊김이고, 끊김에서는 돌아오지 않는다
 */
public final class LagMonitor {

    public enum State {
        NORMAL,
        LAGGING,
        DISCONNECTED
    }

    private final SessionSettings settings;
    private final Deque<Long> samples = new ArrayDeque<>();
    private State state = State.NORMAL;

    public LagMonitor(SessionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** 왕복 시간 하나를 더한다 (음수는 "아직 못 잼"이라 무시) */
    public void addRtt(long rttMillis) {
        if (rttMillis < 0) {
            return;
        }
        samples.addLast(rttMillis);
        while (samples.size() > settings.rttSamples()) {
            samples.removeFirst();
        }
    }

    /** 최근 왕복 시간의 평균(ms), 아직 하나도 없으면 -1 */
    public long averageRttMillis() {
        if (samples.isEmpty()) {
            return -1;
        }
        long sum = 0;
        for (long sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    /**
     * 지금 상태를 다시 판정한다
     *
     * @param silentMillis 마지막으로 무엇이든 받은 뒤 지난 시간
     */
    public State update(long silentMillis) {
        if (state == State.DISCONNECTED) {
            return state;
        }
        long average = averageRttMillis();
        if (silentMillis >= settings.disconnectSilenceMs()) {
            state = State.DISCONNECTED;
        } else if (state == State.NORMAL
                && (average >= settings.lagRttMs() || silentMillis >= settings.lagSilenceMs())) {
            state = State.LAGGING;
        } else if (state == State.LAGGING
                && average < settings.recoverRttMs() && silentMillis < settings.lagSilenceMs()) {
            state = State.NORMAL;
        }
        return state;
    }

    public State state() {
        return state;
    }
}
