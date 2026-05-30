package com.jmane2026.simplyquests.quest;

public enum QuestState {
    LOCKED(0xFF4A4A4A),       // Deep dark charcoal gray (Dependencies not met)
    AVAILABLE(0xFFD3D3D3),    // Sleek, bright light gray (Ready to be worked on)
    PARTIAL(0xFF55FFFF),
    COMPLETED(0xFF67C23A);    // Crisp progression green (Finished)

    private final int color;

    QuestState(int color) {
        this.color = color;
    }

    public int getColor() {
        return this.color;
    }
}