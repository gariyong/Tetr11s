package com.tetr11s.game.battle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class GarbageCodecTest {

    @Test
    void 글자로_바꿨다가_되돌리면_같은_줄이다() {
        List<GarbageLine> lines = TestLines.holes(10, 8, 4);   // 빈 칸이 8, 9, 0, 1열인 줄 4개

        assertEquals(lines, GarbageCodec.decode(GarbageCodec.encode(lines)));
    }

    @Test
    void 한_줄은_X와_점으로_쓰고_줄바꿈으로_나눈다() {
        List<GarbageLine> lines = List.of(TestLines.of("X.X"), TestLines.of(".XX"));

        assertEquals("X.X\n.XX", GarbageCodec.encode(lines));
    }

    @Test
    void 줄이_없으면_빈_문자열이다() {
        assertEquals("", GarbageCodec.encode(List.of()));
        assertEquals(List.of(), GarbageCodec.decode(""));
    }

    @Test
    void 형식이_잘못되면_되돌리지_않는다() {
        // 다른 글자, 끝의 빈 줄, 앞의 빈 줄, 줄마다 다른 너비
        for (String bad : List.of("X?X", "XX\n", "\nXX", "XX\nX")) {
            assertThrows(IllegalArgumentException.class, () -> GarbageCodec.decode(bad), bad);
        }
    }
}