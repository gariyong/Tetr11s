package com.tetr11s.net;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.net.connect.ConnectListener;
import com.tetr11s.net.session.SessionListener;

/**
 * 화면에 띄우는 문구가 모든 경우에 따로 있는지 확인한다
 * (문구는 각 enum 값이 갖고 있다: 접속 단계·실패 이유·거부 이유·종료 이유·결과·대전 모드)
 */
class ScreenMessagesTest {

    @Test
    void 접속_단계_문구에는_포트나_주소가_들어간다() {
        assertEquals("포트 51111에서 상대의 접속을 기다리는 중입니다...", ConnectListener.Step.LISTENING.message("51111"));
        assertTrue(ConnectListener.Step.CONNECTING.message("192.168.0.5").contains("192.168.0.5"));
        assertTrue(ConnectListener.Step.CHECKING.message("192.168.0.7").contains("192.168.0.7"));
    }

    @Test
    void 실패_이유마다_서로_다른_문구가_있다() {
        Set<String> texts = new HashSet<>();
        for (ConnectListener.Failure failure : ConnectListener.Failure.values()) {
            assertFalse(failure.message().isBlank(), failure.name());
            texts.add(failure.message());
        }
        assertEquals(ConnectListener.Failure.values().length, texts.size(), "같은 문구를 쓰는 실패 이유가 있음");
    }

    @Test
    void 서버가_상대를_거부한_이유와_주소를_알려_준다() {
        Set<String> texts = new HashSet<>();
        for (HandshakeException.Reason reason : HandshakeException.Reason.values()) {
            String text = reason.rejectedMessage("192.168.0.9");
            assertTrue(text.startsWith("192.168.0.9에서 접속했지만"), text);
            texts.add(text);
        }
        assertEquals(HandshakeException.Reason.values().length, texts.size());
    }

    @Test
    void 끝난_이유와_결과와_모드마다_서로_다른_문구가_있다() {
        Set<String> ended = new HashSet<>();
        for (SessionListener.End end : SessionListener.End.values()) {
            ended.add(end.message());
        }
        assertEquals(SessionListener.End.values().length, ended.size());

        Set<String> outcomes = new HashSet<>();
        for (SessionListener.Outcome outcome : SessionListener.Outcome.values()) {
            outcomes.add(outcome.message());
        }
        assertEquals(SessionListener.Outcome.values().length, outcomes.size());

        Set<String> modes = new HashSet<>();
        for (BattleMode mode : BattleMode.values()) {
            assertFalse(mode.description().isBlank(), mode.name());
            modes.add(mode.description());
        }
        assertEquals(BattleMode.values().length, modes.size());
    }
}