package com.tetr11s.game.battle;

import java.util.Objects;
import java.util.Random;

import com.tetr11s.game.Board;
import com.tetr11s.game.Difficulty;
import com.tetr11s.game.GameManager;
import com.tetr11s.game.ItemGameManager;
import com.tetr11s.game.tetromino.RouletteWheelGenerator;

/**
 * 대전 한 판의 규칙: 두 플레이어 연결, 승패 판정, 시간제한.
 *
 * 화면, 키 입력, 타이머(AnimationTimer)는 모른다.
 * 화면 쪽에서 매 프레임 각 플레이어를 진행시킨 뒤 update(흐른 시간)를 호출하면 된다.
 *
 * - 한 플레이어가 지운 줄이 상대 보드에 추가되어 공격한다
 * - 한 플레이어가 더 이상 블럭을 쌓지 못하면 다른 플레이어가 승자
 * - 시간제한 모드: 제한 시간이 지나면 그 시점에 점수가 더 높은 사람이 승리
 *
 * 팀 결정:
 * - 두 플레이어는 같은 seed로 같은 블럭 순서를 받는다
 * - 제한 시간은 2분
 * - 동시에 게임오버되거나, 시간 종료 시 동점이면 무승부
 * - 대전 중에는 일시정지가 없다 (화면 쪽에서 togglePause를 호출하지 않는다)
 */
public class BattleManager {

    /** 시간제한 모드의 제한 시간 (2분) */
    public static final long DEFAULT_TIME_LIMIT_MS = 120_000;

    private final BattleMode mode;
    private final GameManager player1;
    private final GameManager player2;
    private final long timeLimitMs;

    private long elapsedMs;
    private BattleResult result;   // 아직 안 끝났으면 null

    /**
     * 같은 seed로 두 플레이어를 만든다.
     *
     * @param seed 블럭 순서를 정하는 값. P2P에서는 서버가 정해서 클라이언트에 보내면 양쪽 순서가 같아진다
     */
    public static BattleManager create(BattleMode mode, Difficulty difficulty, long seed) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        return new BattleManager(mode,
                createPlayer(mode, difficulty, seed),
                createPlayer(mode, difficulty, seed),
                DEFAULT_TIME_LIMIT_MS);
    }

    /** seed를 무작위로 정한다 (로컬 대전용) */
    public static BattleManager create(BattleMode mode, Difficulty difficulty) {
        return create(mode, difficulty, new Random().nextLong());
    }

    /**
     * 플레이어를 직접 넘긴다 (테스트, 또는 특별한 설정이 필요할 때).
     * 두 플레이어의 공격을 서로에게 연결한다.
     */
    public BattleManager(BattleMode mode, GameManager player1, GameManager player2, long timeLimitMs) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.player1 = Objects.requireNonNull(player1, "player1");
        this.player2 = Objects.requireNonNull(player2, "player2");
        if (player1 == player2) {
            throw new IllegalArgumentException("두 플레이어는 서로 다른 게임이어야 함");
        }
        if (timeLimitMs <= 0) {
            throw new IllegalArgumentException("제한 시간은 1ms 이상이어야 함: " + timeLimitMs);
        }
        this.timeLimitMs = timeLimitMs;

        // 한 쪽이 지운 줄은 상대의 대기열로 간다
        player1.setOnAttack(player2::receiveGarbage);
        player2.setOnAttack(player1::receiveGarbage);
    }

    private static GameManager createPlayer(BattleMode mode, Difficulty difficulty, long seed) {
        RouletteWheelGenerator generator = new RouletteWheelGenerator(
                RouletteWheelGenerator.weightsWithI(difficulty.iBlockWeight()), new Random(seed));
        if (mode == BattleMode.ITEM) {
            return new ItemGameManager(difficulty, false, generator);
        }
        return new GameManager(new Board(), generator, difficulty);
    }

    /** 새 대전 시작: 두 게임을 새로 시작하고 시간과 결과를 초기화한다. */
    public void start() {
        elapsedMs = 0;
        result = null;
        player1.start();
        player2.start();
    }

    /**
     * 시간이 흐를 때마다(매 프레임) 호출한다. 각 플레이어를 진행시킨 "뒤에" 호출해야
     * 같은 프레임에 둘 다 게임오버된 경우를 무승부로 판정할 수 있다.
     *
     * @param deltaMs 직전 호출부터 흐른 시간(ms)
     */
    public void update(long deltaMs) {
        if (isFinished()) {
            return;
        }
        if (deltaMs > 0) {
            elapsedMs += deltaMs;
        }
        result = judge();
    }

    // 지금 상태로 결과를 판정한다. 아직 안 끝났으면 null
    private BattleResult judge() {
        boolean over1 = player1.isGameOver();
        boolean over2 = player2.isGameOver();
        if (over1 && over2) {
            return BattleResult.DRAW;
        }
        if (over1) {
            return BattleResult.PLAYER2_WIN;
        }
        if (over2) {
            return BattleResult.PLAYER1_WIN;
        }
        if (mode == BattleMode.TIME_LIMIT && elapsedMs >= timeLimitMs) {
            return byScore();
        }
        return null;
    }

    private BattleResult byScore() {
        int compare = Integer.compare(player1.getScore(), player2.getScore());
        if (compare > 0) {
            return BattleResult.PLAYER1_WIN;
        }
        if (compare < 0) {
            return BattleResult.PLAYER2_WIN;
        }
        return BattleResult.DRAW;
    }

    // ----- 상태 조회 (화면, 테스트용) -----

    public BattleMode getMode() {
        return mode;
    }

    public GameManager getPlayer1() {
        return player1;
    }

    public GameManager getPlayer2() {
        return player2;
    }

    /** 대전 결과. 아직 진행 중이면 null */
    public BattleResult getResult() {
        return result;
    }

    /** 승패가 정해졌으면 true. 이후에는 두 게임 모두 진행시키지 않는다. */
    public boolean isFinished() {
        return result != null;
    }

    /** 시간제한 모드의 남은 시간(ms). 다른 모드에서는 -1 */
    public long getRemainingMillis() {
        if (mode != BattleMode.TIME_LIMIT) {
            return -1;
        }
        return Math.max(0, timeLimitMs - elapsedMs);
    }
}