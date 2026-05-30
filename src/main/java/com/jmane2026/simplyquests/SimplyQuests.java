package com.jmane2026.simplyquests;

import com.jmane2026.simplyquests.client.ClientQuestEvents;
import com.jmane2026.simplyquests.commands.QuestCommands;
import com.jmane2026.simplyquests.config.SimplyQuestsConfig;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.network.SimplyQuestsNetworking;
import com.jmane2026.simplyquests.registry.QuestAttachmentRegistry;
import com.jmane2026.simplyquests.registry.QuestNetworkRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(SimplyQuests.MODID)
public class SimplyQuests {
    public static final String MODID = "simplyquests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SimplyQuests(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        QuestAttachmentRegistry.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(QuestNetworkRegistry::register);
        NeoForge.EVENT_BUS.addListener(QuestCommands::register);
        NeoForge.EVENT_BUS.register(QuestServerEvents.class);
        modContainer.registerConfig(ModConfig.Type.CLIENT, SimplyQuestsConfig.SPEC);
        modEventBus.addListener(SimplyQuestsNetworking::register);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ClientQuestEvents.initClient(modEventBus);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
