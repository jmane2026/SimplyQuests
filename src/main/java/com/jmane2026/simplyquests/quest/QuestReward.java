package com.jmane2026.simplyquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.List;

public class QuestReward {
    public enum RewardType implements StringRepresentable {
        ITEM("item"),
        XP("xp"),
        COMMAND("command");

        private final String name;
        RewardType(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestReward::getId),
            StringRepresentable.fromEnum(RewardType::values).fieldOf("type").forGetter(QuestReward::getType),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(r -> r.getType() == RewardType.ITEM ? Optional.of(r.getItem()) : Optional.empty()),
            Codec.INT.optionalFieldOf("count", 1).forGetter(QuestReward::getCount),
            Codec.STRING.optionalFieldOf("command", "").forGetter(QuestReward::getCommand)
    ).apply(instance, (id, type, item, count, cmd) -> new QuestReward(id, type, item.orElse(Items.AIR), count, cmd)));

    private String id;
    private final RewardType type;
    private final Item item;
    private final int count;
    private final String command;

    public QuestReward(String id, RewardType type, Item item, int count, String command) {
        this.id = id;
        this.type = type;
        this.item = item;
        this.count = count;
        this.command = command;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public RewardType getType() { return type; }
    public Item getItem() { return item; }
    public int getCount() { return count; }
    public String getCommand() { return command; }
}