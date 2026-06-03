package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteImagePayload(String imageId) implements CustomPacketPayload {
    public static final Type<DeleteImagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "delete_image"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteImagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteImagePayload::imageId,
            DeleteImagePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}