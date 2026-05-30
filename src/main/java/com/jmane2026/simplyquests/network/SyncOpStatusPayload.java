package com.jmane2026.simplyquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncOpStatusPayload(boolean isOp, boolean editModeEnabled) implements CustomPacketPayload {
    public static final Type<SyncOpStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "sync_op_status"));
    public static final StreamCodec<FriendlyByteBuf, SyncOpStatusPayload> CODEC = CustomPacketPayload.codec(SyncOpStatusPayload::write, SyncOpStatusPayload::new);

    public SyncOpStatusPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isOp);
        buf.writeBoolean(editModeEnabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}