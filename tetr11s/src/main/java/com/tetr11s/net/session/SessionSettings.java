package com.tetr11s.net.session;

import com.tetr11s.game.battle.BattleManager;

/**
 * 세션의 기준값 모음 (테스트에서는 짧은 값을 넣는다)
 *
 * @param tickMs              연결 상태를 검사하는 주기
 * @param pingIntervalMs      핑을 보내는 주기
 * @param rttSamples          왕복 시간 평균에 쓰는 최근 측정 개수
 * @param lagRttMs            평균 왕복 시간이 이 이상이면 랙
 * @param recoverRttMs        랙 상태에서 평균 왕복 시간이 이 아래로 내려와야 풀림 (표시가 깜빡이지 않게 lagRttMs보다 낮게)
 * @param lagSilenceMs        아무것도 받지 못한 시간이 이 이상이면 랙
 * @param disconnectSilenceMs 아무것도 받지 못한 시간이 이 이상이면 끊김
 * @param countdownMs         게임 시작 전 카운트다운 길이
 * @param drawWindowMs        먼저 게임오버된 뒤 이 시간 안에 상대도 게임오버되면 무승부
 * @param timeLimitMs         시간제한 모드의 제한 시간
 */
public record SessionSettings(long tickMs, long pingIntervalMs, int rttSamples,
                              long lagRttMs, long recoverRttMs, long lagSilenceMs, long disconnectSilenceMs,
                              long countdownMs, long drawWindowMs, long timeLimitMs) {

    public static final SessionSettings DEFAULT = new SessionSettings(
            100, 500, 4,
            200, 150, 1_000, 5_000,
            3_000, 300, BattleManager.DEFAULT_TIME_LIMIT_MS);

    public SessionSettings {
        if (tickMs < 1 || pingIntervalMs < 1 || rttSamples < 1
                || recoverRttMs < 1 || recoverRttMs > lagRttMs
                || lagSilenceMs < 1 || disconnectSilenceMs <= lagSilenceMs
                || countdownMs < 0 || drawWindowMs < 0 || timeLimitMs < 1) {
            throw new IllegalArgumentException("세션 설정값이 잘못됨: " + this);
        }
    }
}
