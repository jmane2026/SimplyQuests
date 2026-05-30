package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SaveChapterPayload(QuestChapter chapter) implements CustomPacketPayload {
    public static final Type<SaveChapterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "save_chapter"));
    public static final StreamCodec<FriendlyByteBuf, SaveChapterPayload> CODEC = CustomPacketPayload.codec(SaveChapterPayload::write, SaveChapterPayload::new);

    public SaveChapterPayload(FriendlyByteBuf buf) {
        this(buf.readLenientJsonWithCodec(QuestChapter.CODEC));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeJsonWithCodec(QuestChapter.CODEC, chapter);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}