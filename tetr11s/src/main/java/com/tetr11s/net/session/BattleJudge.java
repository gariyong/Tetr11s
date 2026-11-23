package com.tetr11s.net.session;

import com.tetr11s.game.battle.BattleResult;

/**
 * P2P 대전의 승패 규칙 (서버가 판정한다. 서버가 PLAYER1, 클라이언트가 PLAYER2)
 *
 * 로컬 대전의 BattleManager와 같은 규칙이다 (BattleManager.judge가 private이라 따로 둔다)
 * - 한쪽이 게임오버되면 다른 쪽이 승리, 둘 다면 무승부
 * - 시간제한 모드에서 시간이 끝나면 점수가 높은 쪽이 승리, 같으면 무승부
 * 규칙을 바꾸면 BattleManager도 같이 바꿔야 한다
 */
public final class BattleJudge {

    private BattleJudge() {
    }

    /** 게임오버로 판정한다. 아무도 게임오버가 아니면 null */
    public static BattleResult byGameOver(boolean hostOver, boolean guestOver) {
        if (hostOver && guestOver) {
            return BattleResult.DRAW;
        }
        if (hostOver) {
            return BattleResult.PLAYER2_WIN;
        }
        if (guestOver) {
            return BattleResult.PLAYER1_WIN;
        }
        return null;
    }

    /** 시간이 끝났을 때 점수로 판정한다 */
    public static BattleResult byScore(int hostScore, int guestScore) {
        int compare = Integer.compare(hostScore, guestScore);
        if (compare > 0) {
            return BattleResult.PLAYER1_WIN;
        }
        if (compare < 0) {
            return BattleResult.PLAYER2_WIN;
        }
        return BattleResult.DRAW;
    }
}
