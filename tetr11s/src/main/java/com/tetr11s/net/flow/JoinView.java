package com.tetr11s.net.flow;

import java.util.List;

/**
 * 클라이언트 접속 화면이 할 수 있는 일 (HostWaitView와 같은 이유로 나눠 둠)
 * 모든 메서드는 화면 스레드에서 부른다
 */
public interface JoinView {

    /** 플레이어가 화면에서 한 일 */
    interface Listener {

        /** Enter나 클릭으로 접속을 요청함 (input은 입력칸의 글자 그대로, 검사는 받는 쪽이 한다) */
        void onConnect(String input);

        /** Esc를 누름 (접속 중이면 접속 취소, 아니면 돌아가기) */
        void onCancel();
    }

    void setListener(Listener listener);

    /** 최근에 접속한 주소들 (가장 최근이 맨 앞, 예: "192.168.0.12", "192.168.0.12:5000") */
    void showRecent(List<String> addresses);

    /** 진행 상황 한 줄 (error면 에러처럼 눈에 띄게) */
    void showStatus(String text, boolean error);

    /** 접속 중에는 입력을 막는다 */
    void setConnecting(boolean connecting);
}