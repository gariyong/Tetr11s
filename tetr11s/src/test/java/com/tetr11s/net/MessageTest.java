package com.tetr11s.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.tetr11s.game.Board;
import com.tetr11s.game.BoardSnapshot;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.game.battle.BattleResult;
import com.tetr11s.game.battle.GarbageLine;
import com.tetr11s.game.battle.TestLines;
import com.tetr11s.game.tetromino.Tetromino;

class MessageTest {

    /** 메시지 종류마다 하나씩 */
    private static List<Message> samples() {
        GameManager game = new GameManager(new Board(), () -> Tetromino.O);
        game.start();

        return List.of(
                new Message.Hello(Message.PROTOCOL_VERSION),
                new Message.Bye(Message.Bye.Reason.LEFT),
                new Message.LobbyState(BattleMode.ITEM, true, false),
                new Message.Ready(true),
                new Message.Start(BattleMode.TIME_LIMIT, Difficulty.HARD, 42L, 120_000, 3_000),
                new Message.Snapshot(7, BoardSnapshot.of(game)),
                new Message.SnapshotAck(7),
                Message.Attack.of(TestLines.holes(Board.WIDTH, 3, 2)),
                new Message.GameOver(1234),
                new Message.TimeUp(),
                new Message.FinalScore(5678),
                new Message.Result(BattleResult.PLAYER2_WIN),
                new Message.Ping(111L),
                new Message.Pong(111L));
    }

    @Test
    void 예시에_모든_메시지_종류가_들어_있다() {
        // 메시지 종류를 새로 만들고 samples()에 넣지 않으면 여기서 알려 준다
        Set<Class<?>> sampled = new HashSet<>();
        for (Message message : samples()) {
            sampled.add(message.getClass());
        }

        assertEquals(Set.of(Message.class.getPermittedSubclasses()), sampled);
    }

    @Test
    void 한_연결로_이어서_보내도_순서대로_같은_메시지가_온다() throws Exception {
        List<Message> sent = samples();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            for (Message message : sent) {
                out.writeObject(message);
            }
        }
        List<Message> received = new ArrayList<>();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            for (int i = 0; i < sent.size(); i++) {
                received.add((Message) in.readObject());
            }
        }

        assertEquals(sent, received);
    }

    @Test
    void 공격_메시지는_공격_줄을_그대로_전한다() {
        List<GarbageLine> lines = TestLines.holes(Board.WIDTH, 3, 2);

        assertEquals(lines, Message.Attack.of(lines).toLines());
    }

    @Test
    void 잘못된_값으로는_만들_수_없다() {
        assertThrows(NullPointerException.class, () -> new Message.Bye(null));
        assertThrows(NullPointerException.class, () -> new Message.LobbyState(null, false, false));
        assertThrows(NullPointerException.class,
                () -> new Message.Start(null, Difficulty.NORMAL, 1L, 120_000, 3_000));
        assertThrows(IllegalArgumentException.class,
                () -> new Message.Start(BattleMode.NORMAL, Difficulty.NORMAL, 1L, 0, 3_000));
        assertThrows(NullPointerException.class, () -> new Message.Snapshot(0, null));
        assertThrows(IllegalArgumentException.class, () -> new Message.Attack("X?X"));
        assertThrows(NullPointerException.class, () -> new Message.Result(null));
    }
}