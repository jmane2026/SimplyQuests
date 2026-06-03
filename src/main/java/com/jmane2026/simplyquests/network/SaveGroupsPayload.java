package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestGroup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SaveGroupsPayload(List<QuestGroup> groups,
                                List<StandaloneChapterInfo> rootChapters) implements CustomPacketPayload {
    public static final Type<SaveGroupsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("simplyquests", "save_groups"));
    public static final StreamCodec<FriendlyByteBuf, SaveGroupsPayload> CODEC = CustomPacketPayload.codec(SaveGroupsPayload::write, SaveGroupsPayload::new);

    public record StandaloneChapterInfo(String name, int order) {
    }

    public SaveGroupsPayload(FriendlyByteBuf buf) {
        this(
                buf.readList(b -> b.readLenientJsonWithCodec(QuestGroup.CODEC)),
                buf.readList(b -> new StandaloneChapterInfo(b.readUtf(), b.readInt()))
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(groups, (b, g) -> b.writeJsonWithCodec(QuestGroup.CODEC, g));
        buf.writeCollection(rootChapters, (b, info) -> {
            b.writeUtf(info.name());
            b.writeInt(info.order());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}