package com.tetr11s.net.session;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tetr11s.game.BoardSnapshot;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.net.CloseReason;
import com.tetr11s.net.Connection;
import com.tetr11s.net.Message;

/**
 * 연결된 상대 한 명과의 P2P 대전 전체: 로비 → 카운트다운 → 게임 → 결과 → 로비 → ...
 * 서버 쪽은 HostSession, 클라이언트 쪽은 GuestSession을 쓴다
 *
 * 공통으로 하는 일 (이 클래스)
 * - 연결 시작과 나가기
 * - 하트비트: 핑을 보내고, 왕복 시간과 무응답 시간으로 랙·끊김을 판정한다
 * - 카운트다운과 게임 중 공격·스냅샷 전달
 * 역할마다 다른 일 (하위 클래스)
 * - 서버: 로비 상태 관리, 시작, 승패 판정
 * - 클라이언트: 준비 요청, 결과 받기
 *
 * 스레드 규칙: 모든 메서드는 화면 스레드(생성할 때 넘긴 실행기)에서 부른다
 * 연결과 타이머의 알림도 전부 그 실행기로 넘어오므로, 세션 상태는 한 스레드만 만지고 자물쇠가 필요 없다
 */
public abstract class Session {

    public enum State {
        LOBBY,
        COUNTDOWN,
        PLAYING,
        /** 내 게임이 끝나서 결과를 기다리는 중 */
        FINISHED,
        /** 시간제한 모드에서 시간이 끝나 최종 점수를 모으는 중 */
        TIME_UP,
        CLOSED
    }

    protected final Connection connection;
    protected final SessionSettings settings;
    protected final SessionListener listener;
    private final Executor executor;
    private final LagMonitor lag;
    private final long pingIntervalNanos;

    protected State state = State.LOBBY;
    private ScheduledExecutorService timer;
    private int round;                  // 판 번호: 지난 판에 예약해 둔 일이 늦게 실행되지 않게
    private Message.Start current;      // 이번 판 설정 (로비에서는 null)
    private long gameStartNanos;
    private long lastPingNanos;
    private LagMonitor.State lastLagState = LagMonitor.State.NORMAL;

