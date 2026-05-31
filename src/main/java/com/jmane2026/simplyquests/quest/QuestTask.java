package com.jmane2026.simplyquests.quest;

import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

public class QuestTask {

    public enum TaskType implements StringRepresentable {
        ITEM("item"),
        KILL("kill"),
        LOCATION("location"),
        CHECKBOX("checkbox"),
        BIOME("biome"),
        OBSERVE("observe");

        private final String name;
        TaskType(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }

    public enum TaskState implements StringRepresentable {
        INCOMPLETE("incomplete"),
        PARTIAL("partial"),
        COMPLETE("complete");

        private final String name;
        TaskState(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }

    // A Codec allows automated JSON parsing and network streaming
    public static final Codec<QuestTask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestTask::getId),
            StringRepresentable.fromEnum(TaskType::values).fieldOf("type").forGetter(QuestTask::getType),
            Codec.STRING.optionalFieldOf("targetId", "").forGetter(QuestTask::getTargetId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(QuestTask::getName),
            Codec.INT.optionalFieldOf("requiredAmount", 1).forGetter(QuestTask::getRequiredAmount),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestTask::isOptional),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(QuestTask::isRepeatable),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(QuestTask::isConsume),
            Codec.INT.optionalFieldOf("targetX", 0).forGetter(QuestTask::getTargetX),
            Codec.INT.optionalFieldOf("targetY", 0).forGetter(QuestTask::getTargetY),
            Codec.INT.optionalFieldOf("targetZ", 0).forGetter(QuestTask::getTargetZ)
    ).apply(instance, (id, type, targetId, name, requiredAmount, isOptional, isRepeatable, consume, tx, ty, tz) ->
            new QuestTask(id, type, targetId, name, requiredAmount, 0, isOptional, isRepeatable, consume, TaskState.INCOMPLETE, tx, ty, tz)));

    private String id;
    private TaskType type;
    private String targetId; // Registry name of Item, Entity, Biome, etc.
    private String name;
    private int requiredAmount;
    private int currentAmount;
    private boolean optional;
    private boolean repeatable;
    private boolean consume;
    private TaskState state;
    private int targetX;
    private int targetY;
    private int targetZ;

    public QuestTask(String id, TaskType type, String targetId, String name, int requiredAmount, int currentAmount, boolean optional, boolean repeatable, boolean consume, TaskState state, int targetX, int targetY, int targetZ) {
        this.id = id;
        this.type = type;
        this.targetId = targetId;
        this.name = name;
        this.requiredAmount = requiredAmount;
        this.currentAmount = currentAmount;
        this.optional = optional;
        this.repeatable = repeatable;
        this.consume = consume;
        this.state = state;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    // --- GETTERS & SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRequiredAmount() { return requiredAmount; }
    public void setRequiredAmount(int requiredAmount) { this.requiredAmount = requiredAmount; }

    public int getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(int currentAmount) { this.currentAmount = currentAmount; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    public boolean isRepeatable() { return repeatable; }
    public void setRepeatable(boolean repeatable) { this.repeatable = repeatable; }

    public boolean isConsume() { return consume; }
    public void setConsume(boolean consume) { this.consume = consume; }

    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }

    public int getTargetX() { return targetX; }
    public void setTargetX(int targetX) { this.targetX = targetX; }

    public int getTargetY() { return targetY; }
    public void setTargetY(int targetY) { this.targetY = targetY; }

    public int getTargetZ() { return targetZ; }
    public void setTargetZ(int targetZ) { this.targetZ = targetZ; }

    public ItemStack getIconStack() {
        try {
            String raw = this.targetId;
            // FIX: If it's a tag, strip the # before parsing the Identifier
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }

            Identifier loc = Identifier.tryParse(raw);
            if (loc == null) return new ItemStack(Items.BARRIER);

            return switch (this.type) {
                case KILL -> {
                    Optional<Holder.Reference<EntityType<?>>> entityHolder = BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (entityHolder.isPresent()) {
                        Object rawValue = entityHolder.get().value();
                        EntityType<?> type;
                        
                        // Use isPresent/get to bypass capture ? issues with orElse
                        if (rawValue instanceof Optional<?> opt) {
                            type = opt.isPresent() ? (EntityType<?>) opt.get() : null;
                        }
                        else type = (EntityType<?>) rawValue;

                        if (type != null) {
                            // Loop through all items to find the corresponding Spawn Egg
                            // This bypasses .byId(), .fromEntity(), and all mapping conflicts
                            for (Item i : BuiltInRegistries.ITEM) {
                                if (i instanceof SpawnEggItem egg && egg.getType(ItemStack.EMPTY) == type) {
                                    yield new ItemStack(i);
                                }
                            }
                        }
                    }
                    yield new ItemStack(Items.IRON_SWORD); // Generic fallback for modded mobs without eggs
                }
                case BIOME -> new ItemStack(Items.GRASS_BLOCK);
                case ITEM, OBSERVE -> {
                    // Try to find a direct item/block first
                    Optional<Item> item = BuiltInRegistries.ITEM.get(loc).map(Holder::value);
                    if (item.isPresent() && item.get() != Items.AIR) yield new ItemStack(item.get());

                    // FIX: Fallback to Block Registry for technical blocks (Cauldrons, wall signs, etc.)
                    Optional<Item> blockItem = BuiltInRegistries.BLOCK.get(loc).map(h -> h.value().asItem());
                    if (blockItem.isPresent() && blockItem.get() != Items.AIR) yield new ItemStack(blockItem.get());

                    // FIX: Handle itemless technical blocks (Liquids, Fire, etc.)
                    if (loc.getNamespace().equals("minecraft")) {
                        String path = loc.getPath();
                        if (path.contains("water")) yield new ItemStack(Items.WATER_BUCKET);
                        if (path.contains("lava")) yield new ItemStack(Items.LAVA_BUCKET);
                        if (path.contains("fire")) yield new ItemStack(Items.FLINT_AND_STEEL);
                        if (path.equals("end_portal")) yield new ItemStack(Items.ENDER_EYE);
                        if (path.equals("nether_portal")) yield new ItemStack(Items.NETHER_BRICK);
                    }

                    // If not an item, check if it's an entity (for Spawn Eggs) - useful for OBSERVE mobs
                    Optional<Holder.Reference<EntityType<?>>> entityHolder = BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (entityHolder.isPresent()) {
                        EntityType<?> type = entityHolder.get().value();
                        for (Item i : BuiltInRegistries.ITEM) {
                            if (i instanceof SpawnEggItem egg && egg.getType(ItemStack.EMPTY) == type) {
                                yield new ItemStack(i);
                            }
                        }
                    }
                    yield new ItemStack(this.type == TaskType.OBSERVE ? Items.SPYGLASS : Items.BARRIER);
                }
                case LOCATION -> new ItemStack(Items.COMPASS);
                case CHECKBOX -> new ItemStack(Items.PAPER);
            };
        } catch (Exception e) {
            // Fallback for invalid Identifiers
            return switch (this.type) {
                case ITEM -> new ItemStack(Items.BARRIER);
                case OBSERVE -> new ItemStack(Items.SPYGLASS);
                case KILL -> new ItemStack(Items.IRON_SWORD);
                case BIOME -> new ItemStack(Items.GRASS_BLOCK);
                case LOCATION -> new ItemStack(Items.COMPASS);
                case CHECKBOX -> new ItemStack(Items.PAPER);
            };
        }
    }

    public String getTargetDisplayName() {
        try {
            if (this.targetId.isEmpty() || this.targetId.equals("minecraft:air")) return "None";

            if (this.targetId.startsWith("#")) {
                String path = targetId.substring(targetId.lastIndexOf(':') + 1);
                path = path.replace('_', ' ').replace('/', ' ');
                StringBuilder sb = new StringBuilder();
                for (String word : path.split(" ")) {
                    if (!word.isEmpty())
                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
                }
                return sb.toString().trim();
            }

            Identifier loc = Identifier.tryParse(this.targetId);
            if (loc == null) return this.targetId;

            return switch (this.type) {
                case ITEM -> {
                    var itemHolder = BuiltInRegistries.ITEM.get(loc);
                    if (itemHolder.isPresent()) {
                        Object rawValue = itemHolder.get().value();
                        Item item = (rawValue instanceof Optional<?> opt) ?
                                (opt.isPresent() ? (Item) opt.get() : Items.AIR) : (Item) rawValue;
                        yield item.getDefaultInstance().getHoverName().getString();
                    }
                    yield this.targetId;
                }
                case KILL -> {
                    var entityHolder = BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (entityHolder.isPresent()) {
                        Object rawValue = entityHolder.get().value();
                        EntityType<?> type = (rawValue instanceof Optional<?> opt) ?
                                (opt.isPresent() ? (EntityType<?>) opt.get() : null) : (EntityType<?>) rawValue;
                        if (type != null) yield type.getDescription().getString();
                    }
                    yield this.targetId;
                }
                case OBSERVE, BIOME -> {
                    String path = loc.getPath().substring(loc.getPath().lastIndexOf('/') + 1).replace('_', ' ');
                    StringBuilder sb = new StringBuilder();
                    for (String word : path.toLowerCase().split(" ")) {
                        if (!word.isEmpty())
                            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
                    }
                    yield sb.toString().trim();
                }
                default -> this.targetId;
            };
        } catch (Exception e) {
            return this.targetId;
        }
    }

    public static String generateTaskId(String questId, String targetName, List<QuestTask> existingTasks) {
        String cleanTarget = targetName.toLowerCase().replace(" ", "_");
        String baseId = questId + "/" + cleanTarget;
        int suffix = 0;
        String candidateId = baseId;
        while (true) {
            String checkId = candidateId; // Effectively final copy for the lambda
            if (existingTasks.stream().noneMatch(t -> t.getId().equals(checkId))) {
                return checkId;
            }
            candidateId = baseId + "_" + (suffix++);
        }
    }

    /**
     * Processes an incoming item stack against this task.
     * @return The amount of items actually used/added to progress.
     */
    public int handleItemAddition(ItemStack stack) {
        // Only process if it's an ITEM task and not already finished
        if (this.type != TaskType.ITEM || this.state == TaskState.COMPLETE) return 0;

        // FIX: Implement Tag matching logic for automatic item additions
        boolean matches;
        if (this.targetId.startsWith("#")) {
            try {
                Identifier loc = Identifier.parse(this.targetId.substring(1));
                var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, loc);
                matches = stack.is(tagKey);
            } catch (Exception e) {
                matches = false;
            }
        } else {
            Identifier loc = Identifier.tryParse(this.targetId);
            matches = loc != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(loc);
        }

        if (matches) {
            int needed = this.requiredAmount - this.currentAmount;
            int toAdd = Math.min(stack.getCount(), needed);

            if (toAdd > 0) {
                this.currentAmount += toAdd;
                
                // Transition state
                this.state = (this.currentAmount >= this.requiredAmount) ? TaskState.COMPLETE : TaskState.PARTIAL;

                // Handle physical item removal if specified in the editor
                if (this.consume) {
                    stack.shrink(toAdd);
                }
                return toAdd;
            }
        }
        return 0;
    }
}