package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.quest.QuestState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SidebarChapter implements SidebarEntry {
    private String id;
    private String name;
    private ItemStack iconStack;
    private QuestState state;

    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double zoom = 1.0;

    public SidebarChapter(String name) {
        this.name = name;
        this.iconStack = new ItemStack(Items.BOOK);
        this.state = QuestState.AVAILABLE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemStack getIconStack() {
        return iconStack;
    }

    public void setIconStack(ItemStack iconStack) {
        this.iconStack = iconStack;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = state;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }
}