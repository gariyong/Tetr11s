package com.tetr11s.net.connect;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 접속할 상대의 주소 (IPv4 + 포트)
 *
 * 화면에서 입력한 글자는 parse로 검사한다
 * 숫자로 된 IP만 받는다 (호스트 이름은 DNS 조회 때문에 몇 초씩 멈출 수 있어서 받지 않음)
 */
public record PeerAddress(InetAddress ip, int port) {

    /** 포트를 적지 않았을 때 쓰는 포트 */
    public static final int DEFAULT_PORT = 51111;

    // 숫자 네 개를 점으로 이은 IP, 뒤에 ":포트"가 붙을 수 있다
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::(\\d{1,5}))?");

    public PeerAddress {
        Objects.requireNonNull(ip, "ip");
        if (!(ip instanceof Inet4Address)) {
            throw new IllegalArgumentException("IPv4 주소만 쓸 수 있음: " + ip);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("포트는 1~65535: " + port);
        }
    }

    /**
     * "192.168.0.5" 또는 "192.168.0.5:51111" 형태의 글자를 주소로 바꾼다 (앞뒤 공백은 무시)
     *
     * @return 형식이 틀리면 빈 Optional
     */
    public static Optional<PeerAddress> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(text.strip());
        if (!m.matches()) {
            return Optional.empty();
        }

        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int value = Integer.parseInt(m.group(i + 1));
            if (value > 255) {
                return Optional.empty();
            }
            bytes[i] = (byte) value;
        }
        int port = m.group(5) == null ? DEFAULT_PORT : Integer.parseInt(m.group(5));
        if (port < 1 || port > 65_535) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PeerAddress(InetAddress.getByAddress(bytes), port));   // 숫자로 바로 만든다 (DNS 조회 없음)
        } catch (UnknownHostException e) {
            throw new AssertionError("4바이트 주소는 항상 만들 수 있음", e);
        }
    }

    /** 소켓 접속에 쓰는 형태 */
    public InetSocketAddress socketAddress() {
        return new InetSocketAddress(ip, port);
    }

    /** 화면 표시와 저장용: 기본 포트면 "192.168.0.5", 아니면 "192.168.0.5:5000" (parse로 되돌릴 수 있음) */
    @Override
    public String toString() {
        String text = ip.getHostAddress();
        return port == DEFAULT_PORT ? text : text + ":" + port;
    }
}