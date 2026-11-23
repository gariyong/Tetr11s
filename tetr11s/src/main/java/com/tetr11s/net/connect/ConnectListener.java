package com.tetr11s.net.connect;

import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;

/**
 * 서버(Host) 대기와 클라이언트(Guest) 접속의 진행 상황을 받는 곳
 *
 * 모든 메서드는 Host.start, Guest.start에 넘긴 실행기에서 불린다 (앱에서는 Platform::runLater)
 * cancel()을 부른 뒤에는 어떤 메서드도 불리지 않는다
 */
public interface ConnectListener {

    /** 진행 단계 (화면의 진행 메시지용) */
    enum Step {
        /** 서버: 포트를 열고 접속을 기다리는 중 (detail: 연 포트) */
        LISTENING,
        /** 클라이언트: 서버에 접속하는 중 (detail: 서버 주소) */
        CONNECTING,
        /** 양쪽: 상대와 연결됐고, 같은 프로그램·같은 버전인지 확인하는 중 (detail: 상대 IP) */
        CHECKING
    }

    /** 실패 이유 (화면의 안내 문구용) */
    enum Failure {
        /** 서버: 그 포트를 이미 다른 프로그램이 쓰고 있음 (다른 Tetr11s 서버가 열려 있는 경우 등) */
        PORT_IN_USE,
        /** 클라이언트: 그 주소에 서버가 없음 (서버를 안 열었거나 주소·포트가 틀림) */
        REFUSED,
        /** 클라이언트: 정해진 시간 안에 서버가 응답하지 않음 (IP가 틀렸거나, 다른 네트워크이거나, 방화벽) */
        TIMEOUT,
        /** 클라이언트: 그 주소로 가는 길이 없음 (와이파이가 꺼짐, 다른 네트워크) */
        UNREACHABLE,
        /** 클라이언트: 확인 도중 서버가 끊음 (서버가 이미 다른 상대와 연결됐을 수 있음) */
        DISCONNECTED,
        /** 클라이언트: 상대가 Tetr11s가 아님 */
        NOT_TETR11S,
        /** 클라이언트: 상대의 프로그램 버전이 다름 */
        VERSION_MISMATCH,
        /** 클라이언트: 연결은 됐지만 상대가 확인에 답하지 않음 */
        HANDSHAKE_TIMEOUT,
        /** 그 밖의 네트워크 오류 */
        IO_ERROR
    }

    /** 진행 단계가 바뀜. detail은 화면에 같이 보여 줄 값 (포트나 주소) */
    void onStep(Step step, String detail);

    /**
     * 서버만: 접속한 상대가 확인에 실패해서 끊고 계속 기다린다 (다른 버전, 다른 프로그램, 답이 없음)
     * 이어서 LISTENING이 다시 온다
     */
    default void onPeerRejected(String address, HandshakeException.Reason reason) {
    }

    /**
     * 연결 완료 (딱 한 번 옴). connection은 아직 start하지 않은 상태라서, 받은 쪽이 start해야 메시지가 오간다
     * 서버는 이 뒤로 다른 접속을 받지 않는다
     */
    void onConnected(Connection connection);

    /** 실패해서 끝남 (딱 한 번 옴) */
    void onFailed(Failure failure, Exception cause);
}