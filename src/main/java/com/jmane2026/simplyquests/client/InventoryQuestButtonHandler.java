package com.jmane2026.simplyquests.client;

import com.jmane2026.simplyquests.SimplyQuests;
import com.jmane2026.simplyquests.client.screen.QuestEditorUI;
import com.jmane2026.simplyquests.client.screen.QuestScreen;
import com.jmane2026.simplyquests.config.SimplyQuestsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = SimplyQuests.MODID, value = Dist.CLIENT)
public class InventoryQuestButtonHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventory) {
            int configX = SimplyQuestsConfig.BOOK_X.get();
            int configY = SimplyQuestsConfig.BOOK_Y.get();
            int screenW = inventory.width;
            int screenH = inventory.height;

            boolean alreadyExists = event.getListenersList().stream().anyMatch(l -> l instanceof QuestBookButton);
            if (alreadyExists) return;

            int x = Math.max(6, Math.min((screenW / 2) + configX, screenW - 22));
            int y = Math.max(6, Math.min((screenH / 2) + configY, screenH - 22));

            event.addListener(new QuestBookButton(x, y, b -> {
                QuestScreen.playClickSound();
                Minecraft.getInstance().setScreen(new QuestScreen());
            }));
        }
    }

    private static class QuestBookButton extends Button {
        private static final ItemStack BOOK_STACK = new ItemStack(Items.BOOK);
        private boolean isDragging = false;
        private boolean wasDragged = false;
        private int dragOffsetX = 0;
        private int dragOffsetY = 0;
        private final OnPress onPressListener;
        private int startX, startY;

        public QuestBookButton(int x, int y, OnPress onPress) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
            this.onPressListener = onPress;
            this.setTooltip(Tooltip.create(Component.literal("Open Quests")));
            this.startX = x;
            this.startY = y;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
            if (event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
                this.isDragging = true;
                this.wasDragged = false;
                this.startX = getX();
                this.startY = getY();
                this.dragOffsetX = (int) event.x() - getX();
                this.dragOffsetY = (int) event.y() - getY();
                return true;
            }
            return super.mouseClicked(event, doubleClicked);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (this.isDragging) {
                long windowHandle = GLFW.glfwGetCurrentContext();
                if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS) {
                    int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

                    int newX = Math.max(6, Math.min(mouseX - dragOffsetX, screenW - 22));
                    int newY = Math.max(6, Math.min(mouseY - dragOffsetY, screenH - 22));

                    this.setX(newX);
                    this.setY(newY);

                    if (Math.abs(newX - startX) > 2 || Math.abs(newY - startY) > 2) {
                        this.wasDragged = true;
                    }
                } else {
                    if (!wasDragged) {
                        this.onPressListener.onPress(this);
                    } else {
                        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                        SimplyQuestsConfig.BOOK_X.set(getX() - (screenW / 2));
                        SimplyQuestsConfig.BOOK_Y.set(getY() - (screenH / 2));
                        SimplyQuestsConfig.SPEC.save();
                    }
                    this.isDragging = false;
                    this.wasDragged = false;
                }
            }

            graphics.item(BOOK_STACK, getX(), getY());

            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x40FFFFFF);
            }

            if (QuestScreen.hasAnyUnclaimedRewards()) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON,
                        getX() + 10, getY() - 2, 0.0f, 0.0f, 8, 8, 8, 8);
            }
        }
    }
}