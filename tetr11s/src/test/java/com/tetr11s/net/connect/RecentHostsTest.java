package com.tetr11s.net.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실제 파일(~/.tetr11s)을 건드리지 않도록, 테스트마다 JUnit이 만들어 주는 임시 폴더를 쓴다
 */
class RecentHostsTest {

    @TempDir
    Path tempDir;

    private Path file;
    private RecentHosts recent;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("tetr11s").resolve("p2p.properties");   // 아직 없는 폴더
        recent = new RecentHosts(file);
    }

    private static PeerAddress address(String text) {
        return PeerAddress.parse(text).orElseThrow();
    }

    private static List<PeerAddress> addresses(String... texts) {
        List<PeerAddress> result = new ArrayList<>();
        for (String text : texts) {
            result.add(address(text));
        }
        return result;
    }

    private void rememberAll(String... texts) {
        for (String text : texts) {
            assertTrue(recent.remember(address(text)));
        }
    }

    @Test
    void 파일이_없으면_빈_목록이다() {
        assertEquals(List.of(), recent.load());
        assertFalse(Files.exists(file), "읽기만 해서는 파일을 만들지 않음");
    }

    @Test
    void 기억한_주소는_프로그램을_다시_켜도_남아_있다() {
        assertTrue(recent.remember(address("192.168.0.5")));

        assertTrue(Files.exists(file), "없던 폴더도 만들어서 저장해야 함");
        assertEquals(addresses("192.168.0.5"), new RecentHosts(file).load());
    }

    @Test
    void 가장_최근에_접속한_주소가_맨_앞이다() {
        rememberAll("192.168.0.1", "192.168.0.2", "192.168.0.3");

        assertEquals(addresses("192.168.0.3", "192.168.0.2", "192.168.0.1"), recent.load());
    }

    @Test
    void 이미_있는_주소로_다시_접속하면_맨_앞으로_옮기고_중복되지_않는다() {
        rememberAll("192.168.0.1", "192.168.0.2", "192.168.0.3", "192.168.0.2");

        assertEquals(addresses("192.168.0.2", "192.168.0.3", "192.168.0.1"), recent.load());
    }

    @Test
    void 목록이_꽉_찼을_때_있던_주소로_다시_접속해도_다른_주소가_빠지지_않는다() {
        rememberAll("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.3");

        assertEquals(addresses("10.0.0.3", "10.0.0.5", "10.0.0.4", "10.0.0.2", "10.0.0.1"), recent.load());
    }

    @Test
    void 다섯_개를_넘으면_가장_오래된_주소가_빠진다() {
        rememberAll("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.6");

        assertEquals(RecentHosts.CAPACITY, recent.load().size());
        assertEquals(addresses("10.0.0.6", "10.0.0.5", "10.0.0.4", "10.0.0.3", "10.0.0.2"), recent.load());
    }

    @Test
    void 포트가_다르면_다른_주소로_기억하고_포트도_그대로_되살린다() {
        rememberAll("192.168.0.5", "192.168.0.5:5000");

        List<PeerAddress> loaded = new RecentHosts(file).load();
        assertEquals(addresses("192.168.0.5:5000", "192.168.0.5"), loaded);
        assertEquals(5000, loaded.get(0).port());
        assertEquals(PeerAddress.DEFAULT_PORT, loaded.get(1).port());
    }

    @Test
    void 망가진_줄과_중복은_건너뛰고_나머지는_읽는다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n",
                "recent.0=not an ip",
                "recent.1=192.168.0.7",
                "recent.2=999.1.1.1",
                "recent.3=192.168.0.7",          // 중복
                "recent.4=10.0.0.2:51112",
                "recent.5=10.0.0.9"));           // 다섯 개 다음은 읽지 않음

        assertEquals(addresses("192.168.0.7", "10.0.0.2:51112"), recent.load());
    }

    @Test
    void 유니코드_이스케이프가_깨진_파일은_빈_목록으로_읽고_다시_저장할_수_있다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "recent.0=\\u12\n");

        assertEquals(List.of(), recent.load());
        assertTrue(recent.remember(address("192.168.0.5")));
        assertEquals(addresses("192.168.0.5"), recent.load());
    }

    @Test
    void 같은_파일의_다른_값은_그대로_둔다() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "memo=keep\nrecent.0=192.168.0.5\n");

        assertTrue(recent.remember(address("10.0.0.1")));

        Properties saved = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            saved.load(input);
        }
        assertEquals("keep", saved.getProperty("memo"));
        assertEquals(addresses("10.0.0.1", "192.168.0.5"), recent.load());
    }

    @Test
    void 저장할_수_없으면_false를_돌려주고_예외는_던지지_않는다() throws IOException {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "폴더가 있어야 할 자리에 파일이 있음");
        RecentHosts broken = new RecentHosts(blocked.resolve("p2p.properties"));

        assertFalse(broken.remember(address("192.168.0.5")));
        assertEquals(List.of(), broken.load());
    }
}
