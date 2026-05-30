package com.jmane2026.simplyquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class CanvasText {
    public static final Codec<CanvasText> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("text").forGetter(CanvasText::getText),
            Codec.DOUBLE.fieldOf("x").forGetter(CanvasText::getX),
            Codec.DOUBLE.fieldOf("y").forGetter(CanvasText::getY),
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(CanvasText::getScale),
            Codec.INT.optionalFieldOf("color", 0xFFFFFFFF).forGetter(CanvasText::getColor)
    ).apply(instance, CanvasText::new));

    private String text;
    private double x;
    private double y;
    private float scale;
    private int color;
    private String chapterName; // Transient, used for filtering

    public CanvasText(String text, double x, double y, float scale, int color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.color = color;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public String getChapterName() { return chapterName; }
    public void setChapterName(String chapterName) { this.chapterName = chapterName; }
}