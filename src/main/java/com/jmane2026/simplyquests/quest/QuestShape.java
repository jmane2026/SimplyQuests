package com.jmane2026.simplyquests.quest;

import net.minecraft.resources.Identifier;

public enum QuestShape {
    SQUARE("square.png"),
    CIRCLE("circle.png"),
    GEAR("gear.png"),
    HEART("heart.png"),
    OCTAGON("octagon.png"),
    HEXAGON("hexagon.png"),
    DIAMOND("diamond.png"),
    STAR("star.png");

    private final Identifier texture;

    QuestShape(String filename) {
        this.texture = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/shapes/" + filename);
    }

    public Identifier getTexture() {
        return this.texture;
    }
}