package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record QuestLockPayload(String questId, boolean lock) implements CustomPacketPayload {
    public static final Type<QuestLockPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "quest_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestLockPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestLockPayload::questId, ByteBufCodecs.BOOL, QuestLockPayload::lock, QuestLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}