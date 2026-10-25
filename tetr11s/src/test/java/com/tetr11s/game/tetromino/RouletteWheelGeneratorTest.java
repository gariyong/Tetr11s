package com.tetr11s.game.tetromino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * 요구사항: 1000번 이상 뽑았을 때 실제 비율이 기대 비율의 ±5% 안에 들어와야 한다.
 *
 * <p>여기서 ±5%는 더 엄격한 "상대 오차"로 본다. 기대 40%라면 38% ~ 42%.
 * 상대 오차 5%를 우연에 흔들리지 않고 통과하려면 횟수가 넉넉해야 해서 100,000번 뽑는다.
 * (PDF 예시처럼 1000번만 뽑는 경우는 따로 ±5%p로 확인한다.)
 */
class RouletteWheelGeneratorTest {

    private static final int TRIALS = 100_000;
    private static final double TOLERANCE = 0.05;

    // ---------- 도우미 ----------

    private static Map<Tetromino, Integer> count(RouletteWheelGenerator gen, int trials) {
        Map<Tetromino, Integer> count = new EnumMap<>(Tetromino.class);
        for (int i = 0; i < trials; i++) {
            count.merge(gen.get(), 1, Integer::sum);
        }
        return count;
    }

    private static List<Tetromino> draw(RouletteWheelGenerator gen, int n) {
        List<Tetromino> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(gen.get());
        }
        return result;
    }

    /** 가중치로 기대 확률을 계산하고, 모든 블럭이 상대 오차 TOLERANCE 안인지 확인. 결과 표도 출력한다. */
    private static void assertDistribution(Map<Tetromino, Integer> weights, long seed) {
        assertDistribution(weights, seed, true);
    }

    private static void assertDistribution(Map<Tetromino, Integer> weights, long seed, boolean print) {
        RouletteWheelGenerator gen = new RouletteWheelGenerator(weights, new Random(seed));
        Map<Tetromino, Integer> count = count(gen, TRIALS);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();

        if (print) {
            System.out.printf("%n[weights %s, seed=%d, trials=%,d]%n", weights, seed, TRIALS);
            System.out.println("type  weight  expected   actual      count    error");
        }
        for (Map.Entry<Tetromino, Integer> e : weights.entrySet()) {
            int n = count.getOrDefault(e.getKey(), 0);
            double expected = (double) e.getValue() / total;
            double actual = (double) n / TRIALS;
            double error = Math.abs(actual - expected) / expected;
            if (print) {
                System.out.printf("%-4s  %4d   %7.2f%%   %7.2f%%   %,7d    %5.2f%%%n",
                    e.getKey(), e.getValue(), expected * 100, actual * 100, n, error * 100);
            }
            assertTrue(error <= TOLERANCE, String.format(
                "%s 기대 %.2f%%, 실제 %.2f%% (오차 %.2f%%)",
                e.getKey(), expected * 100, actual * 100, error * 100));
        }
    }

    // ---------- 확률 분포 (요구사항 핵심) ----------

    @Test
    void Normal_모든_블럭이_같은_확률로_나온다() {
        assertDistribution(RouletteWheelGenerator.weightsWithI(10), 1);
    }

    @Test
    void Easy_I블럭이_20퍼센트_더_나온다() {
        assertDistribution(RouletteWheelGenerator.weightsWithI(12), 2);
    }

    @Test
    void Hard_I블럭이_20퍼센트_덜_나온다() {
        assertDistribution(RouletteWheelGenerator.weightsWithI(8), 3);
    }

    @Test
    void PDF_예시_한_블럭만_가중치_2배면_40퍼센트_나머지_20퍼센트() {
        Map<Tetromino, Integer> weights = new EnumMap<>(Tetromino.class);
        weights.put(Tetromino.I, 10);
        weights.put(Tetromino.O, 20); // B 역할
        weights.put(Tetromino.T, 10);
        weights.put(Tetromino.S, 10);
        assertDistribution(weights, 4);
    }

    @Test
    void PDF_예시를_1000번만_뽑아도_오차는_5퍼센트포인트_이내() {
        Map<Tetromino, Integer> weights = new EnumMap<>(Tetromino.class);
        weights.put(Tetromino.I, 10);
        weights.put(Tetromino.O, 20);
        weights.put(Tetromino.T, 10);
        weights.put(Tetromino.S, 10);
        Map<Tetromino, Integer> count = count(new RouletteWheelGenerator(weights, new Random(5)), 1000);

        // 기대: O 약 400번, 나머지 약 200번 (±50번 = ±5%p)
        assertTrue(Math.abs(count.get(Tetromino.O) - 400) <= 50, "O " + count.get(Tetromino.O));
        for (Tetromino t : List.of(Tetromino.I, Tetromino.T, Tetromino.S)) {
            assertTrue(Math.abs(count.get(t) - 200) <= 50, t + " " + count.get(t));
        }
    }

    @Test
    void 여러_seed에서도_분포가_유지된다() {
        // 특정 seed에서만 우연히 통과한 게 아님을 확인
        for (long seed = 100; seed < 110; seed++) {
            assertDistribution(RouletteWheelGenerator.weightsWithI(12), seed);
            assertDistribution(RouletteWheelGenerator.weightsWithI(8), seed);
        }
    }

    @Test
    void 가중치가_0인_블럭은_나오지_않는다() {
        Map<Tetromino, Integer> weights = RouletteWheelGenerator.uniformWeights();
        weights.put(Tetromino.I, 0);
        Map<Tetromino, Integer> count = count(new RouletteWheelGenerator(weights, new Random(6)), 10_000);
        assertFalse(count.containsKey(Tetromino.I));
        assertEquals(6, count.size());
    }

    @Test
    void 맵에_없는_블럭은_나오지_않는다() {
        Map<Tetromino, Integer> weights = new EnumMap<>(Tetromino.class);
        weights.put(Tetromino.T, 1);
        weights.put(Tetromino.Z, 1);
        Map<Tetromino, Integer> count = count(new RouletteWheelGenerator(weights, new Random(7)), 1000);
        assertEquals(Map.of(Tetromino.T, count.get(Tetromino.T), Tetromino.Z, count.get(Tetromino.Z)), count);
    }

    @Test
    void 블럭이_하나뿐이면_항상_그_블럭이_나온다() {
        RouletteWheelGenerator gen = new RouletteWheelGenerator(Map.of(Tetromino.L, 5), new Random(8));
        for (int i = 0; i < 100; i++) {
            assertEquals(Tetromino.L, gen.get());
        }
    }

    // ---------- 확률 계산 ----------

    @Test
    void probability는_가중치를_전체_합으로_나눈_값이다() {
        RouletteWheelGenerator gen = new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(12));
        assertEquals(72, gen.totalWeight());
        assertTrue(Math.abs(gen.probability(Tetromino.I) - 12.0 / 72) < 1e-12);
        assertTrue(Math.abs(gen.probability(Tetromino.O) - 10.0 / 72) < 1e-12);
    }

    @Test
    void 확률의_합은_1이다() {
        RouletteWheelGenerator gen = new RouletteWheelGenerator(RouletteWheelGenerator.weightsWithI(8));
        double sum = 0;
        for (Tetromino t : Tetromino.values()) {
            sum += gen.probability(t);
        }
        assertTrue(Math.abs(sum - 1.0) < 1e-12);
    }

    @Test
    void 맵에_없는_블럭의_확률은_0이다() {
        RouletteWheelGenerator gen = new RouletteWheelGenerator(Map.of(Tetromino.T, 1));
        assertTrue(gen.probability(Tetromino.I) == 0.0);
    }

    // ---------- 가중치 도우미 ----------

    @Test
    void uniformWeights는_7종_모두_기준_가중치다() {
        Map<Tetromino, Integer> weights = RouletteWheelGenerator.uniformWeights();
        assertEquals(7, weights.size());
        for (Tetromino t : Tetromino.values()) {
            assertEquals(RouletteWheelGenerator.DEFAULT_WEIGHT, weights.get(t), t.toString());
        }
    }

    @Test
    void weightsWithI는_I만_바꾸고_나머지는_기준_가중치다() {
        Map<Tetromino, Integer> weights = RouletteWheelGenerator.weightsWithI(12);
        assertEquals(12, weights.get(Tetromino.I), "I");
        for (Tetromino t : Tetromino.values()) {
            if (t != Tetromino.I) {
                assertEquals(10, weights.get(t), t.toString());
            }
        }
    }

    @Test
    void 기본_생성자는_모든_블럭을_생성한다() {
        Map<Tetromino, Integer> count = count(new RouletteWheelGenerator(), 10_000);
        assertEquals(7, count.size());
    }

    // ---------- 재현성 / 독립성 ----------

    @Test
    void 같은_seed면_같은_순서가_나온다() {
        Map<Tetromino, Integer> w = RouletteWheelGenerator.uniformWeights();
        assertEquals(
            draw(new RouletteWheelGenerator(w, new Random(42)), 50),
            draw(new RouletteWheelGenerator(w, new Random(42)), 50));
    }

    @Test
    void 다른_seed면_순서가_달라진다() {
        Map<Tetromino, Integer> w = RouletteWheelGenerator.uniformWeights();
        assertNotEquals(
            draw(new RouletteWheelGenerator(w, new Random(1)), 50),
            draw(new RouletteWheelGenerator(w, new Random(2)), 50));
    }

    @Test
    void 생성_후_원본_맵을_바꿔도_영향이_없다() {
        Map<Tetromino, Integer> weights = RouletteWheelGenerator.uniformWeights();
        RouletteWheelGenerator gen = new RouletteWheelGenerator(weights);
        weights.put(Tetromino.I, 1000);
        assertEquals(70, gen.totalWeight());
    }

    // ---------- 잘못된 입력 ----------

    @Test
    void 가중치_맵이_null이면_예외() {
        assertThrows(NullPointerException.class, () -> new RouletteWheelGenerator(null, new Random()));
    }

    @Test
    void Random이_null이면_예외() {
        assertThrows(NullPointerException.class,
            () -> new RouletteWheelGenerator(RouletteWheelGenerator.uniformWeights(), null));
    }

    @Test
    void 가중치_맵이_비어있으면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteWheelGenerator(Map.of()));
    }

    @Test
    void 가중치가_음수면_예외() {
        Map<Tetromino, Integer> weights = RouletteWheelGenerator.uniformWeights();
        weights.put(Tetromino.S, -1);
        assertThrows(IllegalArgumentException.class, () -> new RouletteWheelGenerator(weights));
    }

    @Test
    void 가중치가_전부_0이면_예외() {
        assertThrows(IllegalArgumentException.class,
            () -> new RouletteWheelGenerator(Map.of(Tetromino.I, 0, Tetromino.O, 0)));
    }

    @Test
    void 가중치_값이_null이면_예외() {
        Map<Tetromino, Integer> weights = new HashMap<>();
        weights.put(Tetromino.I, null);
        assertThrows(NullPointerException.class, () -> new RouletteWheelGenerator(weights));
    }

    @Test
    void 가중치_합이_int_범위를_넘으면_예외() {
        assertThrows(ArithmeticException.class, () -> new RouletteWheelGenerator(
            Map.of(Tetromino.I, Integer.MAX_VALUE, Tetromino.O, 1)));
    }
}