    /**
     * @param connection 3단계(Host, Guest)가 넘겨준, 아직 start하지 않은 연결
     * @param executor   알림을 부를 실행기 (앱: Platform::runLater)
     */
    protected Session(Connection connection, SessionSettings settings, SessionListener listener, Executor executor) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.lag = new LagMonitor(settings);
        this.pingIntervalNanos = TimeUnit.MILLISECONDS.toNanos(settings.pingIntervalMs());
    }

    // ----- 시작과 끝 -----

    /** 연결과 하트비트를 시작한다 (한 번만 부를 수 있음) */
    public final void start() {
        if (timer != null) {
            throw new IllegalStateException("이미 시작한 세션");
        }
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "p2p-session");
            thread.setDaemon(true);
            return thread;
        });
        lastPingNanos = System.nanoTime() - pingIntervalNanos;   // 첫 검사 때 바로 핑을 보낸다
        connection.start(new ConnectionEvents(), executor);
        timer.scheduleAtFixedRate(() -> post(this::tick), settings.tickMs(), settings.tickMs(), TimeUnit.MILLISECONDS);
        onStarted();
    }

    /** 나간다: Bye를 보내고 연결을 닫는다. 부른 뒤에는 알림이 오지 않는다 (여러 번 불러도 됨) */
    public final void leave() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        stopTimer();
        connection.close(Message.Bye.Reason.LEFT);
    }

    // ----- 화면이 부르는 것 -----

    /** '게임 시작' 버튼을 눌렀다(true) / 취소했다(false). 로비에서만 */
    public abstract void setReady(boolean ready);

    /** 내 화면 상태를 보낸다. 매 프레임 불러도 된다 (상대가 받을 수 있는 만큼 최신 것만 간다) */
    public final void sendSnapshot(BoardSnapshot board) {
        if (state != State.CLOSED) {
            connection.sendSnapshot(board);
        }
    }

    /** 내 게임이 만든 공격 줄을 상대에게 보낸다 (게임 중에만) */
    public final void sendAttack(List<GarbageLine> lines) {
        if (state == State.PLAYING && !lines.isEmpty()) {
            connection.send(Message.Attack.of(lines));
        }
    }

    /** 내 게임이 끝났다 (더 쌓을 수 없음, 또는 기권) */
    public abstract void gameOver(int score);

    /** onTimeUp을 받은 뒤 내 최종 점수를 낸다 */
    public abstract void submitFinalScore(int score);

    // ----- 상태 조회 -----

    public final State state() {
        return state;
    }

    /** 상대의 최신 화면 (아직 받지 못했으면 null) */
    public final BoardSnapshot peerSnapshot() {
        return connection.peerSnapshot();
    }

    /** 이번 판 설정 (로비에서는 null) */
    public final Message.Start currentStart() {
        return current;
    }

    /** 시간제한 모드의 남은 시간(ms). 시간제한 게임 중이 아니면 -1 */
    public final long remainingMillis() {
        if (current == null || current.mode() != BattleMode.TIME_LIMIT
                || (state != State.PLAYING && state != State.FINISHED)) {
            return -1;
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - gameStartNanos);
        return Math.max(0, current.timeLimitMs() - elapsed);
    }

    /** 최근 평균 왕복 시간(ms), 아직 못 쟀으면 -1 */
    public final long rttMillis() {
        return lag.averageRttMillis();
    }

    /** 상대 IP (화면 표시용) */
    public final String remoteAddress() {
        return connection.remoteAddress();
    }

    // ----- 하위 클래스가 채우는 것 -----

    /** start()가 끝날 때 (서버는 첫 로비 상태를 보낸다) */
    protected abstract void onStarted();

    /** Bye, Attack을 뺀 메시지 (역할마다 다르게 처리) */
    protected abstract void handle(Message message);

    /** 카운트다운이 끝나 게임이 시작될 때 (서버는 시간제한 타이머를 건다) */
    protected void onGameStarted(Message.Start start) {
    }

    // ----- 하위 클래스가 쓰는 것 -----

    /** 이번 판 카운트다운을 시작하고, 끝나면 게임 시작을 알린다 */
    protected final void beginCountdown(Message.Start start) {
        state = State.COUNTDOWN;
        current = start;
        round++;
        listener.onCountdown(start);
        later(start.countdownMs(), () -> {
            if (state != State.COUNTDOWN) {
                return;   // 카운트다운 중에 기권함
            }
            state = State.PLAYING;
            gameStartNanos = System.nanoTime();
            listener.onGameStart(start);
            onGameStarted(start);
        });
    }

    /** 이번 판을 끝내고 결과를 알린다. 이 뒤로 로비 상태다 */
    protected final void finishRound(SessionListener.Outcome outcome) {
        state = State.LOBBY;
        current = null;
        round++;
        listener.onResult(outcome);
    }

    /** delayMs 뒤에 화면 스레드에서 task를 실행한다. 그 사이에 판이 바뀌었거나 세션이 끝났으면 실행하지 않는다 */
    protected final void later(long delayMs, Runnable task) {
        int scheduledRound = round;
        try {
            timer.schedule(() -> post(() -> {
                if (state != State.CLOSED && round == scheduledRound) {
                    task.run();
                }
            }), delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // 타이머가 멈춤: 세션이 끝남
        }
    }

    /** 상대 때문에 끝남: 연결을 닫고 이유를 한 번 알린다 */
    protected final void end(SessionListener.End reason) {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        stopTimer();
        connection.close();
        listener.onEnded(reason);
    }

    /** 서버 기준 결과(PLAYER1 = 서버)를 나 기준 승패로 바꾼다 */
    protected static SessionListener.Outcome outcomeFor(BattleResult result, boolean host) {
        return switch (result) {
            case DRAW -> SessionListener.Outcome.DRAW;
            case PLAYER1_WIN -> host ? SessionListener.Outcome.WIN : SessionListener.Outcome.LOSE;
            case PLAYER2_WIN -> host ? SessionListener.Outcome.LOSE : SessionListener.Outcome.WIN;
        };
    }

    // ----- 내부 처리 -----

    private void receive(Message message) {
        if (state == State.CLOSED) {
            return;
        }
        switch (message) {
            case Message.Bye bye -> end(SessionListener.End.PEER_LEFT);
            case Message.Attack attack -> {
                if (state == State.PLAYING) {
                    listener.onAttack(attack.toLines());
                }
            }
            default -> handle(message);
        }
    }

    // 하트비트: tickMs마다 화면 스레드에서 실행된다
    private void tick() {
        if (state == State.CLOSED) {
            return;
        }
        long now = System.nanoTime();
        boolean pingTime = now - lastPingNanos >= pingIntervalNanos;
        if (pingTime) {
            lag.addRtt(connection.rttMillis());   // 지난 핑의 답으로 잰 값 (아직 못 쟀으면 무시됨)
            connection.ping();
            lastPingNanos = now;
        }

        long silent = connection.millisSinceLastReceive();
        LagMonitor.State lagState = lag.update(silent);
        if (lagState == LagMonitor.State.DISCONNECTED) {
            end(SessionListener.End.TIMEOUT);
            return;
        }
        if (pingTime || lagState != lastLagState) {
            lastLagState = lagState;
            listener.onNetworkStatus(lagState == LagMonitor.State.LAGGING, lag.averageRttMillis(), silent);
        }
    }

    private void post(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // 실행기가 멈춤 (프로그램이 끝나는 중)
        }
    }

    private void stopTimer() {
        if (timer != null) {
            timer.shutdownNow();
        }
    }

    /** 연결의 알림을 세션으로 넘긴다 (Connection.Listener를 세션 바깥에 드러내지 않으려고 따로 둠) */
    private final class ConnectionEvents implements Connection.Listener {

        @Override
        public void onMessage(Message message) {
            receive(message);
        }

        @Override
        public void onClosed(CloseReason reason, Exception cause) {
            end(reason == CloseReason.PROTOCOL_ERROR
                    ? SessionListener.End.PROTOCOL_ERROR : SessionListener.End.CONNECTION_LOST);
        }
    }
}
