package com.tetr11s.net.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.BoardSnapshot;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.battle.TestLines;
import com.tetr11s.net.Connection;
import com.tetr11s.net.Message;
import com.tetr11s.net.TestProxy;
import com.tetr11s.net.session.SessionListener.End;
import com.tetr11s.net.session.SessionListener.Outcome;

/**
 * 서버 세션과 클라이언트 세션을 실제 연결(TestProxy 경유)로 이어서 확인한다
 * 두 세션 모두 같은 "화면 스레드"(fx)에서 돌린다
 */
class SessionTest {

    private static final long WAIT_MS = 3_000;
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    /** 테스트용 짧은 기준값: 핑 50ms, 랙 무응답 0.3초, 끊김 1초, 카운트다운 0.1초, 무승부 판정 0.15초, 제한 시간 0.4초 */
    private static final SessionSettings FAST = new SessionSettings(10, 50, 4, 200, 150, 300, 1_000, 100, 150, 400);

    // 화면 스레드 역할: 세션의 메서드와 알림이 모두 여기서 실행된다
    private final ExecutorService fx = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "test-fx");
        thread.setDaemon(true);
        return thread;
    });
    private final List<AutoCloseable> toClose = new CopyOnWriteArrayList<>();

    private final Recorder hostEvents = new Recorder();
    private final Recorder guestEvents = new Recorder();
    private HostSession host;
    private GuestSession guest;
    private Connection guestConnection;
    private TestProxy proxy;

    @AfterEach
    void cleanUp() throws Exception {
        if (host != null) {
            onFx(() -> {
                host.leave();
                guest.leave();
            });
        }
        for (AutoCloseable resource : toClose) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // 이미 닫힘
            }
        }
        fx.shutdownNow();
    }

    // ---------- 로비 ----------

    @Test
    void 시작하면_양쪽이_같은_로비_상태를_받는다() throws Exception {
        startSessions(0);

        Message.LobbyState initial = new Message.LobbyState(BattleMode.NORMAL, false, false);
        assertEquals(initial, hostEvents.next());
        assertEquals(initial, guestEvents.next());
    }

    @Test
    void 준비와_모드_변경이_양쪽에_전달되고_모드를_바꾸면_준비가_풀린다() throws Exception {
        startSessions(0);
        hostEvents.next();
        guestEvents.next();

        onFx(() -> guest.setReady(true));
        Message.LobbyState guestReady = new Message.LobbyState(BattleMode.NORMAL, false, true);
        assertEquals(guestReady, hostEvents.next());
        assertEquals(guestReady, guestEvents.next());

        onFx(() -> host.setMode(BattleMode.ITEM));
        Message.LobbyState item = new Message.LobbyState(BattleMode.ITEM, false, false);
        assertEquals(item, hostEvents.next());
        assertEquals(item, guestEvents.next());
    }

    @Test
    void 한쪽만_준비하면_시작하지_않는다() throws Exception {
        startSessions(0);
        hostEvents.next();
        guestEvents.next();

        onFx(() -> host.setReady(true));

        assertEquals(new Message.LobbyState(BattleMode.NORMAL, true, false), hostEvents.next());
        assertEquals(new Message.LobbyState(BattleMode.NORMAL, true, false), guestEvents.next());
        hostEvents.assertNothingFor(300);
        guestEvents.assertNothingFor(0);
        assertEquals(Session.State.LOBBY, fxGet(() -> host.state()));
    }

    @Test
    void 모드가_바뀌기_전에_누른_준비는_무시한다() throws Exception {
        startSessions(0);
        hostEvents.next();
        guestEvents.next();

        // 서버가 모드를 바꾼 순간, 클라이언트는 아직 바뀐 모드를 받기 전이라 옛 모드(NORMAL)로 준비를 보낸다
        onFx(() -> {
            host.setMode(BattleMode.ITEM);
            guest.setReady(true);
        });

        Message.LobbyState item = new Message.LobbyState(BattleMode.ITEM, false, false);
        assertEquals(item, hostEvents.next());
        assertEquals(item, guestEvents.next());
        hostEvents.assertNothingFor(300);   // 클라이언트 준비가 켜지지 않는다
    }

    @Test
    void 둘_다_준비하면_양쪽이_같은_설정으로_카운트다운_뒤_게임을_시작한다() throws Exception {
        startSessions(0);
        onFx(() -> host.setMode(BattleMode.ITEM));
        awaitLobby(guestEvents, lobby -> lobby.mode() == BattleMode.ITEM);
        onFx(() -> guest.setReady(true));
        awaitLobby(hostEvents, Message.LobbyState::guestReady);
        onFx(() -> host.setReady(true));

        Message.Start hostStart = awaitAfterLobbies(hostEvents, Recorder.Countdown.class).start();
        long countdownAt = System.nanoTime();
        Message.Start guestStart = awaitAfterLobbies(guestEvents, Recorder.Countdown.class).start();
        assertEquals(hostStart, guestStart);   // 모드, 난이도, seed, 제한 시간, 카운트다운이 모두 같다
        assertEquals(BattleMode.ITEM, hostStart.mode());
        assertEquals(Difficulty.HARD, hostStart.difficulty());   // 서버의 난이도

        assertEquals(hostStart, hostEvents.nextOf(Recorder.GameStart.class).start());
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - countdownAt);
        assertTrue(waited >= FAST.countdownMs() - 20, "카운트다운: " + waited + "ms");
        assertEquals(guestStart, guestEvents.nextOf(Recorder.GameStart.class).start());
        assertEquals(Session.State.PLAYING, fxGet(() -> host.state()));
        assertEquals(Session.State.PLAYING, fxGet(() -> guest.state()));
    }

    // ---------- 게임 중 ----------

    @Test
    void 게임_중에_공격과_화면이_상대에게_전달된다() throws Exception {
        startSessions(0);
        startGame(BattleMode.NORMAL);

        List<GarbageLine> lines = TestLines.holes(10, 2, 2);
        onFx(() -> guest.sendAttack(lines));
        assertEquals(new Recorder.Attack(lines), hostEvents.next());

        onFx(() -> host.sendSnapshot(board(77)));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (true) {
            BoardSnapshot seen = fxGet(() -> guest.peerSnapshot());
            if (seen != null && seen.score() == 77) {
                break;
            }
            if (System.nanoTime() > deadline) {
                fail("서버 화면이 클라이언트에 오지 않음");
            }
            Thread.sleep(2);
        }
    }

    // ---------- 승패 ----------

    @Test
    void 클라이언트가_게임오버되면_서버가_이기고_양쪽이_로비로_돌아간다() throws Exception {
        startSessions(0);
        startGame(BattleMode.NORMAL);

        onFx(() -> guest.gameOver(100));

        assertEquals(new Recorder.Result(Outcome.WIN), hostEvents.next());
        assertEquals(new Recorder.Result(Outcome.LOSE), guestEvents.next());
        Message.LobbyState lobby = new Message.LobbyState(BattleMode.NORMAL, false, false);
        assertEquals(lobby, hostEvents.next());
        assertEquals(lobby, guestEvents.next());
        assertEquals(Session.State.LOBBY, fxGet(() -> host.state()));
        assertEquals(Session.State.LOBBY, fxGet(() -> guest.state()));
    }

    @Test
    void 서버가_게임오버되면_클라이언트가_이긴다() throws Exception {
        startSessions(0);
        startGame(BattleMode.NORMAL);

        onFx(() -> host.gameOver(100));

        assertEquals(new Recorder.Result(Outcome.LOSE), hostEvents.next());
        assertEquals(new Recorder.Result(Outcome.WIN), guestEvents.next());
    }

    @Test
    void 거의_동시에_게임오버되면_무승부다() throws Exception {
        startSessions(0);
        startGame(BattleMode.NORMAL);

        onFx(() -> {
            host.gameOver(1);
            guest.gameOver(2);   // 클라이언트의 게임오버가 무승부 판정 시간 안에 서버에 도착한다
        });

        assertEquals(new Recorder.Result(Outcome.DRAW), hostEvents.next());
        assertEquals(new Recorder.Result(Outcome.DRAW), guestEvents.next());
    }

    @Test
    void 시간제한_모드는_시간이_끝나면_점수로_판정한다() throws Exception {
        startSessions(0);

        // 첫 판: 서버 점수가 높음
        startGame(BattleMode.TIME_LIMIT);
        long remaining = fxGet(() -> host.remainingMillis());
        assertTrue(remaining > 0 && remaining <= FAST.timeLimitMs(), "남은 시간: " + remaining);
        assertEquals(new Recorder.TimeUp(), hostEvents.next());
        assertEquals(new Recorder.TimeUp(), guestEvents.next());
        onFx(() -> {
            host.submitFinalScore(500);
            guest.submitFinalScore(300);
        });
        assertEquals(new Recorder.Result(Outcome.WIN), hostEvents.next());
        assertEquals(new Recorder.Result(Outcome.LOSE), guestEvents.next());

        // 둘째 판: 동점
        startGame(BattleMode.TIME_LIMIT);
        assertEquals(new Recorder.TimeUp(), hostEvents.next());
        assertEquals(new Recorder.TimeUp(), guestEvents.next());
        onFx(() -> {
            guest.submitFinalScore(400);
            host.submitFinalScore(400);
        });
        assertEquals(new Recorder.Result(Outcome.DRAW), hostEvents.next());
        assertEquals(new Recorder.Result(Outcome.DRAW), guestEvents.next());
    }

    @Test
    void 결과_뒤에는_로비에서_다시_시작할_수_있다() throws Exception {
        startSessions(0);
        startGame(BattleMode.NORMAL);
        onFx(() -> guest.gameOver(0));
        hostEvents.nextOf(Recorder.Result.class);
        guestEvents.nextOf(Recorder.Result.class);

        Message.Start second = startGame(BattleMode.NORMAL);

        assertNotNull(second);
        assertEquals(Session.State.PLAYING, fxGet(() -> host.state()));
        assertEquals(Session.State.PLAYING, fxGet(() -> guest.state()));
    }

    // ---------- 끝나는 경우 ----------

    @Test
    void 나가면_상대에게_PEER_LEFT가_가고_나간_쪽에는_아무것도_오지_않는다() throws Exception {
        startSessions(0);
        hostEvents.next();
        guestEvents.next();

        onFx(() -> guest.leave());

        assertEquals(new Recorder.Ended(End.PEER_LEFT), hostEvents.next());
        guestEvents.assertNothingFor(300);
        guestEvents.statuses.clear();
        Thread.sleep(200);
        assertTrue(guestEvents.statuses.isEmpty(), "나간 뒤에도 연결 상태 알림이 옴");
        assertEquals(Session.State.CLOSED, fxGet(() -> host.state()));
    }

    @Test
    void 응답이_없으면_랙을_표시한_뒤_끊는다() throws Exception {
        startSessions(0);
        hostEvents.next();
        guestEvents.next();

        proxy.setPaused(true);   // 연결은 둔 채 전달만 멈춘다

        hostEvents.awaitLagging(true);
        guestEvents.awaitLagging(true);
        assertEquals(new Recorder.Ended(End.TIMEOUT), hostEvents.next());
        assertEquals(new Recorder.Ended(End.TIMEOUT), guestEvents.next());
    }

    @Test
    void 지연만_있으면_랙만_표시하고_끊지_않는다() throws Exception {
        startSessions(150);   // 한 방향 150ms (왕복 약 300ms)
        hostEvents.next();
        guestEvents.next();

        hostEvents.awaitLagging(true);
        guestEvents.awaitLagging(true);

        hostEvents.assertNothingFor(1_500);   // 끊김 기준(1초)이 지나도 끊기지 않는다
        guestEvents.assertNothingFor(0);
        assertEquals(Session.State.LOBBY, fxGet(() -> host.state()));
    }

    @Test
    void 연결이_갑자기_끊기면_CONNECTION_LOST() throws Exception {
        startSessions(0);
        hostEvents.next();

        guestConnection.close();   // Bye 없이 소켓을 닫는다 (상대 프로그램이 갑자기 꺼진 상황)

        assertEquals(new Recorder.Ended(End.CONNECTION_LOST), hostEvents.next());
    }

    // ---------- 테스트 도구 ----------

    /** 연결 두 개를 TestProxy를 거쳐 만들고 세션 두 개를 시작한다 (서버 난이도는 HARD) */
    private void startSessions(int delayMs) throws Exception {
        ServerSocket listening = new ServerSocket(0, 1, LOOPBACK);
        toClose.add(listening);
        proxy = TestProxy.start(listening.getLocalPort(), delayMs, 0);
        toClose.add(proxy);

        CompletableFuture<Connection> accepted = CompletableFuture.supplyAsync(() -> {
            try {
                return Connection.open(listening.accept());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        guestConnection = Connection.open(new Socket(LOOPBACK, proxy.port()));
        Connection hostConnection = accepted.get(WAIT_MS, TimeUnit.MILLISECONDS);
        toClose.add(hostConnection::close);
        toClose.add(guestConnection::close);

        host = new HostSession(hostConnection, Difficulty.HARD, FAST, hostEvents, fx);
        guest = new GuestSession(guestConnection, FAST, guestEvents, fx);
        onFx(() -> {
            host.start();
            guest.start();
        });
    }

    /** 로비에서 모드를 정하고 양쪽이 준비해서 게임 시작까지 간다 */
    private Message.Start startGame(BattleMode mode) throws Exception {
        onFx(() -> host.setMode(mode));
        awaitLobby(guestEvents, lobby -> lobby.mode() == mode);
        onFx(() -> guest.setReady(true));
        awaitLobby(hostEvents, Message.LobbyState::guestReady);
        onFx(() -> host.setReady(true));

        Message.Start start = awaitAfterLobbies(hostEvents, Recorder.Countdown.class).start();
        awaitAfterLobbies(guestEvents, Recorder.Countdown.class);
        hostEvents.nextOf(Recorder.GameStart.class);
        guestEvents.nextOf(Recorder.GameStart.class);
        return start;
    }

    /** 로비 상태를 건너뛰다가 condition에 맞는 것이 오면 멈춘다 */
    private static void awaitLobby(Recorder recorder, Predicate<Message.LobbyState> condition)
            throws InterruptedException {
        while (true) {
            Message.LobbyState lobby = recorder.nextOf(Message.LobbyState.class);
            if (condition.test(lobby)) {
                return;
            }
        }
    }

    /** 로비 상태를 건너뛰고 그다음 기록을 type으로 받는다 */
    private static <T> T awaitAfterLobbies(Recorder recorder, Class<T> type) throws InterruptedException {
        while (true) {
            Object event = recorder.next();
            if (!(event instanceof Message.LobbyState)) {
                return assertInstanceOf(type, event);
            }
        }
    }

    private void onFx(Runnable task) throws Exception {
        fx.submit(task).get(WAIT_MS, TimeUnit.MILLISECONDS);
    }

    private <T> T fxGet(Callable<T> task) throws Exception {
        return fx.submit(task).get(WAIT_MS, TimeUnit.MILLISECONDS);
    }

    /** 점수 칸에 번호를 넣은 빈 보드 */
    private static BoardSnapshot board(int score) {
        return new BoardSnapshot(20, 10, new byte[200], 0, null, null, score, 1, 0, "", false);
    }

    /** SessionListener가 받은 것을 순서대로 기록한다 (연결 상태는 자주 와서 따로 모은다) */
    private static final class Recorder implements SessionListener {

        record Countdown(Message.Start start) {
        }

        record GameStart(Message.Start start) {
        }

        record Attack(List<GarbageLine> lines) {
        }

        record TimeUp() {
        }

        record Result(Outcome outcome) {
        }

        record Ended(End reason) {
        }

        record Status(boolean lagging, long rttMillis, long silentMillis) {
        }

        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        private final BlockingQueue<Status> statuses = new LinkedBlockingQueue<>();

        @Override
        public void onLobby(Message.LobbyState lobby) {
            events.add(lobby);
        }

        @Override
        public void onCountdown(Message.Start start) {
            events.add(new Countdown(start));
        }

        @Override
        public void onGameStart(Message.Start start) {
            events.add(new GameStart(start));
        }

        @Override
        public void onAttack(List<GarbageLine> lines) {
            events.add(new Attack(lines));
        }

        @Override
        public void onTimeUp() {
            events.add(new TimeUp());
        }

        @Override
        public void onResult(Outcome outcome) {
            events.add(new Result(outcome));
        }

        @Override
        public void onNetworkStatus(boolean lagging, long rttMillis, long silentMillis) {
            statuses.add(new Status(lagging, rttMillis, silentMillis));
        }

        @Override
        public void onEnded(End reason) {
            events.add(new Ended(reason));
        }

        /** 다음 기록 (WAIT_MS 안에 오지 않으면 실패) */
        Object next() throws InterruptedException {
            Object event = events.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(event, "기다려도 오지 않음");
            return event;
        }

        <T> T nextOf(Class<T> type) throws InterruptedException {
            return assertInstanceOf(type, next());
        }

        /** ms 동안 아무것도 오지 않아야 한다 (연결 상태 알림은 빼고) */
        void assertNothingFor(long ms) throws InterruptedException {
            Object event = events.poll(ms, TimeUnit.MILLISECONDS);
            assertNull(event, "오지 않아야 할 것이 옴: " + event);
        }

        /** 랙 표시가 lagging으로 바뀐 알림이 올 때까지 기다린다 */
        void awaitLagging(boolean lagging) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
            while (System.nanoTime() < deadline) {
                Status status = statuses.poll(50, TimeUnit.MILLISECONDS);
                if (status != null && status.lagging() == lagging) {
                    return;
                }
            }
            fail("랙 표시가 " + lagging + "로 바뀌지 않음");
        }
    }
}
