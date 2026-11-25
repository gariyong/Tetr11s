package com.tetr11s.net.session;

import java.util.List;

import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.net.Message;

/**
 * 세션에서 일어난 일을 받는 곳 (화면)
 *
 * 모든 메서드는 세션을 만들 때 넘긴 실행기(앱에서는 화면 스레드)에서 불린다
 * leave()를 부른 뒤에는 어떤 메서드도 불리지 않는다
 */
public interface SessionListener {

    /** 나 기준 승패 */
    enum Outcome {
        WIN,
        LOSE,
        DRAW
    }

    /** 세션이 끝난 이유 (화면은 에러를 띄우고 P2P 처음 화면으로 돌아간다) */
    enum End {
        /** 상대가 나감 (Bye를 받음) */
        PEER_LEFT,
        /** 연결이 끊김 (상대 프로그램이 꺼짐, 네트워크 오류) */
        CONNECTION_LOST,
        /** 정해진 시간 동안 아무것도 받지 못함 */
        TIMEOUT,
        /** 상대가 잘못된 데이터를 보냄 */
        PROTOCOL_ERROR
    }

    /** 로비 상태가 바뀜: 모드, 양쪽 준비 여부 (서버도 자기가 바꾼 것을 이걸로 받는다) */
    void onLobby(Message.LobbyState lobby);

    /** 양쪽이 준비되어 카운트다운 시작. start.countdownMs() 뒤에 onGameStart가 온다 */
    void onCountdown(Message.Start start);

    /** 게임 시작: start의 모드·난이도·seed로 내 게임을 만들어 시작한다 */
    void onGameStart(Message.Start start);

    /** 상대가 보낸 공격 줄 (내 게임의 receiveGarbage에 넣는다) */
    void onAttack(List<GarbageLine> lines);

    /** 시간제한 모드에서 시간이 끝남: 내 게임을 멈추고 submitFinalScore(점수)를 부른다 */
    void onTimeUp();

    /** 승패가 정해짐. 이 뒤로 세션은 로비 상태다 (준비는 풀리고 모드는 그대로) */
    void onResult(Outcome outcome);

    /**
     * 연결 상태 (핑 주기마다, 그리고 랙 상태가 바뀔 때)
     *
     * @param lagging      랙 표시를 할지
     * @param rttMillis    최근 평균 왕복 시간 (아직 못 쟀으면 -1)
     * @param silentMillis 마지막으로 무엇이든 받은 뒤 지난 시간
     */
    void onNetworkStatus(boolean lagging, long rttMillis, long silentMillis);

    /** 세션이 끝남 (딱 한 번 옴) */
    void onEnded(End reason);
}
