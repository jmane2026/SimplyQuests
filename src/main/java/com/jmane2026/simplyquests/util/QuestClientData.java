package com.jmane2026.simplyquests.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class QuestClientData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FMLPaths.GAMEDIR.get().resolve("simplyquests_local.json").toFile();
    private static Data data = new Data();

    public record ViewState(double x, double y, double zoom) {}

    static {
        load();
    }

    private static void load() {
        if (FILE.exists()) {
            try {
                String json = FileUtils.readFileToString(FILE, StandardCharsets.UTF_8);
                Data loaded = GSON.fromJson(json, Data.class);
                if (loaded != null) data = loaded;
            } catch (Exception ignored) {}
        }
    }

    private static void save() {
        try {
            FileUtils.writeStringToFile(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static String getLastChapter() {
        return data.lastChapter;
    }

    public static void setLastChapter(String name) {
        if (!data.lastChapter.equals(name)) {
            data.lastChapter = name;
            save();
        }
    }

    public static double getZoom() {
        return data.zoom;
    }

    public static void setZoom(double zoom) {
        if (data.zoom != zoom) {
            data.zoom = zoom;
            save();
        }
    }

    public static boolean isEditModeEnabled() {
        return data.editMode;
    }

    public static void setEditModeEnabled(boolean enabled) {
        if (data.editMode != enabled) {
            data.editMode = enabled;
            save();
        }
    }

    public static ViewState getChapterViewState(String chapterId) {
        return data.chapterViews.get(chapterId);
    }

    public static void saveChapterViewState(String chapterId, double x, double y, double zoom) {
        ViewState newState = new ViewState(x, y, zoom);
        ViewState oldState = data.chapterViews.get(chapterId);

        // Only trigger a disk save if the view actually moved/changed
        if (oldState == null || oldState.x != x || oldState.y != y || oldState.zoom != zoom) {
            data.chapterViews.put(chapterId, newState);
            save();
        }
    }

    private static class Data {
        String lastChapter = "";
        double zoom = 1.0;
        boolean editMode = false;
        // Key: Chapter sanitized ID, Value: The specific camera position for that chapter
        Map<String, ViewState> chapterViews = new HashMap<>();
    }
}