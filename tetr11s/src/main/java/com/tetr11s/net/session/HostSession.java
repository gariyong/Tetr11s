package com.tetr11s.net.session;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;

import com.tetr11s.game.Difficulty;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.net.Connection;
import com.tetr11s.net.Message;

/**
 * 서버 쪽 세션: 로비 상태를 갖고, 게임을 시작하고, 승패를 판정한다 (서버가 PLAYER1)
 *
 * 로비
 * - 모드와 양쪽 준비 여부를 갖고, 바뀔 때마다 LobbyState를 보낸다 (자기 화면에도 onLobby로 알림)
 * - 모드를 바꾸면 양쪽 준비가 풀린다
 * - 클라이언트의 Ready는 지금 모드와 같을 때만 받는다 (모드가 바뀌기 전에 누른 것은 무시)
 * - 둘 다 준비되면 seed를 만들어 Start를 보내고 카운트다운을 시작한다
 * 판정
 * - 한쪽이 게임오버되면 drawWindowMs를 기다렸다가 판정한다 (그 사이에 상대도 끝나면 무승부)
 * - 시간제한 모드는 서버 시계로 시간이 끝나면 TimeUp을 보내고, 양쪽 최종 점수가 모이면 판정한다
 */
public final class HostSession extends Session {

    private final Difficulty difficulty;
    private final Random random = new Random();

    // 로비
    private BattleMode mode = BattleMode.NORMAL;
    private boolean hostReady;
    private boolean guestReady;

    // 이번 판 판정
    private boolean hostOver;
    private boolean guestOver;
    private boolean judging;          // 게임오버 판정을 예약했음
    private Integer hostFinalScore;   // 시간 종료 후 받은 점수 (아직이면 null)
    private Integer guestFinalScore;

    /**
     * @param difficulty 이번 대전에 쓸 난이도 (서버의 설정값, 양쪽에 같이 적용됨)
     */
    public HostSession(Connection connection, Difficulty difficulty, SessionListener listener, Executor executor) {
        this(connection, difficulty, SessionSettings.DEFAULT, listener, executor);
    }

    public HostSession(Connection connection, Difficulty difficulty, SessionSettings settings,
                       SessionListener listener, Executor executor) {
        super(connection, settings, listener, executor);
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
    }

    /** 대전 모드를 바꾼다 (로비에서만). 양쪽 준비가 풀린다 */
    public void setMode(BattleMode newMode) {
        Objects.requireNonNull(newMode, "newMode");
        if (state != State.LOBBY || newMode == mode) {
            return;
        }
        mode = newMode;
        hostReady = false;
        guestReady = false;
        broadcastLobby();
    }

    public BattleMode mode() {
        return mode;
    }

    @Override
    public void setReady(boolean ready) {
        if (state != State.LOBBY) {
            return;
        }
        hostReady = ready;
        broadcastLobby();
        startIfBothReady();
    }

    @Override
    public void gameOver(int score) {
        if (state == State.COUNTDOWN || state == State.PLAYING) {
            state = State.FINISHED;
            gameOverOf(true);
        } else if (state == State.TIME_UP) {
            submitFinalScore(score);
        }
    }

    @Override
    public void submitFinalScore(int score) {
        if (state == State.TIME_UP && hostFinalScore == null) {
            hostFinalScore = score;
            finishIfScored();
        }
    }

    @Override
    protected void onStarted() {
        broadcastLobby();
    }

    @Override
    protected void handle(Message message) {
        switch (message) {
            case Message.Ready ready -> {
                if (state == State.LOBBY && ready.mode() == mode) {
                    guestReady = ready.ready();
                    broadcastLobby();
                    startIfBothReady();
                }
            }
            case Message.GameOver over -> {
                if (state == State.COUNTDOWN || state == State.PLAYING || state == State.FINISHED) {
                    gameOverOf(false);
                } else if (state == State.TIME_UP) {
                    guestScore(over.score());   // 시간 종료와 거의 동시에 끝남: 그때 점수로 친다
                }
            }
            case Message.FinalScore finalScore -> {
                if (state == State.TIME_UP) {
                    guestScore(finalScore.score());
                }
            }
            default -> {
                // 서버가 받을 일이 없는 메시지
            }
        }
    }

    @Override
    protected void onGameStarted(Message.Start start) {
        if (start.mode() == BattleMode.TIME_LIMIT) {
            later(start.timeLimitMs(), this::timeUp);
        }
    }

    private void startIfBothReady() {
        if (!hostReady || !guestReady) {
            return;
        }
        Message.Start start = new Message.Start(mode, difficulty, random.nextLong(),
                settings.timeLimitMs(), settings.countdownMs());
        hostReady = false;
        guestReady = false;
        hostOver = false;
        guestOver = false;
        judging = false;
        hostFinalScore = null;
        guestFinalScore = null;
        connection.send(start);
        beginCountdown(start);
    }

    private void gameOverOf(boolean host) {
        if (host) {
            hostOver = true;
        } else {
            guestOver = true;
        }
        if (!judging) {
            judging = true;
            later(settings.drawWindowMs(), () -> finish(BattleJudge.byGameOver(hostOver, guestOver)));
        }
    }

    private void timeUp() {
        if (state != State.PLAYING || judging) {
            return;   // 이미 게임오버로 판정 중
        }
        state = State.TIME_UP;
        connection.send(new Message.TimeUp());
        listener.onTimeUp();
    }

    private void guestScore(int score) {
        if (guestFinalScore == null) {
            guestFinalScore = score;
            finishIfScored();
        }
    }

    private void finishIfScored() {
        if (hostFinalScore != null && guestFinalScore != null) {
            finish(BattleJudge.byScore(hostFinalScore, guestFinalScore));
        }
    }

    private void finish(BattleResult result) {
        connection.send(new Message.Result(result));
        finishRound(outcomeFor(result, true));
        broadcastLobby();   // 다시 로비: 준비는 풀린 상태
    }

    private void broadcastLobby() {
        Message.LobbyState lobby = new Message.LobbyState(mode, hostReady, guestReady);
        connection.send(lobby);
        listener.onLobby(lobby);
    }
}
