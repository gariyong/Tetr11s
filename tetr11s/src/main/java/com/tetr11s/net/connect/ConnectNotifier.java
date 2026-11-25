package com.tetr11s.net.connect;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.tetr11s.net.Connection;
import com.tetr11s.net.HandshakeException;

/**
 * Host와 Guest가 같이 쓰는 알림 도우미
 *
 * 백그라운드 스레드에서 부르면, listener를 실행기(화면 스레드)에서 부른다
 * cancel()한 뒤에는 부르지 않는다 (이미 실행기에 넘긴 알림도 실행되는 순간에 다시 확인해서 버린다)
 */
final class ConnectNotifier {

    private final ConnectListener listener;
    private final Executor callbacks;
    private volatile boolean cancelled;

    ConnectNotifier(ConnectListener listener, Executor callbacks) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    void cancel() {
        cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void step(ConnectListener.Step step, String detail) {
        deliver(() -> listener.onStep(step, detail));
    }

    void rejected(String address, HandshakeException.Reason reason) {
        deliver(() -> listener.onPeerRejected(address, reason));
    }

    void failed(ConnectListener.Failure failure, Exception cause) {
        deliver(() -> listener.onFailed(failure, cause));
    }

    /**
     * 연결 완료를 알린다
     * 알림이 화면에 가기 전에 취소됐다면 연결을 닫는다 (받을 사람이 없으니 남겨 두면 소켓이 샌다)
     */
    void connected(Connection connection) {
        if (cancelled) {
            connection.close();
            return;
        }
        try {
            callbacks.execute(() -> {
                if (cancelled) {
                    connection.close();
                } else {
                    listener.onConnected(connection);
                }
            });
        } catch (RejectedExecutionException e) {
            connection.close();   // 실행기가 이미 멈춤 (프로그램이 끝나는 중)
        }
    }

    private void deliver(Runnable call) {
        if (cancelled) {
            return;
        }
        try {
            callbacks.execute(() -> {
                if (!cancelled) {
                    call.run();
                }
            });
        } catch (RejectedExecutionException e) {
            // 실행기가 이미 멈춤 (프로그램이 끝나는 중): 알릴 곳이 없다
        }
    }
}