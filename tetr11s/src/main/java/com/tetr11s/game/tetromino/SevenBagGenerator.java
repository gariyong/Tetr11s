package com.tetr11s.game.tetromino;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 7-bag 방식의 다음 블럭 생성기.
 *
 * 7종을 한 가방에 하나씩 넣고 섞은 뒤 순서대로 꺼낸다. 가방이 비면 다시 7종을 넣고 섞는다.
 * 그래서 7개마다 모든 블럭이 정확히 한 번씩 나오고, 장기적으로 모든 블럭의 등장 횟수가 같다.
 */
public class SevenBagGenerator implements Supplier<Tetromino> {

    private final Random random;
    private final List<Tetromino> bag = new ArrayList<>();

    public SevenBagGenerator() {
        this(new Random());
    }

    /** 테스트에서 결과를 고정하려면 seed를 정한 Random을 넘긴다. */
    public SevenBagGenerator(Random random) {
        this.random = random;
    }

    @Override
    public Tetromino get() {
        if (bag.isEmpty()) {
            refill();
        }
        return bag.remove(bag.size() - 1);
    }

    private void refill() {
        bag.addAll(List.of(Tetromino.values()));
        Collections.shuffle(bag, random);
    }
}