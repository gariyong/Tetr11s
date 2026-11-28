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
        LISTENING("포트 %s에서 상대의 접속을 기다리는 중입니다..."),
        /** 클라이언트: 서버에 접속하는 중 (detail: 서버 주소) */
        CONNECTING("%s에 접속하는 중입니다..."),
        /** 양쪽: 상대와 연결됐고, 같은 프로그램·같은 버전인지 확인하는 중 (detail: 상대 IP) */
        CHECKING("%s와 연결됐습니다. 같은 버전의 Tetr11s인지 확인하는 중입니다...");

        private final String message;

        Step(String message) {
            this.message = message;
        }

        /** 화면에 보여 줄 문구 (detail: onStep으로 함께 온 값) */
        public String message(String detail) {
            return String.format(message, detail);
        }
    }

    /** 실패 이유 (화면의 안내 문구용) */
    enum Failure {
        PORT_IN_USE("이 포트를 이미 다른 프로그램이 쓰고 있습니다. 열려 있는 다른 Tetr11s 서버를 닫고 다시 시도하세요."),
        REFUSED("그 주소에 열린 서버가 없습니다. 상대가 서버를 열었는지, IP와 포트가 맞는지 확인하세요."),
        TIMEOUT("서버가 응답하지 않습니다. IP가 맞는지, 같은 네트워크인지, 방화벽이 막고 있지 않은지 확인하세요."),
        UNREACHABLE("그 주소로 갈 수 없습니다. 네트워크(와이파이)에 연결되어 있는지 확인하세요."),
        DISCONNECTED("확인 도중 서버가 연결을 끊었습니다. 서버가 이미 다른 상대와 연결됐을 수 있습니다."),
        NOT_TETR11S("상대가 Tetr11s 서버가 아닙니다. 주소를 확인하세요."),
        VERSION_MISMATCH("상대의 Tetr11s 버전이 다릅니다. 같은 버전으로 맞춘 뒤 다시 시도하세요."),
        HANDSHAKE_TIMEOUT("연결은 됐지만 상대가 확인에 답하지 않습니다. 잠시 뒤 다시 시도하세요."),
        IO_ERROR("네트워크 오류가 났습니다. 잠시 뒤 다시 시도하세요.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        /** 화면에 보여 줄 문구 (무엇이 문제인지와 어떻게 하면 되는지) */
        public String message() {
            return message;
        }
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