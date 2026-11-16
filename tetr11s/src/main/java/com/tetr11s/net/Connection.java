package com.tetr11s.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.tetr11s.game.BoardSnapshot;

/**
 * 연결된 소켓 하나로 Message를 주고받는다
 *
 * 사용 순서
 * 1. open(socket): 서로 Hello를 주고받아 같은 프로그램, 같은 버전인지 확인한다 (끝날 때까지 막히므로 화면 스레드에서 부르지 않는다)
 * 2. start(listener, callbacks): 받기 스레드와 보내기 스레드를 시작한다
 * 3. send, sendSnapshot, ping으로 보내고, 받은 메시지는 listener로 온다
 * 4. close() 또는 close(Bye 이유)로 닫는다
 *
 * 보내기 규칙
 * - 일반 메시지는 큐에 넣고 순서대로 전부 보낸다
 * - 스냅샷은 최신 1개만 보관하고, 일반 메시지가 없고 확인(SnapshotAck)을 못 받은 스냅샷이 정해진 개수 미만일 때만 보낸다
 *   (느린 네트워크에서 밀린 스냅샷이 쌓여 상대 화면이 점점 늦어지는 것을 막는다)
 * - 보내는 메서드는 큐에 넣고 바로 돌아온다. 실제 전송은 보내기 스레드가 한다 (네트워크가 막혀도 게임이 멈추지 않음)
 *
 * 받기 규칙
 * - Ping에는 바로 Pong으로, Snapshot에는 바로 SnapshotAck로 답한다 (화면 스레드를 거치지 않음)
 * - 상대 스냅샷은 최신 1개만 보관하고, 화면이 peerSnapshot()으로 가져간다
 * - 나머지 메시지는 받은 순서대로 listener에 넘긴다
 *
 * 콜백 규칙
 * - listener는 start()에서 받은 실행기에서 불린다 (앱에서는 Platform::runLater)
 * - 상대 때문에 끝나면, 그 전에 받은 메시지를 모두 넘긴 뒤 onClosed가 딱 한 번 온다
 * - close()를 부른 뒤에는 어떤 콜백도 오지 않는다
 */
public final class Connection {

    /** 받은 메시지와 연결이 끝났음을 알릴 곳 */
    public interface Listener {

        /** 상대가 보낸 메시지 (Ping, Pong, Snapshot, SnapshotAck는 Connection이 처리하므로 오지 않음) */
        void onMessage(Message message);

        /** 상대 때문에 연결이 끝남 (딱 한 번 옴). cause는 원인 예외 */
        void onClosed(CloseReason reason, Exception cause);
    }

    /**
     * 연결 설정값
     *
     * @param maxUnackedSnapshots 확인을 못 받은 채로 보낼 수 있는 스냅샷 수
     * @param resetIntervalMs     스트림이 기억한 객체를 비우는 주기
     * @param handshakeTimeoutMs  상대의 헤더와 Hello를 기다리는 시간
     * @param byeWaitMs           Bye를 보낸 뒤 상대가 닫기를 기다리는 시간 (지나면 강제로 닫음)
     * @param maxQueuedMessages   보내지 못하고 쌓인 메시지가 이만큼이면 연결이 막힌 것으로 보고 닫음
     */
    public record Options(int maxUnackedSnapshots, long resetIntervalMs, long handshakeTimeoutMs,
                          long byeWaitMs, int maxQueuedMessages) {

        public static final Options DEFAULT = new Options(3, 1_000, 3_000, 500, 1_000);

        public Options {
            if (maxUnackedSnapshots < 1 || resetIntervalMs < 0 || handshakeTimeoutMs < 1
                    || handshakeTimeoutMs > Integer.MAX_VALUE || byeWaitMs < 0 || maxQueuedMessages < 1) {
                throw new IllegalArgumentException("연결 설정값이 잘못됨: " + this);
            }
        }
    }

