package com.jmane2026.simplyquests.registry;

import com.jmane2026.simplyquests.client.SimplyQuestsClientPacketHandler;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.network.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class QuestNetworkRegistry {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        boolean isClient = FMLEnvironment.getDist() == Dist.CLIENT;

        registrar.playToClient(
                SyncQuestProgressPayload.TYPE,
                SyncQuestProgressPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncProgress : (p, c) -> {
                }
        );

        registrar.playToClient(
                SyncPlayerProgressPayload.TYPE,
                SyncPlayerProgressPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncPlayerProgress : (p, c) -> {
                }
        );

        registrar.playToServer(
                SubmitItemTaskPayload.TYPE,
                SubmitItemTaskPayload.STREAM_CODEC,
                QuestServerEvents::handleSubmitItemTask
        );

        registrar.playToClient(
                QuestCompletedPayload.TYPE,
                QuestCompletedPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleQuestCompleted : (p, c) -> {
                }
        );

        registrar.playToClient(
                ChapterCompletedPayload.TYPE,
                ChapterCompletedPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleChapterCompleted : (p, c) -> {
                }
        );

        registrar.playToServer(
                ToggleCheckboxPayload.TYPE,
                ToggleCheckboxPayload.STREAM_CODEC,
                QuestServerEvents::handleToggleCheckbox
        );

        registrar.playToServer(
                AdminCompletePayload.TYPE,
                AdminCompletePayload.STREAM_CODEC,
                QuestServerEvents::handleAdminComplete
        );

        registrar.playToServer(
                AdminResetPayload.TYPE,
                AdminResetPayload.STREAM_CODEC,
                QuestServerEvents::handleAdminReset
        );

        registrar.playToServer(
                ClaimRewardPayload.TYPE,
                ClaimRewardPayload.STREAM_CODEC,
                QuestServerEvents::handleClaimReward
        );

        registrar.playToClient(
                SyncChapterPayload.TYPE,
                SyncChapterPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncChapter : (p, c) -> {
                }
        );

        registrar.playToServer(
                RequestImagePayload.TYPE,
                RequestImagePayload.STREAM_CODEC,
                QuestServerEvents::handleRequestImage
        );

        registrar.playToClient(
                SyncImagePayload.TYPE,
                SyncImagePayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncImage : (p, c) -> {
                }
        );

        registrar.playToServer(
                UploadImagePayload.TYPE,
                UploadImagePayload.STREAM_CODEC,
                ServerPayloadHandler::handleUploadImage
        );

        registrar.playToServer(
                DeleteImagePayload.TYPE,
                DeleteImagePayload.STREAM_CODEC,
                ServerPayloadHandler::handleDeleteImage
        );
    }
}