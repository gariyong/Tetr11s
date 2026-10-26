package com.tetr11s.game.item;

import java.util.Random;
import java.util.function.Supplier;

// 이름은 룰렛인데 상속 받으려다 간단히 먼저 구현
public class ItemRouletteGenerator implements Supplier<Item> {
    private final Random random = new Random();

    @Override
    public Item get() {
        Item[] items = Item.values();
        int index = random.nextInt(items.length);
        return items[index]; // 5가지 아이템(L, W, H, O, M) 중 하나를 랜덤 리턴
    }
}