package com.tetr11s.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 테스트 전용: 느린 네트워크를 흉내 내는 중계기
 *
 * 접속을 하나 받아서 target 포트로 이어 주고, 오가는 바이트에 지연과 속도 제한을 건다 (양쪽 방향 모두 같은 조건)
 * 한쪽이 출력을 닫으면(FIN) 반대쪽에도 그대로 전한다
 */
public final class TestProxy implements AutoCloseable {

    private static final byte[] END = new byte[0];   // "상대가 출력을 닫았음" 표시

    private final ServerSocket server;
    private final int targetPort;
    private final long delayNanos;
    private final int bytesPerSecond;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private volatile boolean paused;

    /**
     * @param delayMs        한 방향에 더할 지연 (0이면 없음)
     * @param bytesPerSecond 한 방향의 최대 속도 (0이면 제한 없음)
     */
    public static TestProxy start(int targetPort, int delayMs, int bytesPerSecond) throws IOException {
        TestProxy proxy = new TestProxy(targetPort, delayMs, bytesPerSecond);
        daemon(proxy::acceptOne, "proxy-accept");
        return proxy;
    }

    private TestProxy(int targetPort, int delayMs, int bytesPerSecond) throws IOException {
        this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        this.targetPort = targetPort;
        this.delayNanos = TimeUnit.MILLISECONDS.toNanos(delayMs);
        this.bytesPerSecond = bytesPerSecond;
    }

    public int port() {
        return server.getLocalPort();
    }

    /**
     * true면 연결은 그대로 둔 채 전달만 멈춘다 (상대가 응답하지 않는 상황 흉내)
     * false로 되돌리면 그동안 쌓인 것부터 다시 전달한다
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : sockets) {
            socket.close();
        }
    }

    private void acceptOne() {
        try {
            Socket client = server.accept();
            Socket target = new Socket(InetAddress.getLoopbackAddress(), targetPort);
            sockets.add(client);
            sockets.add(target);
            client.setTcpNoDelay(true);
            target.setTcpNoDelay(true);
            relay(client, target);
            relay(target, client);
        } catch (IOException ignored) {
            // 테스트가 끝나서 닫힘
        }
    }

    // from에서 읽은 바이트를 to로 넘긴다: 읽는 쪽은 속도를 맞추고, 쓰는 쪽은 지연이 지난 뒤에 쓴다
    private void relay(Socket from, Socket to) {
        BlockingQueue<Chunk> line = new LinkedBlockingQueue<>();
        daemon(() -> pump(from, line), "proxy-read");
        daemon(() -> deliver(line, to), "proxy-write");
    }

    private record Chunk(long dueNanos, byte[] bytes) {
    }

    private void pump(Socket from, BlockingQueue<Chunk> line) {
        int size = bytesPerSecond > 0 ? Math.max(64, bytesPerSecond / 50) : 8192;
        byte[] buffer = new byte[size];
        long start = System.nanoTime();
        long total = 0;
        try {
            InputStream in = from.getInputStream();
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (bytesPerSecond > 0) {
                    // 지금까지 읽은 양이 정해진 속도로 지나갈 시각까지 기다린다
                    long due = start + total * 1_000_000_000L / bytesPerSecond;
                    sleepUntil(due);
                }
                line.add(new Chunk(System.nanoTime() + delayNanos, Arrays.copyOf(buffer, n)));
            }
        } catch (IOException | InterruptedException ignored) {
            // 상대가 갑자기 끊었거나(연결 재설정) 테스트가 끝나서 닫힘
        } finally {
            // 어떻게 끝났든 반대쪽에도 끊겼다고 알린다 (안 알리면 반대쪽은 시간 초과로만 끊김을 알게 됨)
            line.add(new Chunk(System.nanoTime() + delayNanos, END));
        }
    }

    private void deliver(BlockingQueue<Chunk> line, Socket to) {
        try {
            OutputStream out = to.getOutputStream();
            while (true) {
                Chunk chunk = line.take();
                while (paused) {
                    TimeUnit.MILLISECONDS.sleep(5);
                }
                sleepUntil(chunk.dueNanos());
                if (chunk.bytes() == END) {
                    to.shutdownOutput();
                    return;
                }
                out.write(chunk.bytes());
                out.flush();
            }
        } catch (IOException | InterruptedException ignored) {
            // 테스트가 끝나서 닫힘
        }
    }

    private static void sleepUntil(long dueNanos) throws InterruptedException {
        long wait = dueNanos - System.nanoTime();
        if (wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
    }

    private static void daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}