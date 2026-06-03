package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.quest.QuestShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public class QuestShapeRenderer {

    public static void render(QuestShape shape, GuiGraphicsExtractor graphics, int x, int y, int size, int outerColor, int innerColor) {
        if (shape == null) return;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                shape.getTexture(),
                x, y,
                0, 0,
                size, size,
                size, size,
                outerColor
        );

        int borderThickness = 2;
        int innerSize = size - (borderThickness * 2);
        int offset = borderThickness;

        if (innerSize > 0) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    shape.getTexture(),
                    x + offset, y + offset,
                    0, 0,
                    innerSize, innerSize,
                    innerSize, innerSize,
                    innerColor
            );
        }
    }
}