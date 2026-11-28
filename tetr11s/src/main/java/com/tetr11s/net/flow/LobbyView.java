package com.tetr11s.net.flow;

import com.tetr11s.game.battle.BattleMode;

/**
 * 연결 직후의 대기 화면(로비)이 할 수 있는 일 (HostWaitView와 같은 이유로 나눠 둠)
 * 모든 메서드는 화면 스레드에서 부른다
 */
public interface LobbyView {

    /** 플레이어가 화면에서 한 일 */
    interface Listener {

        /** 서버만: 모드를 고름 (←→ 또는 클릭) */
        void onModeSelected(BattleMode mode);

        /** '게임 시작' / '준비 취소'를 누름 (Enter 또는 클릭) */
        void onToggleReady();

        /** Esc를 누름 (대전에서 나가기) */
        void onLeave();
    }

    void setListener(Listener listener);

    /**
     * 연결 정보 (화면을 열 때 한 번)
     *
     * @param host        내가 서버인가 (서버만 모드를 바꿀 수 있다)
     * @param peerAddress 상대 IP
     * @param difficulty  난이도 안내 (예: "난이도 보통", 클라이언트는 "난이도는 서버 설정")
     */
    void showConnection(boolean host, String peerAddress, String difficulty);

    /** 로비 상태가 바뀔 때마다: 지금 모드, 내 준비 여부, 상대 준비 여부 */
    void showLobby(BattleMode mode, boolean myReady, boolean peerReady);

    /** 둘 다 준비됨: millis 동안 카운트다운을 보여 준다 (그동안은 Esc만 받는다) */
    void showCountdown(long millis);

    /** 지난 판 결과 (없으면 빈 글자) */
    void showResult(String text);

    /**
     * 연결 상태 (lagging이면 눈에 띄게)
     *
     * @param rttMillis 최근 평균 왕복 시간 (아직 못 쟀으면 음수)
     */
    void showNetwork(boolean lagging, long rttMillis);

    /** 그 밖의 안내 */
    void showMessage(String text);
}