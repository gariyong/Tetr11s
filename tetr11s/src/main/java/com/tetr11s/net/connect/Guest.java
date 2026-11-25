package com.tetr11s.net.connect;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;

/**
 * 클라이언트 쪽: 서버에 접속하고, 버전 확인까지 마친 Connection을 넘겨준다
 *
 * 흐름 (백그라운드 스레드)
 * 1. 서버에 접속한다 (정해진 시간까지)          → CONNECTING
 * 2. 같은 프로그램·같은 버전인지 확인한다        → CHECKING
 * 3. 확인되면 연결을 넘긴다                    → onConnected
 * 어디서든 실패하면 이유와 함께 끝난다            → onFailed
 *
 * 주소 형식 검사는 화면에서 PeerAddress.parse로 먼저 한다
 * 넘겨준 Connection은 아직 start하지 않은 상태다
 */
public final class Guest {

    /** 서버에 접속할 때 기다리는 시간 */
    public static final int CONNECT_TIMEOUT_MS = 3_000;

    private final PeerAddress address;
    private final int connectTimeoutMs;
    private final Connection.Options options;
    private final ConnectNotifier notifier;
    private final Socket socket = new Socket();

    private Guest(PeerAddress address, int connectTimeoutMs, Connection.Options options, ConnectNotifier notifier) {
        this.address = address;
        this.connectTimeoutMs = connectTimeoutMs;
        this.options = options;
        this.notifier = notifier;
    }

    /** 서버에 접속한다 (바로 돌아오고, 진행 상황은 listener로 온다) */
    public static Guest start(PeerAddress address, ConnectListener listener, Executor callbacks) {
        return start(address, CONNECT_TIMEOUT_MS, Connection.Options.DEFAULT, listener, callbacks);
    }

    /**
     * @param connectTimeoutMs 접속을 기다리는 시간
     * @param callbacks        listener를 부를 실행기 (앱: Platform::runLater)
     */
    public static Guest start(PeerAddress address, int connectTimeoutMs, Connection.Options options,
                              ConnectListener listener, Executor callbacks) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(options, "options");
        if (connectTimeoutMs < 1) {
            throw new IllegalArgumentException("접속 대기 시간은 1ms 이상: " + connectTimeoutMs);
        }
        Guest guest = new Guest(address, connectTimeoutMs, options, new ConnectNotifier(listener, callbacks));
        Thread thread = new Thread(guest::run, "p2p-guest");
        thread.setDaemon(true);
        thread.start();
        return guest;
    }

    /** 접속을 그만둔다 (접속 중이든 확인 중이든 바로 멈춤). 부른 뒤에는 콜백이 오지 않는다 */
    public void cancel() {
        notifier.cancel();
        try {
            socket.close();   // 막혀 있던 connect나 확인이 예외로 깨어난다
        } catch (IOException ignored) {
            // 닫다가 난 오류는 할 수 있는 일이 없다
        }
    }

    private void run() {
        notifier.step(ConnectListener.Step.CONNECTING, address.toString());
        try {
            socket.connect(address.socketAddress(), connectTimeoutMs);
        } catch (IOException e) {
            notifier.failed(connectFailure(e), e);   // 취소해서 닫힌 것이면 notifier가 무시한다
            return;
        }

        notifier.step(ConnectListener.Step.CHECKING, address.ip().getHostAddress());
        try {
            notifier.connected(Connection.open(socket, options));
        } catch (IOException e) {
            notifier.failed(handshakeFailure(e), e);
        }
    }

    /** 접속 단계의 예외를 실패 이유로 바꾼다 (예외 메시지는 운영체제와 상관없이 JDK가 정한 영어 문장) */
    static ConnectListener.Failure connectFailure(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return ConnectListener.Failure.TIMEOUT;        // 정해진 시간이 지남
        }
        if (e instanceof NoRouteToHostException) {
            return ConnectListener.Failure.UNREACHABLE;    // 가는 길이 없음
        }
        if (e instanceof ConnectException) {
            String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            return message.contains("timed out")
                    ? ConnectListener.Failure.TIMEOUT      // 운영체제가 정한 시간이 먼저 지남
                    : ConnectListener.Failure.REFUSED;     // 그 주소에 서버가 없음
        }
        return ConnectListener.Failure.IO_ERROR;
    }

    /** 확인 단계의 예외를 실패 이유로 바꾼다 */
    static ConnectListener.Failure handshakeFailure(IOException e) {
        if (e instanceof HandshakeException handshake) {
            return switch (handshake.reason()) {
                case TIMEOUT -> ConnectListener.Failure.HANDSHAKE_TIMEOUT;
                case NOT_TETR11S -> ConnectListener.Failure.NOT_TETR11S;
                case VERSION_MISMATCH -> ConnectListener.Failure.VERSION_MISMATCH;
            };
        }
        return ConnectListener.Failure.DISCONNECTED;   // 확인 도중 서버가 끊음
    }
}