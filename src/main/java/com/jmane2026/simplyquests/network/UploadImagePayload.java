package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UploadImagePayload(String fileName, byte[] data) implements CustomPacketPayload {
    public static final Type<UploadImagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "upload_image"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadImagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UploadImagePayload::fileName,
            ByteBufCodecs.BYTE_ARRAY, UploadImagePayload::data,
            UploadImagePayload::new
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}