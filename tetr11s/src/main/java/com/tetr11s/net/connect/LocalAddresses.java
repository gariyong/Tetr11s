package com.tetr11s.net.connect;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 서버 대기 화면에 보여 줄 '내 IP' 찾기
 *
 * 컴퓨터에는 네트워크 카드가 여러 개 있을 수 있다 (와이파이, 유선, VPN, VirtualBox·Docker·WSL 같은 가상 카드)
 * 가상 카드의 주소를 알려 주면 상대가 접속할 수 없으므로, 같은 공유기에 있는 상대가 쓸 수 있을 만한 주소를 앞에 둔다
 *
 * 순서
 * 1. 가상 카드가 아닌 것
 * 2. 운영체제가 바깥으로 나갈 때 쓰는 주소 (기본 경로)
 * 3. 공유기 안쪽 주소 (192.168.x.x, 10.x.x.x, 172.16~31.x.x)
 * 빼는 것: 꺼진 카드, 자기 자신(127.x), 자동 할당에 실패한 주소(169.254.x), VPN 터널, IPv6
 */
public final class LocalAddresses {

    // 이름이 이렇게 시작하면 가상 카드로 본다 (맥, 리눅스)
    private static final List<String> VIRTUAL_NAME_PREFIXES = List.of(
            "docker", "br-", "veth", "virbr", "vmnet", "vboxnet", "bridge", "utun", "tun", "tap", "awdl", "llw", "zt", "tailscale");

    // 설명에 이 말이 들어 있으면 가상 카드로 본다 (윈도우는 이름이 eth3 같은 식이고 제품명은 설명에 나옴)
    private static final List<String> VIRTUAL_DESCRIPTION_WORDS = List.of(
            "virtual", "vmware", "vethernet", "wsl", "docker", "tap-windows", "wintun", "wireguard", "tailscale", "zerotier", "hamachi");

    // 기본 경로를 알아낼 때 '보내는 척'할 주소: 문서용으로 예약된 주소라 실제로 쓰는 곳이 없다 (어차피 아무것도 보내지 않음)
    private static final byte[] OUTSIDE = {(byte) 192, 0, 2, 1};

    /** 네트워크 카드 하나에 붙은 주소 하나 (순서를 정하는 데 필요한 것만) */
    record Candidate(String name, String description, InetAddress address,
                     boolean up, boolean loopback, boolean pointToPoint) {

        Candidate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(address, "address");
        }
    }

    private LocalAddresses() {
    }

    /**
     * 다른 PC가 이 컴퓨터에 접속할 때 쓸 수 있을 만한 IPv4 주소들 (가장 그럴듯한 것이 맨 앞)
     * 네트워크에 연결되어 있지 않으면 빈 목록
     *
     * 윈도우에서 네트워크 카드가 많으면 수백 ms 걸릴 수 있으므로 화면 스레드 밖에서 부른다
     */
    public static List<InetAddress> find() {
        return choose(candidates(), defaultRouteAddress());
    }

    /**
     * 후보 중에서 쓸 수 있는 주소만 골라 순서를 정한다 (같은 주소는 한 번만)
     *
     * @param route 운영체제가 바깥으로 나갈 때 쓰는 주소 (모르면 null)
     */
    static List<InetAddress> choose(List<Candidate> candidates, InetAddress route) {
        Comparator<Candidate> order = Comparator
                .comparing((Candidate c) -> isVirtual(c))                  // 진짜 카드가 앞
                .thenComparing(c -> !c.address().equals(route))            // 기본 경로가 앞
                .thenComparing(c -> !c.address().isSiteLocalAddress());    // 공유기 안쪽 주소가 앞

        Set<InetAddress> addresses = new LinkedHashSet<>();
        candidates.stream()
                .filter(LocalAddresses::isUsable)
                .sorted(order)   // 순서가 같으면 원래 순서 유지
                .forEach(c -> addresses.add(c.address()));
        return List.copyOf(addresses);
    }

    // 다른 PC가 이 주소로 접속할 수 있는가
    private static boolean isUsable(Candidate c) {
        InetAddress address = c.address();
        return c.up() && !c.loopback() && !c.pointToPoint()
                && address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress()
                && !address.isMulticastAddress();
    }

    private static boolean isVirtual(Candidate c) {
        String name = c.name().toLowerCase(Locale.ROOT);
        String description = c.description().toLowerCase(Locale.ROOT);
        return VIRTUAL_NAME_PREFIXES.stream().anyMatch(name::startsWith)
                || VIRTUAL_DESCRIPTION_WORDS.stream().anyMatch(description::contains);
    }

    // 이 컴퓨터의 모든 네트워크 카드와 주소. 목록을 얻지 못하면 빈 목록
    private static List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> cards = NetworkInterface.getNetworkInterfaces();
            if (cards == null) {
                return result;
            }
            for (NetworkInterface card : Collections.list(cards)) {
                try {
                    boolean up = card.isUp();
                    boolean loopback = card.isLoopback();
                    boolean pointToPoint = card.isPointToPoint();
                    String description = Objects.requireNonNullElse(card.getDisplayName(), "");
                    for (InetAddress address : Collections.list(card.getInetAddresses())) {
                        result.add(new Candidate(card.getName(), description, address, up, loopback, pointToPoint));
                    }
                } catch (SocketException e) {
                    // 이 카드만 건너뛴다
                }
            }
        } catch (SocketException e) {
            // 목록을 얻지 못함
        }
        return result;
    }

    /**
     * 운영체제가 바깥(공유기 너머)으로 나갈 때 쓰는 내 주소. 알 수 없으면 null
     * UDP 소켓은 connect해도 실제로 아무것도 보내지 않는다 (어느 카드로 나갈지만 정해짐)
     */
    private static InetAddress defaultRouteAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByAddress(OUTSIDE), 9);
            InetAddress local = socket.getLocalAddress();
            return local == null || local.isAnyLocalAddress() ? null : local;
        } catch (IOException | RuntimeException e) {   // 네트워크가 없음 등
            return null;
        }
    }
}
