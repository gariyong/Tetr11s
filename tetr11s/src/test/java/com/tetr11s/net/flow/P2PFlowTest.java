package com.tetr11s.net.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tetr11s.game.Difficulty;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.net.Connection;
import com.tetr11s.net.Message;
import com.tetr11s.net.connect.PeerAddress;
import com.tetr11s.net.connect.RecentHosts;
import com.tetr11s.net.session.Session;
import com.tetr11s.net.session.SessionListener;
import com.tetr11s.net.session.SessionSettings;

/**
 * P2P 흐름 전체를 실제 소켓으로 확인한다 (화면은 가짜: 무엇을 보여 달라고 했는지 기록만 한다)
 *
 * 서버 흐름과 클라이언트 흐름을 하나씩 만들어 127.0.0.1로 연결한다
 * 두 흐름은 실제 앱처럼 하나의 화면 스레드(test-fx)에서 돈다
 */
class P2PFlowTest {

    private static final long WAIT_MS = 5_000;
    // 빠른 세션: 카운트다운 0.1초, 시간제한 0.4초, 무응답 1초면 끊김
    private static final SessionSettings FAST = new SessionSettings(10, 50, 4, 200, 150, 300, 1_000, 100, 150, 400);
    private static final P2PFlow.Config CONFIG = new P2PFlow.Config(InetAddress.getLoopbackAddress(), 0,
            Connection.Options.DEFAULT, 1_000, FAST);

    @TempDir
    Path tempDir;

    private ExecutorService fx;
    private Side host;
    private Side guest;

    @BeforeEach
    void setUp() {
        fx = Executors.newSingleThreadExecutor(task -> new Thread(task, "test-fx"));
        host = new Side("host");
        guest = new Side("guest");
    }

    @AfterEach
    void tearDown() throws Exception {
        onFx(() -> {
            host.flow.close();
            guest.flow.close();
        });
        fx.shutdownNow();
    }

    // ---------- 서버 대기와 접속 ----------

    @Test
    void 서버_대기_화면에_내_주소와_대기_상태가_뜬다() throws Exception {
        onFx(host.flow::openHost);

        host.await("host");
        host.await("addresses:[10.0.0.5]");
        host.awaitStarting("status:포트 ");
    }

    @Test
    void 접속하면_양쪽에_로비가_뜨고_클라이언트는_주소를_기억한다() throws Exception {
        String address = connect();

        host.await("connection:true");
        guest.await("connection:false");
        host.await("lobby:NORMAL,false,false");
        guest.await("lobby:NORMAL,false,false");
        assertEquals(List.of(PeerAddress.parse(address).orElseThrow()), guest.recent.load());
    }

    @Test
    void 잘못된_주소는_접속하지_않고_안내한다() throws Exception {
        onFx(guest.flow::openJoin);
        guest.await("join");

        onFx(() -> guest.joinListener.onConnect("not an ip"));

        guest.awaitStarting("status:!IP 주소 형식이 아닙니다");
        guest.assertNo("connecting:true", 200);
    }

    @Test
    void 서버가_없는_주소면_실패를_알리고_다시_입력할_수_있게_된다() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        onFx(guest.flow::openJoin);
        guest.await("join");

        onFx(() -> guest.joinListener.onConnect("127.0.0.1:" + closedPort));

