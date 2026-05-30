package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleCheckboxPayload(
        String questId,
        String taskId
) implements CustomPacketPayload {

    public static final Type<ToggleCheckboxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "toggle_checkbox"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleCheckboxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToggleCheckboxPayload::questId,
            ByteBufCodecs.STRING_UTF8, ToggleCheckboxPayload::taskId,
            ToggleCheckboxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}