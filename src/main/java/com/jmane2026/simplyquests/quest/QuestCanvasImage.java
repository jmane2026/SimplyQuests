package com.jmane2026.simplyquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class QuestCanvasImage {
    public static final Codec<QuestCanvasImage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestCanvasImage::getId),
            Codec.STRING.fieldOf("imageId").forGetter(QuestCanvasImage::getImageId),
            Codec.DOUBLE.fieldOf("x").forGetter(QuestCanvasImage::getX),
            Codec.DOUBLE.fieldOf("y").forGetter(QuestCanvasImage::getY),
            Codec.DOUBLE.fieldOf("width").forGetter(QuestCanvasImage::getWidth),
            Codec.DOUBLE.fieldOf("height").forGetter(QuestCanvasImage::getHeight),
            Codec.FLOAT.optionalFieldOf("rotation", 0f).forGetter(QuestCanvasImage::getRotation),
            Codec.FLOAT.optionalFieldOf("alpha", 1f).forGetter(QuestCanvasImage::getAlpha)
    ).apply(instance, QuestCanvasImage::new));

    private final String id;
    private final String imageId; // The filename on disk (e.g., "cool_logo.png")
    private double x, y, width, height;
    private float rotation, alpha;
    private String chapterName;

    public QuestCanvasImage(String id, String imageId, double x, double y, double width, double height, float rotation, float alpha) {
        this.id = id;
        this.imageId = imageId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.alpha = alpha;
    }

    public String getId() { return id; }
    public String getImageId() { return imageId; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = alpha; }
    public String getChapterName() { return chapterName; }
    public void setChapterName(String name) { this.chapterName = name; }
}
