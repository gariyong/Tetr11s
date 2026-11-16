package com.tetr11s.net;

import java.io.IOException;
import java.util.Objects;

/**
 * 연결 직후 서로 같은 프로그램, 같은 버전인지 확인하다가 실패함 (Connection.open에서 던진다)
 */
public class HandshakeException extends IOException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 정해진 시간 안에 상대가 답하지 않음 */
        TIMEOUT,
        /** 상대가 Tetr11s가 아님 (그 포트를 다른 프로그램이 쓰고 있음) */
        NOT_TETR11S,
        /** 상대의 프로그램 버전이 다름 (Message.PROTOCOL_VERSION이 다름) */
        VERSION_MISMATCH
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