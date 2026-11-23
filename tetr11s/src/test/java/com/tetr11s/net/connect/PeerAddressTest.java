package com.tetr11s.net.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PeerAddressTest {

    @Test
    void IP만_적으면_기본_포트를_쓴다() {
        PeerAddress address = PeerAddress.parse("192.168.0.5").orElseThrow();

        assertEquals("192.168.0.5", address.ip().getHostAddress());
        assertEquals(PeerAddress.DEFAULT_PORT, address.port());
    }

    @Test
    void 포트를_붙이면_그_포트를_쓰고_앞뒤_공백은_무시한다() {
        PeerAddress address = PeerAddress.parse("  10.0.0.1:5000 ").orElseThrow();

        assertEquals("10.0.0.1", address.ip().getHostAddress());
        assertEquals(5000, address.port());
    }

    @Test
    void 각_자리는_0부터_255까지_받는다() {
        assertEquals("0.0.0.0", PeerAddress.parse("0.0.0.0").orElseThrow().ip().getHostAddress());
        assertEquals("255.255.255.255", PeerAddress.parse("255.255.255.255").orElseThrow().ip().getHostAddress());
        assertEquals("127.0.0.1", PeerAddress.parse("127.0.0.1").orElseThrow().ip().getHostAddress());
    }

    @Test
    void 형식이_틀리면_빈_값이다() {
        List<String> bad = Arrays.asList(
                null, "", "   ",
                "abc", "localhost", "example.com",   // 글자, 호스트 이름
                "192.168.0", "192.168.0.5.1",           // 점 개수
                "256.0.0.1", "1.2.3.999",               // 255 초과
                "1.2.3.4:0", "1.2.3.4:65536",           // 포트 범위
                "1.2.3.4:", "1.2.3.4:abc", ":5000",
                "-1.2.3.4", "1.2.3.4 :5000", "1. 2.3.4", "::1");

        for (String text : bad) {
            assertEquals(Optional.empty(), PeerAddress.parse(text), "\"" + text + "\"");
        }
    }

    @Test
    void 글자로_바꿨다가_되돌리면_같은_주소다() {
        for (String text : List.of("192.168.0.5", "10.0.0.1:5000")) {
            PeerAddress address = PeerAddress.parse(text).orElseThrow();

            assertEquals(text, address.toString());
            assertEquals(address, PeerAddress.parse(address.toString()).orElseThrow());
        }
    }

    @Test
    void IPv4가_아니거나_포트가_범위_밖이면_만들_수_없다() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("::1");   // 숫자 주소라 DNS 조회 없음
        InetAddress ipv4 = InetAddress.getByName("127.0.0.1");

        assertThrows(IllegalArgumentException.class, () -> new PeerAddress(ipv6, 5000));
        assertThrows(IllegalArgumentException.class, () -> new PeerAddress(ipv4, 0));
        assertThrows(IllegalArgumentException.class, () -> new PeerAddress(ipv4, 65_536));
        assertTrue(new PeerAddress(ipv4, 65_535).port() > 0);
    }
}