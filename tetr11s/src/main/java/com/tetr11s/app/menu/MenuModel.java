package com.tetr11s.app.menu;

import java.util.List;

/**
 * 키보드로 조작하는 메뉴의 선택 상태.
 *
 * 항목 목록과 지금 선택된 위치만 관리한다. 화면은 모른다.
 * 맨 위에서 위로, 맨 아래에서 아래로 가면 반대쪽 끝으로 넘어간다.
 */
public class MenuModel {

    private final List<MenuEntry> entries;
    private int selectedIndex = 0;

    public MenuModel(List<MenuEntry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("메뉴 항목은 1개 이상이어야 함");
        }

        this.entries = List.copyOf(entries);
    }

    public List<MenuEntry> entries() {
        return entries;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public MenuEntry selected() {
        return entries.get(selectedIndex);
    }

    public void moveUp() {
        selectedIndex = Math.floorMod(selectedIndex - 1, entries.size());
    }

    public void moveDown() {
        selectedIndex = Math.floorMod(selectedIndex + 1, entries.size());
    }

    // 선택된 항목의 동작을 실행
    public void activate() {
        selected().action().run();
    }
}
