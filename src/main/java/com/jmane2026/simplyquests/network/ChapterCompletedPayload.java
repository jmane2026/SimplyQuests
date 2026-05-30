package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.ByteBufCodecs;

public record ChapterCompletedPayload(
        String name,
        ItemStack icon
) implements CustomPacketPayload {

    public static final Type<ChapterCompletedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "chapter_completed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChapterCompletedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChapterCompletedPayload::name,
            ItemStack.STREAM_CODEC, ChapterCompletedPayload::icon,
            ChapterCompletedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}