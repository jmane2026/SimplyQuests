package com.jmane2026.simplyquests.registry;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.player.PlayerQuestProgress;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class QuestAttachmentRegistry {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SimplyQuests.MODID);

    public static final Supplier<AttachmentType<PlayerQuestProgress>> PLAYER_PROGRESS =
            ATTACHMENT_TYPES.register("player_progress", () -> AttachmentType.builder(PlayerQuestProgress::new)
                    .serialize(PlayerQuestProgress.CODEC)
                    .copyOnDeath()
                    .build());
}