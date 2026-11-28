package com.tetr11s.net.flow;

import java.util.List;

/**
 * 서버 대기 화면이 할 수 있는 일
 *
 * 화면(JavaFX)은 이 일만 하고, 언제 무엇을 보여 줄지는 P2P 흐름 담당이 정한다
 * 모든 메서드는 화면 스레드에서 부른다
 */
public interface HostWaitView {

    /**
     * 상대에게 알려 줄 내 주소들 (가장 그럴듯한 것이 맨 앞, 예: "192.168.0.12")
     * 비어 있으면 주소를 찾지 못했다는 안내를 보여 준다
     */
    void showAddresses(List<String> addresses);

    /** 진행 상황 한 줄 (error면 에러처럼 눈에 띄게) */
    void showStatus(String text, boolean error);

    /** Esc(취소)를 눌렀을 때 할 일 */
    void setOnCancel(Runnable onCancel);
}