package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SyncQuestTreePayload(List<QuestChapter> chapters, List<QuestGroup> groups) implements CustomPacketPayload {
    public static final Type<SyncQuestTreePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "sync_quest_tree"));
    public static final StreamCodec<FriendlyByteBuf, SyncQuestTreePayload> CODEC = CustomPacketPayload.codec(SyncQuestTreePayload::write, SyncQuestTreePayload::new);

    public SyncQuestTreePayload(FriendlyByteBuf buf) {
        this(buf.readList(b -> b.readLenientJsonWithCodec(QuestChapter.CODEC)),
             buf.readList(b -> b.readLenientJsonWithCodec(QuestGroup.CODEC)));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(chapters, (b, c) -> b.writeJsonWithCodec(QuestChapter.CODEC, c));
        buf.writeCollection(groups, (b, g) -> b.writeJsonWithCodec(QuestGroup.CODEC, g));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}