package com.tetr11s.net.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tetr11s.net.connect.LocalAddresses.Candidate;

/**
 * 실제 컴퓨터의 네트워크 카드는 PC마다 다르므로, 순서 정하기(choose)는 가짜 카드 목록으로 확인한다
 * 마지막 테스트만 이 컴퓨터의 실제 카드로 돌려 본다
 */
class LocalAddressesTest {

    private static InetAddress ip(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    /** 켜져 있는 보통 카드 (맥·리눅스처럼 설명이 이름과 같음) */
    private static Candidate card(String name, InetAddress address) {
        return card(name, name, address);
    }

    private static Candidate card(String name, String description, InetAddress address) {
        return new Candidate(name, description, address, true, false, false);
    }

    @Test
    void 다른_PC가_접속할_수_없는_주소는_뺀다() throws UnknownHostException {
        List<Candidate> cards = List.of(
                new Candidate("lo0", "lo0", ip(127, 0, 0, 1), true, true, false),             // 자기 자신
                new Candidate("en1", "en1", ip(192, 168, 0, 20), false, false, false),        // 꺼진 카드
                card("en0", ip(169, 254, 3, 4)),                                              // 자동 할당 실패
                new Candidate("utun3", "utun3", ip(10, 8, 0, 2), true, false, true),          // VPN 터널
                card("en0", InetAddress.getByName("2001:db8::12")),                           // IPv6
                card("en0", ip(192, 168, 0, 12)));

        assertEquals(List.of(ip(192, 168, 0, 12)), LocalAddresses.choose(cards, null));
    }

    @Test
    void 운영체제가_바깥으로_나갈_때_쓰는_주소가_맨_앞이다() {
        // 유선 카드가 먼저 나오지만, 실제로 공유기로 나가는 건 와이파이
        List<Candidate> cards = List.of(
                card("en5", ip(10, 0, 0, 5)),
                card("en0", ip(192, 168, 0, 12)));

        assertEquals(List.of(ip(192, 168, 0, 12), ip(10, 0, 0, 5)),
                LocalAddresses.choose(cards, ip(192, 168, 0, 12)));
    }

    @Test
    void 기본_경로를_모르면_공유기_안쪽_주소가_앞이다() {
        List<Candidate> cards = List.of(
                card("eth1", ip(203, 0, 113, 7)),    // 공인 주소
                card("eth0", ip(172, 20, 1, 3)));    // 172.16~31은 공유기 안쪽

        assertEquals(List.of(ip(172, 20, 1, 3), ip(203, 0, 113, 7)), LocalAddresses.choose(cards, null));
    }

    @Test
    void 가상_카드의_주소는_뒤로_보낸다() {
        List<Candidate> cards = List.of(
                card("eth3", "VirtualBox Host-Only Ethernet Adapter", ip(192, 168, 56, 1)),   // 윈도우: 설명으로 구분
                card("docker0", ip(172, 17, 0, 1)),                                           // 리눅스·맥: 이름으로 구분
                card("bridge100", ip(192, 168, 2, 1)),
                card("wlan0", "Intel(R) Wi-Fi 6 AX201 160MHz", ip(192, 168, 0, 12)));

        assertEquals(List.of(ip(192, 168, 0, 12), ip(192, 168, 56, 1), ip(172, 17, 0, 1), ip(192, 168, 2, 1)),
                LocalAddresses.choose(cards, null));
    }

    @Test
    void 기본_경로가_가상_카드여도_진짜_카드가_먼저이고_가상_카드_중에서는_앞이다() {
        List<Candidate> cards = List.of(
                card("eth5", "Hyper-V Virtual Ethernet Adapter", ip(172, 28, 80, 1)),
                card("eth6", "Hyper-V Virtual Ethernet Adapter #2", ip(192, 168, 0, 30)),
                card("wlan0", "Wi-Fi", ip(192, 168, 0, 12)));

        assertEquals(List.of(ip(192, 168, 0, 12), ip(192, 168, 0, 30), ip(172, 28, 80, 1)),
                LocalAddresses.choose(cards, ip(192, 168, 0, 30)));
    }

    @Test
    void 같은_주소는_한_번만_나온다() {
        List<Candidate> cards = List.of(
                card("en0", ip(192, 168, 0, 12)),
                card("en0", ip(192, 168, 0, 12)));

        assertEquals(List.of(ip(192, 168, 0, 12)), LocalAddresses.choose(cards, null));
    }

    @Test
    void 쓸_수_있는_주소가_없으면_빈_목록이다() {
        assertEquals(List.of(), LocalAddresses.choose(List.of(), null));
        assertEquals(List.of(), LocalAddresses.choose(
                List.of(new Candidate("lo", "lo", ip(127, 0, 0, 1), true, true, false)), ip(127, 0, 0, 1)));
    }

    @Test
    void 이_컴퓨터에서_찾은_주소는_모두_다른_PC가_접속할_수_있는_IPv4_주소다() {
        // 네트워크에 연결되어 있지 않은 컴퓨터에서는 빈 목록일 수 있다
        for (InetAddress address : LocalAddresses.find()) {
            assertInstanceOf(Inet4Address.class, address);
            assertFalse(address.isLoopbackAddress(), address.toString());
            assertFalse(address.isLinkLocalAddress(), address.toString());
        }
    }
}
