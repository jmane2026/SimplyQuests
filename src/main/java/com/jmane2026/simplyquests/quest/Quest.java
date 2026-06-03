package com.jmane2026.simplyquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

public class Quest {
    // CODEC for saving/loading to JSON
    private record QuestSettings(boolean isOptional, boolean isRepeatable, boolean useTaskIcon) {
        public static final Codec<QuestSettings> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.fieldOf("isOptional").forGetter(QuestSettings::isOptional),
                Codec.BOOL.fieldOf("isRepeatable").forGetter(QuestSettings::isRepeatable),
                Codec.BOOL.optionalFieldOf("useTaskIcon", false).forGetter(QuestSettings::useTaskIcon)
        ).apply(inst, QuestSettings::new));
    }

    private record QuestData(String id, String chapterName, String title, String subTitle, String description,
                             double x, double y, QuestShape shape, float size, QuestSettings settings,
                             List<String> dependencies, List<QuestTask> tasks, List<QuestReward> rewards,
                             String lockedBy, Item logo) {}

    private static final Codec<QuestData> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestData::id),
            Codec.STRING.fieldOf("chapterName").forGetter(QuestData::chapterName),
            Codec.STRING.fieldOf("title").forGetter(QuestData::title),
            Codec.STRING.optionalFieldOf("subTitle", "").forGetter(QuestData::subTitle),
            Codec.STRING.optionalFieldOf("description", "A new quest awaits!").forGetter(QuestData::description),
            Codec.DOUBLE.fieldOf("x").forGetter(QuestData::x),
            Codec.DOUBLE.fieldOf("y").forGetter(QuestData::y),
            Codec.stringResolver(QuestShape::name, QuestShape::valueOf).fieldOf("shape").forGetter(QuestData::shape),
            Codec.FLOAT.fieldOf("size").forGetter(QuestData::size),
            QuestSettings.CODEC.fieldOf("settings").forGetter(QuestData::settings),
            Codec.STRING.listOf().fieldOf("dependencies").forGetter(QuestData::dependencies),
            QuestTask.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(QuestData::tasks),
            QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestData::rewards),
            Codec.STRING.optionalFieldOf("lockedBy", "").forGetter(QuestData::lockedBy),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("logo", Items.PAPER).forGetter(QuestData::logo)
    ).apply(instance, QuestData::new));

    public static final Codec<Quest> CODEC = DATA_CODEC.xmap(d -> {
        Quest q = new Quest(d.id(), d.chapterName(), d.title(), d.x(), d.y());
        q.setSubTitle(d.subTitle());
        q.setDescription(d.description());
        q.setShape(d.shape());
        q.setState(QuestState.LOCKED); // All quests start locked and are unlocked via updateQuestStates()
        q.setSize(d.size());
        q.setOptional(d.settings().isOptional());
        q.setRepeatable(d.settings().isRepeatable());
        q.getDependencies().addAll(d.dependencies());
        q.getTasks().addAll(d.tasks());
        q.getRewards().addAll(d.rewards());
        q.setUseTaskIcon(d.settings().useTaskIcon());
        q.setLockedBy(d.lockedBy());
        q.setLogo(d.logo());
        return q;
    }, q -> new QuestData(q.getId(), q.getChapterName(), q.getTitle(), q.getSubTitle(), q.getDescription(),
            q.getX(), q.getY(), q.getShape(), q.getSize(), new QuestSettings(q.isOptional(), q.isRepeatable(), q.isUseTaskIcon()),
            q.getDependencies(), q.getTasks(), q.getRewards(), q.getLockedBy(), q.getLogo()));

    // Core Data
    private String id;
    private String chapterName;
    private String title = " ";
    private String subTitle = "";
    private String description = "A new quest awaits!";
    private double x;
    private double y;

    // Editor/UI Properties
    private Item logo = Items.BOOK;
    private QuestShape shape = QuestShape.CIRCLE;
    private float size = 24.0f;
    private QuestState state = QuestState.LOCKED;
    private boolean isOptional = false;
    private boolean isRepeatable = false;
    private List<String> dependencies = new ArrayList<>();
    private List<QuestTask> tasks = new ArrayList<>();
    private List<QuestReward> rewards = new ArrayList<>();
    private boolean useTaskIcon = false;
    private String lockedBy = ""; // Transient field for multiplayer sync

    // Constructor (Unified)
    public Quest(String id, String chapterName, String title, double x, double y) {
        this.id = id;
        this.chapterName = chapterName;
        this.title = title;
        this.x = x;
        this.y = y;
    }

    // --- GETTERS & SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getChapterName() { return chapterName; }
    public void setChapterName(String chapterName) { this.chapterName = chapterName; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubTitle() { return subTitle; }
    public void setSubTitle(String subTitle) { this.subTitle = subTitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public Item getLogo() { return logo; }
    public void setLogo(Item logo) { this.logo = logo; }

    public QuestShape getShape() { return shape; }
    public void setShape(QuestShape shape) { this.shape = shape; }

    public float getSize() { return size; }
    public void setSize(float size) { this.size = size; }

    public QuestState getState() { return state; }
    public void setState(QuestState state) { this.state = state; }

    public boolean isOptional() { return isOptional; }
    public void setOptional(boolean optional) { this.isOptional = optional; }

    public boolean isRepeatable() { return isRepeatable; }
    public void setRepeatable(boolean repeatable) { this.isRepeatable = repeatable; }

    public boolean isUseTaskIcon() { return useTaskIcon; }
    public void setUseTaskIcon(boolean useTaskIcon) { this.useTaskIcon = useTaskIcon; }
    
    public static String sanitizePath(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    public static String generateQuestId(String group, String chapter, String title, List<Quest> allQuests) {
        String cleanChapter = sanitizePath(chapter);
        String cleanTitle = sanitizePath(title);

        String path;
        if (group != null && !group.isEmpty()) {
            String cleanGroup = sanitizePath(group);
            path = String.format("%s/%s/%s", cleanGroup, cleanChapter, cleanTitle);
        } else {
            // Standalone logic: chapter/quest_name
            path = String.format("%s/%s", cleanChapter, cleanTitle);
        }

        String baseId = "simplyquests:" + path;
        int index = 0;
        String finalId = baseId;

        // Check for collisions and increment index
        while (idExists(finalId, allQuests)) {
            finalId = baseId + "_" + index;
            index++;
        }
        return finalId;
    }

    private static boolean idExists(String id, List<Quest> allQuests) {
        return allQuests.stream().anyMatch(q -> q.getId().equals(id));
    }

    public static Quest getQuestById(String id, List<Quest> allQuests) {
        return allQuests.stream()
                .filter(q -> q.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public Quest (Quest other) {
        this.chapterName = other.chapterName;
        this.id = other.id;
        this.title = other.title;
        this.subTitle = other.subTitle;
        this.description = other.description;
        this.x = other.x;
        this.y = other.y;
        this.logo = other.logo;
        this.shape = other.shape;
        this.size = other.size;
        this.state = other.state;
        this.isOptional = other.isOptional;
        this.isRepeatable = other.isRepeatable;
        this.useTaskIcon = other.useTaskIcon;

        this.dependencies = new ArrayList<>(other.dependencies);
        this.tasks = new ArrayList<>();
        for (QuestTask task : other.tasks) {
            this.tasks.add(new QuestTask(task.getId(), task.getType(), task.getTargetId(), task.getName(), task.getRequiredAmount(), task.getCurrentAmount(), task.isOptional(), task.isRepeatable(), task.isConsume(), task.getState(), task.getTargetX(), task.getTargetY(), task.getTargetZ(), task.isIcon()));
        }
        this.rewards = new ArrayList<>();
        for (QuestReward reward : other.rewards) {
            this.rewards.add(new QuestReward(reward));
        }
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    // Safe implementation of a setter for a collection
    public void setDependencies(List<String> newDependencies) {
        this.dependencies.clear();
        if (newDependencies != null) {
            this.dependencies.addAll(newDependencies);
        }
    }

    public List<QuestTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<QuestTask> tasks) {
        this.tasks.clear();
        if (tasks != null) this.tasks.addAll(tasks);
    }

    public List<QuestReward> getRewards() { return rewards; }

    public void setRewards(List<QuestReward> rewards) {
        this.rewards.clear();
        if (rewards != null) this.rewards.addAll(rewards);
    }

    public void addDependency(String questId) {
        if (!this.dependencies.contains(questId)) {
            this.dependencies.add(questId);
        }
    }

    public void addDependency(Quest dependency) {
        if (dependency != null) {
            addDependency(dependency.getId());
        }
    }

    // Added for completeness—vital for an editor UI!
    public void removeDependency(String questId) {
        this.dependencies.remove(questId);
    }

    /**
     * Iterates through all tasks to check if an obtained item progresses the quest.
     * @return true if any task state or progress was changed.
     */
    public boolean processItemPickup(ItemStack stack) {
        // Quests only track items if they are currently active (Available or Partial)
        // If Locked or already Completed, we ignore the input.
        if (this.state == QuestState.LOCKED || this.state == QuestState.COMPLETED) return false;

        boolean anyChanged = false;
        for (QuestTask task : this.tasks) {
            int added = task.handleItemAddition(stack);
            if (added > 0) {
                anyChanged = true;
            }
            // If the item stack is exhausted (fully consumed by a task), stop checking further tasks
            if (stack.isEmpty()) break;
        }
        return anyChanged;
    }
}