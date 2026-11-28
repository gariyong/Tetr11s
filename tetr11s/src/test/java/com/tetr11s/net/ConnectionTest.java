package com.tetr11s.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.BoardSnapshot;
import com.tetr11s.game.battle.BattleMode;

class ConnectionTest {

    /** 무언가를 기다리는 최대 시간 (이보다 오래 걸리면 실패) */
    private static final long WAIT_MS = 3_000;
    /** 느린 연결 테스트의 속도: 8KB/s (64kbps). 스냅샷을 초당 60개 보내면 감당하지 못하는 속도 */
    private static final int SLOW_LINK = 8_000;

    // 화면 스레드 역할: 콜백을 한 스레드에서 순서대로 실행한다
    private final ExecutorService callbacks = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "test-callbacks");
        thread.setDaemon(true);
        return thread;
    });
    private final List<AutoCloseable> toClose = new ArrayList<>();

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

    // ---------- 연결 확인 (open) ----------

    @Test
    void 같은_버전끼리는_연결된다() throws Exception {
        Pair p = connect();

        assertFalse(p.client().isClosed());
        assertFalse(p.server().isClosed());
        assertEquals("127.0.0.1", p.client().remoteAddress());
    }

    @Test
    void 답이_없는_상대는_정해진_시간_뒤에_실패한다() throws Exception {
        Connection.Options shortWait = new Connection.Options(3, 1_000, 300, 500, 1_000);
        ServerSocket listening = listen();
        Future<Connection> accepted = acceptAndOpen(listening, shortWait);

        long start = System.nanoTime();
        toClose.add(new Socket(InetAddress.getLoopbackAddress(), listening.getLocalPort()));   // 아무것도 보내지 않는 상대

        assertEquals(HandshakeException.Reason.TIMEOUT, handshakeFailure(accepted).reason());
        long ms = elapsedMillis(start);
        assertTrue(ms >= 250 && ms < 2_000, "걸린 시간: " + ms + "ms");
    }

    @Test
    void Tetr11s가_아닌_상대는_거부한다() throws Exception {
        // 1. 자바 직렬화가 아닌 데이터를 보내는 상대 (예: 웹 브라우저)
        ServerSocket listening = listen();
        Future<Connection> accepted = acceptAndOpen(listening, Connection.Options.DEFAULT);
        Socket web = new Socket(InetAddress.getLoopbackAddress(), listening.getLocalPort());
        toClose.add(web);
        web.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

        assertEquals(HandshakeException.Reason.NOT_TETR11S, handshakeFailure(accepted).reason());

        // 2. 첫 메시지가 Hello가 아닌 상대
        ServerSocket listening2 = listen();
        Future<Connection> accepted2 = acceptAndOpen(listening2, Connection.Options.DEFAULT);
        toClose.add(new RawPeer(listening2.getLocalPort(), new Message.Ready(true, BattleMode.NORMAL)));

        assertEquals(HandshakeException.Reason.NOT_TETR11S, handshakeFailure(accepted2).reason());
    }

    @Test
    void 버전이_다르면_거부한다() throws Exception {
        ServerSocket listening = listen();
        Future<Connection> accepted = acceptAndOpen(listening, Connection.Options.DEFAULT);
        toClose.add(new RawPeer(listening.getLocalPort(), new Message.Hello(Message.PROTOCOL_VERSION + 1)));

        assertEquals(HandshakeException.Reason.VERSION_MISMATCH, handshakeFailure(accepted).reason());
    }

    // ---------- 일반 메시지 ----------

    @Test
    void 일반_메시지는_보낸_순서대로_전부_도착한다() throws Exception {
        Pair p = connect();
        start(p.client());
        Recorder server = start(p.server());

        for (int i = 0; i < 500; i++) {
            p.client().send(new Message.GameOver(i));
        }

        for (int i = 0; i < 500; i++) {
            assertEquals(new Message.GameOver(i), server.next());
        }
        server.assertNothingFor(100);
    }

    @Test
    void Connection이_처리하는_메시지는_send로_보낼_수_없다() throws Exception {
        Pair p = connect();
        List<Message> internal = List.of(
                new Message.Hello(Message.PROTOCOL_VERSION),
                new Message.Bye(),
                new Message.Snapshot(1, board(0)),
                new Message.SnapshotAck(1),
                new Message.Ping(1),
                new Message.Pong(1));

        for (Message message : internal) {
            assertThrows(IllegalArgumentException.class, () -> p.client().send(message),
                    message.getClass().getSimpleName());
        }
    }

    // ---------- 스냅샷 ----------

    @Test
    void 스냅샷은_200ms_안에_도착한다() throws Exception {
        Pair p = connect();
        start(p.client());
        start(p.server());

        long start = System.nanoTime();
        p.client().sendSnapshot(board(7));
        waitForSnapshot(p.server(), 7);

        long ms = elapsedMillis(start);
        assertTrue(ms < 200, "걸린 시간: " + ms + "ms");
    }

    @Test
    void 스냅샷을_연달아_보내면_마지막_것은_반드시_도착한다() throws Exception {
        Pair p = connect();
        start(p.client());
        start(p.server());

        for (int i = 1; i <= 500; i++) {
            p.client().sendSnapshot(board(i));
        }

        waitForSnapshot(p.server(), 500);
    }

    @Test
    void 확인을_받지_못한_스냅샷이_3개면_더_보내지_않고_확인이_오면_최신_것을_보낸다() throws Exception {
        ServerSocket listening = listen();
        Future<Connection> accepted = acceptAndOpen(listening, Connection.Options.DEFAULT);
        RawPeer peer = new RawPeer(listening.getLocalPort(), new Message.Hello(Message.PROTOCOL_VERSION));
        toClose.add(peer);
        Connection connection = accepted.get(WAIT_MS, TimeUnit.MILLISECONDS);
        toClose.add(connection::close);
        start(connection);

        for (int i = 1; i <= 10; i++) {
            connection.sendSnapshot(board(i));
            Thread.sleep(50);
        }

        // 1, 2, 3번만 오고 멈춘다 (가짜 상대는 SnapshotAck를 보내지 않음)
        for (int i = 1; i <= 3; i++) {
            Message.Snapshot snapshot = assertInstanceOf(Message.Snapshot.class, peer.next());
            assertEquals(i, snapshot.seq());
            assertEquals(i, snapshot.board().score());
        }
        assertNull(peer.poll(300), "확인 없이 4번째 스냅샷을 보냄");

        // 3번까지 받았다고 알리면, 그 사이에 쌓인 4~10번 중 최신(10)만 온다
        peer.send(new Message.SnapshotAck(3));
        Message.Snapshot snapshot = assertInstanceOf(Message.Snapshot.class, peer.next());
        assertEquals(4, snapshot.seq());
        assertEquals(10, snapshot.board().score());
        assertNull(peer.poll(200), "보낼 것이 없는데 또 보냄");
    }

    @Test
    void 느린_연결에_스냅샷을_쏟아부어도_일반_메시지는_늦지_않는다() throws Exception {
        Pair p = connect(0, SLOW_LINK);
        start(p.client());
        Recorder server = start(p.server());
        Thread spammer = repeat(5, () -> p.client().sendSnapshot(board(1)));   // 5ms마다 (연결이 감당 못하는 양)
        Thread.sleep(1_000);

        long start = System.nanoTime();
        p.client().send(new Message.GameOver(42));
        assertEquals(new Message.GameOver(42), server.next());
        long ms = elapsedMillis(start);
        spammer.interrupt();

        assertTrue(ms < 500, "걸린 시간: " + ms + "ms");
    }

    @Test
    void 느린_연결에서도_스냅샷_지연이_쌓이지_않는다() throws Exception {
        Pair p = connect(0, SLOW_LINK);
        start(p.client());
        start(p.server());

        // 16ms마다(초당 약 60개) 보낸 시각을 점수 칸에 넣어서 보낸다
        long base = System.nanoTime();
        Thread sender = repeat(16, () -> p.client().sendSnapshot(board((int) elapsedMillis(base))));

        // 받은 스냅샷마다 "도착 시각 - 보낸 시각"을 기록한다
        List<Long> early = new ArrayList<>();   // 0.5~1.5초에 도착한 것
        List<Long> late = new ArrayList<>();    // 2~3초에 도착한 것
        int lastScore = -1;
        while (elapsedMillis(base) < 3_000) {
            BoardSnapshot snapshot = p.server().peerSnapshot();
            if (snapshot != null && snapshot.score() != lastScore) {
                lastScore = snapshot.score();
                long now = elapsedMillis(base);
                long age = now - lastScore;
                if (now >= 500 && now < 1_500) {
                    early.add(age);
                } else if (now >= 2_000) {
                    late.add(age);
                }
            }
            Thread.sleep(1);
        }
        sender.interrupt();

        long earlyMedian = median(early);
        long lateMedian = median(late);
        assertTrue(lateMedian < 400, "2~3초 지연(중앙값): " + lateMedian + "ms");
        assertTrue(lateMedian - earlyMedian < 150,
                "지연이 늘어남: " + earlyMedian + "ms → " + lateMedian + "ms");
    }

    // ---------- 핑 ----------

    @Test
    void 핑에는_자동으로_답이_오고_왕복_시간이_기록된다() throws Exception {
        Pair p = connect();
        Recorder client = start(p.client());
        Recorder server = start(p.server());
        assertEquals(-1, p.client().rttMillis());

        p.client().ping();
        waitUntil(() -> p.client().rttMillis() >= 0, "왕복 시간이 기록되지 않음");

        assertTrue(p.client().rttMillis() < 200, "왕복 시간: " + p.client().rttMillis() + "ms");
        assertTrue(p.server().millisSinceLastReceive() < 1_000);
        // Ping, Pong은 Connection이 처리하므로 listener로 오지 않는다
        client.assertNothingFor(100);
        server.assertNothingFor(0);
    }

    // ---------- 닫기 ----------

    @Test
    void 상대가_닫으면_PEER_CLOSED가_한_번만_온다() throws Exception {
        Pair p = connect();
        Recorder client = start(p.client());
        Recorder server = start(p.server());

        p.server().close();
        p.server().close();   // 두 번 불러도 괜찮다

        assertEquals(new Recorder.Closed(CloseReason.PEER_CLOSED), client.next());
        client.assertNothingFor(300);
        server.assertNothingFor(0);   // 닫은 쪽에는 아무것도 오지 않는다
        assertTrue(p.client().isClosed());
        assertTrue(p.server().isClosed());
    }

    @Test
    void close를_부른_뒤에는_이미_받아_둔_메시지도_넘기지_않는다() throws Exception {
        Pair p = connect();
        start(p.client());
        // 화면 스레드가 바빠서 콜백이 밀린 상황
        ManualExecutor busy = new ManualExecutor();
        Recorder server = new Recorder();
        p.server().start(server, busy);

        p.client().send(new Message.GameOver(1));
        waitUntil(() -> busy.pending() > 0, "메시지를 받지 못함");
        p.server().close();
        busy.runAll();

        server.assertNothingFor(100);
    }

    @Test
    void Bye는_앞서_보낸_메시지_다음에_도착하고_그_뒤에_닫힌다() throws Exception {
        Pair p = connect();
        Recorder client = start(p.client());
        Recorder server = start(p.server());

        p.client().send(new Message.GameOver(5));
        p.client().closeWithBye();

        assertEquals(new Message.GameOver(5), server.next());
        assertEquals(new Message.Bye(), server.next());
        assertEquals(new Recorder.Closed(CloseReason.PEER_CLOSED), server.next());
        server.assertNothingFor(200);
        client.assertNothingFor(0);
    }

    @Test
    void 잘못된_데이터를_받으면_PROTOCOL_ERROR로_닫힌다() throws Exception {
        // Message가 아닌 객체, 허용하지 않은 클래스, 깨진 바이트
        List<Object> badData = List.of("문자열", new ArrayList<>(List.of(1, 2)), new byte[] {0, 1, 2});

        for (Object bad : badData) {
            ServerSocket listening = listen();
            Future<Connection> accepted = acceptAndOpen(listening, Connection.Options.DEFAULT);
            RawPeer peer = new RawPeer(listening.getLocalPort(), new Message.Hello(Message.PROTOCOL_VERSION));
            toClose.add(peer);
            Connection connection = accepted.get(WAIT_MS, TimeUnit.MILLISECONDS);
            toClose.add(connection::close);
            Recorder recorder = start(connection);

            try {
                if (bad instanceof byte[] bytes) {
                    peer.sendRaw(bytes);
                } else {
                    peer.send(bad);
                }
            } catch (IOException ignored) {
                // Connection이 앞부분만 보고 거부하고 끊으면, 나머지를 보내다가 실패할 수 있다
            }

            assertEquals(new Recorder.Closed(CloseReason.PROTOCOL_ERROR), recorder.next(), bad.toString());
        }
    }

    @Test
    void 닫으면_스레드가_끝나고_스레드는_데몬이다() throws Exception {
        Pair p = connect();
        start(p.client());
        start(p.server());
        List<Thread> threads = new ArrayList<>(p.client().threads());
        threads.addAll(p.server().threads());
        assertEquals(4, threads.size());

        p.client().close();
        p.server().close();

        for (Thread thread : threads) {
            assertTrue(thread.isDaemon(), thread.getName());
            thread.join(WAIT_MS);
            assertFalse(thread.isAlive(), thread.getName() + "가 끝나지 않음");
        }
    }

    @Test
    void 보내지_못한_메시지가_너무_많이_쌓이면_닫힌다() throws Exception {
        Connection.Options smallQueue = new Connection.Options(3, 1_000, 3_000, 500, 5);
        Pair p = connect(smallQueue, 0, 0);
        // start 전이라 보내기 스레드가 없어서 쌓이기만 한다

        for (int i = 0; i < 5; i++) {
            p.client().send(new Message.GameOver(i));
        }
        assertFalse(p.client().isClosed());

        p.client().send(new Message.GameOver(5));
        assertTrue(p.client().isClosed());
    }

    // ---------- 테스트 도구 ----------

    private record Pair(Connection client, Connection server) {
    }

    private Pair connect() throws Exception {
        return connect(0, 0);
    }

    private Pair connect(int delayMs, int bytesPerSecond) throws Exception {
        return connect(Connection.Options.DEFAULT, delayMs, bytesPerSecond);
    }

    /**
     * localhost에서 서로 연결된 Connection 두 개 (start는 하지 않음)
     * delayMs나 bytesPerSecond가 0보다 크면 TestProxy를 거쳐서 연결한다
     */
    private Pair connect(Connection.Options clientOptions, int delayMs, int bytesPerSecond) throws Exception {
        ServerSocket listening = listen();
        int port = listening.getLocalPort();
        if (delayMs > 0 || bytesPerSecond > 0) {
            TestProxy proxy = TestProxy.start(port, delayMs, bytesPerSecond);
            toClose.add(proxy);
            port = proxy.port();
        }
        Future<Connection> accepted = acceptAndOpen(listening, Connection.Options.DEFAULT);
        Connection client = Connection.open(new Socket(InetAddress.getLoopbackAddress(), port), clientOptions);
        toClose.add(client::close);
        Connection server = accepted.get(WAIT_MS, TimeUnit.MILLISECONDS);
        toClose.add(server::close);
        return new Pair(client, server);
    }

    private ServerSocket listen() throws IOException {
        ServerSocket listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        toClose.add(listening);
        return listening;
    }

    /** 다른 스레드에서 접속을 받고 Connection.open까지 한다 */
    private static Future<Connection> acceptAndOpen(ServerSocket listening, Connection.Options options) {
        CompletableFuture<Connection> result = new CompletableFuture<>();
        daemon(() -> {
            try {
                result.complete(Connection.open(listening.accept(), options));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private static HandshakeException handshakeFailure(Future<Connection> opening) {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> opening.get(WAIT_MS, TimeUnit.MILLISECONDS));
        return assertInstanceOf(HandshakeException.class, e.getCause());
    }

    private Recorder start(Connection connection) {
        Recorder recorder = new Recorder();
        connection.start(recorder, callbacks);
        return recorder;
    }

    /** 점수 칸에 번호를 넣은 빈 보드 (어느 스냅샷인지 구분하는 용도) */
    private static BoardSnapshot board(int score) {
        return new BoardSnapshot(20, 10, new byte[200], 0, null, null, score, 1, 0, "", false);
    }

    private static void waitForSnapshot(Connection connection, int score) throws InterruptedException {
        waitUntil(() -> connection.peerSnapshot() != null && connection.peerSnapshot().score() == score,
                score + "번 스냅샷이 오지 않음");
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

    /** periodMs마다 task를 실행하는 스레드 (interrupt하면 멈춤) */
    private static Thread repeat(long periodMs, Runnable task) {
        return daemon(() -> {
            try {
                while (true) {
                    task.run();
                    Thread.sleep(periodMs);
                }
            } catch (InterruptedException ignored) {
                // 멈춤
            }
        });
    }

    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "test-helper");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static long median(List<Long> values) {
        assertFalse(values.isEmpty(), "측정값이 없음");
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** listener가 받은 것을 순서대로 기록한다 */
    private static final class Recorder implements Connection.Listener {

        record Closed(CloseReason reason) {
        }

        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(Message message) {
            events.add(message);
        }

        @Override
        public void onClosed(CloseReason reason, Exception cause) {
            events.add(new Closed(reason));
        }

        /** 다음 기록 (WAIT_MS 안에 오지 않으면 실패) */
        Object next() throws InterruptedException {
            Object event = events.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(event, "기다려도 오지 않음");
            return event;
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

    /**
     * 손으로 조종하는 가짜 상대
     * 첫 메시지(보통 Hello)만 보내 두고, 그 뒤에는 테스트가 시키는 것만 보낸다 (SnapshotAck, Pong을 알아서 보내지 않음)
     */
    private static final class RawPeer implements AutoCloseable {

        private final Socket socket;
        private final ObjectOutputStream out;
        private final BlockingQueue<Object> received = new LinkedBlockingQueue<>();

        RawPeer(int port, Message first) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(first);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            daemon(() -> {
                try {
                    in.readObject();   // 상대의 Hello는 건너뛴다
                    while (true) {
                        received.add(in.readObject());
                    }
                } catch (Exception ignored) {
                    // 연결이 끝남
                }
            });
        }

        void send(Object object) throws IOException {
            out.writeObject(object);
            out.flush();
        }

        /** 직렬화하지 않은 바이트를 그대로 보낸다 */
        void sendRaw(byte[] bytes) throws IOException {
            out.flush();
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
        }

        Object next() throws InterruptedException {
            Object message = poll(WAIT_MS);
            assertNotNull(message, "기다려도 오지 않음");
            return message;
        }

        Object poll(long ms) throws InterruptedException {
            return received.poll(ms, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}