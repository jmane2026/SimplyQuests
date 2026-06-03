package com.jmane2026.simplyquests.data;

import com.jmane2026.simplyquests.quest.CanvasText;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestCanvasImage;
import com.jmane2026.simplyquests.quest.QuestState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public class QuestChapter {
    public static final Codec<QuestChapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("group").forGetter(QuestChapter::getGroupName),
            Codec.INT.optionalFieldOf("groupOrder", 0).forGetter(QuestChapter::getGroupOrder),
            Codec.INT.optionalFieldOf("groupColor", 0xFFFFFFFF).forGetter(QuestChapter::getGroupColor),
            Codec.STRING.fieldOf("name").forGetter(QuestChapter::getName),
            Codec.STRING.optionalFieldOf("title", "").forGetter(QuestChapter::getTitle),
            Codec.INT.optionalFieldOf("chapterOrder", 0).forGetter(QuestChapter::getChapterOrder),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon", Items.BOOK).forGetter(QuestChapter::getIcon),
            Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(QuestChapter::getQuests),
            CanvasText.CODEC.listOf().optionalFieldOf("canvasTexts", List.of()).forGetter(QuestChapter::getCanvasTexts),
            QuestCanvasImage.CODEC.listOf().optionalFieldOf("canvasImages", List.of()).forGetter(QuestChapter::getCanvasImages),
            Codec.DOUBLE.optionalFieldOf("offsetX", 0.0).forGetter(QuestChapter::getOffsetX),
            Codec.DOUBLE.optionalFieldOf("offsetY", 0.0).forGetter(QuestChapter::getOffsetY),
            Codec.DOUBLE.optionalFieldOf("zoom", 1.0).forGetter(QuestChapter::getZoom)
    ).apply(instance, QuestChapter::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestChapter> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private String groupName;
    private int groupOrder;
    private final int groupColor;
    private final String name;
    private final String title;
    private int chapterOrder;
    private final Item icon;
    private final List<Quest> quests;
    private final List<CanvasText> canvasTexts;
    private final List<QuestCanvasImage> canvasImages;
    private double offsetX;
    private double offsetY;
    private double zoom;
    private QuestState state = QuestState.AVAILABLE;

    public QuestChapter(String groupName, int groupOrder, int groupColor, String name, String title, int chapterOrder, Item icon, List<Quest> quests, List<CanvasText> canvasTexts, List<QuestCanvasImage> canvasImages, double offsetX, double offsetY, double zoom) {
        this.groupName = groupName;
        this.groupOrder = groupOrder;
        this.groupColor = groupColor;
        this.name = name;
        this.title = title;
        this.chapterOrder = chapterOrder;
        this.icon = icon;
        this.quests = quests;
        this.canvasTexts = canvasTexts;
        this.canvasImages = canvasImages;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.zoom = zoom;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getZoom() {
        return zoom;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setGroupOrder(int groupOrder) {
        this.groupOrder = groupOrder;
    }

    public void setChapterOrder(int chapterOrder) {
        this.chapterOrder = chapterOrder;
    }

    public int getGroupOrder() {
        return groupOrder;
    }

    public int getGroupColor() {
        return groupColor;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public int getChapterOrder() {
        return chapterOrder;
    }

    public Item getIcon() {
        return icon;
    }

    public List<Quest> getQuests() {
        return quests;
    }

    public List<CanvasText> getCanvasTexts() {
        return canvasTexts;
    }

    public List<QuestCanvasImage> getCanvasImages() {
        return canvasImages;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = state;
    }
}