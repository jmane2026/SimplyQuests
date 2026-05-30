package com.jmane2026.simplyquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public class QuestGroup {
    public static final Codec<QuestGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(QuestGroup::getName),
            Codec.STRING.optionalFieldOf("title", "").forGetter(QuestGroup::getTitle),
            Codec.INT.optionalFieldOf("color", 0xFFFFFFFF).forGetter(QuestGroup::getColor),
            Codec.INT.optionalFieldOf("order", 0).forGetter(QuestGroup::getOrder),
            Codec.BOOL.optionalFieldOf("expanded", true).forGetter(QuestGroup::isExpanded),
            Codec.STRING.listOf().optionalFieldOf("chapters", List.of()).forGetter(QuestGroup::getChapterNames)
    ).apply(instance, QuestGroup::new));

    private final String name;
    private final String title;
    private final int color;
    private final int order;
    private final boolean expanded;
    private final List<String> chapterNames;

    public QuestGroup(String name, String title, int color, int order, boolean expanded, List<String> chapterNames) {
        this.name = name;
        this.title = title;
        this.color = color;
        this.order = order;
        this.expanded = expanded;
        this.chapterNames = new ArrayList<>(chapterNames);
    }

    public String getName() { return name; }
    public String getTitle() { return title; }
    public int getColor() { return color; }
    public int getOrder() { return order; }
    public boolean isExpanded() { return expanded; }
    public List<String> getChapterNames() { return chapterNames; }
}