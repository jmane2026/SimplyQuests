package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record QuestCompletedPayload(
        String title,
        ItemStack icon
) implements CustomPacketPayload {

    public static final Type<QuestCompletedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "quest_completed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestCompletedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestCompletedPayload::title,
            ItemStack.STREAM_CODEC, QuestCompletedPayload::icon,
            QuestCompletedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}