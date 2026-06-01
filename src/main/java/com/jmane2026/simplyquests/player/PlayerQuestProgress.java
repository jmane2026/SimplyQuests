package com.jmane2026.simplyquests.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PlayerQuestProgress {

    // In PlayerQuestProgress.java
    public static final MapCodec<PlayerQuestProgress> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("completed", List.of())
                    .xmap(HashSet::new, List::copyOf).forGetter(p -> p.completedQuests),
            Codec.STRING.listOf().optionalFieldOf("claimed", List.of())
                    .xmap(HashSet::new, List::copyOf).forGetter(p -> p.claimedRewards),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("taskProgress", Map.of())
                    .forGetter(p -> Map.copyOf(p.taskProgress)),
            Codec.STRING.listOf().optionalFieldOf("completedChapters", List.of())
                    .xmap(HashSet::new, List::copyOf).forGetter(p -> p.completedChapters),
            Codec.BOOL.optionalFieldOf("editMode", false).forGetter(p -> p.editMode)
    ).apply(instance, (comp, claimed, task, chapters, edit) -> new PlayerQuestProgress(comp, claimed, task, chapters, edit)));

    private final HashSet<String> completedQuests;
    private final HashSet<String> claimedRewards;
    private final Map<String, Integer> taskProgress;
    private final HashSet<String> completedChapters;
    private boolean editMode;

    public PlayerQuestProgress() {
        this.completedQuests = new HashSet<>();
        this.claimedRewards = new HashSet<>();
        this.taskProgress = new HashMap<>();
        this.completedChapters = new HashSet<>();
        this.editMode = false;
    }

    public PlayerQuestProgress(HashSet<String> completedQuests, HashSet<String> claimedRewards, Map<String, Integer> taskProgress, HashSet<String> completedChapters, boolean editMode) {
        this.completedQuests = new HashSet<>(completedQuests);
        this.claimedRewards = new HashSet<>(claimedRewards);
        this.taskProgress = new HashMap<>(taskProgress);
        this.completedChapters = new HashSet<>(completedChapters);
        this.editMode = editMode;
    }

    public int getTaskAmount(String taskId) { return taskProgress.getOrDefault(taskId, 0); }
    public void setTaskAmount(String taskId, int amount) {
        if (taskId != null) taskProgress.put(taskId, amount);
    }
    
    public void resetTaskProgress(String taskId) {
        taskProgress.remove(taskId);
    }

    public boolean isQuestComplete(String questId) { return completedQuests.contains(questId); }
    public void completeQuest(String questId) { if (questId != null) completedQuests.add(questId); }

    public boolean isChapterComplete(String name) { return completedChapters.contains(name); }
    public void completeChapter(String name) { if (name != null) completedChapters.add(name); }

    public boolean isRewardClaimed(String questId) { return claimedRewards.contains(questId); }
    public void claimReward(String questId) { if (questId != null) claimedRewards.add(questId); }
    public void unclaimReward(String rewardId) { claimedRewards.remove(rewardId); }

    public void resetQuest(String questId) {
        completedQuests.remove(questId);
        // When a quest is reset, also clear its task progress
        // We can't easily iterate tasks here, so this will be handled in QuestServerEvents
        // This method primarily handles the completed status.
    }
    public void resetChapter(String name) { completedChapters.remove(name); }

    public HashSet<String> getCompletedQuests() {
        return this.completedQuests;
    }

    public HashSet<String> getClaimedRewards() {
        return this.claimedRewards;
    }

    public Map<String, Integer> getTaskProgressMap() {
        return this.taskProgress;
    }

    public HashSet<String> getCompletedChapters() {
        return this.completedChapters;
    }

    public boolean isEditMode() { return editMode; }
    public void setEditMode(boolean editMode) { this.editMode = editMode; }
}