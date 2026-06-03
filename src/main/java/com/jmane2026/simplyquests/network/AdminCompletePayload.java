package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record AdminCompletePayload(
        String questId,
        Optional<String> taskId,
        boolean complete
) implements CustomPacketPayload {

    public static final Type<AdminCompletePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "admin_complete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminCompletePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminCompletePayload::questId,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), AdminCompletePayload::taskId,
            ByteBufCodecs.BOOL, AdminCompletePayload::complete,
            AdminCompletePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
