package com.tetr11s.net.connect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * 클라이언트가 최근에 접속했던 서버 주소 (최대 5개, 가장 최근이 맨 앞)
 *
 * - 접속에 성공한 주소만 기억한다 (잘못 입력한 주소는 남지 않음)
 * - 이미 있는 주소로 다시 접속하면 맨 앞으로 옮긴다 (같은 주소는 한 번만)
 * - 설정(save.properties)·기록(scores.properties)과 다른 파일(p2p.properties)에 저장한다
 *
 * 파일이 없거나 망가졌으면 빈 목록으로 읽는다
 * 저장에 실패해도 예외를 던지지 않는다 (접속은 이미 성공했으니, 목록을 못 남겼다고 게임을 멈출 이유가 없음)
 */
public final class RecentHosts {

    /** 기억하는 주소 수 */
    public static final int CAPACITY = 5;

    private static final String KEY_PREFIX = "recent.";   // recent.0 = 가장 최근

    private final Path path;

    /** 실제 파일(~/.tetr11s/p2p.properties)을 쓴다 */
    public RecentHosts() {
        this(Paths.get(System.getProperty("user.home"), ".tetr11s", "p2p.properties"));
    }

    /**
     * @param path 저장할 파일 (테스트에서는 임시 폴더 안의 파일)
     */
    public RecentHosts(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    /** 최근에 접속한 주소들 (가장 최근이 맨 앞, 최대 CAPACITY개). 망가진 줄은 건너뛴다 */
    public List<PeerAddress> load() {
        return addressesIn(read());
    }

    /**
     * 접속에 성공한 서버 주소를 맨 앞에 기억한다
     * 이미 있으면 맨 앞으로 옮기고, CAPACITY개를 넘으면 가장 오래된 것을 지운다
     *
     * @return 파일에 저장했으면 true, 저장하지 못했으면 false
     */
    public boolean remember(PeerAddress address) {
        Objects.requireNonNull(address, "address");
        Properties properties = read();

        Set<PeerAddress> addresses = new LinkedHashSet<>();   // 순서를 지키면서 중복을 없앤다
        addresses.add(address);
        addresses.addAll(addressesIn(properties));

        // 예전 목록만 지운다 (같은 파일의 다른 값은 그대로 둔다)
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(KEY_PREFIX)) {
                properties.remove(key);
            }
        }
        int index = 0;
        for (PeerAddress each : addresses) {
            if (index == CAPACITY) {
                break;
            }
            properties.setProperty(KEY_PREFIX + index, each.toString());   // parse로 되돌릴 수 있는 형태
            index++;
        }
        return write(properties);
    }

    // recent.0 ~ recent.(CAPACITY-1)을 순서대로 읽는다. 주소가 아니거나 중복이면 건너뛴다
    private static List<PeerAddress> addressesIn(Properties properties) {
        Set<PeerAddress> addresses = new LinkedHashSet<>();
        for (int index = 0; index < CAPACITY; index++) {
            PeerAddress.parse(properties.getProperty(KEY_PREFIX + index)).ifPresent(addresses::add);
        }
        return List.copyOf(addresses);
    }

    // 파일 전체를 읽는다. 없거나 읽을 수 없으면 비어 있는 Properties
    private Properties read() {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException e) {   // 읽기 실패, 또는 유니코드 이스케이프가 깨진 파일
            System.err.println("최근 접속 주소 파일을 읽을 수 없습니다: " + e.getMessage());
            return new Properties();
        }
        return properties;
    }

    private boolean write(Properties properties) {
        try {
            Path folder = path.toAbsolutePath().getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Tetr11s P2P recent hosts");
            }
            return true;
        } catch (IOException e) {
            System.err.println("최근 접속 주소를 저장할 수 없습니다: " + path + " (" + e + ")");
            return false;
        }
    }
}
