package com.jmane2026.simplyquests.network;

import com.jmane2026.simplyquests.client.SimplyQuestsClientPacketHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SimplyQuestsNetworking {

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("simplyquests");

        boolean isClient = FMLEnvironment.getDist() == Dist.CLIENT;

        registrar.playToServer(
                QuestLockPayload.TYPE,
                QuestLockPayload.CODEC,
                ServerPayloadHandler::handleQuestLock
        );

        registrar.playToServer(
                SaveChapterPayload.TYPE,
                SaveChapterPayload.CODEC,
                ServerPayloadHandler::handleSaveChapter
        );

        registrar.playToServer(
                SaveGroupsPayload.TYPE,
                SaveGroupsPayload.CODEC,
                ServerPayloadHandler::handleSaveGroups
        );

        registrar.playToServer(
                DeleteChapterPayload.TYPE,
                DeleteChapterPayload.CODEC,
                ServerPayloadHandler::handleDeleteChapter
        );

        registrar.playToServer(
                DeleteGroupPayload.TYPE,
                DeleteGroupPayload.CODEC,
                ServerPayloadHandler::handleDeleteGroup
        );

        registrar.playToServer(
                DeleteQuestPayload.TYPE,
                DeleteQuestPayload.CODEC,
                ServerPayloadHandler::handleDeleteQuest
        );

        registrar.playToServer(
                DeleteCanvasTextPayload.TYPE,
                DeleteCanvasTextPayload.CODEC,
                ServerPayloadHandler::handleDeleteCanvasText
        );

        registrar.playToClient(
                SyncQuestTreePayload.TYPE,
                SyncQuestTreePayload.CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncQuestTree : (p, c) -> {
                }
        );

        registrar.playToClient(
                SyncOpStatusPayload.TYPE,
                SyncOpStatusPayload.CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSyncOpStatus : (p, c) -> {
                }
        );

        registrar.playToClient(
                SimpleErrorPayload.TYPE,
                SimpleErrorPayload.STREAM_CODEC,
                isClient ? SimplyQuestsClientPacketHandler::handleSimpleError : (p, c) -> {
                }
        );
    }
}