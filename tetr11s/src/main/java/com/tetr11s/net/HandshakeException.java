package com.tetr11s.net;

import java.io.IOException;
import java.util.Objects;

/**
 * 연결 직후 서로 같은 프로그램, 같은 버전인지 확인하다가 실패함 (Connection.open에서 던진다)
 */
public class HandshakeException extends IOException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        TIMEOUT("확인에 답하지 않아"),
        NOT_TETR11S("Tetr11s가 아니라서"),
        VERSION_MISMATCH("버전이 달라서");

        private final String why;

        Reason(String why) {
            this.why = why;
        }

        /** 서버 화면용: address에서 접속한 상대를 이 이유로 끊고 계속 기다린다는 안내 */
        public String rejectedMessage(String address) {
            return address + "에서 접속했지만 " + why + " 연결을 끊었습니다. 계속 기다립니다.";
        }
    }

    private final Reason reason;

    public HandshakeException(Reason reason, Throwable cause) {
        super("상대 확인 실패: " + reason, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}