        guest.await("connecting:true");
        guest.await("connecting:false");
        guest.awaitStarting("status:!");
        assertEquals(List.of(), guest.recent.load(), "실패한 주소는 기억하지 않음");
    }

    @Test
    void 서버_대기를_취소하면_첫_화면으로_돌아가고_포트가_닫힌다() throws Exception {
        onFx(host.flow::openHost);
        int port = awaitHostPort();

        onFx(() -> host.hostCancel.run());

        host.await("menu:");
        assertThrows(IOException.class, () -> new Socket(InetAddress.getLoopbackAddress(), port).close());
    }

    // ---------- 로비 ----------

    @Test
    void 서버가_모드를_바꾸고_둘_다_준비하면_카운트다운_뒤_대전이_시작된다() throws Exception {
        connectAndWaitLobby();

        onFx(() -> host.lobbyListener.onModeSelected(BattleMode.ITEM));
        guest.await("lobby:ITEM,false,false");

        onFx(() -> guest.lobbyListener.onToggleReady());
        host.await("lobby:ITEM,false,true");
        guest.await("lobby:ITEM,true,false");

        onFx(() -> host.lobbyListener.onToggleReady());
        host.await("countdown");
        guest.await("countdown");
        host.await("battle:ITEM");
        guest.await("battle:ITEM");
        host.awaitStarting("message:대전이 시작됐습니다");
    }

    @Test
    void 클라이언트는_모드를_바꿀_수_없다() throws Exception {
        connectAndWaitLobby();

        onFx(() -> guest.lobbyListener.onModeSelected(BattleMode.TIME_LIMIT));

        host.assertNo("lobby:TIME_LIMIT,false,false", 300);
    }

    @Test
    void 시간제한_모드는_시간이_끝나면_결과를_보여_주고_다시_로비_상태가_된다() throws Exception {
        connectAndWaitLobby();
        onFx(() -> host.lobbyListener.onModeSelected(BattleMode.TIME_LIMIT));
        guest.await("lobby:TIME_LIMIT,false,false");

        onFx(() -> {
            host.lobbyListener.onToggleReady();
            guest.lobbyListener.onToggleReady();
        });

        // 대전 화면이 아직 없어서 양쪽 다 0점으로 낸다
        host.await("result:" + SessionListener.Outcome.DRAW.message());
        guest.await("result:" + SessionListener.Outcome.DRAW.message());
        host.await("lobby:TIME_LIMIT,false,false");
        guest.await("lobby:TIME_LIMIT,false,false");
    }

    // ---------- 끝내기 ----------

    @Test
    void 나가면_나는_첫_화면으로_가고_상대는_이유와_함께_첫_화면으로_간다() throws Exception {
        connectAndWaitLobby();

        onFx(() -> guest.lobbyListener.onLeave());

        guest.await("menu:");
        host.await("menu:" + SessionListener.End.PEER_LEFT.message());
    }

    @Test
    void 프로그램을_끝내면_상대에게_나갔다고_알린다() throws Exception {
        connectAndWaitLobby();

        onFx(host.flow::shutdown);

        guest.await("menu:" + SessionListener.End.PEER_LEFT.message());
    }

    @Test
    void 접속_중에_취소하면_다시_입력할_수_있다() throws Exception {
        // 연결은 받지만 아무 말도 하지 않는 가짜 서버: 클라이언트는 확인 단계에서 기다리게 된다
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            onFx(guest.flow::openJoin);
            guest.await("join");
            onFx(() -> guest.joinListener.onConnect("127.0.0.1:" + silent.getLocalPort()));
            guest.await("connecting:true");

            onFx(() -> guest.joinListener.onCancel());

            guest.await("connecting:false");
            guest.await("status:접속을 취소했습니다.");
            guest.assertNo("lobby", 300);
        }
    }

    // ---------- 도우미 ----------

    /** 서버를 열고 클라이언트가 접속한다. 접속한 주소를 돌려준다 */
    private String connect() throws Exception {
        onFx(host.flow::openHost);
        String address = "127.0.0.1:" + awaitHostPort();
        onFx(guest.flow::openJoin);
        guest.await("join");
        onFx(() -> guest.joinListener.onConnect(address));
        return address;
    }

    private void connectAndWaitLobby() throws Exception {
        connect();
        host.await("lobby:NORMAL,false,false");
        guest.await("lobby:NORMAL,false,false");
    }

    private int awaitHostPort() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (System.nanoTime() < deadline) {
            int port = fxGet(() -> host.flow.hostPort());
            if (port > 0) {
                return port;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("서버가 포트를 열지 못함");
    }

    private void onFx(Runnable task) throws Exception {
        fx.submit(task).get(WAIT_MS, TimeUnit.MILLISECONDS);
    }

    private <T> T fxGet(Callable<T> task) throws Exception {
        return fx.submit(task).get(WAIT_MS, TimeUnit.MILLISECONDS);
    }

    /** 한쪽(서버 또는 클라이언트)의 흐름과, 흐름이 화면에 시킨 일의 기록 */
    private final class Side implements P2PFlow.Screens {

        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        final RecentHosts recent;
        final P2PFlow flow;
        Runnable hostCancel;
        JoinView.Listener joinListener;
        LobbyView.Listener lobbyListener;

        Side(String name) {
            recent = new RecentHosts(tempDir.resolve(name).resolve("p2p.properties"));
            flow = new P2PFlow(this, fx, CONFIG, recent,
                    () -> List.of(ip(10, 0, 0, 5)), () -> Difficulty.NORMAL);
        }

        /** 이 기록이 나올 때까지 기다린다 (그 사이의 다른 기록은 지나간다) */
        void await(String expected) throws InterruptedException {
            awaitMatching(expected::equals, expected);
        }

        void awaitStarting(String prefix) throws InterruptedException {
            awaitMatching(event -> event.startsWith(prefix), prefix + "...");
        }

        private void awaitMatching(Predicate<String> match, String description) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
            while (true) {
                long left = deadline - System.nanoTime();
                String event = events.poll(Math.max(0, left), TimeUnit.NANOSECONDS);
                if (event == null) {
                    fail("'" + description + "' 기록이 오지 않음");
                }
                if (match.test(event)) {
                    return;
                }
            }
        }

        void assertNo(String prefix, long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return;
                }
                String event = events.poll(left, TimeUnit.NANOSECONDS);
                if (event != null && event.startsWith(prefix)) {
                    fail("오면 안 되는 기록: " + event);
                }
            }
        }

        @Override
        public void showMenu(String message) {
            events.add("menu:" + (message == null ? "" : message));
        }

        @Override
        public HostWaitView showHostWait() {
            events.add("host");
            return new HostWaitView() {
                @Override
                public void showAddresses(List<String> addresses) {
                    events.add("addresses:" + addresses);
                }

                @Override
                public void showStatus(String text, boolean error) {
                    events.add("status:" + (error ? "!" : "") + text);
                }

                @Override
                public void setOnCancel(Runnable onCancel) {
                    hostCancel = onCancel;
                }
            };
        }

        @Override
        public JoinView showJoin() {
            events.add("join");
            return new JoinView() {
                @Override
                public void setListener(Listener listener) {
                    joinListener = listener;
                }

                @Override
                public void showRecent(List<String> addresses) {
                    events.add("recent:" + addresses);
                }

                @Override
                public void showStatus(String text, boolean error) {
                    events.add("status:" + (error ? "!" : "") + text);
                }

                @Override
                public void setConnecting(boolean connecting) {
                    events.add("connecting:" + connecting);
                }
            };
        }

        @Override
        public LobbyView showLobby() {
            events.add("lobbyScreen");
            return new LobbyView() {
                @Override
                public void setListener(Listener listener) {
                    lobbyListener = listener;
                }

                @Override
                public void showConnection(boolean isHost, String peerAddress, String difficulty) {
                    events.add("connection:" + isHost);
                }

                @Override
                public void showLobby(BattleMode mode, boolean myReady, boolean peerReady) {
                    events.add("lobby:" + mode + "," + myReady + "," + peerReady);
                }

                @Override
                public void showCountdown(long millis) {
                    events.add("countdown");
                }

                @Override
                public void showResult(String text) {
                    if (!text.isEmpty()) {
                        events.add("result:" + text);
                    }
                }

                @Override
                public void showNetwork(boolean lagging, long rttMillis) {
                    // 0.05초마다 와서 기록하지 않는다
                }

                @Override
                public void showMessage(String text) {
                    if (!text.isEmpty()) {
                        events.add("message:" + text);
                    }
                }
            };
        }

        @Override
        public boolean showBattle(Session session, Message.Start start) {
            events.add("battle:" + start.mode());
            return false;   // 대전 화면은 아직 없음 (7단계)
        }
    }

    private static InetAddress ip(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
