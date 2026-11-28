package com.tetr11s.net.flow;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.tetr11s.game.Difficulty;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;
import com.tetr11s.net.Message;
import com.tetr11s.net.connect.ConnectListener;
import com.tetr11s.net.connect.Guest;
import com.tetr11s.net.connect.Host;
import com.tetr11s.net.connect.PeerAddress;
import com.tetr11s.net.connect.RecentHosts;
import com.tetr11s.net.session.GuestSession;
import com.tetr11s.net.session.HostSession;
import com.tetr11s.net.session.Session;
import com.tetr11s.net.session.SessionListener;
import com.tetr11s.net.session.SessionSettings;

/**
 * P2P 대전의 흐름 담당: 네트워크(Host, Guest, Session)에서 일어난 일을 보고 어느 화면에 무엇을 보여 줄지 정한다
 *
 * P2P 첫 화면 → 서버 열기 → 서버 대기 ─┐
 *            → 서버에 접속 → 접속 ─────┴→ 로비 → (카운트다운) → 대전 → 결과 → 로비 → ...
 * 끊기거나 상대가 나가면 이유와 함께 P2P 첫 화면으로 돌아간다
 *
 * 화면은 Screens와 HostWaitView·JoinView·LobbyView로만 다뤄서 JavaFX 없이 테스트할 수 있다
 * 스레드 규칙: 모든 메서드는 화면 스레드(생성할 때 넘긴 실행기)에서 부르고, 네트워크 알림도 그 실행기로 온다
 */
public final class P2PFlow {

    /** 흐름이 화면을 바꿀 때 쓰는 것 (앱에서는 ScreenManager, 테스트에서는 가짜 화면) */
    public interface Screens {

        /** P2P 첫 화면 (message: 돌아온 이유, 없으면 null) */
        void showMenu(String message);

        HostWaitView showHostWait();

        JoinView showJoin();

        LobbyView showLobby();

        /**
         * 카운트다운이 끝나 대전이 시작됨. 대전 화면을 띄웠으면 true
         * (대전 화면은 7단계에서 붙인다. 그 전에는 false를 돌려주고, 흐름은 로비에 안내만 띄운다)
         */
        boolean showBattle(Session session, Message.Start start);
    }

    /**
     * 네트워크 설정 (앱은 DEFAULT, 테스트는 127.0.0.1과 빈 포트, 빠른 세션 설정)
     *
     * @param bindAddress 서버가 접속을 받을 주소 (null이면 모든 네트워크)
     * @param port        서버가 열 포트 (0이면 비어 있는 포트)
     */
    public record Config(InetAddress bindAddress, int port, Connection.Options connection,
                         int connectTimeoutMs, SessionSettings session) {

        public static final Config DEFAULT = new Config(null, PeerAddress.DEFAULT_PORT, Connection.Options.DEFAULT,
                Guest.CONNECT_TIMEOUT_MS, SessionSettings.DEFAULT);

        public Config {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(session, "session");
        }
    }

    // 창을 닫을 때 Bye가 상대에게 갈 시간 (보내기 스레드가 데몬이라, 기다리지 않으면 보내기 전에 프로그램이 끝날 수 있다)
    private static final long BYE_FLUSH_MS = 200;

    private final Screens screens;
    private final Executor fx;
    private final Config config;
    private final RecentHosts recent;
    private final Supplier<List<InetAddress>> myAddresses;
    private final Supplier<Difficulty> difficulty;

    private int generation;          // 화면이 바뀔 때마다 1씩: 지난 화면에 늦게 도착한 알림을 버리려고
    private Host host;               // 서버 대기 중
    private Guest guest;             // 접속 중
    private Session session;         // 연결된 뒤
    private boolean isHost;
    private LobbyView lobby;         // 로비를 보여 주는 중이면 그 화면, 대전 화면이면 null
    private boolean myReady;
    private String difficultyText;   // 로비 위쪽에 보여 줄 난이도 안내 (대전 뒤 로비를 다시 띄울 때 씀)

