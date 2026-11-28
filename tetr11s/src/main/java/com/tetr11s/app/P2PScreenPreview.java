package com.tetr11s.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;

import com.tetr11s.game.battle.BattleMode;
import com.tetr11s.net.connect.LocalAddresses;
import com.tetr11s.net.connect.PeerAddress;
import com.tetr11s.net.connect.RecentHosts;
import com.tetr11s.net.flow.HostWaitView;
import com.tetr11s.net.flow.JoinView;
import com.tetr11s.net.flow.LobbyView;
import com.tetr11s.settings.SettingsStore;

/**
 * [임시] P2P 흐름 담당(6단계 4번)을 붙이기 전까지 P2P 화면을 미리 보는 용도
 *
 * 네트워크는 쓰지 않고, 화면 모양과 키 조작만 확인한다
 * - 서버 열기: 내 IP를 찾아 보여 준다
 * - 서버에 접속: 최근 주소를 보여 주고, 올바른 IP를 넣고 Enter를 누르면 가짜 로비를 연다
 * - 로비: 상대는 항상 '준비 완료'라서, '게임 시작'을 누르면 카운트다운까지 볼 수 있다
 * 흐름 담당을 붙이면 ScreenManager의 P2P 메뉴가 그쪽을 부르게 바꾸고 이 파일은 지운다
 */
final class P2PScreenPreview {

    private P2PScreenPreview() {
    }

    static void host(ScreenManager screens) throws IOException {
        HostWaitView view = screens.showP2PHostScreen();
        view.setOnCancel(() -> backToMenu(screens));
        view.showAddresses(LocalAddresses.find().stream().map(InetAddress::getHostAddress).toList());
        view.showStatus("미리보기: 아직 접속을 받지 않습니다", false);
    }

    static void join(ScreenManager screens) throws IOException {
        JoinView view = screens.showP2PJoinScreen();
        view.showRecent(new RecentHosts().load().stream().map(PeerAddress::toString).toList());
        view.showStatus("미리보기: IP를 넣고 Enter를 누르면 로비 화면을 보여 줍니다", false);
        view.setListener(new JoinView.Listener() {
            @Override
            public void onConnect(String input) {
                if (PeerAddress.parse(input).isEmpty()) {
                    view.showStatus("IP 주소 형식이 아닙니다 (예: 192.168.0.12)", true);
                    return;
                }
                try {
                    lobby(screens, input.trim());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void onCancel() {
                backToMenu(screens);
            }
        });
    }

    private static void lobby(ScreenManager screens, String peer) throws IOException {
        LobbyView view = screens.showP2PLobbyScreen();
        BattleMode[] mode = {BattleMode.NORMAL};
        boolean[] myReady = {false};

        view.showConnection(true, peer, "난이도 " + SettingsStore.loadDifficulty().displayName());
        view.showLobby(mode[0], false, true);
        view.showNetwork(false, 12);
        view.showMessage("미리보기: 상대 없이 화면만 보여 줍니다");
        view.setListener(new LobbyView.Listener() {
            @Override
            public void onModeSelected(BattleMode selected) {
                mode[0] = selected;
                myReady[0] = false;
                view.showLobby(selected, false, true);
            }

            @Override
            public void onToggleReady() {
                myReady[0] = !myReady[0];
                view.showLobby(mode[0], myReady[0], true);
                if (myReady[0]) {
                    view.showCountdown(3_000);
                }
            }

            @Override
            public void onLeave() {
                backToMenu(screens);
            }
        });
    }

    private static void backToMenu(ScreenManager screens) {
        try {
            screens.showP2PMenu();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}