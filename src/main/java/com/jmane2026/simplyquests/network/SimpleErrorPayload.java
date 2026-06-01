package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SimpleErrorPayload(String message) implements CustomPacketPayload {
    public static final Type<SimpleErrorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "simple_error"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SimpleErrorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SimpleErrorPayload::message, SimpleErrorPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}