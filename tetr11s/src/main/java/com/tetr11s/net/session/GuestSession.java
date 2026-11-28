package com.tetr11s.net.session;

import java.util.concurrent.Executor;

import com.tetr11s.net.Connection;
import com.tetr11s.net.Message;

/**
 * 클라이언트 쪽 세션: 서버가 알려 주는 대로 따라간다 (클라이언트가 PLAYER2)
 *
 * - 로비 상태는 서버가 보낸 LobbyState를 그대로 보여 준다
 * - '게임 시작'을 누르면 그때 보이던 모드와 함께 Ready를 보낸다
 * - Start를 받으면 카운트다운, TimeUp을 받으면 최종 점수를 보내고, Result를 받으면 로비로 돌아간다
 */
public final class GuestSession extends Session {

    private Message.LobbyState lobby;   // 서버가 마지막으로 알려 준 로비 상태 (처음 받기 전에는 null)
    private boolean finalScoreSent;

    public GuestSession(Connection connection, SessionListener listener, Executor executor) {
        this(connection, SessionSettings.DEFAULT, listener, executor);
    }

    public GuestSession(Connection connection, SessionSettings settings, SessionListener listener, Executor executor) {
        super(connection, settings, listener, executor);
    }

    @Override
    public void setReady(boolean ready) {
        if (state == State.LOBBY && lobby != null) {
            connection.send(new Message.Ready(ready, lobby.mode()));
        }
    }

    @Override
    public void gameOver(int score) {
        if (state == State.PLAYING) {
            state = State.FINISHED;
            connection.send(new Message.GameOver(score));
        }
    }

    @Override
    public void submitFinalScore(int score) {
        if (state == State.TIME_UP && !finalScoreSent) {
            finalScoreSent = true;
            connection.send(new Message.FinalScore(score));
        }
    }

    @Override
    protected void onStarted() {
        // 서버가 첫 로비 상태를 보내 줄 때까지 기다린다
    }

    @Override
    protected void handle(Message message) {
        switch (message) {
            case Message.LobbyState newLobby -> {
                lobby = newLobby;
                if (state == State.LOBBY) {
                    listener.onLobby(newLobby);
                }
            }
            case Message.Start start -> {
                if (state == State.LOBBY) {
                    finalScoreSent = false;
                    beginCountdown(start);
                }
            }
            case Message.TimeUp timeUp -> {
                if (state == State.PLAYING || state == State.FINISHED) {
                    state = State.TIME_UP;
                    listener.onTimeUp();
                }
            }
            case Message.Result result -> {
                if (state != State.LOBBY) {
                    finishRound(outcomeFor(result.result(), false));
                }
            }
            default -> {
                // 클라이언트가 받을 일이 없는 메시지
            }
        }
    }
}
