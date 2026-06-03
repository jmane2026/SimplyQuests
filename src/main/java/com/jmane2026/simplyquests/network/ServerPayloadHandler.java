package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.data.QuestManager;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import com.jmane2026.simplyquests.quest.CanvasText;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestCanvasImage;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerPayloadHandler {

    public static void handleQuestLock(final QuestLockPayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;
        context.enqueueWork(() -> {
            var manager = QuestServerEvents.getQuestManager();
            Quest target = manager.questLookup.get(payload.questId());
            if (target != null) {
                String currentPlayer = context.player().getName().getString();
                if (payload.lock()) {
                    if (target.getLockedBy().isEmpty()) target.setLockedBy(currentPlayer);
                } else {
                    if (target.getLockedBy().equals(currentPlayer)) target.setLockedBy("");
                }
                broadcastFullSync();
            }
        });
    }

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
            String playerName = context.player().getName().getString();
            String chName = payload.chapter().getName();
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", chName.toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));

            QuestChapter oldChapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            QuestChapter chapterToSave = payload.chapter();

            if (oldChapter != null) {
                Map<String, Quest> questMap = new LinkedHashMap<>();

                for (Quest q : oldChapter.getQuests()) {
                    if (q.getLockedBy().equals(playerName)) {
                        boolean inPayload = payload.chapter().getQuests().stream().anyMatch(i -> i.getId().equals(q.getId()));
                        if (!inPayload) continue;
                    }
                    questMap.put(q.getId(), q);
                }

                payload.chapter().getQuests().forEach(q -> {
                    q.setLockedBy("");
                    questMap.put(q.getId(), q);
                });

                questMap.values().stream().filter(q -> q.getLockedBy().equals(playerName)).forEach(q -> q.setLockedBy(""));

                Map<String, CanvasText> textMap = new LinkedHashMap<>();
                oldChapter.getCanvasTexts().forEach(t -> textMap.put(t.getId(), t));
                payload.chapter().getCanvasTexts().forEach(t -> textMap.put(t.getId(), t));

                Map<String, QuestCanvasImage> imgMap = new LinkedHashMap<>();
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
                List<Quest> mutableQuests = new ArrayList<>(chapter.getQuests());

                Quest target = mutableQuests.stream().filter(q -> q.getId().equals(payload.questId())).findFirst().orElse(null);

                if (target != null) {
                    mutableQuests.remove(target);

                    var server = context.player().level().getServer();
                    if (server != null) server.getPlayerList().getPlayers().forEach(p -> {
                        var progress = p.getData(QuestAttachmentRegistry.PLAYER_PROGRESS.get());
                        progress.getCompletedQuests().remove(payload.questId());
                        for (QuestTask task : target.getTasks()) {
                            progress.getTaskProgressMap().remove(task.getId());
                        }
                    });

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
                List<CanvasText> mutableTexts = new ArrayList<>(chapter.getCanvasTexts());
                mutableTexts.removeIf(t -> t.getId().equals(payload.textId()));
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

            Map<String, QuestGroup> groupMap = new LinkedHashMap<>();
            manager.getGroups().forEach(g -> groupMap.put(g.getName(), g));
            payload.groups().forEach(g -> groupMap.put(g.getName(), g));

            Map<String, SaveGroupsPayload.StandaloneChapterInfo> rootMap = new LinkedHashMap<>();
            manager.getRootChapters().forEach(r -> rootMap.put(r.name(), r));
            payload.rootChapters().forEach(r -> rootMap.put(r.name(), r));

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
            Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", payload.chapterName());
            String internalId = chId.getPath();
            var manager = QuestServerEvents.getQuestManager();

            manager.getRootChapters().removeIf(r -> r.name().equals(internalId));
            manager.getGroups().forEach(g -> g.getChapterNames().remove(internalId));

            QuestChapter chapter = QuestServerEvents.getQuestManager().getChapters().get(chId);
            if (chapter != null && context.player().level().getServer() != null) {
                var server = context.player().level().getServer();
                server.getPlayerList().getPlayers().forEach(player -> {
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

            manager.getGroups().removeIf(g -> g.getName().equals(Quest.sanitizePath(targetName)) || g.getTitle().equals(targetName));

            manager.saveGroups(manager.getGroups(), manager.getRootChapters());

            broadcastFullSync();
        });
    }

    public static void handleUploadImage(final UploadImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            if (!payload.fileName().toLowerCase().endsWith(".png")) {
                context.reply(new SimpleErrorPayload("Upload rejected: Only .png files are allowed."));
                return;
            }

            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.fileName());
                Files.write(target, payload.data());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void handleDeleteImage(final DeleteImagePayload payload, final IPayloadContext context) {
        if (!isOp(context)) return;

        context.enqueueWork(() -> {
            try {
                Path dir = QuestManager.getImagesDirectory();
                Path target = dir.resolve(payload.imageId());
                Files.deleteIfExists(target);
            } catch (Exception e) {
                e.printStackTrace();
            }

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

        List<QuestChapter> chaptersSnapshot = new ArrayList<>(manager.getChapters().values());
        List<QuestGroup> groupsSnapshot = new ArrayList<>(manager.getGroups());

        PacketDistributor.sendToAllPlayers(new SyncQuestTreePayload(chaptersSnapshot, groupsSnapshot));
    }
}