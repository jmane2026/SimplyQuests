package com.jmane2026.simplyquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class QuestReward {
    public enum RewardType implements StringRepresentable {
        ITEM("item"),
        XP("xp"),
        COMMAND("command");

        private final String name;

        RewardType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestReward::getId),
            StringRepresentable.fromEnum(RewardType::values).fieldOf("type").forGetter(QuestReward::getType),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item", Items.AIR).forGetter(QuestReward::getItem),
            Codec.INT.optionalFieldOf("count", 1).forGetter(QuestReward::getCount),
            Codec.STRING.optionalFieldOf("command", "").forGetter(QuestReward::getCommand),
            Codec.list(Codec.lazyInitialized(() -> QuestReward.CODEC)).optionalFieldOf("subRewards", List.of()).forGetter(QuestReward::getSubRewards)
    ).apply(instance, QuestReward::new));

    private String id;
    private RewardType type;
    private Item item;
    private int count;
    private String command;
    private List<QuestReward> subRewards = new ArrayList<>();

    public QuestReward(String id, RewardType type, Item item, int count, String command, List<QuestReward> subRewards) {
        this.id = id;
        this.type = type;
        this.item = item;
        this.count = count;
        this.command = command;
        this.subRewards = new ArrayList<>(subRewards);
    }

    public QuestReward(QuestReward other) {
        this.id = other.id;
        this.type = other.type;
        this.item = other.item;
        this.count = other.count;
        this.command = other.command;
        this.subRewards = new ArrayList<>(other.subRewards);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public static String generateRewardId(String questId, String typeName, List<QuestReward> existingRewards) {
        String cleanTarget = typeName.toLowerCase().replace(" ", "_");
        String baseId = questId + "/reward_" + cleanTarget;
        int suffix = 0;
        String candidateId = baseId;
        while (true) {
            String checkId = candidateId;
            if (existingRewards.stream().noneMatch(r -> r.getId().equals(checkId))) {
                return candidateId;
            }
            candidateId = baseId + "_" + (suffix++);
        }
    }

    public RewardType getType() {
        return type;
    }

    public void setType(RewardType type) {
        this.type = type;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<QuestReward> getSubRewards() {
        return subRewards;
    }

    public void setSubRewards(List<QuestReward> subRewards) {
        this.subRewards = subRewards;
    }
}