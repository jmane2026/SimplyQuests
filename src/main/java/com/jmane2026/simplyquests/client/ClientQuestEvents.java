package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix3x2f;
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
        NeoForge.EVENT_BUS.addListener(ClientQuestEvents::onScreenRender);
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

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) {
            if (QuestScreen.hasAnyClaimableRewards()) {
                // 1. Iterate through all widgets on the screen
                for (GuiEventListener child : inv.children()) {
                    // 2. Identify our button by its class name (as seen in your debug logs)
                    if (child.getClass().getSimpleName().equals("QuestBookButton") && child instanceof AbstractWidget widget) {
                        int badgeSize = 8;

                        // 3. Anchor the badge to the ACTUAL top-right corner of the found widget
                        // This works regardless of docking, guiLeft, or config math.
                        int badgeX = widget.getX() + widget.getWidth() - 7;
                        int badgeY = widget.getY() - 1;

                        event.getGuiGraphics().pose().pushMatrix();
                        
                        // Apply the 2D matrix translation as required by your environment mappings
                        event.getGuiGraphics().pose().translate(0.0f, 0.0f, new Matrix3x2f());

                        // 4. Render the badge
                        event.getGuiGraphics().blit(
                                RenderPipelines.GUI_TEXTURED,
                                QuestEditorUI.CLAIM_ICON,
                                badgeX, badgeY,
                                0.0f, 0.0f,
                                badgeSize, badgeSize, badgeSize, badgeSize
                        );

                        event.getGuiGraphics().pose().popMatrix();

                        break; // Stop searching once the button is found and decorated
                    }
                }
            }
        }
    }

    private static void onToastAdd(ToastAddEvent event) {
        // This blocks vanilla advancement popups from appearing
        if (event.getToast() instanceof AdvancementToast) {
            event.setCanceled(true);
        }
    }
}