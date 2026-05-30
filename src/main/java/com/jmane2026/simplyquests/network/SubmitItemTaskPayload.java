package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SubmitItemTaskPayload(
        String questId,
        String taskId
) implements CustomPacketPayload {

    public static final Type<SubmitItemTaskPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "submit_item_task"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitItemTaskPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SubmitItemTaskPayload::questId,
            ByteBufCodecs.STRING_UTF8, SubmitItemTaskPayload::taskId,
            SubmitItemTaskPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}