package com.jmane2026.simplyquests.events;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestManager;
import com.jmane2026.simplyquests.network.*;
import com.mojang.logging.LogUtils;
import com.jmane2026.simplyquests.quest.QuestGlobalState;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import com.jmane2026.simplyquests.util.QuestSyncHelper;
import com.jmane2026.simplyquests.util.RewardGiver;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class QuestServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final QuestManager QUEST_MANAGER = new QuestManager();

    // Optimization: Cache of quests that are currently "Eligible" for item auto-sync per player
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
            // 1. Sync the Quest Tree (Groups and Chapters) so the sidebar isn't empty
            PacketDistributor.sendToPlayer(player, new SyncQuestTreePayload(
                    new ArrayList<>(QUEST_MANAGER.getChapters().values()),
                    QUEST_MANAGER.getGroups()
            ));

            // Centralize the login sync using our helper
            QuestSyncHelper.syncPlayerProgress(player);
            rebuildActiveQuestCache(player);
            syncAndCheckCompletions(player);
            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

            // Sync OP status for Edit Mode security
            NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
            boolean isOp = player.level().getServer().getPlayerList().isOp(identity);
            PacketDistributor.sendToPlayer(player, new SyncOpStatusPayload(isOp, progress.isEditMode()));
        }
    }

    /**
     * Updates the edit mode for a specific player and persists it to their NBT.
     */
    public static void updateEditModeAndSync(ServerPlayer player, boolean enabled) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
        progress.setEditMode(enabled);
        player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // Save to NBT

        NameAndId identity = new NameAndId(player.getGameProfile().id(), player.getGameProfile().name());
        boolean isOp = player.level().getServer().getPlayerList().isOp(identity);
        PacketDistributor.sendToPlayer(player, new SyncOpStatusPayload(isOp, enabled));
    }

    /**
     * Rebuilds the item cache for all online players. 
     * Crucial to call this after any quest tree structure changes (saves/renames).
     */
    public static void refreshAllCaches(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            rebuildActiveQuestCache(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Memory Cleanup: Prevent the map from growing indefinitely
        ACTIVE_ITEM_CACHE.remove(event.getEntity().getUUID());
    }

    public static void handleSubmitItemTask(final SubmitItemTaskPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Quest quest = QUEST_MANAGER.getAllQuests().stream()
                        .filter(q -> q.getId().equals(payload.questId()))
                        .findFirst().orElse(null);

                PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
                if (quest == null || progress.isQuestComplete(quest.getId()) || !isQuestUnlocked(quest, progress)) return;

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

                // 1. Check the mouse cursor (Carried Stack)
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && itemMatcher.test(carried)) {
                    int toTake = Math.min(carried.getCount(), needed - foundCount);
                    carried.shrink(toTake);
                    foundCount += toTake;
                }

                // 2. Scan and remove from main inventory slots
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
                    player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
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

                if (payload.taskId().isPresent()) {
                    // Complete/Reset specific task
                    QuestTask task = quest.getTasks().stream()
                            .filter(t -> t.getId().equals(payload.taskId().get()))
                            .findFirst().orElse(null);
                    if (task != null) {
                        progress.setTaskAmount(task.getId(), payload.complete() ? task.getRequiredAmount() : 0);
                        player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
                    }
                } else {
                    // Complete/Reset whole quest
                    for (QuestTask task : quest.getTasks()) {
                        progress.setTaskAmount(task.getId(), payload.complete() ? task.getRequiredAmount() : 0);
                    }
                    if (!payload.complete()) {
                        // If resetting the whole quest, clear its completion status
                        progress.resetQuest(quest.getId());
                        // Also clear all task progress for this quest
                        for (QuestTask task : quest.getTasks()) {
                            progress.resetTaskProgress(task.getId());
                        }
                        // Also clear all claimed rewards for this quest
                        for (QuestReward reward : quest.getRewards()) {
                            progress.unclaimReward(reward.getId());
                        }
                        // If this quest was part of a completed chapter, reset the chapter completion as well
                        // This ensures the chapter completed toast can be re-triggered if all quests are reset
                        QuestChapter chapter = QUEST_MANAGER.getChapters().values().stream()
                                .filter(c -> c.getName().equals(quest.getChapterName()))
                                .findFirst().orElse(null);
                        if (chapter != null) {
                            progress.resetChapter(chapter.getName());
                        }
                    }
                    player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
                }
                syncAndCheckCompletions(player);
                
                // FIX: If this was a reset, immediately re-scan inventory so existing items are counted
                if (!payload.complete()) processItemAcquisition(player, ItemStack.EMPTY);

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

                // 1. Identify which chapters need resetting
                if (groupReset) {
                    String targetGroupId = payload.groupName().get();
                    // We compare against the groupName field, which we ensured is sanitized during saves
                    targets = chapters.stream().filter(c -> c.getGroupName().equals(targetGroupId)).toList();
                } else if (chapterReset) {
                    String targetChapterId = payload.chapterName().get();
                    targets = chapters.stream().filter(c -> c.getName().equals(targetChapterId)).toList();
                }

                // 2. Reset progress for all quests in those chapters
                for (QuestChapter ch : targets) {
                    progress.resetChapter(ch.getName());
                    for (Quest q : ch.getQuests()) {
                        progress.resetQuest(q.getId());
                        for (QuestTask task : q.getTasks()) {
                            progress.resetTaskProgress(task.getId());
                        }
                        for (QuestReward reward : q.getRewards()) {
                            progress.unclaimReward(reward.getId());
                        }
                    }
                }

                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                syncAndCheckCompletions(player);
                
                // FIX: Immediately re-scan inventory after a group/chapter reset
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
                // Allow toggling unless the quest doesn't exist or is locked
                if (quest == null || !isQuestUnlocked(quest, progress)) return;

                QuestTask task = quest.getTasks().stream()
                        .filter(t -> t.getId().equals(payload.taskId()))
                        .findFirst().orElse(null);

                if (task == null || task.getType() != QuestTask.TaskType.CHECKBOX) return;
                
                // Only allow completion (0 -> required), do not allow "uncompleting" via player click
                if (progress.getTaskAmount(task.getId()) >= task.getRequiredAmount()) return;

                int current = progress.getTaskAmount(task.getId());
                progress.setTaskAmount(task.getId(), current >= task.getRequiredAmount() ? 0 : task.getRequiredAmount());
                player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE

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
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
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
        // Run checks every 20 ticks (1 second) to save CPU
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % 20 != 0) return;

            List<Quest> allQuests = QUEST_MANAGER.getAllQuests();
            
            boolean progressMade = false;
            PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
            
            // In 26.1, Biomes are Holders. We unwrap the key and map it to get the Identifier directly.
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
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
                            progressMade = true;
                        }
                    } else if (task.getType() == QuestTask.TaskType.OBSERVE) {
                        // Perform a hybrid raytrace to detect both Blocks/Fluids and Entities (Range: 20 blocks)
                        double range = 20.0;
                        float partialTicks = 0.0f;
                        
                        // 1. Check for Blocks and Fluids (liquids enabled)
                        HitResult blockHit = player.pick(range, partialTicks, true);
                        
                        // 2. Check for Entities along the same look vector
                        Vec3 eyePos = player.getEyePosition(1.0f);
                        Vec3 viewVec = player.getViewVector(partialTicks);
                        Vec3 reachVec = eyePos.add(viewVec.scale(range));
                        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0, 1.0, 1.0);
                        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePos, reachVec, searchBox, (e) -> !e.isSpectator() && e.isPickable(), range * range);

                        String target = task.getTargetId();
                        boolean isTag = target.startsWith("#");
                        boolean matched = false;
                        String debugHitId = "Nothing";

                        // 1. Check Entities
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

                        // 2. Check Blocks/Fluids (If entity didn't already match)
                        if (!matched && blockHit.getType() == HitResult.Type.BLOCK) {
                            var state = player.level().getBlockState(((BlockHitResult)blockHit).getBlockPos());
                            debugHitId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            
                            if (isTag) {
                                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(target.substring(1)));
                                if (state.is(tagKey)) matched = true;
                            } else if (target.equals(debugHitId)) {
                                matched = true;
                            }
                        }
                        
                        // FIX: Actually apply the progress if matched
                        if (matched) {
                            progress.setTaskAmount(task.getId(), 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
                            progressMade = true;
                        }
                    } else if (task.getType() == QuestTask.TaskType.LOCATION) {
                        double distSq = player.blockPosition().distSqr(new net.minecraft.core.Vec3i(task.getTargetX(), task.getTargetY(), task.getTargetZ()));
                        // If player is within 4 blocks of the target coordinate
                        if (distSq <= 16.0) {
                            progress.setTaskAmount(task.getId(), 1);
                            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
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
        if (event.getPlayer() instanceof ServerPlayer player) processItemAcquisition(player, event.getItemEntity().getItem());
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

    /**
     * Re-calculates which quests have non-consuming item tasks and are currently unlocked for the player.
     */
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
        // The 'stack' argument is our heartbeat. We ignore its type and perform a full inventory reconciliation.
        Inventory inventory = player.getInventory();
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);

        Map<Item, Integer> inventoryCounts = new HashMap<>();

        // 1. Build a map of current inventory counts (Source of Truth)
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (!invStack.isEmpty()) {
                Item item = invStack.getItem();
                inventoryCounts.put(item, inventoryCounts.getOrDefault(item, 0) + invStack.getCount());
            }
        }

        // 1b. Include the item currently held on the mouse cursor (The "Carried" stack)
        // This ensures items manually picked up from crafting/containers are detected immediately.
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            Item item = carried.getItem();
            inventoryCounts.put(item, inventoryCounts.getOrDefault(item, 0) + carried.getCount());
        }

        List<Quest> activeQuests = ACTIVE_ITEM_CACHE.getOrDefault(player.getUUID(), List.of());
        boolean repeat;

        // 2. Process tasks and handle chain reactions
        do {
            repeat = false;
            boolean progressMadeThisPass = false;

            for (Quest quest : activeQuests) {
                // We don't need isUnlocked check here anymore as the cache only contains unlocked quests

                for (QuestTask task : quest.getTasks()) {
                    // Only auto-sync non-consuming ITEM tasks
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
                        player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress); // FORCE NBT SAVE
                        progressMadeThisPass = true;
                    }
                }
            }
            if (progressMadeThisPass) {
                if (syncAndCheckCompletions(player)) repeat = true;
            }
        } while (repeat);
    }

    /**
     * Centralized logic to sync tasks and trigger Quest/Chapter completion toasts.
     */
    private static boolean syncAndCheckCompletions(ServerPlayer player) {
        PlayerQuestProgress progress = player.getData(QuestAttachmentRegistry.PLAYER_PROGRESS);
        QuestManager.CompletionResults results = QUEST_MANAGER.updateQuestStates(progress);

        // 1. Send Toasts for Quests
        for (Quest q : results.quests()) {
            ItemStack toastIcon = ItemStack.EMPTY;

            // FIX: If "Use Task Icon" is enabled, we MUST resolve the task's icon first,
            // ignoring any manual icon that might be saved in the quest's logo field.
            if (q.isUseTaskIcon() && !q.getTasks().isEmpty()) {
                QuestTask firstTask = q.getTasks().get(0);
                if (firstTask.getTargetId().startsWith("#")) {
                    try {
                        Identifier loc = Identifier.parse(firstTask.getTargetId().substring(1));
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);
                        // Pick the first item in the tag as the representative toast icon
                        toastIcon = BuiltInRegistries.ITEM.getTags()
                                .filter(s -> s.key().equals(tagKey))
                                .findFirst()
                                .flatMap(s -> s.stream().findFirst())
                                .map(h -> new ItemStack(h.value()))
                                .orElse(new ItemStack(Items.BOOK));
                    } catch (Exception ignored) {}
                } else {
                    toastIcon = firstTask.getIconStack();
                }
            } else {
                // If not using task icon, use the manually assigned logo
                toastIcon = new ItemStack(q.getLogo());
            }

            // Final check: If the icon is still empty or a barrier (failed parse), use a Book as a fallback
            if (toastIcon.isEmpty() || toastIcon.is(Items.BARRIER)) toastIcon = new ItemStack(Items.BOOK);

            PacketDistributor.sendToPlayer(player, new QuestCompletedPayload(q.getTitle(), toastIcon));
        }

        // 2. Send Toasts for Chapters
        for (QuestChapter ch : results.chapters()) {
            PacketDistributor.sendToPlayer(player, new ChapterCompletedPayload(ch.getName(), new ItemStack(ch.getIcon())));
        }

        // If any completions occurred, we MUST save the updated sets to the player's NBT
        if (!results.quests().isEmpty() || !results.chapters().isEmpty()) {
            player.setData(QuestAttachmentRegistry.PLAYER_PROGRESS, progress);
        }

        // FIX: Always rebuild the cache here. Resets (which have empty results) change eligibility 
        // just as much as completions do.
        rebuildActiveQuestCache(player);

        // 3. Efficient Global Sync: Tell the client exactly what our NBT looks like
        QuestSyncHelper.syncPlayerProgress(player);

        return !results.quests().isEmpty() || !results.chapters().isEmpty();
    }

    /**
     * Helper to verify if all dependencies for a quest are met for a specific player.
     */
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
                
                // We need to find the reward and determine if it's a top-level reward or a sub-reward choice
                for (Quest quest : QUEST_MANAGER.getAllQuests()) {
                    for (QuestReward reward : quest.getRewards()) {
                        QuestReward targetToGive = null;
                        QuestReward bundleToClaim = null;

                        // 1. Check if the ID matches the top-level reward
                        if (reward.getId().equals(payload.rewardId())) {
                            targetToGive = reward;
                        } else {
                            // 2. Check if the ID matches a choice inside this reward bundle
                            for (QuestReward sub : reward.getSubRewards()) {
                                if (sub.getId().equals(payload.rewardId())) {
                                    targetToGive = sub;
                                    bundleToClaim = reward; // If a sub-reward is picked, the parent bundle is finished
                                    break;
                                }
                            }
                        }

                        if (targetToGive != null) {
                            // Validation: Check if quest is complete and the reward (or its parent bundle) isn't claimed yet
                            boolean alreadyClaimed = bundleToClaim != null ? progress.isRewardClaimed(bundleToClaim.getId()) : progress.isRewardClaimed(targetToGive.getId());

                            if (progress.isQuestComplete(quest.getId()) && !alreadyClaimed) {
                                // 3. Grant the reward (Item, XP, or Command)
                                RewardGiver.giveReward(player, targetToGive);

                                // 4. Mark IDs as claimed in NBT:
                                // If a choice was made, mark the parent bundle as claimed.
                                // Otherwise, mark the reward itself as claimed.
                                if (bundleToClaim != null) {
                                    progress.claimReward(bundleToClaim.getId());
                                } else {
                                    progress.claimReward(targetToGive.getId());
                                }

                                // --- REPEATABLE RESET LOGIC ---
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
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}