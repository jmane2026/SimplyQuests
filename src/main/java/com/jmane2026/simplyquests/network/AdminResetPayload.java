package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record AdminResetPayload(
        Optional<String> groupName,
        Optional<String> chapterName
) implements CustomPacketPayload {

    public static final Type<AdminResetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "admin_reset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminResetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), AdminResetPayload::groupName,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), AdminResetPayload::chapterName,
            AdminResetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
