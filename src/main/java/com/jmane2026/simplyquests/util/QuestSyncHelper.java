package com.jmane2026.simplyquests.util;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.network.SyncChapterPayload;
import com.jmane2026.simplyquests.network.SyncPlayerProgressPayload;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class QuestSyncHelper {

    public static void syncPlayerProgress(ServerPlayer player) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

        SyncPlayerProgressPayload payload = new SyncPlayerProgressPayload(
                progress.getCompletedQuests().stream().toList(),
                progress.getTaskProgressMap(),
                progress.getClaimedRewards().stream().toList()
        );

        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void broadcastChapterUpdate(Identifier id, QuestChapter chapter) {
        PacketDistributor.sendToAllPlayers(new SyncChapterPayload(id, chapter));
    }
}