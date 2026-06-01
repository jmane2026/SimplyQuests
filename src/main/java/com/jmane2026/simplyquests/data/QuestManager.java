package com.jmane2026.simplyquests.data;

import com.jmane2026.simplyquests.quest.QuestState;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.jmane2026.simplyquests.quest.Quest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jmane2026.simplyquests.network.SaveGroupsPayload.StandaloneChapterInfo;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class QuestManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Map<Identifier, QuestChapter> chapters = new HashMap<>();
    private List<QuestGroup> groups = new ArrayList<>();
    private List<StandaloneChapterInfo> rootChapters = new ArrayList<>();
    // Create a Gson instance for pretty-printing our quest files
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public QuestManager() {
        super(QuestChapter.CODEC, FileToIdConverter.json("quests"));
    }

    public List<QuestGroup> getGroups() {
        return this.groups;
    }

    public List<StandaloneChapterInfo> getRootChapters() {
        return this.rootChapters;
    }

    /**
     * Replaces the current chapters with a list provided by the server.
     */
    public void setChaptersFromList(List<QuestChapter> list) {
        this.chapters.clear();
        for (QuestChapter ch : list) {
            // FIX: Use the standardized strict regex to ensure IDs match the server's file system
            String sanitized = ch.getName().toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath("simplyquests", sanitized);
            this.chapters.put(id, ch);
        }
    }

    /**
     * Replaces the current group definitions with a list provided by the server.
     */
    public void setGroups(List<QuestGroup> list) {
        this.groups.clear();
        this.groups.addAll(list);
    }

    /**
     * Saves the list of sidebar groups to groups.json in the config folder.
     */
    public void saveGroups(List<QuestGroup> groups, List<StandaloneChapterInfo> rootChapters) {
        File file = FMLPaths.CONFIGDIR.get().resolve("simplyquests").resolve("groups.json").toFile();
        try {
            JsonObject root = new JsonObject();
            JsonElement groupsJson = QuestGroup.CODEC.listOf().encodeStart(JsonOps.INSTANCE, groups)
                    .getOrThrow(msg -> new RuntimeException("Failed to encode groups: " + msg));
            
            JsonArray rootsJson = new JsonArray();
            rootChapters.forEach(info -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", info.name());
                obj.addProperty("order", info.order());
                rootsJson.add(obj);
            });

            root.add("groups", groupsJson);
            root.add("rootChapters", rootsJson);

            FileUtils.writeStringToFile(file, GSON.toJson(root), "UTF-8");
            this.groups = groups;
            this.rootChapters = rootChapters;
            reconcileChapterStructure();
        } catch (Exception e) {
            LOGGER.error("Simply Quests: Failed to save groups.json", e);
        }
    }

    /**
     * Loads the list of groups from groups.json.
     */
    private void loadGroups() {
        File file = FMLPaths.CONFIGDIR.get().resolve("simplyquests").resolve("groups.json").toFile();
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            
            if (root.has("groups")) {
                QuestGroup.CODEC.listOf().parse(JsonOps.INSTANCE, root.get("groups"))
                        .resultOrPartial(err -> LOGGER.error("Failed to parse groups in groups.json: {}", err))
                        .ifPresent(loaded -> {
                            this.groups = new ArrayList<>(loaded);
                            this.groups.sort(Comparator.comparingInt(QuestGroup::getOrder));
                        });
            }

            if (root.has("rootChapters")) {
                this.rootChapters = new ArrayList<>();
                root.getAsJsonArray("rootChapters").forEach(e -> {
                    if (e.isJsonObject()) {
                        JsonObject obj = e.getAsJsonObject();
                        this.rootChapters.add(new StandaloneChapterInfo(obj.get("name").getAsString(), obj.get("order").getAsInt()));
                    } else {
                        this.rootChapters.add(new StandaloneChapterInfo(e.getAsString(), 0));
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Simply Quests: Failed to load groups.json", e);
        }
    }

    /**
     * Forces all in-memory chapters to align their metadata (Group, Order) with the manifest.
     */
    private void reconcileChapterStructure() {
        for (QuestChapter chapter : this.chapters.values()) {
            boolean found = false;
            // Check if chapter belongs to a group in the manifest
            for (QuestGroup group : this.groups) {
                // Check against either name or title to ensure match during renames/migrations
                int childIdx = group.getChapterNames().indexOf(chapter.getName());
                if (childIdx != -1) {
                    chapter.setGroupName(group.getName());
                    chapter.setGroupOrder(group.getOrder());
                    chapter.setChapterOrder(childIdx);
                    found = true; break;
                }
            }
            // Check if it's a standalone root chapter in the manifest
            if (!found) {
                for (StandaloneChapterInfo info : this.rootChapters) {
                    if (info.name().equals(chapter.getName())) {
                        chapter.setGroupName("");
                        chapter.setGroupOrder(info.order());
                        chapter.setChapterOrder(0);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Returns the directory where quest definitions are stored for modpack distribution.
     * Path: .minecraft/config/simplyquests/chapters/
     */
    public static Path getConfigDirectory() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("simplyquests").resolve("chapters");
        File dir = configPath.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return configPath;
    }

    public static Path getImagesDirectory() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("simplyquests").resolve("images");
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }

    /**
     * Saves a chapter to the config directory as a JSON file.
     * This allows modpack creators to distribute quests via the /config folder.
     */
    public void saveChapter(Identifier id, QuestChapter chapter) {
        Path dir = getConfigDirectory();
        // We use the ID path as the filename (e.g., "introduction.json")
        File file = dir.resolve(id.getPath() + ".json").toFile();

        try {
            // Use the Codec to convert our Chapter object into a JsonElement
            JsonElement json = QuestChapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter)
                    .getOrThrow(msg -> new RuntimeException("Failed to encode chapter: " + msg));

            // Write the JSON to the file with pretty printing
            String jsonString = GSON.toJson(json);
            FileUtils.writeStringToFile(file, jsonString, "UTF-8");
            
            LOGGER.info("Simply Quests: Saved chapter {} to {}", id, file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Simply Quests: Failed to save chapter {}", id, e);
        }
    }

    /**
     * Manually scans the config directory and loads chapters into the provided map.
     */
    public void loadConfigChapters(Map<Identifier, QuestChapter> targetMap) {
        File dir = getConfigDirectory().toFile();
        if (!dir.exists()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                QuestChapter.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> LOGGER.error("Failed to parse config quest {}: {}", file.getName(), err))
                        .ifPresent(chapter -> {
                            // FIX: Ensure filenames are sanitized into valid Identifiers during load
                            String name = file.getName().replace(".json", "").toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
                            Identifier id = Identifier.fromNamespaceAndPath("simplyquests", name);
                            targetMap.put(id, chapter);
                        });
            } catch (Exception e) {
                LOGGER.error("Failed to read quest file {}", file.getName(), e);
            }
        }
    }

    /**
     * Re-registers a chapter in memory after a save to ensure the UI stays in sync.
     */
    public void updateChapterInMemory(Identifier id, QuestChapter chapter) {
        Map<Identifier, QuestChapter> mutable = new HashMap<>(this.chapters);
        mutable.put(id, chapter);
        this.chapters = mutable;
        updateQuestStates(new PlayerQuestProgress()); // Refresh AVAILABLE/LOCKED states using a blank progress template
    }

    /**
     * Deletes a chapter file from the config folder. 
     * Used during renames to prevent duplicate files.
     */
    public void deleteChapterFile(Identifier id) {
        Path dir = getConfigDirectory();
        File file = dir.resolve(id.getPath() + ".json").toFile();
        if (file.exists()) {
            file.delete();
            Map<Identifier, QuestChapter> mutable = new HashMap<>(this.chapters);
            mutable.remove(id);
            this.chapters = mutable;
            LOGGER.info("Simply Quests: Deleted old chapter file {}", file.getAbsolutePath());
        }
    }

    @Override
    protected void apply(Object o, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        Map<Identifier, QuestChapter> newChapters = new HashMap<>();

        if (o instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> {
                if (key instanceof Identifier location && value instanceof QuestChapter chapter) {
                    newChapters.put(location, chapter);
                }
            });
        }

        // Overlay config chapters on top of resource pack chapters directly into the new map
        loadConfigChapters(newChapters);
        this.chapters = newChapters;

        // 1. Load groups manifest
        loadGroups();
        // 2. Overwrite chapter metadata based on the manifest
        reconcileChapterStructure();

        // 2. Recalculate states (LOCKED -> AVAILABLE) on the server thread
        updateQuestStates(new PlayerQuestProgress());
        LOGGER.info("Simply Quests: Total active chapters: {}", this.chapters.size());
    }

    /**
     * Server-side state calculation.
     * Unlocks quests whose dependencies are met and updates progress status.
     * @return A result object containing lists of newly completed Quests and Chapters during this specific pass.
     */
    public CompletionResults updateQuestStates(PlayerQuestProgress progress) {
        List<Quest> newlyCompletedQuests = new ArrayList<>();
        List<QuestChapter> newlyCompletedChapters = new ArrayList<>();

        // 1. Gather all valid IDs from the current registry for validation
        List<Quest> allQuests = getAllQuests();
        Map<String, Quest> lookup = new HashMap<>();
        Set<String> validTaskIds = new HashSet<>();
        Set<String> validChapterNames = new HashSet<>();

        this.chapters.values().forEach(ch -> validChapterNames.add(ch.getName()));
        for (Quest q : allQuests) {
            lookup.put(q.getId(), q);
            for (QuestTask t : q.getTasks()) validTaskIds.add(t.getId());
        }

        // 2. PRUNING: Remove "Zombie" data from player progress that no longer exists in config
        // This prevents recreated quests from inheriting progress from deleted versions.
        
        // Prune Completed Quests
        progress.getCompletedQuests().removeIf(id -> !lookup.containsKey(id));
        
        // Prune Task Progress
        // FIX: Use getTaskProgressMap() and check if the ID exists in our valid set
        progress.getTaskProgressMap().keySet().removeIf(id -> !validTaskIds.contains(id));
        
        // Prune Claimed Rewards (IDs are chapter/quest/reward_name)
        progress.getClaimedRewards().removeIf(id -> {
            int lastSlash = id.lastIndexOf('/');
            if (lastSlash == -1) return true; // Prune malformed or legacy IDs
            String questId = id.substring(0, lastSlash);
            return !lookup.containsKey(questId);
        });

        // Prune Completed Chapters
        progress.getCompletedChapters().removeIf(name -> !validChapterNames.contains(name));

        Map<String, QuestState> computedStates = new HashMap<>();

        for (Quest quest : allQuests) {
            boolean allDependenciesCompleted = true;
            for (String depId : quest.getDependencies()) {
                Quest dependency = lookup.get(depId);
                if (dependency == null || !progress.isQuestComplete(dependency.getId())) {
                    allDependenciesCompleted = false;
                    break;
                }
            }

            boolean wasCompleted = progress.isQuestComplete(quest.getId());
            if (!allDependenciesCompleted) {
                computedStates.put(quest.getId(), QuestState.LOCKED);
            } else {
                List<QuestTask> tasks = quest.getTasks();
                if (!tasks.isEmpty()) {
                    boolean anyStarted = false;
                    boolean allRequiredDone = true;
                    boolean hasRequiredTasks = false;
                    boolean anyOptionalDone = false;

                    for (QuestTask task : tasks) {
                        int current = progress.getTaskAmount(task.getId());
                        boolean taskDone = current >= task.getRequiredAmount();

                        if (!task.isOptional()) {
                            hasRequiredTasks = true;
                            if (!taskDone) allRequiredDone = false;
                        } else {
                            if (taskDone) anyOptionalDone = true;
                        }

                        if (current > 0) anyStarted = true;
                    }

                    // Completion Logic: 
                    // 1. If main tasks exist, all of them must be finished.
                    // 2. If ONLY optional tasks exist, at least one must be finished.
                    boolean isDone = hasRequiredTasks ? allRequiredDone : anyOptionalDone;

                    if (isDone) {
                        computedStates.put(quest.getId(), QuestState.COMPLETED);
                    } else if (anyStarted) {
                        computedStates.put(quest.getId(), QuestState.PARTIAL);
                    } else {
                        computedStates.put(quest.getId(), QuestState.AVAILABLE);
                    }
                } else {
                    computedStates.put(quest.getId(), QuestState.AVAILABLE);
                }
            }

            // Track transition to COMPLETED
            if (!wasCompleted && computedStates.get(quest.getId()) == QuestState.COMPLETED) {
                progress.completeQuest(quest.getId());
                newlyCompletedQuests.add(quest);
            }
        }

        // Recalculate Chapter States
        for (QuestChapter chapter : this.chapters.values()) {
            List<Quest> chapterQuests = chapter.getQuests();
            if (chapterQuests.isEmpty()) {
                chapter.setState(QuestState.AVAILABLE);
                continue;
            }

            boolean anyCompleted = false;
            boolean allNonOptionalCompleted = true;
            int nonOptionalCount = 0;

            for (Quest q : chapterQuests) {
                if (progress.isQuestComplete(q.getId())) anyCompleted = true;
                if (!q.isOptional()) {
                    nonOptionalCount++;
                    if (!progress.isQuestComplete(q.getId())) allNonOptionalCompleted = false;
                }
            }

            QuestState newState = (nonOptionalCount > 0 ? allNonOptionalCompleted : anyCompleted)
                    ? QuestState.COMPLETED : (anyCompleted ? QuestState.PARTIAL : QuestState.AVAILABLE);

            chapter.setState(newState);

            // Use the persistent player data to check if this is a NEW completion
            // This prevents duplicate toasts in Singleplayer and across restarts
            if (!progress.isChapterComplete(chapter.getName()) && newState == QuestState.COMPLETED) {
                progress.completeChapter(chapter.getName());
                newlyCompletedChapters.add(chapter);
            }
        }

        return new CompletionResults(newlyCompletedQuests, newlyCompletedChapters);
    }

    public record CompletionResults(List<Quest> quests, List<QuestChapter> chapters) {}

    public Map<Identifier, QuestChapter> getChapters() {
        return this.chapters;
    }

    /**
     * Flattens all chapters into a single list of quests for server-side processing.
     */
    public List<Quest> getAllQuests() {
        List<Quest> all = new ArrayList<>();
        this.chapters.values().forEach(chapter -> all.addAll(chapter.getQuests()));
        return all;
    }
}