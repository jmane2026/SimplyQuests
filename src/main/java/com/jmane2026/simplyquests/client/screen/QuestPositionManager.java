package com.jmane2026.simplyquests.client.screen;

import java.util.HashSet;
import java.util.Set;

public class QuestPositionManager {
    private static SidebarChapter lastActiveChapter = null;

    private static double globalZoom = 1.0;

    private static final Set<String> EXPANDED_GROUPS = new HashSet<>();

    public static SidebarChapter getLastActiveChapter() {
        return lastActiveChapter;
    }

    public static void setLastActiveChapter(SidebarChapter chapter) {
        lastActiveChapter = chapter;
    }

    public static double getGlobalZoom() {
        return globalZoom;
    }

    public static void setGlobalZoom(double zoom) {
        globalZoom = zoom;
    }

    public static boolean isGroupExpanded(String title) {
        return EXPANDED_GROUPS.contains(title);
    }

    public static void setGroupExpanded(String title, boolean expanded) {
        if (expanded) {
            EXPANDED_GROUPS.add(title);
        } else {
            EXPANDED_GROUPS.remove(title);
        }
    }
}