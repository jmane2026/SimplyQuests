package com.jmane2026.simplyquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteGroupPayload(String groupName) implements CustomPacketPayload {
    public static final Type<DeleteGroupPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "delete_group"));
    public static final StreamCodec<FriendlyByteBuf, DeleteGroupPayload> CODEC = CustomPacketPayload.codec(DeleteGroupPayload::write, DeleteGroupPayload::new);

    public DeleteGroupPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(groupName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}