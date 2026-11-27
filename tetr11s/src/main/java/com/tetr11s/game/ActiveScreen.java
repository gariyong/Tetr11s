package com.tetr11s.game;

import javafx.scene.Scene;

/**
 * ScreenManager가 게임 루프와 포커스 리스너를 일관되게 관리하기 위한 공통 실행 화면 인터페이스.
 *
 * 싱글 게임(GameScreen), 로컬 대전(BattleScreen), 향후 P2P 대전 화면까지
 * ScreenManager 코드 수정 없이 다형성으로 관리할 수 있게 한다(OCP).
 */
public interface ActiveScreen {

    /** 대상 Scene에 키 입력 핸들러를 연결한다. */
    void attachToScene(Scene targetScene);

    /** 게임과 게임 루프를 시작한다. */
    void start();

    /** 게임 루프를 멈추고 입력 상태를 정리한다. */
    void stop();

    /** 창 포커스를 잃었을 때 눌려 있던 모든 키 상태를 해제한다. */
    void releaseAllKeys();
}