    /**
     * 받을 수 있는 클래스 목록 (이 밖의 클래스가 오면 읽지 않고 거부)
     * maxrefs, maxbytes는 연결이 살아 있는 동안 계속 누적돼서 몇 초 만에 한도에 걸리므로 쓰지 않는다
     */
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=5;maxarray=1024;"
            + "com.tetr11s.net.Message$*;com.tetr11s.game.BoardSnapshot;"
            + "com.tetr11s.game.Difficulty;com.tetr11s.game.battle.BattleMode;com.tetr11s.game.battle.BattleResult;"
            + "com.tetr11s.game.tetromino.Tetromino;com.tetr11s.game.item.Item;"
            + "java.lang.Enum;!*");

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final Options options;

    // 보내기 (lock으로 보호)
    private final Object lock = new Object();
    private final Deque<Message> queue = new ArrayDeque<>();
    private BoardSnapshot pendingSnapshot;   // 아직 보내지 않은 내 최신 스냅샷
    private int lastSentSeq;                 // 마지막으로 보낸 스냅샷 번호
    private int lastAckedSeq;                // 상대가 받았다고 알려 온 마지막 번호
    private boolean byeQueued;               // Bye까지 보내고 출력을 닫는다

    // 받기
    private final AtomicReference<BoardSnapshot> peerSnapshot = new AtomicReference<>();
    private volatile long lastReceivedNanos;
    private volatile long rttNanos = -1;

    // 상태
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean ended = new AtomicBoolean();   // 끝남: 새로 보내지 않는다
    private volatile boolean silenced;                          // close()를 불렀음: 콜백을 보내지 않는다
    private volatile Listener listener;
    private volatile Executor callbacks;
    private Thread reader;
    private Thread writer;

    private Connection(Socket socket, ObjectOutputStream out, ObjectInputStream in, Options options) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.options = options;
        this.lastReceivedNanos = System.nanoTime();
    }

    // 열기

    public static Connection open(Socket socket) throws IOException {
        return open(socket, Options.DEFAULT);
    }

    /**
     * 서로 Hello를 주고받아 같은 프로그램, 같은 버전인지 확인한다 (끝날 때까지 막힘)
     * 실패하면 소켓을 닫고 예외를 던진다
     *
     * @param socket 연결된 소켓 (서버가 accept한 것이든 클라이언트가 connect한 것이든 같다)
     * @throws HandshakeException 시간 초과, 상대가 Tetr11s가 아님, 버전이 다름
     * @throws IOException        그 밖의 네트워크 오류 (확인 도중 상대가 끊음, 다른 스레드가 소켓을 닫음 등)
     */
    public static Connection open(Socket socket, Options options) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(options, "options");
        try {
            socket.setTcpNoDelay(true);   // 작은 메시지를 모으지 않고 바로 보낸다
            socket.setSoTimeout((int) options.handshakeTimeoutMs());

            // 1. 내 헤더와 Hello를 먼저 보낸다 (양쪽이 먼저 받으려고 하면 서로 기다리다 멈춘다)
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.writeObject(new Message.Hello(Message.PROTOCOL_VERSION));
            out.flush();

            // 2. 상대의 헤더와 Hello를 받는다
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
            in.setObjectInputFilter(FILTER);
            Object first = in.readObject();
            if (!(first instanceof Message.Hello hello)) {
                throw new HandshakeException(HandshakeException.Reason.NOT_TETR11S, null);
            }
            if (hello.protocolVersion() != Message.PROTOCOL_VERSION) {
                throw new HandshakeException(HandshakeException.Reason.VERSION_MISMATCH, null);
            }

            socket.setSoTimeout(0);   // 이제부터는 기다리는 시간 제한 없음
            return new Connection(socket, out, in, options);
        } catch (SocketTimeoutException e) {
            closeQuietly(socket);
            throw new HandshakeException(HandshakeException.Reason.TIMEOUT, e);
        } catch (ObjectStreamException | ClassNotFoundException e) {
            // 헤더가 다름(다른 프로그램), 모르는 클래스, 필터가 거부한 클래스
            closeQuietly(socket);
            throw new HandshakeException(HandshakeException.Reason.NOT_TETR11S, e);
        } catch (IOException | RuntimeException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    /**
     * 받기 스레드와 보내기 스레드를 시작한다 (한 번만 부를 수 있음)
     *
     * @param listener  받은 메시지와 연결이 끝났음을 알릴 곳
     * @param callbacks listener를 부를 실행기 (앱: Platform::runLater, 테스트: 일반 스레드)
     */
    public void start(Listener listener, Executor callbacks) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(callbacks, "callbacks");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 시작한 연결");
        }
        this.listener = listener;
        this.callbacks = callbacks;
        this.reader = newThread(this::readLoop, "p2p-read");
        this.writer = newThread(this::writeLoop, "p2p-write");
        reader.start();
        writer.start();
    }

    // ----- 보내기 -----

    /**
     * 일반 메시지를 보낸다 (순서대로 전부 감). 끝난 연결이면 아무 일도 하지 않는다
     * Connection이 직접 처리하는 메시지(Hello, Bye, Snapshot, SnapshotAck, Ping, Pong)는 보낼 수 없다
     * 스냅샷은 sendSnapshot, 핑은 ping, Bye는 close(이유)를 쓴다
     */
    public void send(Message message) {
        Objects.requireNonNull(message, "message");
        if (isInternal(message)) {
            throw new IllegalArgumentException("send로 보낼 수 없는 메시지: " + message.getClass().getSimpleName());
        }
        enqueue(message, false);
    }

    /** 내 화면 상태를 보낸다. 아직 보내지 못한 이전 스냅샷은 버린다 (최신 것만 의미가 있음) */
    public void sendSnapshot(BoardSnapshot board) {
        Objects.requireNonNull(board, "board");
        synchronized (lock) {
            if (ended.get()) {
                return;
            }
            pendingSnapshot = board;
            lock.notifyAll();
        }
    }

    /** 왕복 시간을 잰다. 답(Pong)이 오면 rttMillis()가 바뀐다 */
    public void ping() {
        enqueue(new Message.Ping(System.nanoTime()), false);
    }

    // ----- 닫기 -----

    /** 바로 닫는다. 보내지 못한 메시지는 버린다. 부른 뒤에는 콜백이 오지 않는다 (여러 번 불러도 됨) */
    public void close() {
        silenced = true;
        synchronized (lock) {
            ended.set(true);
            queue.clear();
            pendingSnapshot = null;
            lock.notifyAll();
        }
        closeSocket();
    }

    /**
     * 그때까지 보낸 메시지와 Bye를 보낸 뒤 닫는다. 바로 돌아온다 (실제 전송은 보내기 스레드가 함)
     * 상대가 닫기를 byeWaitMs까지 기다리고, 그래도 안 닫히면 강제로 닫는다. 부른 뒤에는 콜백이 오지 않는다
     */
    public void close(Message.Bye.Reason reason) {
        Objects.requireNonNull(reason, "reason");
        silenced = true;
        synchronized (lock) {
            if (ended.getAndSet(true)) {
                return;   // 이미 끝난 연결
            }
            pendingSnapshot = null;
            queue.addLast(new Message.Bye(reason));
            byeQueued = true;
            lock.notifyAll();
        }
        CompletableFuture.delayedExecutor(options.byeWaitMs(), TimeUnit.MILLISECONDS).execute(this::closeSocket);
    }

    // ----- 상태 조회 -----

    /** 상대의 최신 스냅샷 (아직 받지 못했으면 null) */
    public BoardSnapshot peerSnapshot() {
        return peerSnapshot.get();
    }

    /** 마지막으로 잰 왕복 시간(ms), 아직 재지 못했으면 -1 */
    public long rttMillis() {
        long rtt = rttNanos;
        return rtt < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(rtt);
    }

    /** 마지막으로 무엇이든 받은 뒤 지난 ms 시간 (랙과 끊김을 판정할 때 쓴다) */
    public long millisSinceLastReceive() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedNanos);
    }

    /** 상대 IP 주소 (화면 표시용) */
    public String remoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    /** 끝난 연결인지 (내가 닫았거나 상대 때문에 끝남) */
    public boolean isClosed() {
        return ended.get();
    }

    /** 테스트용: 받기, 보내기 스레드 (start 전에는 빈 목록) */
    List<Thread> threads() {
        return started.get() ? List.of(reader, writer) : List.of();
    }

    // ----- 받기 스레드 -----

    private void readLoop() {
        try {
            while (true) {
                Object received = in.readObject();
                lastReceivedNanos = System.nanoTime();
                if (!(received instanceof Message message)) {
                    throw new InvalidObjectException("Message가 아님: " + received.getClass().getName());
                }
                handle(message);
            }
        } catch (EOFException e) {
            end(CloseReason.PEER_CLOSED, e);
        } catch (ObjectStreamException | ClassNotFoundException e) {
            end(CloseReason.PROTOCOL_ERROR, e);
        } catch (IOException e) {
            end(CloseReason.IO_ERROR, e);   // 내가 닫아서 난 예외라면 end()가 무시한다
        } finally {
            closeSocket();   // Bye를 보내고 상대가 닫기를 기다리던 중이었다면 여기서 마저 닫는다
        }
    }

    private void handle(Message message) {
        switch (message) {
            case Message.Ping ping -> enqueue(new Message.Pong(ping.sentNanos()), true);
            case Message.Pong pong -> rttNanos = Math.max(0, System.nanoTime() - pong.sentNanos());
            case Message.Snapshot snapshot -> {
                enqueue(new Message.SnapshotAck(snapshot.seq()), true);
                peerSnapshot.set(snapshot.board());
            }
            case Message.SnapshotAck ack -> {
                synchronized (lock) {
                    // 보낸 적 없는 번호는 믿지 않는다
                    lastAckedSeq = Math.max(lastAckedSeq, Math.min(ack.seq(), lastSentSeq));
                    lock.notifyAll();
                }
            }
            default -> deliver(() -> listener.onMessage(message));
        }
    }

    // ----- 보내기 스레드 -----

    private void writeLoop() {
        long resetIntervalNanos = TimeUnit.MILLISECONDS.toNanos(options.resetIntervalMs());
        long lastReset = System.nanoTime();
        try {
            Message next;
            while ((next = takeNext()) != null) {
                out.writeObject(next);
                if (System.nanoTime() - lastReset >= resetIntervalNanos) {
                    out.reset();   // 보낸 객체 기억을 비운다 (받는 쪽 기억도 같이 비워짐)
                    lastReset = System.nanoTime();
                }
                out.flush();
            }
            if (isByeQueued()) {
                socket.shutdownOutput();   // Bye 뒤에 "더 보낼 것 없음"을 알린다. 상대가 닫으면 받기 스레드가 끝난다
            }
        } catch (IOException e) {
            end(CloseReason.IO_ERROR, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            end(CloseReason.IO_ERROR, null);
        }
    }

    /** 다음에 보낼 메시지. 보낼 것이 생길 때까지 기다리고, 끝났는데 보낼 것도 없으면 null */
    private Message takeNext() throws InterruptedException {
        synchronized (lock) {
            while (true) {
                if (!queue.isEmpty()) {
                    return queue.pollFirst();
                }
                if (ended.get()) {
                    return null;
                }
                if (pendingSnapshot != null && lastSentSeq - lastAckedSeq < options.maxUnackedSnapshots()) {
                    Message snapshot = new Message.Snapshot(++lastSentSeq, pendingSnapshot);
                    pendingSnapshot = null;
                    return snapshot;
                }
                lock.wait();
            }
        }
    }

    private boolean isByeQueued() {
        synchronized (lock) {
            return byeQueued;
        }
    }

    // ----- 내부 처리용 -----

    /**
     * 보낼 큐에 넣는다. urgent면 맨 앞에 넣는다
     * (Pong과 SnapshotAck는 늦게 답할수록 상대가 잰 왕복 시간과 스냅샷 전송이 나빠진다)
     */
    private void enqueue(Message message, boolean urgent) {
        boolean overflow;
        synchronized (lock) {
            if (ended.get()) {
                return;
            }
            overflow = queue.size() >= options.maxQueuedMessages();
            if (!overflow) {
                if (urgent) {
                    queue.addFirst(message);
                } else {
                    queue.addLast(message);
                }
                lock.notifyAll();
            }
        }
        if (overflow) {
            end(CloseReason.IO_ERROR, new IOException("보내지 못한 메시지가 너무 많이 쌓임"));
        }
    }

    /** 상대 때문에, 또는 네트워크 오류로 끝남. 처음 한 번만 처리하고 onClosed를 알린다 */
    private void end(CloseReason reason, Exception cause) {
        synchronized (lock) {
            if (ended.getAndSet(true)) {
                return;   // 이미 끝남 (내가 닫았거나, 다른 스레드가 먼저 알렸음)
            }
            queue.clear();
            pendingSnapshot = null;
            lock.notifyAll();
        }
        closeSocket();
        deliver(() -> listener.onClosed(reason, cause));
    }

    /** 콜백 실행기에서 listener를 부른다. 그 사이에 close()를 불렀다면 부르지 않는다 */
    private void deliver(Runnable call) {
        Executor executor = callbacks;
        if (executor == null) {
            return;   // 아직 start 전
        }
        try {
            executor.execute(() -> {
                if (!silenced) {
                    call.run();
                }
            });
        } catch (RejectedExecutionException e) {
            // 실행기가 이미 멈춤 (프로그램이 끝나는 중): 알릴 곳이 없다
        }
    }

    private static boolean isInternal(Message message) {
        return message instanceof Message.Hello || message instanceof Message.Bye
                || message instanceof Message.Snapshot || message instanceof Message.SnapshotAck
                || message instanceof Message.Ping || message instanceof Message.Pong;
    }

    private Thread newThread(Runnable task, String name) {
        Thread thread = new Thread(task, name + "-" + remoteAddress());
        thread.setDaemon(true);   // 이 스레드 때문에 창을 닫아도 프로그램이 안 끝나는 일이 없도록
        return thread;
    }

    private void closeSocket() {
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫다가 난 오류는 할 수 있는 일이 없다
        }
    }
}