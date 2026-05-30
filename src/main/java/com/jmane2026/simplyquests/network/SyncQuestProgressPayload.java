package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.quest.QuestTask;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncQuestProgressPayload(
        String questId,
        String taskId,
        int currentAmount,
        QuestTask.TaskState state
) implements CustomPacketPayload {

    public static final Type<SyncQuestProgressPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "sync_quest_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQuestProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncQuestProgressPayload::questId,
            ByteBufCodecs.STRING_UTF8, SyncQuestProgressPayload::taskId,
            ByteBufCodecs.VAR_INT, SyncQuestProgressPayload::currentAmount,
            ByteBufCodecs.idMapper(i -> QuestTask.TaskState.values()[i], QuestTask.TaskState::ordinal), SyncQuestProgressPayload::state,
            SyncQuestProgressPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}