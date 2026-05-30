package com.jmane2026.simplyquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class QuestToast implements Toast {
    private final String title;
    private final ItemStack icon;
    private Visibility visibility = Visibility.SHOW;

    public QuestToast(String title, ItemStack icon) {
        this.title = title;
        this.icon = icon;
    }

    @Override
    public void update(ToastManager toastManager, long timeSinceLastVisible) {
        // Update the visibility state based on time
        this.visibility = timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeSinceLastVisible) {
        // 1. Draw a semi-transparent dark background (0xCC is ~80% opacity)
        graphics.fill(0, 0, 130, 32, 0xCC111115);

        // 2. Draw a clean white border to define the toast shape
        graphics.outline(0, 0, 130, 32, 0xFFFFFFFF);

        // 3. Render the centered "Quest Completed!" text
        graphics.text(font, Component.literal("Quest Completed!"), 32, 12, 0xFFFFFF00);

        // 4. Render the icon (No pipeline conflict since we aren't using GUI_TEXTURED)
        graphics.fakeItem(icon, 8, 8);
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public int width() {
        return 130;
    }
}