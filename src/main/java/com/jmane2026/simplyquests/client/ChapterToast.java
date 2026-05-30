package com.jmane2026.simplyquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ChapterToast implements Toast {
    private final ItemStack icon;
    private Visibility visibility = Visibility.SHOW;

    public ChapterToast(ItemStack icon) {
        this.icon = icon;
    }

    @Override
    public void update(ToastManager toastManager, long timeSinceLastVisible) {
        this.visibility = timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeSinceLastVisible) {
        // 1. Draw semi-transparent dark background
        graphics.fill(0, 0, 130, 32, 0xCC111115);
        // 2. Draw white border
        graphics.outline(0, 0, 130, 32, 0xFFFFFFFF);

        // 3. Render "Chapter Completed!" header (Yellow)
        graphics.text(font, Component.literal("Chapter Completed!"), 32, 12, 0xFFFFFF00);

        // 5. Render the Chapter Icon
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