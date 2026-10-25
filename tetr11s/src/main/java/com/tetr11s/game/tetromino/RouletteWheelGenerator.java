package com.tetr11s.game.tetromino;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 룰렛 휠 선택(Roulette Wheel Selection) 방식의 다음 블럭 생성기.
 *
 * 각 블럭이 가중치만큼 룰렛 칸을 차지한다고 생각한다. 가중치가 I=12, 나머지=10이면
 * 전체 칸은 72개이고, I는 12/72 확률로 뽑힌다.
 *
 * 뽑는 방법 (누적합 방식):
 * 생성할 때 가중치를 차례로 더한 누적합을 만들어 둔다. 예: I=12, O=10 → [12, 22, ...]</li>
 * 0 이상 전체 합 미만의 난수 r을 하나 뽑는다.
 * r보다 큰 누적합이 처음 나오는 블럭을 고른다.
 *
 * 가중치 맵에 없는 블럭이나 가중치가 0인 블럭은 나오지 않는다.
 */
public class RouletteWheelGenerator implements Supplier<Tetromino> {

    // 기준 가중치. 난이도에 따라 I 블럭 가중치만 이 값에서 바뀐다
    public static final int DEFAULT_WEIGHT = 10;

    private final Random random;
    private final Map<Tetromino, Integer> weights;
    private final List<Tetromino> types = new ArrayList<>();
    private final List<Integer> cumulative = new ArrayList<>();
    private final int total;

    // 모든 블럭 가중치가 같은 기본 생성기
    public RouletteWheelGenerator() {
        this(uniformWeights());
    }

    public RouletteWheelGenerator(Map<Tetromino, Integer> weights) {
        this(weights, new Random());
    }

    /**
     * @param weights 블럭별 가중치 (0 이상, 합은 1 이상)
     * @param random  테스트에서 결과를 고정하려면 seed를 정한 Random을 넘긴다
     */
    public RouletteWheelGenerator(Map<Tetromino, Integer> weights, Random random) {
        Objects.requireNonNull(weights, "weights");
        this.random = Objects.requireNonNull(random, "random");
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("가중치가 비어 있음");
        }

        // EnumMap으로 복사: 원본을 나중에 바꿔도 영향이 없고, 순서가 항상 같다
        this.weights = new EnumMap<>(Tetromino.class);
        for (Map.Entry<Tetromino, Integer> e : weights.entrySet()) {
            Tetromino type = Objects.requireNonNull(e.getKey(), "블럭 종류가 null");
            Integer weight = Objects.requireNonNull(e.getValue(), type + " 가중치가 null");
            if (weight < 0) {
                throw new IllegalArgumentException(type + " 가중치가 음수: " + weight);
            }
            this.weights.put(type, weight);
        }

        int sum = 0;
        for (Map.Entry<Tetromino, Integer> e : this.weights.entrySet()) {
            if (e.getValue() == 0) {
                continue; // 룰렛 칸이 없으니 절대 뽑히지 않는다
            }
            sum = Math.addExact(sum, e.getValue());
            types.add(e.getKey());
            cumulative.add(sum);
        }
        if (sum == 0) {
            throw new IllegalArgumentException("가중치 합이 0");
        }
        this.total = sum;
    }

    @Override
    public Tetromino get() {
        int r = random.nextInt(total); // 0 ~ total-1
        for (int i = 0; i < types.size(); i++) {
            if (r < cumulative.get(i)) {
                return types.get(i);
            }
        }
        throw new AssertionError("도달할 수 없음: r=" + r + ", total=" + total);
    }

    // 이 블럭이 한 번에 뽑힐 확률 (가중치 / 전체 합). 맵에 없으면 0
    public double probability(Tetromino type) {
        return (double) weights.getOrDefault(type, 0) / total;
    }

    // 가중치 전체 합
    public int totalWeight() {
        return total;
    }

    // 모든 블럭이 DEFAULT_WEIGHT인 가중치
    public static Map<Tetromino, Integer> uniformWeights() {
        return weightsWithI(DEFAULT_WEIGHT);
    }

    /**
     * I 블럭만 따로 정하고 나머지는 DEFAULT_WEIGHT인 가중치.
     * 난이도 연결용: Easy = weightsWithI(12), Normal = weightsWithI(10), Hard = weightsWithI(8)
     */
    public static Map<Tetromino, Integer> weightsWithI(int iWeight) {
        Map<Tetromino, Integer> map = new EnumMap<>(Tetromino.class);
        for (Tetromino t : Tetromino.values()) {
            map.put(t, DEFAULT_WEIGHT);
        }
        map.put(Tetromino.I, iWeight);
        
        return map;
    }
}