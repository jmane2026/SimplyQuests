package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteCanvasTextPayload(String text, double x, double y, String chapterName) implements CustomPacketPayload {
    public static final Type<DeleteCanvasTextPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "delete_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteCanvasTextPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteCanvasTextPayload::text, ByteBufCodecs.DOUBLE, DeleteCanvasTextPayload::x,
            ByteBufCodecs.DOUBLE, DeleteCanvasTextPayload::y, ByteBufCodecs.STRING_UTF8, DeleteCanvasTextPayload::chapterName,
            DeleteCanvasTextPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}