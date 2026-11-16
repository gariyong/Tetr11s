package com.tetr11s.net;

/**
 * 상대 때문에 연결이 끝난 이유 (Connection.Listener.onClosed로 온다)
 *
 * 내가 close()를 불러서 닫은 경우는 알리지 않는다 (부른 쪽이 이미 알고 있음)
 */
public enum CloseReason {
    /** 상대가 연결을 정상적으로 닫음 (나가기, 프로그램 종료) */
    PEER_CLOSED,
    /** 네트워크 오류 (연결 끊김, 상대 PC 꺼짐, 보내지 못한 메시지가 너무 많이 쌓임) */
    IO_ERROR,
    /** 상대가 잘못된 데이터를 보냄 (깨진 데이터, 허용하지 않은 클래스, Message가 아닌 객체) */
    PROTOCOL_ERROR
}