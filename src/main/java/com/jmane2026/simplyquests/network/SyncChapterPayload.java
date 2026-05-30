package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.data.QuestChapter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncChapterPayload(Identifier id, QuestChapter chapter) implements CustomPacketPayload {
    public static final Type<SyncChapterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "sync_chapter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChapterPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SyncChapterPayload::id,
            QuestChapter.STREAM_CODEC, SyncChapterPayload::chapter,
            SyncChapterPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}