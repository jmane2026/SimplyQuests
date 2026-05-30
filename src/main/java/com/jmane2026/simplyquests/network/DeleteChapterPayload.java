package com.jmane2026.simplyquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteChapterPayload(String chapterName) implements CustomPacketPayload {
    public static final Type<DeleteChapterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "delete_chapter"));
    public static final StreamCodec<FriendlyByteBuf, DeleteChapterPayload> CODEC = CustomPacketPayload.codec(DeleteChapterPayload::write, DeleteChapterPayload::new);

    public DeleteChapterPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(chapterName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}