    /**
     * @param fx          화면 스레드 실행기 (앱: Platform::runLater)
     * @param myAddresses 내 IP 찾기 (앱: LocalAddresses::find, 오래 걸릴 수 있어 따로 돌린다)
     * @param difficulty  서버가 쓸 난이도 (앱: SettingsStore::loadDifficulty)
     */
    public P2PFlow(Screens screens, Executor fx, Config config, RecentHosts recent,
                   Supplier<List<InetAddress>> myAddresses, Supplier<Difficulty> difficulty) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.config = Objects.requireNonNull(config, "config");
        this.recent = Objects.requireNonNull(recent, "recent");
        this.myAddresses = Objects.requireNonNull(myAddresses, "myAddresses");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
    }

    // ----- P2P 첫 화면에서 고르는 것 -----

    /** 서버 열기: 서버 대기 화면을 띄우고 접속을 기다린다 */
    public void openHost() {
        close();
        int current = generation;
        HostWaitView view = screens.showHostWait();
        view.setOnCancel(this::backToMenu);
        view.showStatus("서버를 여는 중입니다...", false);
        findMyAddresses(view, current);
        host = Host.start(config.bindAddress(), config.port(), config.connection(), new ConnectListener() {
            @Override
            public void onStep(Step step, String detail) {
                view.showStatus(step.message(detail), false);
            }

            @Override
            public void onPeerRejected(String address, HandshakeException.Reason reason) {
                view.showStatus(reason.rejectedMessage(address), true);
            }

            @Override
            public void onConnected(Connection connection) {
                host = null;
                Difficulty level = difficulty.get();
                startSession(new HostSession(connection, level, config.session(), new Events(), fx),
                        true, "난이도 " + level.displayName());
            }

            @Override
            public void onFailed(Failure failure, Exception cause) {
                host = null;
                view.showStatus(failure.message(), true);
            }
        }, fx);
    }

    /** 서버에 접속: 접속 화면을 띄운다 (실제 접속은 주소를 넣고 Enter를 눌렀을 때) */
    public void openJoin() {
        close();
        JoinView view = screens.showJoin();
        view.showRecent(recent.load().stream().map(PeerAddress::toString).toList());
        view.showStatus("", false);
        view.setListener(new JoinView.Listener() {
            @Override
            public void onConnect(String input) {
                connect(view, input);
            }

            @Override
            public void onCancel() {
                if (guest != null) {
                    guest.cancel();
                    guest = null;
                    view.setConnecting(false);
                    view.showStatus("접속을 취소했습니다.", false);
                } else {
                    backToMenu();
                }
            }
        });
    }

    // ----- 끝내기 -----

    /** 하던 것을 모두 멈춘다: 서버 대기·접속을 취소하고, 연결돼 있으면 Bye를 보내고 닫는다 (여러 번 불러도 됨) */
    public void close() {
        generation++;
        lobby = null;
        if (host != null) {
            host.cancel();
            host = null;
        }
        if (guest != null) {
            guest.cancel();
            guest = null;
        }
        if (session != null) {
            session.leave();
            session = null;
        }
    }

    /** 프로그램이 끝날 때: close하고, 연결돼 있었으면 Bye가 나갈 시간을 잠깐 준다 */
    public void shutdown() {
        boolean connected = session != null;
        close();
        if (connected) {
            try {
                Thread.sleep(BYE_FLUSH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ----- 테스트용 -----

    /** 서버가 실제로 연 포트 (서버 대기 중이 아니면 -1) */
    int hostPort() {
        Host waiting = host;
        return waiting == null ? -1 : waiting.localPort();
    }

    // ----- 내부 처리 -----

    private void backToMenu() {
        close();
        screens.showMenu(null);
    }

    // 내 IP는 오래 걸릴 수 있어서(윈도우) 따로 찾고, 찾으면 그때도 같은 화면일 때만 보여 준다
    private void findMyAddresses(HostWaitView view, int current) {
        String port = config.port() == 0 || config.port() == PeerAddress.DEFAULT_PORT ? "" : ":" + config.port();
        Thread thread = new Thread(() -> {
            List<String> addresses = new ArrayList<>();
            for (InetAddress address : myAddresses.get()) {
                addresses.add(address.getHostAddress() + port);
            }
            fx.execute(() -> {
                if (generation == current) {
                    view.showAddresses(addresses);
                }
            });
        }, "p2p-my-ip");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect(JoinView view, String input) {
        if (guest != null || session != null) {
            return;   // 이미 접속 중
        }
        PeerAddress address = PeerAddress.parse(input).orElse(null);
        if (address == null) {
            view.showStatus("IP 주소 형식이 아닙니다. 예: 192.168.0.12 (포트를 바꿨다면 192.168.0.12:5000)", true);
            return;
        }
        view.setConnecting(true);
        guest = Guest.start(address, config.connectTimeoutMs(), config.connection(), new ConnectListener() {
            @Override
            public void onStep(Step step, String detail) {
                view.showStatus(step.message(detail), false);
            }

            @Override
            public void onConnected(Connection connection) {
                guest = null;
                recent.remember(address);   // 접속에 성공한 주소만 기억한다
                startSession(new GuestSession(connection, config.session(), new Events(), fx),
                        false, "난이도는 서버 설정을 따릅니다");
            }

            @Override
            public void onFailed(Failure failure, Exception cause) {
                guest = null;
                view.setConnecting(false);
                view.showStatus(failure.message(), true);
            }
        }, fx);
    }

    private void startSession(Session newSession, boolean asHost, String difficultyText) {
        generation++;
        session = newSession;
        isHost = asHost;
        myReady = false;
        showLobby(difficultyText);
        session.start();   // 서버는 여기서 첫 로비 상태를 보낸다
    }

    private void showLobby(String text) {
        difficultyText = text;
        lobby = screens.showLobby();
        lobby.setListener(new LobbyView.Listener() {
            @Override
            public void onModeSelected(BattleMode mode) {
                if (session instanceof HostSession hostSession) {
                    hostSession.setMode(mode);
                }
            }

            @Override
            public void onToggleReady() {
                if (session != null) {
                    session.setReady(!myReady);
                }
            }

            @Override
            public void onLeave() {
                backToMenu();
            }
        });
        lobby.showConnection(isHost, session.remoteAddress(), text);
    }

    /** 세션이 알려 주는 일 (startSession마다 새로 만들어서, 지난 세션의 알림과 섞이지 않게 한다) */
    private final class Events implements SessionListener {

        private final int owner = generation + 1;   // startSession이 1 올린 뒤의 값

        private boolean mine() {
            return generation == owner && session != null;
        }

        @Override
        public void onLobby(Message.LobbyState state) {
            if (!mine()) {
                return;
            }
            myReady = isHost ? state.hostReady() : state.guestReady();
            boolean peerReady = isHost ? state.guestReady() : state.hostReady();
            if (lobby != null) {
                lobby.showLobby(state.mode(), myReady, peerReady);
            }
        }

        @Override
        public void onCountdown(Message.Start start) {
            if (mine() && lobby != null) {
                lobby.showResult("");
                lobby.showCountdown(start.countdownMs());
            }
        }

        @Override
        public void onGameStart(Message.Start start) {
            if (!mine()) {
                return;
            }
            if (screens.showBattle(session, start)) {
                lobby = null;   // 대전 화면으로 넘어감
            } else if (lobby != null) {
                lobby.showMessage("대전이 시작됐습니다. 대전 화면은 다음 단계에서 연결됩니다 (Esc로 나가기)");
            }
        }

        @Override
        public void onAttack(List<GarbageLine> lines) {
            // 대전 화면(7단계)이 받는다
        }

        @Override
        public void onTimeUp() {
            if (mine() && lobby != null) {
                session.submitFinalScore(0);   // 대전 화면이 아직 없을 때: 점수 0으로 낸다 (7단계에서는 대전 화면이 낸다)
            }
        }

        @Override
        public void onResult(Outcome outcome) {
            if (!mine()) {
                return;
            }
            if (lobby == null) {
                showLobby(difficultyText);   // 대전 화면에서 로비로 돌아온다
            }
            lobby.showMessage("");
            lobby.showResult(outcome.message());
        }

        @Override
        public void onNetworkStatus(boolean lagging, long rttMillis, long silentMillis) {
            if (mine() && lobby != null) {
                lobby.showNetwork(lagging, rttMillis);
            }
        }

        @Override
        public void onEnded(End reason) {
            if (!mine()) {
                return;
            }
            session = null;   // 이미 닫혔으니 close에서 다시 닫지 않는다
            close();
            screens.showMenu(reason.message());
        }
    }
}
