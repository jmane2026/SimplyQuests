package com.jmane2026.simplyquests.client.screen;

import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.ArrayList;
import java.util.List;

public class SidebarGroup implements SidebarEntry {
    private String name;
    private String title;
    private final int titleColor;
    private boolean isExpanded;
    private final List<SidebarChapter> chapters = new ArrayList<>();

    public SidebarGroup(String title, int titleColor) {
        this.title = title;
        this.titleColor = titleColor;
        this.isExpanded = false;
    }

    public String getName() { return name != null ? name : title; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getTitleColor() { return titleColor; }

    public boolean isExpanded() {
        // FIX: Use the unique internal name for tracking expansion state
        return QuestPositionManager.isGroupExpanded(this.getName());
    }

    public void toggleExpanded() {
        boolean nextState = !this.isExpanded();
        QuestPositionManager.setGroupExpanded(this.getName(), nextState);
    }

    public List<SidebarChapter> getChapters() {
        return chapters;
    }

    public void addChapter(SidebarChapter chapter) {
        this.chapters.add(chapter);
    }
}