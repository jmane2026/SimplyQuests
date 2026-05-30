package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncImagePayload(String imageId, byte[] data) implements CustomPacketPayload {
    public static final Type<SyncImagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "sync_image"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncImagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncImagePayload::imageId,
            ByteBufCodecs.BYTE_ARRAY, SyncImagePayload::data,
            SyncImagePayload::new
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}