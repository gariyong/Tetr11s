package com.tetr11s.net;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.tetr11s.game.BoardSnapshot;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.game.battle.GarbageCodec;
import com.tetr11s.game.battle.GarbageLine;

/**
 * 두 PC가 주고받는 메시지 전체
 *
 * 규칙
 * 모든 메시지는 만든 뒤 바뀌지 않는다 (record 이용)
 * 안에는 기본형, String, enum, BoardSnapshot만 넣는다
 * 서버(방을 연 쪽)가 PLAYER1, 클라이언트(접속한 쪽)가 PLAYER2다
 *
 * sealed라서 여기 적힌 종류 말고는 Message가 될 수 없다
 */
public sealed interface Message extends Serializable {

    /** 메시지나 BoardSnapshot의 구성을 바꾸면 1 올리고, 서로 다르면 접속을 끊는다 */
    int PROTOCOL_VERSION = 3;

    /** 연결 직후 양쪽이 서로 보낸다 */
    record Hello(int protocolVersion) implements Message {
    }

    /** 연결을 끊기 직전에 이유를 알린다: 상대가 나감 (버전이 다른 상대는 연결 직후 Hello에서 이미 걸러진다) */
    record Bye() implements Message {
    }

    /** 서버 -> 클라이언트: 지금 대기 상태 */
    record LobbyState(BattleMode mode, boolean hostReady, boolean guestReady) implements Message {
        public LobbyState {
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * 클라이언트 -> 서버: '게임 시작'을 눌렀다, 취소했다
     * mode는 그때 클라이언트에 보이던 모드 (그 사이 서버가 모드를 바꿨으면 서버가 무시한다)
     */
    record Ready(boolean ready, BattleMode mode) implements Message {
        public Ready {
            Objects.requireNonNull(mode, "mode");
        }
    }

    /**
     * 서버 -> 클라이언트: 대전 시작
     *
     * @param seed        블럭 순서를 정하는 값 (양쪽이 같은 순서를 받기 위해)
     * @param timeLimitMs 시간제한 모드의 제한 시간
     * @param countdownMs 시작 전 카운트다운 길이
     */
    record Start(BattleMode mode, Difficulty difficulty, long seed, long timeLimitMs, long countdownMs)
            implements Message {
        public Start {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(difficulty, "difficulty");
            if (timeLimitMs <= 0 || countdownMs < 0) {
                throw new IllegalArgumentException("시간이 잘못됨: " + timeLimitMs + ", " + countdownMs);
            }
        }
    }

    /** 송신자의 화면 상태, seq는 보낼 때마다 1씩 커진다 */
    record Snapshot(int seq, BoardSnapshot board) implements Message {
        public Snapshot {
            Objects.requireNonNull(board, "board");
        }
    }

    /** 수신자의 'seq 번호의 Snapshot을 받았다'는 Ack (보내는 쪽은 이걸 보고 다음 것을 보낸다) */
    record SnapshotAck(int seq) implements Message {
    }

    /** 송신자가 보내는 공격 줄 (lines는 GarbageCodec 형식) */
    record Attack(String lines) implements Message {
        // 생성 시 형식이 맞는지 검사
        public Attack {
            GarbageCodec.decode(lines);
        }

        //송신자가 줄 목록 -> 메시지 형태로 변환
        public static Attack of(List<GarbageLine> lines) {
            return new Attack(GarbageCodec.encode(lines));
        }

        //수신자가 메시지 -> 줄 목록 형태로 변환
        public List<GarbageLine> toLines() {
            return GarbageCodec.decode(lines);
        }
    }

    /** 클라이언트 -> 서버: 내 게임이 끝났다 (더 쌓을 수 없음, 또는 기권) */
    record GameOver(int score) implements Message {
    }

    /** 서버 -> 클라이언트: 제한 시간이 끝났다 */
    record TimeUp() implements Message {
    }

    /** 클라이언트 -> 서버: TimeUp을 받은 시점의 내 점수 */
    record FinalScore(int score) implements Message {
    }

    /** 서버 -> 클라이언트: 승패 */
    record Result(BattleResult result) implements Message {
        public Result {
            Objects.requireNonNull(result, "result");
        }
    }

    /** 송신자가 보낸 시각을 담아 보낸다 */
    record Ping(long sentNanos) implements Message {
    }

    /** 수신자가 받은 Ping의 시각을 그대로 돌려준다 (송신자가 자기 시계로 왕복 시간(RTT)을 잰다) */
    record Pong(long sentNanos) implements Message {
    }
}