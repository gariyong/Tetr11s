package com.tetr11s.net.connect;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.Executor;

import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;

/**
 * 서버 쪽: 포트를 열고 클라이언트를 기다렸다가, 버전 확인까지 마친 Connection을 넘겨준다
 *
 * 흐름 (백그라운드 스레드)
 * 1. 포트를 연다 (실패하면 PORT_IN_USE)                       → LISTENING
 * 2. 접속을 기다린다
 * 3. 누가 오면 같은 프로그램·같은 버전인지 확인한다              → CHECKING
 *    - 확인에 실패한 상대는 끊고 2로 돌아간다                    → onPeerRejected, LISTENING
 * 4. 확인되면 대기 포트를 닫고(이후 접속은 거부) 연결을 넘긴다     → onConnected
 *
 * 넘겨준 Connection은 아직 start하지 않은 상태다
 */
public final class Host {

    private final InetAddress bindAddress;   // null이면 모든 네트워크에서 받는다
    private final int port;
    private final Connection.Options options;
    private final ConnectNotifier notifier;

    private volatile ServerSocket server;    // 접속을 기다리는 소켓
    private volatile Socket checking;        // 지금 확인 중인 상대

    private Host(InetAddress bindAddress, int port, Connection.Options options, ConnectNotifier notifier) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.options = options;
        this.notifier = notifier;
    }

    /** 모든 네트워크에서 접속을 받는 서버를 연다 (바로 돌아오고, 진행 상황은 listener로 온다) */
    public static Host start(int port, ConnectListener listener, Executor callbacks) {
        return start(null, port, Connection.Options.DEFAULT, listener, callbacks);
    }

    /**
     * @param bindAddress 접속을 받을 주소 (null이면 모든 네트워크, 테스트에서는 127.0.0.1)
     * @param port        열 포트 (0이면 비어 있는 포트를 고른다. 테스트용)
     * @param callbacks   listener를 부를 실행기 (앱: Platform::runLater)
     */
    public static Host start(InetAddress bindAddress, int port, Connection.Options options,
                             ConnectListener listener, Executor callbacks) {
        Objects.requireNonNull(options, "options");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("포트는 0~65535: " + port);
        }
        Host host = new Host(bindAddress, port, options, new ConnectNotifier(listener, callbacks));
        Thread thread = new Thread(host::run, "p2p-host");
        thread.setDaemon(true);
        thread.start();
        return host;
    }

    /**
     * 그만 기다린다 (대기 중이든 확인 중이든 바로 멈춤). 부른 뒤에는 콜백이 오지 않는다
     * 이미 onConnected로 넘겨준 Connection은 건드리지 않는다 (받은 쪽이 닫는다)
     */
    public void cancel() {
        notifier.cancel();
        closeQuietly(server);
        closeQuietly(checking);
    }

    /** 실제로 연 포트 (아직 열기 전이면 -1) */
    public int localPort() {
        ServerSocket listening = server;
        return listening == null ? -1 : listening.getLocalPort();
    }

    private void run() {
        ServerSocket listening;
        try {
            listening = new ServerSocket();
            server = listening;
            if (notifier.isCancelled()) {   // 소켓을 만드는 사이에 취소됨
                closeQuietly(listening);
                return;
            }
            listening.setReuseAddress(true);   // 방금 끝난 연결의 흔적이 남아 있어도 같은 포트를 다시 열 수 있게
            listening.bind(new InetSocketAddress(bindAddress, port), 1);
        } catch (IOException e) {
            notifier.failed(e instanceof BindException
                    ? ConnectListener.Failure.PORT_IN_USE : ConnectListener.Failure.IO_ERROR, e);
            return;
        }

        String listeningPort = String.valueOf(listening.getLocalPort());
        notifier.step(ConnectListener.Step.LISTENING, listeningPort);

        while (true) {
            Socket socket;
            try {
                socket = listening.accept();
            } catch (IOException e) {
                notifier.failed(ConnectListener.Failure.IO_ERROR, e);   // 취소해서 닫힌 것이면 notifier가 무시한다
                return;
            }
            checking = socket;
            if (notifier.isCancelled()) {   // 접속을 받는 사이에 취소됨
                closeQuietly(socket);
                return;
            }

            String peer = socket.getInetAddress().getHostAddress();
            notifier.step(ConnectListener.Step.CHECKING, peer);
            try {
                Connection connection = Connection.open(socket, options);
                closeQuietly(listening);   // 이제부터 오는 접속은 거부된다
                notifier.connected(connection);
                return;
            } catch (HandshakeException e) {
                notifier.rejected(peer, e.reason());   // 다른 버전, 다른 프로그램, 답 없음: 끊고 계속 기다린다
            } catch (IOException e) {
                // 확인 도중 상대가 끊음 (또는 취소): 계속 기다린다
            } finally {
                checking = null;
            }

            if (notifier.isCancelled()) {
                return;
            }
            notifier.step(ConnectListener.Step.LISTENING, listeningPort);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 닫다가 난 오류는 할 수 있는 일이 없다
        }
    }
}