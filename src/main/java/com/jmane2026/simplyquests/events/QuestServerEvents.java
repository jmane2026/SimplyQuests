package com.jmane2026.simplyquests.events;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestManager;
import com.jmane2026.simplyquests.network.*;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import com.jmane2026.simplyquests.util.QuestSyncHelper;
import com.jmane2026.simplyquests.util.RewardGiver;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class QuestServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final QuestManager QUEST_MANAGER = new QuestManager();

    private static final Map<UUID, List<Quest>> ACTIVE_ITEM_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(SimplyQuests.MODID, "quest_manager"),
                QUEST_MANAGER
        );
    }

    public static QuestManager getQuestManager() {
        return QUEST_MANAGER;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SyncQuestTreePayload(
                    new ArrayList<>(QUEST_MANAGER.getChapters().values()),
                    QUEST_MANAGER.getGroups()
            ));

            QuestSyncHelper.syncPlayerProgress(player);
            rebuildActiveQuestCache(player);
            syncAndCheckCompletions(player);
            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

            NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
            boolean isOp = player.level().getServer().getPlayerList().isOp(identity);
            PacketDistributor.sendToPlayer(player, new SyncOpStatusPayload(isOp, progress.isEditMode()));
        }
    }

    public static void updateEditModeAndSync(ServerPlayer player, boolean enabled) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
        progress.setEditMode(enabled);
        player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);

        NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
        boolean isOp = player.level().getServer().getPlayerList().isOp(identity);
        PacketDistributor.sendToPlayer(player, new SyncOpStatusPayload(isOp, enabled));
    }

    public static void refreshAllCaches(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            rebuildActiveQuestCache(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {

        ACTIVE_ITEM_CACHE.remove(event.getEntity().getUUID());
    }

    public static void handleSubmitItemTask(final SubmitItemTaskPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Quest quest = QUEST_MANAGER.getAllQuests().stream()
                        .filter(q -> q.getId().equals(payload.questId()))
                        .findFirst().orElse(null);

                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
                if (quest == null || progress.isQuestComplete(quest.getId()) || !isQuestUnlocked(quest, progress))
                    return;

                QuestTask task = quest.getTasks().stream()
                        .filter(t -> t.getId().equals(payload.taskId()))
                        .findFirst().orElse(null);

                if (task == null || task.getType() != QuestTask.TaskType.ITEM || !task.isConsume()) return;

                String rawTarget = task.getTargetId();
                java.util.function.Predicate<ItemStack> itemMatcher;

                if (rawTarget.startsWith("#")) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, Identifier.parse(rawTarget.substring(1)));
                    itemMatcher = stack -> stack.is(tagKey);
                } else {
                    Identifier targetId = Identifier.tryParse(rawTarget);
                    if (targetId == null) return;
                    Item targetItem = BuiltInRegistries.ITEM.get(targetId).map(Holder::value).orElse(null);
                    if (targetItem == null) return;
                    itemMatcher = stack -> stack.is(targetItem);
                }

                int current = progress.getTaskAmount(task.getId());
                int needed = task.getRequiredAmount() - current;
                int foundCount = 0;

                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && itemMatcher.test(carried)) {
                    int toTake = Math.min(carried.getCount(), needed - foundCount);
                    carried.shrink(toTake);
                    foundCount += toTake;
                }

                Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize() && foundCount < needed; i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && itemMatcher.test(stack)) {
                        int toTake = Math.min(stack.getCount(), needed - foundCount);
                        stack.shrink(toTake);
                        foundCount += toTake;
                    }
                    if (foundCount >= needed) break;
                }

                if (foundCount > 0) {
                    progress.setTaskAmount(task.getId(), current + foundCount);
                    player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                    syncAndCheckCompletions(player);
                }
            }
        });
    }

    public static void handleAdminComplete(final AdminCompletePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Quest quest = QUEST_MANAGER.getAllQuests().stream()
                        .filter(q -> q.getId().equals(payload.questId()))
                        .findFirst().orElse(null);
                if (quest == null) return;
                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

                if (!payload.complete()) {
                    String qId = quest.getId();
                    progress.getCompletedQuests().remove(qId);

                    progress.getClaimedRewards().removeIf(id -> id.equals(qId) || id.startsWith(qId + "/"));

                    if (payload.taskId().isPresent()) {
                        progress.setTaskAmount(payload.taskId().get(), 0);
                    } else {
                        for (QuestTask task : quest.getTasks()) {
                            progress.resetTaskProgress(task.getId());
                        }

                        String chName = quest.getChapterName();
                        QuestChapter chapter = QUEST_MANAGER.getChapters().values().stream()
                                .filter(c -> c.getName().equals(chName))
                                .findFirst().orElse(null);
                        if (chapter != null) {
                            progress.resetChapter(chapter.getName());
                        }
                    }
                } else {

                    if (payload.taskId().isPresent()) {
                        QuestTask task = quest.getTasks().stream().filter(t -> t.getId().equals(payload.taskId().get())).findFirst().orElse(null);
                        if (task != null) progress.setTaskAmount(task.getId(), task.getRequiredAmount());
                    } else {
                        for (QuestTask task : quest.getTasks()) {
                            progress.setTaskAmount(task.getId(), task.getRequiredAmount());
                        }
                        progress.completeQuest(quest.getId());
                    }
                }

                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                syncAndCheckCompletions(player);
                if (!payload.complete() && payload.taskId().isEmpty()) processItemAcquisition(player, ItemStack.EMPTY);
            }
        });
    }

    public static void handleAdminReset(final AdminResetPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
                List<QuestChapter> chapters = new ArrayList<>(QUEST_MANAGER.getChapters().values());

                boolean groupReset = payload.groupName().isPresent();
                boolean chapterReset = payload.chapterName().isPresent();
                List<QuestChapter> targets = new ArrayList<>();

                if (groupReset) {
                    String targetGroupId = payload.groupName().get();
                    targets = chapters.stream().filter(c -> c.getGroupName().equals(targetGroupId)).toList();
                } else if (chapterReset) {
                    String targetChapterId = payload.chapterName().get();
                    targets = chapters.stream().filter(c -> c.getName().equals(targetChapterId)).toList();
                }

                for (QuestChapter ch : targets) {
                    progress.resetChapter(ch.getName());
                    for (Quest questInChapter : ch.getQuests()) {
                        String qId = questInChapter.getId();
                        progress.resetQuest(qId);

                        progress.getClaimedRewards().removeIf(id -> id.equals(qId) || id.startsWith(qId + "/"));

                        for (QuestTask task : questInChapter.getTasks()) {
                            progress.resetTaskProgress(task.getId());
                        }
                    }
                }

                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                syncAndCheckCompletions(player);

                processItemAcquisition(player, ItemStack.EMPTY);
            }
        });
    }

    public static void handleToggleCheckbox(final ToggleCheckboxPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Quest quest = QUEST_MANAGER.getAllQuests().stream()
                        .filter(q -> q.getId().equals(payload.questId()))
                        .findFirst().orElse(null);

                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

                if (quest == null || !isQuestUnlocked(quest, progress)) return;

                QuestTask task = quest.getTasks().stream()
                        .filter(t -> t.getId().equals(payload.taskId()))
                        .findFirst().orElse(null);

                if (task == null || task.getType() != QuestTask.TaskType.CHECKBOX) return;

                if (progress.getTaskAmount(task.getId()) >= task.getRequiredAmount()) return;

                int current = progress.getTaskAmount(task.getId());
                progress.setTaskAmount(task.getId(), current >= task.getRequiredAmount() ? 0 : task.getRequiredAmount());
                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);

                syncAndCheckCompletions(player);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            Identifier killedId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (killedId == null) return;

            boolean progressMade = false;
            List<Quest> allQuests = QUEST_MANAGER.getAllQuests();

            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

            for (Quest quest : allQuests) {
                if (progress.isQuestComplete(quest.getId()) || !isQuestUnlocked(quest, progress)) continue;

                for (QuestTask task : quest.getTasks()) {
                    boolean matched = false;
                    if (task.getType() == QuestTask.TaskType.KILL) {
                        if (task.getTargetId().startsWith("#")) {
                            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(task.getTargetId().substring(1)));
                            if (event.getEntity().getType().builtInRegistryHolder().is(tagKey)) matched = true;
                        } else if (task.getTargetId().equals(killedId.toString())) {
                            matched = true;
                        }
                    }

                    if (matched) {
                        int current = progress.getTaskAmount(task.getId());
                        if (current < task.getRequiredAmount()) {
                            progress.setTaskAmount(task.getId(), current + 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                            progressMade = true;
                        }
                    }
                }
            }

            if (progressMade) {
                syncAndCheckCompletions(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % 20 != 0) return;

            List<Quest> allQuests = QUEST_MANAGER.getAllQuests();

            boolean progressMade = false;
            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

            Identifier currentBiome = player.level().getBiome(player.blockPosition())
                    .unwrapKey().map(ResourceKey::identifier).orElse(null);

            for (Quest quest : allQuests) {
                boolean complete = progress.isQuestComplete(quest.getId());
                boolean unlocked = isQuestUnlocked(quest, progress);

                if (complete || !unlocked) continue;

                for (QuestTask task : quest.getTasks()) {
                    if (progress.getTaskAmount(task.getId()) >= task.getRequiredAmount()) continue;

                    if (task.getType() == QuestTask.TaskType.BIOME && currentBiome != null) {
                        if (task.getTargetId().equals(currentBiome.toString())) {
                            progress.setTaskAmount(task.getId(), 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                            progressMade = true;
                        }
                    } else if (task.getType() == QuestTask.TaskType.OBSERVE) {

                        double range = 20.0;
                        float partialTicks = 0.0f;

                        HitResult blockHit = player.pick(range, partialTicks, true);

                        Vec3 eyePos = player.getEyePosition(1.0f);
                        Vec3 viewVec = player.getViewVector(partialTicks);
                        Vec3 reachVec = eyePos.add(viewVec.scale(range));
                        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0, 1.0, 1.0);
                        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePos, reachVec, searchBox, (e) -> !e.isSpectator() && e.isPickable(), range * range);

                        String target = task.getTargetId();
                        boolean isTag = target.startsWith("#");
                        boolean matched = false;
                        String debugHitId = "Nothing";

                        if (entityHit != null) {
                            EntityType<?> type = entityHit.getEntity().getType();
                            debugHitId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                            if (isTag) {
                                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(target.substring(1)));
                                if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tagKey)) matched = true;
                            } else if (target.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) {
                                matched = true;
                            }
                        }

                        if (!matched && blockHit.getType() == HitResult.Type.BLOCK) {
                            var state = player.level().getBlockState(((BlockHitResult) blockHit).getBlockPos());
                            debugHitId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

                            if (isTag) {
                                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(target.substring(1)));
                                if (state.is(tagKey)) matched = true;
                            } else if (target.equals(debugHitId)) {
                                matched = true;
                            }
                        }

                        if (matched) {
                            progress.setTaskAmount(task.getId(), 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                            progressMade = true;
                        }
                    } else if (task.getType() == QuestTask.TaskType.LOCATION) {
                        double distSq = player.blockPosition().distSqr(new net.minecraft.core.Vec3i(task.getTargetX(), task.getTargetY(), task.getTargetZ()));

                        if (distSq <= 16.0) {
                            progress.setTaskAmount(task.getId(), 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                            progressMade = true;
                        }
                    }
                }
            }

            if (progressMade) {
                syncAndCheckCompletions(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player)
            processItemAcquisition(player, event.getItemEntity().getItem());
    }

    @SubscribeEvent
    public static void onPlayerCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            processItemAcquisition(player, event.getCrafting());
        }
    }

    @SubscribeEvent
    public static void onPlayerSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            processItemAcquisition(player, event.getSmelting());
        }
    }

    private static void rebuildActiveQuestCache(ServerPlayer player) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

        List<Quest> eligible = QUEST_MANAGER.getAllQuests().stream()
                .filter(q -> !progress.isQuestComplete(q.getId()))
                .filter(q -> isQuestUnlocked(q, progress))
                .filter(q -> q.getTasks().stream().anyMatch(t -> t.getType() == QuestTask.TaskType.ITEM && !t.isConsume()))
                .toList();

        ACTIVE_ITEM_CACHE.put(player.getUUID(), eligible);
    }

    private static void processItemAcquisition(ServerPlayer player, ItemStack stack) {

        Inventory inventory = player.getInventory();
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

        Map<Item, Integer> inventoryCounts = new HashMap<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (!invStack.isEmpty()) {
                Item item = invStack.getItem();
                inventoryCounts.put(item, inventoryCounts.getOrDefault(item, 0) + invStack.getCount());
            }
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            Item item = carried.getItem();
            inventoryCounts.put(item, inventoryCounts.getOrDefault(item, 0) + carried.getCount());
        }

        List<Quest> activeQuests = ACTIVE_ITEM_CACHE.getOrDefault(player.getUUID(), List.of());
        boolean repeat;

        do {
            repeat = false;
            boolean progressMadeThisPass = false;

            for (Quest quest : activeQuests) {

                for (QuestTask task : quest.getTasks()) {

                    if (task.getType() != QuestTask.TaskType.ITEM || task.isConsume()) continue;

                    String rawTarget = task.getTargetId();
                    int totalInInventory = 0;

                    if (rawTarget.startsWith("#")) {
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, Identifier.parse(rawTarget.substring(1)));
                        for (Map.Entry<Item, Integer> entry : inventoryCounts.entrySet()) {
                            if (BuiltInRegistries.ITEM.wrapAsHolder(entry.getKey()).is(tagKey)) {
                                totalInInventory += entry.getValue();
                            }
                        }
                    } else {
                        Item targetItem = BuiltInRegistries.ITEM.get(Identifier.parse(rawTarget)).map(Holder::value).orElse(null);
                        if (targetItem == null) continue;
                        totalInInventory = inventoryCounts.getOrDefault(targetItem, 0);
                    }

                    if (progress.getTaskAmount(task.getId()) != totalInInventory) {
                        progress.setTaskAmount(task.getId(), totalInInventory);
                        player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                        progressMadeThisPass = true;
                    }
                }
            }
            if (progressMadeThisPass) {
                if (syncAndCheckCompletions(player)) repeat = true;
            }
        } while (repeat);
    }

    private static boolean syncAndCheckCompletions(ServerPlayer player) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
        QuestManager.CompletionResults results = QUEST_MANAGER.updateQuestStates(progress);

        for (Quest q : results.quests()) {
            ItemStack toastIcon = ItemStack.EMPTY;

            if (q.isUseTaskIcon() && !q.getTasks().isEmpty()) {
                QuestTask firstTask = q.getTasks().get(0);
                if (firstTask.getTargetId().startsWith("#")) {
                    try {
                        Identifier loc = Identifier.parse(firstTask.getTargetId().substring(1));
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);

                        toastIcon = BuiltInRegistries.ITEM.getTags()
                                .filter(s -> s.key().equals(tagKey))
                                .findFirst()
                                .flatMap(s -> s.stream().findFirst())
                                .map(h -> new ItemStack(h.value()))
                                .orElse(new ItemStack(Items.BOOK));
                    } catch (Exception ignored) {
                    }
                } else {
                    toastIcon = firstTask.getIconStack();
                }
            } else {

                toastIcon = new ItemStack(q.getLogo());
            }

            if (toastIcon.isEmpty() || toastIcon.is(Items.BARRIER)) toastIcon = new ItemStack(Items.BOOK);

            PacketDistributor.sendToPlayer(player, new QuestCompletedPayload(q.getTitle(), toastIcon));
        }

        for (QuestChapter ch : results.chapters()) {
            PacketDistributor.sendToPlayer(player, new ChapterCompletedPayload(ch.getName(), new ItemStack(ch.getIcon())));
        }

        if (!results.quests().isEmpty() || !results.chapters().isEmpty()) {
            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
        }

        rebuildActiveQuestCache(player);

        QuestSyncHelper.syncPlayerProgress(player);

        return !results.quests().isEmpty() || !results.chapters().isEmpty();
    }

    private static boolean isQuestUnlocked(Quest quest, PlayerQuestProgress progress) {
        for (String depId : quest.getDependencies()) {
            if (!progress.isQuestComplete(depId)) return false;
        }
        return true;
    }

    public static void handleClaimReward(final ClaimRewardPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

                for (Quest quest : QUEST_MANAGER.getAllQuests()) {
                    for (QuestReward reward : quest.getRewards()) {
                        QuestReward targetToGive = null;
                        QuestReward bundleToClaim = null;

                        if (reward.getId().equals(payload.rewardId())) {
                            targetToGive = reward;
                        } else {

                            for (QuestReward sub : reward.getSubRewards()) {
                                if (sub.getId().equals(payload.rewardId())) {
                                    targetToGive = sub;
                                    bundleToClaim = reward;
                                    break;
                                }
                            }
                        }

                        if (targetToGive != null) {

                            boolean alreadyClaimed = bundleToClaim != null ? progress.isRewardClaimed(bundleToClaim.getId()) : progress.isRewardClaimed(targetToGive.getId());

                            if (progress.isQuestComplete(quest.getId()) && !alreadyClaimed) {

                                RewardGiver.giveReward(player, targetToGive);

                                if (bundleToClaim != null) {
                                    progress.claimReward(bundleToClaim.getId());
                                } else {
                                    progress.claimReward(targetToGive.getId());
                                }

                                if (quest.isRepeatable()) {
                                    boolean allRewardsClaimed = quest.getRewards().stream()
                                            .allMatch(r -> progress.isRewardClaimed(r.getId()));

                                    if (allRewardsClaimed) {
                                        progress.resetQuest(quest.getId());
                                        for (QuestTask task : quest.getTasks()) {
                                            progress.resetTaskProgress(task.getId());
                                        }
                                        for (QuestReward r : quest.getRewards()) {
                                            progress.unclaimReward(r.getId());
                                        }
                                    }
                                }

                                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                                syncAndCheckCompletions(player);
                            }
                            return;
                        }
                    }
                }
            }
        });
    }

    public static void handleRequestImage(final RequestImagePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Path imagePath = QuestManager.getImagesDirectory().resolve(payload.imageId());
                if (Files.exists(imagePath)) {
                    try {
                        byte[] data = Files.readAllBytes(imagePath);
                        PacketDistributor.sendToPlayer(player, new SyncImagePayload(payload.imageId(), data));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }
}