package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteQuestPayload(String questId, String chapterName) implements CustomPacketPayload {
    public static final Type<DeleteQuestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "delete_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteQuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteQuestPayload::questId, ByteBufCodecs.STRING_UTF8, DeleteQuestPayload::chapterName, DeleteQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}