package com.jmane2026.simplyquests.quest;

public enum QuestState {
    LOCKED(0xFF4A4A4A),
    AVAILABLE(0xFFD3D3D3),
    PARTIAL(0xFF55FFFF),
    COMPLETED(0xFF67C23A);

    private final int color;

    QuestState(int color) {
        this.color = color;
    }

    public int getColor() {
        return this.color;
    }
}