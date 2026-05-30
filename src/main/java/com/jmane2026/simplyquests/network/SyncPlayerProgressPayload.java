package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncPlayerProgressPayload(
        List<String> completedQuests,
        Map<String, Integer> taskProgress,
        List<String> claimedRewards
) implements CustomPacketPayload {

    public static final Type<SyncPlayerProgressPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "sync_player_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncPlayerProgressPayload::completedQuests,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SyncPlayerProgressPayload::taskProgress,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncPlayerProgressPayload::claimedRewards,
            SyncPlayerProgressPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}