package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class ServerPayloadHandler {

    private static boolean isOp(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
            return player.level().getServer().getPlayerList().isOp(identity);
        }
        return false;
    }

    public static void handleSaveChapter(final SaveChapterPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            String chName = payload.chapter().getName();
            // Sanitize: Replace any character that is NOT a-z, 0-9, /, ., _, or - with an underscore
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", chName.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            QuestServerEvents.getQuestManager().saveChapter(chId, payload.chapter());
            QuestServerEvents.getQuestManager().updateChapterInMemory(chId, payload.chapter());
            broadcastFullSync();
        });
    }

    public static void handleSaveGroups(final SaveGroupsPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            QuestServerEvents.getQuestManager().saveGroups(payload.groups(), payload.rootChapters());
            broadcastFullSync();
        });
    }

    public static void handleDeleteChapter(final DeleteChapterPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // Sanitize name for deletion to match the saved file format
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            QuestServerEvents.getQuestManager().deleteChapterFile(chId);
            broadcastFullSync();
        });
    }

    public static void handleDeleteGroup(final DeleteGroupPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // The screen already requests deletion for individual chapters,
            // the server just needs to ensure groups.json is updated if needed.
            // Currently, saveGroups handles the manifest update.
            broadcastFullSync();
        });
    }

    private static void broadcastFullSync() {
        var manager = QuestServerEvents.getQuestManager();

        // FIX: Create snapshots (copies) of the collections.
        // This prevents ConcurrentModificationException by ensuring the network thread
        // has its own stable copy of the data that won't change during encoding.
        List<QuestChapter> chaptersSnapshot = new ArrayList<>(manager.getChapters().values());
        List<QuestGroup> groupsSnapshot = new ArrayList<>(manager.getGroups());

        PacketDistributor.sendToAllPlayers(new SyncQuestTreePayload(chaptersSnapshot, groupsSnapshot));
    }
}