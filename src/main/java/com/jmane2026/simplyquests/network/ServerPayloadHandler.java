package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.data.QuestManager;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.quest.CanvasText;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestCanvasImage;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

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

            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", chName.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));

            // SMART MERGE: Instead of replacing the chapter, merge the incoming objects.
            // This allows Player A and B to create quests simultaneously without deleting each other's work.
            QuestChapter oldChapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            QuestChapter chapterToSave = payload.chapter();

            if (oldChapter != null) {
                // 1. Merge Quests (Update existing by ID, add new ones)
                Map<String, Quest> questMap = new LinkedHashMap<>();
                oldChapter.getQuests().forEach(q -> questMap.put(q.getId(), q));
                // Payload (Client) always overwrites the Server's version of the same ID
                payload.chapter().getQuests().forEach(q -> questMap.put(q.getId(), q));

                // 2. Merge Canvas Texts (Match by approximate identity)
                Map<String, CanvasText> textMap = new LinkedHashMap<>();
                oldChapter.getCanvasTexts().forEach(t -> textMap.put(t.getText() + t.getX() + t.getY(), t));
                payload.chapter().getCanvasTexts().forEach(t -> textMap.put(t.getText() + t.getX() + t.getY(), t));

                // 3. Merge Canvas Images
                Map<String, QuestCanvasImage> imgMap = new LinkedHashMap<>();
                // FIX: Use unique instance ID instead of filename (imageId) to allow duplicates of the same file
                oldChapter.getCanvasImages().forEach(i -> imgMap.put(i.getId(), i));
                payload.chapter().getCanvasImages().forEach(i -> imgMap.put(i.getId(), i));

                chapterToSave = new QuestChapter(
                        payload.chapter().getGroupName(), payload.chapter().getGroupOrder(), payload.chapter().getGroupColor(),
                        payload.chapter().getName(), payload.chapter().getTitle(), payload.chapter().getChapterOrder(),
                        payload.chapter().getIcon(), new ArrayList<>(questMap.values()),
                        new ArrayList<>(textMap.values()), new ArrayList<>(imgMap.values()),
                        payload.chapter().getOffsetX(), payload.chapter().getOffsetY(), payload.chapter().getZoom()
                );
            }

            QuestServerEvents.getQuestManager().saveChapter(chId, chapterToSave);
            QuestServerEvents.getQuestManager().updateChapterInMemory(chId, chapterToSave);
            broadcastFullSync();
        });
    }

    public static void handleDeleteQuest(final DeleteQuestPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;
        context.enqueueWork(() -> {
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            QuestChapter chapter = QuestServerEvents.getQuestManager().getChapters().get(chId);

            if (chapter != null) {
                // FIX: Create a mutable copy of the Quest list to avoid UnsupportedOperationException
                List<Quest> mutableQuests = new ArrayList<>(chapter.getQuests());

                // Find the target quest to get its tasks for progress cleanup
                Quest target = mutableQuests.stream().filter(q -> q.getId().equals(payload.questId())).findFirst().orElse(null);

                if (target != null) {
                    mutableQuests.remove(target);

                    // 2. Clear progress for all online players
                    var server = context.player().level().getServer();
                    if (server != null) server.getPlayerList().getPlayers().forEach(p -> {
                        var progress = p.getData(QuestAttachmentRegistry.PLAYER_PROGRESS.get());
                        progress.getCompletedQuests().remove(payload.questId());
                        for (QuestTask task : target.getTasks()) {
                            progress.getTaskProgressMap().remove(task.getId());
                        }
                    });

                    // 3. Rebuild and Save the Chapter
                    QuestChapter updatedChapter = new QuestChapter(
                            chapter.getGroupName(), chapter.getGroupOrder(), chapter.getGroupColor(),
                            chapter.getName(), chapter.getTitle(), chapter.getChapterOrder(),
                            chapter.getIcon(), mutableQuests, chapter.getCanvasTexts(), chapter.getCanvasImages(),
                            chapter.getOffsetX(), chapter.getOffsetY(), chapter.getZoom()
                    );

                    QuestServerEvents.getQuestManager().saveChapter(chId, updatedChapter);
                    QuestServerEvents.getQuestManager().updateChapterInMemory(chId, updatedChapter);
                    broadcastFullSync();
                }
            }
        });
    }

    public static void handleDeleteCanvasText(final DeleteCanvasTextPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;
        context.enqueueWork(() -> {
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            QuestChapter chapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            if (chapter != null) {
                // FIX: Create a mutable copy to avoid UnsupportedOperationException
                List<CanvasText> mutableTexts = new ArrayList<>(chapter.getCanvasTexts());
                mutableTexts.removeIf(t ->
                        t.getText().equals(payload.text()) &&
                                Math.abs(t.getX() - payload.x()) < 0.1 &&
                                Math.abs(t.getY() - payload.y()) < 0.1
                );
                QuestChapter updatedChapter = new QuestChapter(
                        chapter.getGroupName(), chapter.getGroupOrder(), chapter.getGroupColor(),
                        chapter.getName(), chapter.getTitle(), chapter.getChapterOrder(),
                        chapter.getIcon(), chapter.getQuests(), mutableTexts, chapter.getCanvasImages(),
                        chapter.getOffsetX(), chapter.getOffsetY(), chapter.getZoom()
                );

                QuestServerEvents.getQuestManager().saveChapter(chId, updatedChapter);
                QuestServerEvents.getQuestManager().updateChapterInMemory(chId, updatedChapter);
                broadcastFullSync();
            }
        });
    }

    public static void handleSaveGroups(final SaveGroupsPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            var manager = QuestServerEvents.getQuestManager();

            // 1. Merge Groups: Use a Map to combine existing server groups with the incoming update
            Map<String, QuestGroup> groupMap = new LinkedHashMap<>();
            manager.getGroups().forEach(g -> groupMap.put(g.getName(), g));
            // Incoming data from client always wins for the groups they sent
            payload.groups().forEach(g -> groupMap.put(g.getName(), g));

            // 2. Merge Root (Standalone) Chapters
            Map<String, SaveGroupsPayload.StandaloneChapterInfo> rootMap = new LinkedHashMap<>();
            manager.getRootChapters().forEach(r -> rootMap.put(r.name(), r));
            payload.rootChapters().forEach(r -> rootMap.put(r.name(), r));

            // 3. Save the merged result
            List<QuestGroup> mergedGroups = new ArrayList<>(groupMap.values());
            List<SaveGroupsPayload.StandaloneChapterInfo> mergedRoots = new ArrayList<>(rootMap.values());
            
            manager.saveGroups(mergedGroups, mergedRoots);
            
            broadcastFullSync();
            QuestServerEvents.refreshAllCaches(((ServerPlayer) context.player()).level().getServer());
        });
    }

    public static void handleDeleteChapter(final DeleteChapterPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName()); // Now receiving ID
            String internalId = chId.getPath();
            var manager = QuestServerEvents.getQuestManager();

            // 1. Remove from Standalone list in manifest
            manager.getRootChapters().removeIf(r -> r.name().equals(internalId));
            // 2. Remove from any Groups in manifest
            manager.getGroups().forEach(g -> g.getChapterNames().remove(internalId));
            
            // Wipe progress for all quests in the deleted chapter for all players
            QuestChapter chapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            if (chapter != null && context.player().level().getServer() != null) {
                var server = context.player().level().getServer();
                server.getPlayerList().getPlayers().forEach(player -> {
                    // FIX: Use the correct Supplier constant PLAYER_PROGRESS.get()
                    PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS.get());
                    for (Quest q : chapter.getQuests()) {
                        progress.getCompletedQuests().remove(q.getId());
                        for (QuestTask task : q.getTasks()) progress.getTaskProgressMap().remove(task.getId());
                    }
                    progress.getCompletedChapters().remove(chapter.getName());
                });
            }

            manager.saveGroups(manager.getGroups(), manager.getRootChapters());
            manager.deleteChapterFile(chId);
            broadcastFullSync();
            QuestServerEvents.refreshAllCaches(((ServerPlayer) context.player()).level().getServer());
        });
    }

    public static void handleDeleteGroup(final DeleteGroupPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            var manager = QuestServerEvents.getQuestManager();
            String targetName = payload.groupName();
            
            // 1. Physically remove the group from the server list
            manager.getGroups().removeIf(g -> g.getName().equals(Quest.sanitizePath(targetName)) || g.getTitle().equals(targetName));
            
            // 2. Save the updated manifest
            manager.saveGroups(manager.getGroups(), manager.getRootChapters());
            
            broadcastFullSync();
        });
    }

    public static void handleUploadImage(final UploadImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // Security Check: If somehow the client sent a non-png, reject and notify
            if (!payload.fileName().toLowerCase().endsWith(".png")) {
                context.reply(new SimpleErrorPayload("Upload rejected: Only .png files are allowed."));
                return;
            }

            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.fileName());
                Files.write(target, payload.data());
                // Note: We don't broadcast the image here; clients will request it via RequestImagePayload when they see the ID in the chapter file
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void handleDeleteImage(final DeleteImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            // 1. Delete physical file
            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.imageId());
                Files.deleteIfExists(target);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Remove references from ALL chapters to prevent "Smart Merge" from restoring them
            try {
                var manager = QuestServerEvents.getQuestManager();
                for (Map.Entry<Identifier, QuestChapter> entry : manager.getChapters().entrySet()) {
                    QuestChapter ch = entry.getValue();
                    List<QuestCanvasImage> mutableImages = new ArrayList<>(ch.getCanvasImages());

                    if (mutableImages.removeIf(img -> img.getImageId().equals(payload.imageId()))) {
                        QuestChapter updated = new QuestChapter(
                                ch.getGroupName(), ch.getGroupOrder(), ch.getGroupColor(),
                                ch.getName(), ch.getTitle(), ch.getChapterOrder(),
                                ch.getIcon(), ch.getQuests(), ch.getCanvasTexts(), mutableImages,
                                ch.getOffsetX(), ch.getOffsetY(), ch.getZoom()
                        );
                        manager.saveChapter(entry.getKey(), updated);
                        manager.updateChapterInMemory(entry.getKey(), updated);
                    }
                }
                broadcastFullSync();
            } catch (Exception e) {
                e.printStackTrace();
            }
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