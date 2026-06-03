package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.client.screen.QuestScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

public class ClientQuestEvents {

    public static final KeyMapping OPEN_QUEST_KEY = new KeyMapping(
            "key.simplyquests.open",
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.MISC
    );

    public static void initClient(IEventBus modBus) {
        modBus.addListener(ClientQuestEvents::onRegisterKeyMappings);

        NeoForge.EVENT_BUS.addListener(ClientQuestEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClientQuestEvents::onToastAdd);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_QUEST_KEY);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.level != null && mc.screen == null) {
                if (OPEN_QUEST_KEY.consumeClick()) {
                    mc.setScreen(new QuestScreen());
                }
            }
        }
    }

    private static void onToastAdd(ToastAddEvent event) {
        if (event.getToast() instanceof AdvancementToast) {
            event.setCanceled(true);
        }
    }
}