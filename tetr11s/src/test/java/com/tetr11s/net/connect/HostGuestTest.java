package com.tetr11s.net.connect;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.net.CloseReason;
import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;
import com.tetr11s.net.Message;
import com.tetr11s.net.connect.ConnectListener.Failure;
import com.tetr11s.net.connect.ConnectListener.Step;

/**
 * 서버 대기(Host)와 클라이언트 접속(Guest)
 *
 * 서버는 127.0.0.1에서만 연다 (모든 네트워크에서 열면 macOS가 테스트할 때마다 방화벽 허용 창을 띄울 수 있음)
 */
class HostGuestTest {

    // 무언가를 기다리는 최대 시간 (Windows는 닫힌 포트에 접속하면 거부를 알기까지 2초쯤 걸려서 넉넉히)
    private static final long WAIT_MS = 5_000;
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final Connection.Options SHORT_HANDSHAKE = new Connection.Options(3, 1_000, 300, 500, 1_000);

    // 화면 스레드 역할: 콜백을 한 스레드에서 순서대로 실행한다
    private final ExecutorService callbacks = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "test-callbacks");
        thread.setDaemon(true);
        return thread;
    });
    private final List<AutoCloseable> toClose = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUp() {
        for (AutoCloseable resource : toClose) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // 이미 닫힘
            }
        }
        callbacks.shutdownNow();
    }

    // ---------- 정상 접속 ----------

    @Test
    void 서버를_열고_클라이언트가_접속하면_양쪽에_연결이_넘어온다() throws Exception {
        Recorder hostEvents = new Recorder();
        Host host = startHost(0, hostEvents);
        int port = awaitListening(hostEvents);
        assertEquals(port, host.localPort());

        Recorder guestEvents = new Recorder();
        startGuest(port, guestEvents);

        assertEquals(new Recorder.StepEvent(Step.CONNECTING, new PeerAddress(LOOPBACK, port).toString()), guestEvents.next());
        assertEquals(new Recorder.StepEvent(Step.CHECKING, "127.0.0.1"), guestEvents.next());
        Connection guestSide = awaitConnected(guestEvents);
        assertEquals(new Recorder.StepEvent(Step.CHECKING, "127.0.0.1"), hostEvents.next());
        Connection hostSide = awaitConnected(hostEvents);

        // 넘어온 연결은 아직 start 전: start하면 메시지가 오간다
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        hostSide.start(into(received), callbacks);
        guestSide.start(into(new LinkedBlockingQueue<>()), callbacks);
        guestSide.send(new Message.Ready(true, BattleMode.NORMAL));
        assertEquals(new Message.Ready(true, BattleMode.NORMAL), received.poll(WAIT_MS, TimeUnit.MILLISECONDS));
    }

    // ---------- 클라이언트 실패 ----------

    @Test
    void 서버가_없는_포트에_접속하면_REFUSED() throws Exception {
        int closedPort;
        try (ServerSocket temporary = new ServerSocket(0, 1, LOOPBACK)) {
            closedPort = temporary.getLocalPort();   // 비어 있던 포트를 알아내고 바로 닫는다
        }
        Recorder guestEvents = new Recorder();
        startGuest(closedPort, guestEvents);

        assertEquals(Step.CONNECTING, guestEvents.nextStep());
        assertEquals(new Recorder.Failed(Failure.REFUSED), guestEvents.next());
        guestEvents.assertNothingFor(100);
    }

    @Test
    void 접속은_되지만_답하지_않는_서버면_HANDSHAKE_TIMEOUT() throws Exception {
        ServerSocket silent = listen();   // 운영체제가 접속은 받아 주지만 프로그램은 아무 말도 하지 않는다
        Recorder guestEvents = new Recorder();
        toClose.add(Guest.start(new PeerAddress(LOOPBACK, silent.getLocalPort()), Guest.CONNECT_TIMEOUT_MS,
                SHORT_HANDSHAKE, guestEvents, callbacks)::cancel);

        assertEquals(Step.CONNECTING, guestEvents.nextStep());
        assertEquals(Step.CHECKING, guestEvents.nextStep());
        assertEquals(new Recorder.Failed(Failure.HANDSHAKE_TIMEOUT), guestEvents.next());
    }

    @Test
    void 상대가_다른_버전이거나_Tetr11s가_아니면_클라이언트는_실패한다() throws Exception {
        // 다른 버전의 서버
        ServerSocket oldServer = listen();
        fakeServer(oldServer, socket -> {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new Message.Hello(Message.PROTOCOL_VERSION + 1));
            out.flush();
        });
        Recorder first = new Recorder();
        startGuest(oldServer.getLocalPort(), first);
        assertEquals(Step.CONNECTING, first.nextStep());
        assertEquals(Step.CHECKING, first.nextStep());
        assertEquals(new Recorder.Failed(Failure.VERSION_MISMATCH), first.next());

        // 다른 프로그램 (예: SSH 서버)
        ServerSocket otherProgram = listen();
        fakeServer(otherProgram, socket -> socket.getOutputStream().write(
                "SSH-2.0-OpenSSH_9.6\r\n".getBytes(StandardCharsets.US_ASCII)));
        Recorder second = new Recorder();
        startGuest(otherProgram.getLocalPort(), second);
        assertEquals(Step.CONNECTING, second.nextStep());
        assertEquals(Step.CHECKING, second.nextStep());
        assertEquals(new Recorder.Failed(Failure.NOT_TETR11S), second.next());
    }

    @Test
    void 확인_중에_클라이언트를_취소하면_콜백이_오지_않는다() throws Exception {
        ServerSocket silent = listen();
        Recorder guestEvents = new Recorder();
        Guest guest = startGuest(silent.getLocalPort(), guestEvents);
        assertEquals(Step.CONNECTING, guestEvents.nextStep());
        assertEquals(Step.CHECKING, guestEvents.nextStep());

        guest.cancel();

        guestEvents.assertNothingFor(500);
    }

    // ---------- 서버 ----------

    @Test
    void 다른_버전이나_다른_프로그램이_접속하면_서버는_끊고_계속_기다린다() throws Exception {
        Recorder hostEvents = new Recorder();
        startHost(0, hostEvents);
        int port = awaitListening(hostEvents);

        // 다른 버전의 클라이언트
        Socket oldClient = new Socket(LOOPBACK, port);
        toClose.add(oldClient);
        ObjectOutputStream out = new ObjectOutputStream(oldClient.getOutputStream());
        out.writeObject(new Message.Hello(Message.PROTOCOL_VERSION + 1));
        out.flush();
        assertEquals(new Recorder.StepEvent(Step.CHECKING, "127.0.0.1"), hostEvents.next());
        assertEquals(new Recorder.Rejected("127.0.0.1", HandshakeException.Reason.VERSION_MISMATCH), hostEvents.next());
        assertEquals(new Recorder.StepEvent(Step.LISTENING, String.valueOf(port)), hostEvents.next());

        // 다른 프로그램 (예: 웹 브라우저)
        Socket browser = new Socket(LOOPBACK, port);
        toClose.add(browser);
        browser.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(new Recorder.StepEvent(Step.CHECKING, "127.0.0.1"), hostEvents.next());
        assertEquals(new Recorder.Rejected("127.0.0.1", HandshakeException.Reason.NOT_TETR11S), hostEvents.next());
        assertEquals(new Recorder.StepEvent(Step.LISTENING, String.valueOf(port)), hostEvents.next());

        // 그 뒤에 온 정상 클라이언트와는 연결된다
        Recorder guestEvents = new Recorder();
        startGuest(port, guestEvents);
        assertEquals(Step.CHECKING, hostEvents.nextStep());
        awaitConnected(hostEvents);
        assertEquals(Step.CONNECTING, guestEvents.nextStep());
        assertEquals(Step.CHECKING, guestEvents.nextStep());
        awaitConnected(guestEvents);
    }

    @Test
    void 이미_쓰는_포트로_서버를_열면_PORT_IN_USE() throws Exception {
        ServerSocket taken = listen();
        Recorder hostEvents = new Recorder();

        startHost(taken.getLocalPort(), hostEvents);

        assertEquals(new Recorder.Failed(Failure.PORT_IN_USE), hostEvents.next());
    }

    @Test
    void 대기_중에_취소하면_콜백이_오지_않고_같은_포트로_바로_다시_열_수_있다() throws Exception {
        Recorder first = new Recorder();
        Host host = startHost(0, first);
        int port = awaitListening(first);

        host.cancel();
        first.assertNothingFor(300);

        Recorder second = new Recorder();
        startHost(port, second);
        assertEquals(new Recorder.StepEvent(Step.LISTENING, String.valueOf(port)), second.next());
    }

    @Test
    void 첫_상대와_연결된_뒤에는_다른_클라이언트가_접속할_수_없다() throws Exception {
        Recorder hostEvents = new Recorder();
        startHost(0, hostEvents);
        int port = awaitListening(hostEvents);
        Recorder firstGuest = new Recorder();
        startGuest(port, firstGuest);
        assertEquals(Step.CHECKING, hostEvents.nextStep());
        awaitConnected(hostEvents);

        Recorder secondGuest = new Recorder();
        startGuest(port, secondGuest);

        assertEquals(Step.CONNECTING, secondGuest.nextStep());
        assertEquals(new Recorder.Failed(Failure.REFUSED), secondGuest.next());
    }

    @Test
    void 연결이_끝난_뒤_같은_포트로_서버를_다시_열_수_있다() throws Exception {
        // 첫 판
        Recorder hostEvents = new Recorder();
        startHost(0, hostEvents);
        int port = awaitListening(hostEvents);
        Recorder guestEvents = new Recorder();
        startGuest(port, guestEvents);
        assertEquals(Step.CHECKING, hostEvents.nextStep());
        awaitConnected(hostEvents).close();
        guestEvents.nextStep();
        guestEvents.nextStep();
        awaitConnected(guestEvents).close();

        // 같은 포트로 다시
        Recorder hostAgain = new Recorder();
        startHost(port, hostAgain);
        assertEquals(new Recorder.StepEvent(Step.LISTENING, String.valueOf(port)), hostAgain.next());
        Recorder guestAgain = new Recorder();
        startGuest(port, guestAgain);
        assertEquals(Step.CHECKING, hostAgain.nextStep());
        awaitConnected(hostAgain);
    }

    @Test
    void 연결_완료_알림이_화면에_가기_전에_취소하면_연결을_닫는다() throws Exception {
        // 화면 스레드가 바빠서 서버 쪽 알림이 밀린 상황
        ManualExecutor busy = new ManualExecutor();
        Recorder hostEvents = new Recorder();
        Host host = Host.start(LOOPBACK, 0, Connection.Options.DEFAULT, hostEvents, busy);
        toClose.add(host::cancel);
        waitUntil(() -> host.localPort() > 0, "서버가 열리지 않음");

        Recorder guestEvents = new Recorder();
        startGuest(host.localPort(), guestEvents);
        guestEvents.nextStep();
        guestEvents.nextStep();
        Connection guestSide = awaitConnected(guestEvents);
        waitUntil(() -> busy.pending() == 3, "서버 쪽 알림이 쌓이지 않음");   // LISTENING, CHECKING, 연결 완료

        host.cancel();
        busy.runAll();

        hostEvents.assertNothingFor(100);
        // 서버 쪽 연결이 닫혔으므로 클라이언트는 끊김을 받는다
        BlockingQueue<CloseReason> closed = new LinkedBlockingQueue<>();
        guestSide.start(new Connection.Listener() {
            @Override
            public void onMessage(Message message) {
            }

            @Override
            public void onClosed(CloseReason reason, Exception cause) {
                closed.add(reason);
            }
        }, callbacks);
        assertEquals(CloseReason.PEER_CLOSED, closed.poll(WAIT_MS, TimeUnit.MILLISECONDS));
    }

    // ---------- 실패 이유 분류 ----------

    @Test
    void 접속_예외를_실패_이유로_바꾼다() {
        // 실제 네트워크로는 운영체제마다 재현 방법이 달라서 예외를 직접 만들어 확인한다
        assertEquals(Failure.TIMEOUT, Guest.connectFailure(new SocketTimeoutException("Connect timed out")));
        assertEquals(Failure.TIMEOUT, Guest.connectFailure(new ConnectException("Connection timed out")));
        assertEquals(Failure.UNREACHABLE, Guest.connectFailure(new NoRouteToHostException("No route to host")));
        assertEquals(Failure.REFUSED, Guest.connectFailure(new ConnectException("Connection refused")));
        assertEquals(Failure.REFUSED, Guest.connectFailure(new ConnectException("Connection refused: connect")));   // Windows
        assertEquals(Failure.IO_ERROR, Guest.connectFailure(new SocketException("Socket closed")));
    }

    @Test
    void 확인_단계_예외를_실패_이유로_바꾼다() {
        assertEquals(Failure.HANDSHAKE_TIMEOUT,
                Guest.handshakeFailure(new HandshakeException(HandshakeException.Reason.TIMEOUT, null)));
        assertEquals(Failure.NOT_TETR11S,
                Guest.handshakeFailure(new HandshakeException(HandshakeException.Reason.NOT_TETR11S, null)));
        assertEquals(Failure.VERSION_MISMATCH,
                Guest.handshakeFailure(new HandshakeException(HandshakeException.Reason.VERSION_MISMATCH, null)));
        assertEquals(Failure.DISCONNECTED, Guest.handshakeFailure(new EOFException()));
        assertEquals(Failure.DISCONNECTED, Guest.handshakeFailure(new SocketException("Connection reset")));
    }

    // ---------- 테스트 도구 ----------

    private Host startHost(int port, Recorder recorder) {
        Host host = Host.start(LOOPBACK, port, Connection.Options.DEFAULT, recorder, callbacks);
        toClose.add(host::cancel);
        return host;
    }

    private Guest startGuest(int port, Recorder recorder) {
        Guest guest = Guest.start(new PeerAddress(LOOPBACK, port), recorder, callbacks);
        toClose.add(guest::cancel);
        return guest;
    }

    /** LISTENING을 받을 때까지 기다리고, 연 포트를 돌려준다 */
    private static int awaitListening(Recorder recorder) throws InterruptedException {
        Recorder.StepEvent event = assertInstanceOf(Recorder.StepEvent.class, recorder.next());
        assertEquals(Step.LISTENING, event.step());
        return Integer.parseInt(event.detail());
    }

    private Connection awaitConnected(Recorder recorder) throws InterruptedException {
        Connection connection = assertInstanceOf(Recorder.Connected.class, recorder.next()).connection();
        toClose.add(connection::close);
        return connection;
    }

    private ServerSocket listen() throws IOException {
        ServerSocket listening = new ServerSocket(0, 1, LOOPBACK);
        toClose.add(listening);
        return listening;
    }

    private interface SocketAction {
        void run(Socket socket) throws IOException;
    }

    /** 접속을 하나 받아서 action을 하고, 연결은 열어 둔 채로 두는 가짜 서버 */
    private void fakeServer(ServerSocket listening, SocketAction action) {
        Thread thread = new Thread(() -> {
            try {
                Socket socket = listening.accept();
                toClose.add(socket);
                action.run(socket);
            } catch (IOException ignored) {
                // 테스트가 끝나서 닫힘
            }
        }, "fake-server");
        thread.setDaemon(true);
        thread.start();
    }

    private static Connection.Listener into(BlockingQueue<Message> received) {
        return new Connection.Listener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
            }

            @Override
            public void onClosed(CloseReason reason, Exception cause) {
            }
        };
    }

    private static void waitUntil(BooleanSupplier condition, String failMessage) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail(failMessage);
            }
            Thread.sleep(1);
        }
    }

    /** ConnectListener가 받은 것을 순서대로 기록한다 */
    private static final class Recorder implements ConnectListener {

        record StepEvent(Step step, String detail) {
        }

        record Rejected(String address, HandshakeException.Reason reason) {
        }

        record Connected(Connection connection) {
        }

        record Failed(Failure failure) {
        }

        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();

        @Override
        public void onStep(Step step, String detail) {
            events.add(new StepEvent(step, detail));
        }

        @Override
        public void onPeerRejected(String address, HandshakeException.Reason reason) {
            events.add(new Rejected(address, reason));
        }

        @Override
        public void onConnected(Connection connection) {
            events.add(new Connected(connection));
        }

        @Override
        public void onFailed(Failure failure, Exception cause) {
            events.add(new Failed(failure));
        }

        /** 다음 기록 (WAIT_MS 안에 오지 않으면 실패) */
        Object next() throws InterruptedException {
            Object event = events.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(event, "기다려도 오지 않음");
            return event;
        }

        /** 다음 기록이 진행 단계여야 하고, 그 단계를 돌려준다 */
        Step nextStep() throws InterruptedException {
            return assertInstanceOf(StepEvent.class, next()).step();
        }

        /** ms 동안 아무것도 오지 않아야 한다 */
        void assertNothingFor(long ms) throws InterruptedException {
            Object event = events.poll(ms, TimeUnit.MILLISECONDS);
            assertNull(event, "오지 않아야 할 것이 옴: " + event);
        }
    }

    /** 화면 스레드가 바쁜 상황: 콜백을 바로 실행하지 않고 모아 두었다가 runAll()에서 실행한다 */
    private static final class ManualExecutor implements Executor {

        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }
}