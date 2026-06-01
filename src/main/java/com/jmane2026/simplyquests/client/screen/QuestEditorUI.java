package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.quest.Quest;
import com.jmane2026.simplyquests.quest.QuestReward;
import com.jmane2026.simplyquests.quest.QuestShape;
import com.jmane2026.simplyquests.quest.QuestTask;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import com.jmane2026.simplyquests.SimplyQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import java.util.*;
import java.util.List;

import static java.awt.Color.HSBtoRGB;
import static java.awt.Color.RGBtoHSB;

public class QuestEditorUI {
    Font font = Minecraft.getInstance().font;
    private static final Identifier CHECKMARK_TEXTURE = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/shapes/checkmark.png");
    public static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/settings.png");
    public static final Identifier CLAIM_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/shapes/claim.png");
    public static final Identifier CLAIM_ALL_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/claim_all.png");
    public static final Identifier FLOW_ARROW = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/flow_arrow.png");
    public static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/close.png");
    public static final Identifier SCALE_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/scale.png");
    public static final Identifier ROTATE_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/rotate.png");
    public static final Identifier ALPHA_ICON = Identifier.fromNamespaceAndPath("simplyquests", "textures/gui/alpha.png");

    private final String[] labels = {"Icon", "Title", "Sub-Title", "Description", "Shape", "Size", "Optional", "Repeatable", "Dependencies"};

    public boolean isIconPickerOpen = false;
    public boolean isDependencyPickerOpen = false;
    public boolean isShapePickerOpen;
    public boolean isRewardModeOpen = false;
    public boolean isSubTitleOpen = false;
    public boolean isDescriptionOpen = false;
    public boolean isTitleOpen = false;
    public boolean isRemoveDependencyMode = false;
    public boolean isNameOpen = false;
    public boolean isTaskMode = false;
    public boolean isTargetPickerOpen = false;
    public boolean isColorPickerOpen = false;
    public boolean isTypePickerOpen = false;
    public QuestTask.TaskType currentTaskType = QuestTask.TaskType.CHECKBOX;
    public QuestReward.RewardType currentRewardType = QuestReward.RewardType.ITEM;
    public boolean isHexEditing = false;
    public boolean isQuantityOpen = false;
    public boolean isXOpen = false;
    public boolean isYOpen = false;
    public boolean isZOpen = false;

    // Pre-cache registries for faster searching
    public static List<Item> availableIcons = BuiltInRegistries.ITEM.stream()
            .filter(item -> item != net.minecraft.world.item.Items.AIR)
            .toList();

    // Cache for 3D entity models to prevent lag during rendering
    private final Map<EntityType<?>, LivingEntity> entityCache = new HashMap<>();

    public double scrollOffset = 0;
    public String searchQuery = "";
    public String hexQuery = "";
    public int hexCursorIndex = 0;
    public int hexTextScrollOffset = 0;
    public int cursorIndex = 0; // Where the typing cursor is
    public int textScrollOffset = 0; // How much the text is shifted to the left
    public int selectionStart = -1; // -1 means nothing selected
    public int selectionEnd = -1;
    public static final int PICKER_COLUMNS = 5;

    // --- PERFORMANCE CACHE ---
    private String lastQuery = null;
    private QuestTask.TaskType lastType = null;
    private List<Item> cachedIcons = new ArrayList<>();
    private List<String> cachedTargets = new ArrayList<>();
    private List<Quest> cachedDependencies = new ArrayList<>();


    public record PickerBounds(int x, int y, int w, int h, int barHeight) {}

    public PickerBounds getPickerBounds(int panelX, int panelY, int panelHeight) {
        int columns = 5;
        int cellSize = 18;
        int searchBarHeight = 16;
        int pickerW = (columns * cellSize) + 10;
        // Make height match the panel exactly
        int pickerH = panelHeight;
        int pickerX = panelX + 300 + 5; // 300 is your panel width
        int pickerY = panelY;
        return new PickerBounds(pickerX, pickerY, pickerW, pickerH, searchBarHeight);
    }

    public boolean isPickerOpen() {
        return this.isIconPickerOpen ||
                this.isDependencyPickerOpen ||
                this.isShapePickerOpen ||
                this.isTypePickerOpen ||
                this.isTargetPickerOpen ||
                this.isTitleOpen ||
                this.isSubTitleOpen ||
                this.isDescriptionOpen ||
                this.isNameOpen ||
                this.isQuantityOpen ||
                this.isXOpen ||
                this.isYOpen ||
                this.isZOpen;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int panelX, int panelY, int panelWidth, int panelHeight, Quest quest, List<Quest> allQuests) {

        // --- 1. LAYOUT CONSTANTS ---
        int leftMargin = panelX + 15;
        int dividerX = panelX + 90;
        int valueX = dividerX + 10;
        int rowHeight = 22;
        int startY = panelY + 30; // Standardized to match Task/Reward editors

        // --- 1b. HEADER ---
        graphics.text(font, Component.literal("Quest Editor"), leftMargin, panelY + 10, QuestScreen.COL_TEXT);

        // Live Preview Node in Header (Top Right)
        int stateColor = QuestScreen.getStateColor(quest.getState());
        QuestShapeRenderer.render(quest.getShape(), graphics, panelX + panelWidth - 35, panelY + 4, 16, stateColor, QuestScreen.COL_UI_BG);
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + panelWidth - 35 + 8.0f, panelY + 4 + 8.0f);
        graphics.pose().scale(1.0f, 1.0f); // FIX: Removed redundant scale, renderCenteredItem handles padding
        drawQuestIcon(graphics, quest, -8, -8, 16);
        graphics.pose().popMatrix();

        graphics.horizontalLine(panelX, panelX + panelWidth - 1, panelY + 22, QuestScreen.COL_UI_BORDER);

        // --- 2. DIVIDERS ---
        // Spans from startY to the button area at the bottom
        int footerDividerY = panelY + panelHeight - 24;

        // FIX: Vertical line starts at the header divider (22) and ends at the footer divider
        graphics.verticalLine(dividerX, panelY + 22, footerDividerY, QuestScreen.COL_UI_BORDER);
        // Horizontal footer line
        graphics.horizontalLine(panelX, panelX + panelWidth - 1, footerDividerY, QuestScreen.COL_UI_BORDER);

        // --- 3. RENDER ROWS ---
        int editBtnWidth = 45;
        int editBtnRightEdge = panelX + panelWidth - 15; // Right-aligned Edit button
        int editBtnLeft = editBtnRightEdge - editBtnWidth;

        for (int i = 0; i < labels.length; i++) {
            int rowY = startY + (i * rowHeight);

            // Label
            graphics.text(font, labels[i] + ":", leftMargin, rowY + 2, QuestScreen.COL_TEXT);

            String value = getQuestValue(quest, labels[i]);
            int valueColor = 0xFFFFFFFF; // Default white
            String editText = "Edit";

            // Check if this row is a boolean type
            if (labels[i].equals("Optional") || labels[i].equals("Repeatable")) {
                // Use state colors for boolean feedback
                valueColor = value.equalsIgnoreCase("true") ? QuestScreen.COL_STATE_COMPLETED : QuestScreen.COL_ERROR;
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Toggle", QuestScreen.COL_BUTTON_BASE);
                graphics.text(font, value, valueX, rowY + 2, valueColor);
            } else if (labels[i].equals("Icon")) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(valueX + 8.0f, rowY - 2 + 8.0f);
                graphics.pose().scale(1.0f, 1.0f); // FIX: Removed redundant scale
                drawQuestIcon(graphics, quest, -8, -8, 16);
                graphics.pose().popMatrix();
                // FIX: Dynamically determine the label for the Icon row
                String itemName = "None";
                if (quest.isUseTaskIcon()) {
                    QuestTask provider = quest.getTasks().stream()
                            .filter(t -> t.getIconStack().getItem() == quest.getLogo() || (t.getType() == QuestTask.TaskType.CHECKBOX && quest.getLogo() == Items.AIR))
                            .findFirst().orElse(null);
                    itemName = (provider != null) ? "Task: " + provider.getTargetDisplayName() : "Task Icon";
                } else {
                    itemName = new ItemStack(quest.getLogo()).getHoverName().getString();
                }
                graphics.text(this.font, itemName, valueX + 20, rowY + 2, QuestScreen.COL_TEXT);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Change", QuestScreen.COL_BUTTON_BASE);
            } else if (labels[i].equals("Dependencies")) {
                String count = getQuestValue(quest, "Dependencies");
                graphics.text(font, "(" + count + ")", valueX, rowY + 2, QuestScreen.COL_TEXT);

                int btnW = 45;
                int btnH = 14;
                int gap = 5;
                int addBtnX = editBtnRightEdge - btnW;
                int delBtnX = addBtnX - btnW - gap;

                drawButton(graphics, mouseX, mouseY, addBtnX, rowY, btnW, btnH, "Add", QuestScreen.COL_BUTTON_BASE);
                drawButton(graphics, mouseX, mouseY, delBtnX, rowY, btnW, btnH, "Remove", QuestScreen.COL_BUTTON_BASE);
            } else if (labels[i].equals("Shape")) {
                QuestShape currentShape = quest.getShape();
                int color = QuestScreen.getStateColor(quest.getState());
                int shapeSize = 12;
                QuestShapeRenderer.render(currentShape, graphics, valueX, rowY, shapeSize, color, QuestScreen.COL_UI_BG);

                graphics.text(font, currentShape.name(), valueX + 16, rowY + 2, QuestScreen.COL_TEXT);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Change", QuestScreen.COL_BUTTON_BASE);
            } else if (labels[i].equals("Size")) {
                // --- SLIDER LOGIC ---
                int sliderX = valueX;
                int sliderW = editBtnLeft - valueX - 10;
                int sliderY = rowY + 6;

                float min = 24f; // "1.0"
                float max = 240f; // "10.0"
                float current = quest.getSize();
                float progress = Math.max(0, Math.min(1, (current - min) / (max - min)));

                // Track
                graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 2, QuestScreen.COL_INPUT_BG);
                graphics.fill(sliderX, sliderY, sliderX + (int)(progress * sliderW), sliderY + 2, QuestScreen.COL_SLIDER_TRACK);

                // Handle
                int handleX = sliderX + (int)(progress * sliderW);
                graphics.fill(handleX - 2, sliderY - 3, handleX + 2, sliderY + 5, QuestScreen.COL_UI_BORDER);
                int labelWidth = font.width(labels[i] + ":");
                graphics.text(font, String.format("%.1fx", current / 24f), leftMargin + labelWidth + 5, rowY + 2, QuestScreen.COL_TEXT);
            } else if (labels[i].equals("Title")) {
                int boxWidth = editBtnLeft - valueX - 5; // Fill the space before the button
                int boxHeight = 14;

                // 2. Logic to switch between display and edit mode
                if (isTitleOpen) {
                    // Draw the editable box
                    drawEditableText(graphics, searchQuery, valueX, rowY, boxWidth, boxHeight, false);
                    drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Submit", QuestScreen.COL_BUTTON_BASE);
                } else {
                    int maxTitleWidth = editBtnLeft - valueX - 5;
                    String displayTitle = truncate(quest.getTitle(), maxTitleWidth);
                    graphics.text(font, displayTitle, valueX, rowY + 2, QuestScreen.COL_TEXT);

                    drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Edit", QuestScreen.COL_BUTTON_BASE);
                }
            } else if (labels[i].equals("Sub-Title") || labels[i].equals("Description")) {
                int maxValWidth = editBtnLeft - valueX - 5;
                String displayVal = truncate(value, maxValWidth);
                graphics.text(font, displayVal, valueX, rowY + 2, QuestScreen.COL_TEXT);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Edit", QuestScreen.COL_BUTTON_BASE);
            } else {
                // Size or other simple values
                graphics.text(font, value, valueX, rowY + 2, valueColor);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Edit", QuestScreen.COL_BUTTON_BASE);
            }
        }

        // --- 4. ALIGNED CANCEL/SAVE BUTTONS ---
        int btnY = panelY + panelHeight - 20;
        int btnWidth = 45;
        int saveRightEdge = editBtnRightEdge; // Align right edge with Edit buttons
        int saveLeft = saveRightEdge - btnWidth;
        int cancelLeft = saveLeft - 50;

        int cancelColor = getButtonColor(mouseX, mouseY, cancelLeft, btnY, btnWidth, 14, QuestScreen.COL_BUTTON_BASE);
        int saveColor = getButtonColor(mouseX, mouseY, saveLeft, btnY, btnWidth, 14, QuestScreen.COL_BUTTON_BASE);

        // Cancel
        graphics.fill(cancelLeft, btnY, cancelLeft + btnWidth, btnY + 14, cancelColor);
        graphics.text(font, "Cancel", cancelLeft + 5, btnY + 3, QuestScreen.COL_TEXT);

        // Save
        graphics.fill(saveLeft, btnY, saveRightEdge, btnY + 14, saveColor);
        graphics.text(font, "Save", saveLeft + 8, btnY + 3, QuestScreen.COL_TEXT);

        if (isDependencyPickerOpen) {
            renderPickerFrame(graphics, getPickerBounds(panelX, panelY, panelHeight), true, searchQuery, () -> {
                PickerBounds b = getPickerBounds(panelX, panelY, panelHeight);
                List<Quest> filteredQuests = getCachedDependencies(allQuests, quest);
                Quest hoveredQuest = null;

                // 1. Define the uniform height
                int itemRowHeight = 16;

                // Inset scissor by 1px on left, right, and bottom to protect the border
                graphics.enableScissor(b.x() + 1, b.y() + b.barHeight(), b.x() + b.w() - 1, b.y() + b.h() - 1);

                for (int i = 0; i < filteredQuests.size(); i++) {
                    Quest q = filteredQuests.get(i);

                    // 2. Consistent Y calculation
                    int rowY = (b.y() + b.barHeight() + 5) + (i * itemRowHeight) - (int)scrollOffset;

                    // 3. Hover Detection (Matching the rendered area)
                    if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= rowY && mouseY <= rowY + itemRowHeight) {
                        graphics.fill(b.x(), rowY, b.x() + b.w(), rowY + itemRowHeight, QuestScreen.COL_HOVER_MENU);
                        hoveredQuest = q;
                    }

                    // 4. Render Icon and Text
                    float scale = 0.7f;
                    // Center the icon vertically in the 16px row (rowY + 1)
                    int iconY = (int)((rowY + 1) / scale);

                    graphics.pose().pushMatrix();
                    graphics.pose().scale(scale, scale);

                    // Scale the item inside the list row for better visibility
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(((b.x() + 4) / scale) + 8.0f, iconY + 8.0f);
                    graphics.pose().scale(1.0f, 1.0f); // FIX: Removed redundant scale
                    drawQuestIcon(graphics, q, -8, -8, 16);
                    graphics.pose().popMatrix();

                    // Centered text vertical alignment (rowY + 3)
                    graphics.text(font, q.getTitle(), (int)((b.x() + 18) / scale), (int)((rowY + 3) / scale), QuestScreen.COL_TEXT);

                    graphics.pose().popMatrix();
                }
                graphics.disableScissor();

                if (hoveredQuest != null) {
                    // Build detailed path tooltip: Group > Chapter > Quest
                    String tooltipText = "";
                    var manager = QuestServerEvents.getQuestManager();
                    
                    // Look up the actual Chapter data using the sanitized ID stored in the quest
                    // FIX: Always sanitize the path before creating an Identifier to prevent crashes
                    Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", Quest.sanitizePath(hoveredQuest.getChapterName()));
                    QuestChapter chapter = manager.getChapters().get(chId);

                    if (chapter != null) {
                        String chTitle = (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) ? chapter.getTitle() : chapter.getName();
                        String grpTitle = "";

                        // If the chapter belongs to a group, find the pretty title for that group
                        if (chapter.getGroupName() != null && !chapter.getGroupName().isEmpty()) {
                            String gId = Quest.sanitizePath(chapter.getGroupName());
                            grpTitle = manager.getGroups().stream()
                                    .filter(g -> g.getName().equals(gId))
                                    .map(QuestGroup::getTitle)
                                    .findFirst().orElse("");
                        }
                        tooltipText = grpTitle.isEmpty() ? chTitle + " > " + hoveredQuest.getTitle() : grpTitle + " > " + chTitle + " > " + hoveredQuest.getTitle();
                    } else {
                        tooltipText = hoveredQuest.getTitle();
                    }
                    drawCustomTooltip(graphics, tooltipText, mouseX, mouseY);
                }
            });
        } else if (isShapePickerOpen) {
            renderPickerFrame(graphics, getPickerBounds(panelX, panelY, panelHeight), false, "", () -> {
                PickerBounds b = getPickerBounds(panelX, panelY, panelHeight);
                QuestShape[] shapes = QuestShape.values();

                // Inset scissor by 1px to protect the border
                graphics.enableScissor(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1);

                for (int i = 0; i < shapes.length; i++) {
                    QuestShape shape = shapes[i];
                    int rowY = b.y() + 5 + (i * 16);
                    int rowX = b.x() + 5;

                    // Hover detection
                    if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= rowY && mouseY <= rowY + 16) {
                        graphics.fill(b.x(), rowY, b.x() + b.w(), rowY + 16, QuestScreen.COL_HOVER_MENU);
                    }

                    // Render the shape preview (Draws a 10x10 version of the shape)
                    QuestShapeRenderer.render(shape, graphics, rowX, rowY + 2, 12, QuestScreen.COL_STATE_COMPLETED, QuestScreen.COL_UI_BG);

                    // Render the text shifted to the right to make room for the shape
                    graphics.text(font, shape.name(), b.x() + 22, rowY + 4, QuestScreen.COL_TEXT);
                }
                graphics.disableScissor();
            });
        }
    }

    // Extracted the switch into a helper for cleaner code
    private String getQuestValue(Quest quest, String label) {
        if (quest == null) return "None";
        return switch(label) {
            case "Title" -> quest.getTitle();
            case "Sub-Title" -> quest.getSubTitle();
            case "Description" -> quest.getDescription();
            case "Optional" -> String.valueOf(quest.isOptional());
            case "Repeatable" -> String.valueOf(quest.isRepeatable());
            case "Icon" -> quest.getLogo().toString();
            case "Shape" -> quest.getShape().toString();
            case "Size" -> String.valueOf(quest.getSize());
            case "Dependencies" -> String.valueOf(quest.getDependencies().size());
            default -> "Unknown";
        };
    }

    // Helper to identify which field was clicked
    public String getFieldAt(double mouseY, int panelY) {
        int startY = panelY + 30; // FIX: Unified with standardized render startY
        int relativeY = (int) (mouseY - startY);

        String[] activeLabels = isRewardModeOpen ? getRewardLabels(currentRewardType) : (isTaskMode ? getTaskLabels(currentTaskType) : this.labels);

        // Use method-specific rowHeight
        int rowH = (isTaskMode || isRewardModeOpen) ? 26 : 22;
        int clickedRow = relativeY / rowH;
        if (clickedRow >= 0 && clickedRow < activeLabels.length) {
            return activeLabels[clickedRow];
        }
        return null;
    }

    private int getIndexForField(String fieldName) {
        String[] activeLabels = isRewardModeOpen ? getRewardLabels(currentRewardType) : (isTaskMode ? getTaskLabels(currentTaskType) : this.labels);

        for (int i = 0; i < activeLabels.length; i++) {
            if (activeLabels[i].equals(fieldName)) {
                return i;
            }
        }
        return -1;
    }

    private String[] getTaskLabels(QuestTask.TaskType type) {
        return switch (type) {
            case ITEM -> new String[]{"Type", "Target", "Name", "Quantity", "Optional", "Repeatable", "Consume"};
            case KILL -> new String[]{"Type", "Target", "Name", "Quantity", "Optional", "Repeatable"};
            case CHECKBOX -> new String[]{"Type", "Name", "Optional", "Repeatable"};
            case BIOME, OBSERVE -> new String[]{"Type", "Target", "Name", "Optional", "Repeatable"};
            case LOCATION -> new String[]{"Type", "X", "Y", "Z", "Name", "Optional", "Repeatable"};
        };
    }

    private String[] getRewardLabels(QuestReward.RewardType type) {
        return switch (type) {
            case ITEM -> new String[]{"Type", "Target", "Quantity"};
            case XP -> new String[]{"Type", "Quantity"};
            case COMMAND -> new String[]{"Type", "Command"};
        };
    }

    public void renderTaskEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int panelX, int panelY, int panelWidth, int panelHeight, QuestTask task, Quest selectedQuest, boolean isUsedAsIcon) {
        // Sync internal type for helper methods
        this.currentTaskType = task.getType();

        int leftMargin = panelX + 15;
        int dividerX = panelX + 90;
        int valueX = dividerX + 10;
        int rowHeight = 26;
        int startY = panelY + 30;

        String[] labels = getTaskLabels(task.getType());

        String headerText = "Task Editor";
        graphics.text(font, Component.literal(headerText), leftMargin, panelY + 10, QuestScreen.COL_TEXT);

        // Draw the derived task icon in the header
        drawTaskIcon(graphics, task, task.getCurrentAmount(), QuestScreen.COL_UI_BG, panelX + panelWidth - 35, panelY + 4, mouseX, mouseY, 16);

        // Layout Dividers
        int footerDividerY = panelY + panelHeight - 24;
        graphics.horizontalLine(panelX, panelX + panelWidth - 1, panelY + 22, QuestScreen.COL_UI_BORDER);
        graphics.horizontalLine(panelX, panelX + panelWidth - 1, footerDividerY, QuestScreen.COL_UI_BORDER);

        // FIX: Start vertical line at the header divider
        graphics.verticalLine(dividerX, panelY + 22, footerDividerY, QuestScreen.COL_UI_BORDER);

        String pendingTaskOverlay = null;
        int overlayX = 0;
        int overlayY = 0;

        // Calculate layout constants once outside the loop so they are accessible to the final overlay pass
        int editBtnWidth = 45;
        int editBtnRightEdge = panelX + panelWidth - 15;
        int editBtnLeft = editBtnRightEdge - editBtnWidth;
        int maxTextWidth = editBtnLeft - valueX - 5;

        for (int i = 0; i < labels.length; i++) {
            int rowY = startY + (i * rowHeight);
            graphics.text(font, labels[i] + ":", leftMargin, rowY + 2, QuestScreen.COL_TEXT);

            String field = labels[i];
            String value = "";
            String buttonText = "Edit"; // Default button text
            boolean isNumericRow = labels[i].equals("Quantity");
            boolean isCoordRow = labels[i].equals("X") || labels[i].equals("Y") || labels[i].equals("Z");

            switch (labels[i]) {
                case "Type" -> { value = task.getType().name(); buttonText = "Change"; }
                case "Target" -> { value = task.getTargetDisplayName(); }
                case "Name" -> value = task.getName().isEmpty() ? "(Default)" : task.getName();
                case "Quantity" -> value = String.valueOf(task.getRequiredAmount()); // Shown if not editing
                case "Optional" -> { value = String.valueOf(task.isOptional()); buttonText = "Toggle"; }
                case "Repeatable" -> { value = String.valueOf(task.isRepeatable()); buttonText = "Toggle"; }
                case "Consume" -> { value = String.valueOf(task.isConsume()); buttonText = "Toggle"; }
                case "X" -> value = String.valueOf(task.getTargetX());
                case "Y" -> value = String.valueOf(task.getTargetY());
                case "Z" -> value = String.valueOf(task.getTargetZ());
            }

            // If this is the Target row, render the icon preview to the left of the text
            if (field.equals("Target")) {
                drawTaskIcon(graphics, task, task.getCurrentAmount(), QuestScreen.COL_UI_BG, valueX, rowY - 2, mouseX, mouseY, 16);
            }

            if ((isNumericRow && isQuantityOpen) || (labels[i].equals("X") && isXOpen) || (labels[i].equals("Y") && isYOpen) || (labels[i].equals("Z") && isZOpen)) {
                drawEditableText(graphics, searchQuery, valueX, rowY, maxTextWidth, 14, false);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Submit", QuestScreen.COL_BUTTON_BASE);
            } else if (field.equals("Name") && isNameOpen) {
                drawEditableText(graphics, searchQuery, valueX, rowY, maxTextWidth, 14, false);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Submit", QuestScreen.COL_BUTTON_BASE);
            } else {
                String displayVal = value;
                int textXOffset = field.equals("Target") ? 20 : 0; // Shift text if icon is present

                if (isNumericRow || isCoordRow || field.equals("Name") || field.equals("Target")) {
                    displayVal = truncate(value, maxTextWidth - textXOffset);
                }

                boolean isTruncated = !value.equals(displayVal);
                boolean isHovered = mouseX >= valueX + textXOffset && mouseX <= valueX + textXOffset + font.width(displayVal) &&
                                   mouseY >= rowY + 2 && mouseY <= rowY + 2 + font.lineHeight;

                // Handle Overlay queue
                if (isHovered && isTruncated && !isNameOpen && !isQuantityOpen && !isXOpen && !isYOpen && !isZOpen) {
                    pendingTaskOverlay = value;
                    overlayX = valueX;
                    overlayY = rowY;
                } else {
                    int valueColor = (value.equalsIgnoreCase("true") ? 0xFF55FF55 : value.equalsIgnoreCase("false") ? 0xFFFF5555 : 0xFFFFFFFF);
                    graphics.text(font, displayVal, valueX + textXOffset, rowY + 2, valueColor);
                }

                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, buttonText, QuestScreen.COL_BUTTON_BASE);
            }
        }

        // --- USE AS QUEST ICON CHECKBOX ---
        int cbX = panelX + 15;
        int cbY = panelY + panelHeight - 18;
        int cbSize = 10;

        graphics.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, isUsedAsIcon ? QuestScreen.COL_STATE_COMPLETED : QuestScreen.COL_INPUT_BG);
        graphics.outline(cbX, cbY, cbSize, cbSize, QuestScreen.COL_UI_BORDER);
        if (isUsedAsIcon) {
            graphics.fill(cbX + 2, cbY + 2, cbX + cbSize - 2, cbY + cbSize - 2, QuestScreen.COL_STATE_COMPLETED);
        }
        graphics.text(font, "Use as Quest Icon?", cbX + cbSize + 5, cbY + 1, QuestScreen.COL_TEXT);

        int btnWidth = 45;
        int btnY = panelY + panelHeight - 20;
        int saveRightEdge = panelX + panelWidth - 15;
        int saveLeft = saveRightEdge - btnWidth;
        int cancelLeft = saveLeft - 50;

        int cancelColor = getButtonColor(mouseX, mouseY, cancelLeft, btnY, btnWidth, 14, QuestScreen.COL_BUTTON_BASE);
        int saveColor = getButtonColor(mouseX, mouseY, saveLeft, btnY, btnWidth, 14, QuestScreen.COL_BUTTON_BASE);

        graphics.fill(cancelLeft, btnY, cancelLeft + btnWidth, btnY + 14, cancelColor);
        graphics.text(font, "Cancel", cancelLeft + 5, btnY + 3, QuestScreen.COL_TEXT);

        graphics.fill(saveLeft, btnY, saveRightEdge, btnY + 14, saveColor);
        graphics.text(font, "Save", saveLeft + 8, btnY + 3, QuestScreen.COL_TEXT);

        // Sub-pickers and Overlays
        renderSubPickers(graphics, mouseX, mouseY, panelX, panelY, panelHeight, task.getType(), task);

        if (pendingTaskOverlay != null) QuestScreen.renderStaticOverlayFromUI(graphics, pendingTaskOverlay, overlayX, overlayY, maxTextWidth + 10);
    }

    public List<String> getFilteredTargets(QuestTask.TaskType type) {
        String q = searchQuery.toLowerCase();
        List<String> results = new ArrayList<>();

        if (type == QuestTask.TaskType.ITEM) {
            if (q.startsWith("#")) {
                // TAG SEARCH MODE: Only show tags if the user explicitly starts with #
                String tagQuery = q.substring(1);
                BuiltInRegistries.ITEM.getTags()
                        .map(tag -> tag.key().location().toString())
                        .filter(path -> path.contains(tagQuery))
                        .map(path -> "#" + path)
                        .forEach(results::add);
            } else {
                // ITEM SEARCH MODE: Standard item lookup
                for (Item item : BuiltInRegistries.ITEM) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(item);
                    if (id != null) {
                        String idStr = id.toString().toLowerCase();
                        String name = item.getName(item.getDefaultInstance()).getString().toLowerCase();
                        if (idStr.contains(q) || name.contains(q)) {
                            results.add(idStr);
                        }
                    }
                }
            }
        } else if (type == QuestTask.TaskType.KILL) {
            if (q.startsWith("#")) {
                String tagQuery = q.substring(1);
                BuiltInRegistries.ENTITY_TYPE.getTags()
                        .filter(set -> set.stream().anyMatch(h -> h.value().getCategory() != MobCategory.MISC))
                        .map(set -> set.key().location().toString())
                        .filter(path -> path.contains(tagQuery))
                        .map(path -> "#" + path)
                        .forEach(results::add);
            } else {
                for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
                    if (et.getCategory() != MobCategory.MISC) {
                        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                        if (id != null) {
                            String idStr = id.toString().toLowerCase();
                            String name = et.getDescription().getString().toLowerCase();
                            if (idStr.contains(q) || name.contains(q)) {
                                results.add(idStr);
                            }
                        }
                    }
                }
            }
        } else if (type == QuestTask.TaskType.OBSERVE) {
            if (q.startsWith("#")) {
                String tagQuery = q.substring(1);
                BuiltInRegistries.ENTITY_TYPE.getTags()
                        .filter(set -> set.stream().anyMatch(h -> h.value().getCategory() != MobCategory.MISC))
                        .map(set -> set.key().location().toString()).filter(p -> p.contains(tagQuery))
                        .map(p -> "#" + p).forEach(results::add);
                BuiltInRegistries.BLOCK.getTags()
                        .map(set -> set.key().location().toString()).filter(p -> p.contains(tagQuery))
                        .map(p -> "#" + p).forEach(results::add);
            } else {
                for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
                    if (et.getCategory() != MobCategory.MISC) {
                        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                        if (id != null && (id.toString().contains(q) || et.getDescription().getString().toLowerCase().contains(q)))
                            results.add(id.toString());
                    }
                }
                for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                    if (id.toString().contains(q)) results.add(id.toString());
                }
            }
        } else if (type == QuestTask.TaskType.BIOME) {
            var registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME);
            for (Identifier id : registry.keySet()) {
                String rawId = id.toString();
                String friendlyName = toTitleCase(id.getPath());

                if (rawId.toLowerCase().contains(q) || friendlyName.toLowerCase().contains(q)) {
                    results.add(rawId);
                }
            }
        }

        Collections.sort(results);
        return results;
    }

    public void renderRewardEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int panelX, int panelY, int panelWidth, int panelHeight, QuestReward reward) {
        this.currentRewardType = reward.getType();
        int leftMargin = panelX + 15;
        int dividerX = panelX + 90;
        int valueX = dividerX + 10;
        int rowHeight = 26;
        int startY = panelY + 30;

        String headerText = "Reward Editor";
        graphics.text(font, Component.literal(headerText), leftMargin, panelY + 10, QuestScreen.COL_TEXT);
        drawRewardIcon(graphics, reward, QuestScreen.COL_STATE_COMPLETED, QuestScreen.COL_UI_BG, panelX + panelWidth - 35, panelY + 4, 16, false);

        // Layout Dividers
        int footerDividerY = panelY + panelHeight - 24;
        graphics.horizontalLine(panelX, panelX + panelWidth - 1, panelY + 22, QuestScreen.COL_UI_BORDER);
        graphics.horizontalLine(panelX, panelX + panelWidth - 1, footerDividerY, QuestScreen.COL_UI_BORDER);

        // FIX: Start vertical line at the header divider
        graphics.verticalLine(dividerX, panelY + 22, footerDividerY, QuestScreen.COL_UI_BORDER);

        String[] labels = reward.getType() == QuestReward.RewardType.ITEM ? new String[]{"Type", "Target", "Quantity"} :
                (reward.getType() == QuestReward.RewardType.XP ? new String[]{"Type", "Quantity"} : new String[]{"Type", "Command"});

        int editBtnWidth = 45;
        int editBtnRightEdge = panelX + panelWidth - 15;
        int editBtnLeft = editBtnRightEdge - editBtnWidth;
        int maxTextWidth = editBtnLeft - valueX - 5;

        for (int i = 0; i < labels.length; i++) {
            int rowY = startY + (i * rowHeight);
            graphics.text(font, labels[i] + ":", leftMargin, rowY + 2, QuestScreen.COL_TEXT);

            String value = switch (labels[i]) {
                case "Type" -> reward.getType().name();
                case "Target" -> reward.getItem() == Items.AIR ? "None" : reward.getItem().getDefaultInstance().getHoverName().getString();
                case "Quantity" -> String.valueOf(reward.getCount());
                case "Command" -> reward.getCommand();
                default -> "";
            };

            if ((labels[i].equals("Quantity") && isQuantityOpen) || (labels[i].equals("Command") && isNameOpen)) {
                drawEditableText(graphics, searchQuery, valueX, rowY, maxTextWidth, 14, false);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, "Submit", QuestScreen.COL_BUTTON_BASE);
            } else {
                String displayVal = truncate(value, maxTextWidth);
                graphics.text(font, displayVal, valueX, rowY + 2, 0xFFFFFFFF);
                drawButton(graphics, mouseX, mouseY, editBtnLeft, rowY, editBtnWidth, 14, labels[i].equals("Type") ? "Change" : "Edit", QuestScreen.COL_BUTTON_BASE);
            }
        }

        // Footer Buttons
        int btnY = panelY + panelHeight - 20;

        // Add Choice Button (Bottom Left)
        drawButton(graphics, mouseX, mouseY, panelX + 15, btnY, 60, 14, "Add Choice", QuestScreen.COL_BUTTON_BASE);
        if (!reward.getSubRewards().isEmpty()) {
            graphics.text(font, "Choices: " + reward.getSubRewards().size(), panelX + 80, btnY + 3, QuestScreen.COL_TEXT_GOLD);
        }

        drawButton(graphics, mouseX, mouseY, editBtnLeft - 50, btnY, 45, 14, "Cancel", QuestScreen.COL_BUTTON_BASE);
        drawButton(graphics, mouseX, mouseY, editBtnLeft, btnY, 45, 14, "Save", QuestScreen.COL_BUTTON_BASE);

        // Sub-pickers for Reward Editor
        renderSubPickers(graphics, mouseX, mouseY, panelX, panelY, panelHeight, QuestTask.TaskType.ITEM, null);
    }

    private void renderSubPickers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int panelX, int panelY, int panelHeight, QuestTask.TaskType taskType, QuestTask task) {
        if (isTypePickerOpen) {
            renderPickerFrame(graphics, getPickerBounds(panelX, panelY, panelHeight), false, "", () -> {
                PickerBounds b = getPickerBounds(panelX, panelY, panelHeight);
                Object[] types = isRewardModeOpen ? QuestReward.RewardType.values() : QuestTask.TaskType.values();
                graphics.enableScissor(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1);
                for (int i = 0; i < types.length; i++) {
                    int rowY = b.y() + 5 + (i * 16);
                    if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= rowY && mouseY <= rowY + 16) {
                        graphics.fill(b.x(), rowY, b.x() + b.w(), rowY + 16, QuestScreen.COL_HOVER_MENU);
                    }
                    graphics.text(font, types[i].toString(), b.x() + 5, rowY + 4, QuestScreen.COL_TEXT);
                }
                graphics.disableScissor();
            });
        }

        if (isTargetPickerOpen || isIconPickerOpen) {
            renderPickerFrame(graphics, getPickerBounds(panelX, panelY, panelHeight), true, searchQuery, () -> {
                PickerBounds b = getPickerBounds(panelX, panelY, panelHeight);
                
                if (isIconPickerOpen) {
                    List<Item> filteredItems = getCachedIcons();
                    int columns = PICKER_COLUMNS;
                    int cellSize = 18;
                    int totalContentHeight = (int) Math.ceil(filteredItems.size() / (double) columns) * cellSize;
                    int visibleHeight = b.h() - b.barHeight() - 10;
                    if (scrollOffset > Math.max(0, totalContentHeight - visibleHeight)) scrollOffset = Math.max(0, totalContentHeight - visibleHeight);

                    graphics.enableScissor(b.x() + 1, b.y() + b.barHeight(), b.x() + b.w() - 1, b.y() + b.h() - 1);
                    String hoveredName = null;
                    boolean showCheckmark = searchQuery.isEmpty() || "checkmark".contains(searchQuery.toLowerCase());
                    int totalEntries = filteredItems.size() + (showCheckmark ? 1 : 0);

                    for (int i = 0; i < totalEntries; i++) {
                        int ix = b.x() + 5 + (i % columns) * cellSize;
                        int iy = (b.y() + 16 + 5) + (i / columns) * cellSize - (int) scrollOffset;

                        if (mouseX > ix && mouseX < ix + 16 && mouseY > iy && mouseY < iy + 16) {
                            graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, QuestScreen.COL_HOVER_MENU);
                            if (showCheckmark && i == 0) hoveredName = "Checkmark";
                            else {
                                Item item = filteredItems.get(showCheckmark ? i - 1 : i);
                                hoveredName = item.getDefaultInstance().getHoverName().getString();
                            }
                        }

                        if (iy >= b.y() + b.barHeight() && iy < b.y() + b.h() - 1) {
                            if (showCheckmark && i == 0) drawCheckmark(graphics, ix, iy, 16);
                            else graphics.item(new ItemStack(filteredItems.get(showCheckmark ? i - 1 : i)), ix, iy);
                        }
                    }
                    graphics.disableScissor();
                    if (hoveredName != null) drawCustomTooltip(graphics, hoveredName, mouseX, mouseY);
                } else if (taskType != null && task != null) {
                    // Target Picker logic for Tasks
                    List<String> targets = getCachedTargets(taskType);
                    int columns = (taskType == QuestTask.TaskType.BIOME) ? 1 : (taskType == QuestTask.TaskType.KILL ? 3 : PICKER_COLUMNS);
                    int cellSize = (taskType == QuestTask.TaskType.BIOME) ? 16 : (taskType == QuestTask.TaskType.KILL ? 30 : 18);
                    int totalContentHeight = (int) Math.ceil(targets.size() / (double) columns) * cellSize;
                    int visibleHeight = b.h() - b.barHeight() - 10;
                    if (scrollOffset > Math.max(0, totalContentHeight - visibleHeight)) scrollOffset = Math.max(0, totalContentHeight - visibleHeight);

                    graphics.enableScissor(b.x() + 1, b.y() + b.barHeight(), b.x() + b.w() - 1, b.y() + b.h() - 1);
                    String hoveredTarget = null;

                    for (int i = 0; i < targets.size(); i++) {
                        String targetId = targets.get(i);
                        int ix = b.x() + 5 + (i % columns) * cellSize;
                        int iy = (b.y() + 16 + 5) + (i / columns) * cellSize - (int) scrollOffset;
                        int slotW = (taskType == QuestTask.TaskType.BIOME) ? b.w() - 10 : cellSize - 2;

                        boolean isHovered = mouseX > ix && mouseX < ix + slotW && mouseY > iy && mouseY < iy + cellSize - 2;
                        int innerColor = isHovered ? QuestScreen.COL_PANEL_HEADER : QuestScreen.COL_UI_BG;
                        if (isHovered) {
                            graphics.fill(ix - 1, iy - 1, ix + slotW + 1, iy + cellSize - 1, QuestScreen.COL_HOVER_MENU);
                            hoveredTarget = targetId;
                        }

                        if (iy >= b.y() + b.barHeight() && iy < b.y() + b.h() - 1) {
                            QuestTask temp = new QuestTask("", taskType, targetId, "", 1, 0, false, false, false, QuestTask.TaskState.INCOMPLETE, 0, 0, 0, false);
                            drawTaskIcon(graphics, temp, 0, innerColor, ix, iy, mouseX, mouseY, cellSize - 2);
                            if (taskType == QuestTask.TaskType.BIOME) {
                                graphics.text(font, temp.getTargetDisplayName(), ix + 20, iy + 4, QuestScreen.COL_TEXT);
                            }
                        }
                    }
                    graphics.disableScissor();
                    if (hoveredTarget != null) {
                        QuestTask tempForName = new QuestTask("", taskType, hoveredTarget, "", 1, 0, false, false, false, QuestTask.TaskState.INCOMPLETE, 0, 0, 0, false);
                        drawCustomTooltip(graphics, tempForName.getTargetDisplayName(), mouseX, mouseY);
                    }
                }
            });
        }
    }

    public void drawRewardIcon(GuiGraphicsExtractor graphics, QuestReward reward, int circleColor, int innerColor, int x, int y, int size, boolean isClaimable) {
        QuestShapeRenderer.render(QuestShape.CIRCLE, graphics, x, y, size, circleColor, innerColor);
        ItemStack stack = switch (reward.getType()) {
            case ITEM -> new ItemStack(reward.getItem(), reward.getCount());
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case COMMAND -> new ItemStack(Items.COMMAND_BLOCK);
        };
        renderCenteredItem(graphics, stack, x, y, size);

        // Render the "!" or "Claim" indicator in the top right corner
        if (isClaimable) {
            int claimSize = 8; // Small indicator icon
            graphics.blit(RenderPipelines.GUI_TEXTURED, CLAIM_ICON, x + size - 7, y - 1, 
                    0.0f, 0.0f, claimSize, claimSize, claimSize, claimSize);
        }
    }

    public void drawTaskIcon(GuiGraphicsExtractor graphics, QuestTask task, int currentAmount, int innerColor, int x, int y, int mouseX, int mouseY, int size) {
        // 1. Derive the state locally from the synced data
        boolean isComplete = currentAmount >= task.getRequiredAmount();
        boolean isStarted = currentAmount > 0;

        int circleColor = isComplete ? QuestScreen.COL_STATE_COMPLETED :
                (isStarted ? QuestScreen.COL_STATE_PARTIAL : QuestScreen.COL_STATE_AVAILABLE);
        int iconSize = size;
        // 1. Draw the background circle for ALL task types
        QuestShapeRenderer.render(QuestShape.CIRCLE, graphics, x, y, iconSize, circleColor, innerColor);

        // Entity Rendering Pass
        if (task.getType() == QuestTask.TaskType.KILL || task.getType() == QuestTask.TaskType.OBSERVE) {
            String targetId = task.getTargetId();
            if (targetId.startsWith("#")) {
                try {
                    TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(targetId.substring(1)));
                    List<EntityType<?>> validList = new ArrayList<>();
                    BuiltInRegistries.ENTITY_TYPE.get(tagKey).ifPresent(set -> {
                        for (Holder<EntityType<?>> h : set) {
                            if (h.value().getCategory() != MobCategory.MISC) validList.add(h.value());
                        }
                    });
                    if (!validList.isEmpty()) {
                        int index = (int) ((Util.getMillis() / 1000) % validList.size());
                        renderEntityIcon(graphics, validList.get(index), x, y, iconSize);
                        return;
                    }
                } catch (Exception ignored) {}
            } else {
                Identifier loc = Identifier.tryParse(targetId);
                if (loc != null && !loc.getPath().equals("air")) {
                    var optHolder = BuiltInRegistries.ENTITY_TYPE.get(loc);
                    if (optHolder.isPresent()) {
                        renderEntityIcon(graphics, optHolder.get().value(), x, y, iconSize);
                        return;
                    }
                }
            }
            
            // Fallback for KILL tasks if no entity model is available
            if (task.getType() == QuestTask.TaskType.KILL) {
                renderCenteredItem(graphics, new ItemStack(Items.IRON_SWORD), x, y, iconSize);
                return;
            }
        }

        // Item/Tag/Special Rendering Pass
        if (task.getType() != QuestTask.TaskType.CHECKBOX) {
            // For Item, Biome, and Location - Render the item scaled and centered inside the circle
            if (task.getTargetId().startsWith("#")) {
                // TAG ICON LOGIC: Show the first valid item in the tag as the icon
                try {
                    Identifier loc = Identifier.parse(task.getTargetId().substring(1));
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);

                    // FIX: Use Stream filter/findFirst because getTags() returns a Stream, not an Iterable
                    HolderSet.Named<Item> foundSet = BuiltInRegistries.ITEM.getTags()
                            .filter(s -> s.key().equals(tagKey))
                            .findFirst().orElse(null);

                    if (foundSet != null && foundSet.size() > 0) {
                        // CYCLE LOGIC: Use system time to pick an index that changes every 1000ms (1 second)
                        int index = (int) ((Util.getMillis() / 1000) % foundSet.size());
                        Item cyclingItem = foundSet.stream().skip(index).findFirst().map(Holder::value).orElse(Items.BARRIER);
                        renderCenteredItem(graphics, new ItemStack(cyclingItem), x, y, iconSize);
                    } else {
                        renderCenteredItem(graphics, new ItemStack(Items.BARRIER), x, y, iconSize);
                    }
                } catch (Exception e) {
                    renderCenteredItem(graphics, new ItemStack(Items.BARRIER), x, y, iconSize);
                }
            } else {
                renderCenteredItem(graphics, task.getIconStack(), x, y, iconSize);
            }
        } else {
            // For Checkbox - Always render the checkmark as the primary icon.
            // We use 0.55 padding to match the task node style.
            drawCheckmark(graphics, x, y, iconSize);
        }
    }

    public void drawQuestIcon(GuiGraphicsExtractor graphics, Quest quest, int x, int y, int size) {
        if (quest.isUseTaskIcon() && !quest.getTasks().isEmpty()) {
            QuestTask provider = null;
            for (QuestTask t : quest.getTasks()) {
                if (t.isIcon()) {
                    provider = t;
                    break;
                }
            }
            // Legacy Fallback
            if (provider == null) provider = quest.getTasks().get(0);

            if (provider != null) {
                String targetId = provider.getTargetId();
                // Revert: Entity tasks on the canvas now use a standard sword icon to avoid coordinate conflicts
                if (provider.getType() == QuestTask.TaskType.KILL || provider.getType() == QuestTask.TaskType.OBSERVE) {
                    renderCenteredItem(graphics, new ItemStack(Items.IRON_SWORD), x, y, size);
                    return;
                } else if (provider.getType() == QuestTask.TaskType.CHECKBOX) {
                    drawCheckmark(graphics, x, y, size);
                    return;
                }
                renderCenteredItem(graphics, new ItemStack(quest.getLogo()), x, y, size);
                return;
            }
        }

        if (quest.getLogo() == Items.AIR) {
            drawCheckmark(graphics, x, y, size);
        } else {
            renderCenteredItem(graphics, new ItemStack(quest.getLogo()), x, y, size);
        }
    }

    public void drawCheckmark(GuiGraphicsExtractor graphics, int x, int y, int size) {
        // Apply 45% internal padding (checkmark occupies 55% of the node area)
        // This ensures the checkmark sits safely inside shape borders and circle frames without clipping.
        float paddingFactor = 0.55f;
        int scaledSize = Math.max(1, (int) (size * paddingFactor));
        int offset = (size - scaledSize) / 2;

        // FIX: Sample the full 16x16 checkmark region and use Matrix scale for the padding
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + offset, y + offset);
        float checkScale = scaledSize / 16.0f;
        graphics.pose().scale(checkScale, checkScale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHECKMARK_TEXTURE, 0, 0, 
                0.0f, 0.0f, 16, 16, 16, 16);
        graphics.pose().popMatrix();
    }

    public String toTitleCase(String input) {
        String path = input.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : path.toLowerCase().split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void renderCenteredItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int iconSize) {
        // Balanced scale to 0.55 for consistent visibility without crowding the node borders (approx 2-3px reduction)
        float itemScale = (iconSize / 16f) * 0.55f;
        // Calculate internal offset to center the scaled item within the iconSize box
        float offset = (iconSize - (16 * itemScale)) / 2f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + offset, y + offset);
        graphics.pose().scale(itemScale, itemScale);
        graphics.item(stack, 0, 0); // Always draw at 0,0 relative to translated origin
        graphics.pose().popMatrix();
    }

    // New method to get the exact Y-coordinate of a field's row
    public int getRowY(String fieldName, int panelY) {
        int index = getIndexForField(fieldName);
        if (index == -1) return -1;

        int startY = panelY + 30; // FIX: Unified with standardized render startY
        int rowH = (isTaskMode || isRewardModeOpen) ? 26 : 22;
        return startY + (index * rowH);
    }

    public int getButtonColor(int mouseX, int mouseY, int btnX, int btnY, int btnWidth, int btnHeight, int baseColor) {
        // Block hover highlights if a modal or picker is open on top
        if (isSubTitleOpen || isDescriptionOpen || isIconPickerOpen || isDependencyPickerOpen || 
            isTypePickerOpen || isTargetPickerOpen || isColorPickerOpen || isShapePickerOpen) {
            return baseColor;
        }

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnWidth &&
                mouseY >= btnY && mouseY <= btnY + btnHeight;
        return isHovered ? baseColor + QuestScreen.COL_BUTTON_HOVER_DELTA : baseColor;
    }

    // Checks if the mouse is inside the Cancel button area
    public boolean isClickingCancel(int mouseX, int mouseY, int panelX, int panelY, int panelHeight) {
        int btnY = panelY + panelHeight - 20;
        int btnWidth = 45;
        int saveRightEdge = panelX + 300 - 15;
        int saveLeft = saveRightEdge - btnWidth;
        int cancelLeft = saveLeft - 50;

        return mouseX >= cancelLeft && mouseX <= cancelLeft + btnWidth &&
                mouseY >= btnY && mouseY <= btnY + 14;
    }

    public boolean isClickingSave(int mouseX, int mouseY, int panelX, int panelY, int panelHeight) {
        int btnY = panelY + panelHeight - 20;
        int btnWidth = 45;
        int saveRightEdge = panelX + 300 - 15;
        int saveLeft = saveRightEdge - btnWidth;

        return mouseX >= saveLeft && mouseX <= saveRightEdge &&
                mouseY >= btnY && mouseY <= btnY + 14;
    }

    // --- CACHED GETTERS ---

    public List<Item> getCachedIcons() {
        if (searchQuery.equals(lastQuery) && !cachedIcons.isEmpty()) return cachedIcons;
        this.cachedIcons = getFilteredIcons();
        this.lastQuery = searchQuery;
        return cachedIcons;
    }

    private List<Item> getFilteredIcons() {
        if (searchQuery.isEmpty()) return availableIcons;
        String query = searchQuery.toLowerCase();

        return availableIcons.stream()
                .filter(item -> {
                    String displayName = item.getName(item.getDefaultInstance()).getString().toLowerCase();
                    // Get the unique ID, e.g., "minecraft:diamond"
                    String registryName = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();

                    return displayName.contains(query) || registryName.contains(query);
                })
                .toList();
    }

    public List<String> getCachedTargets(QuestTask.TaskType type) {
        if (searchQuery.equals(lastQuery) && type == lastType && !cachedTargets.isEmpty()) return cachedTargets;
        this.cachedTargets = getFilteredTargets(type);
        this.lastQuery = searchQuery;
        this.lastType = type;
        return cachedTargets;
    }

    public List<Quest> getCachedDependencies(List<Quest> allQuests, Quest questToModify) {
        // Note: We don't cache allQuests size here because dependencies can change via right-click
        // but the query is the main performance killer.
        if (searchQuery.equals(lastQuery) && !cachedDependencies.isEmpty()) return cachedDependencies;
        this.cachedDependencies = getFilteredDependencies(allQuests, questToModify);
        this.lastQuery = searchQuery;
        return cachedDependencies;
    }

    private void drawCustomTooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        int maxWidth = 200; // Consistent max width for floating pickers
        var lines = this.font.split(Component.literal(text), maxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 6;
        int boxX = mouseX + 12;
        int boxY = mouseY - 12;
        int boxWidth = textWidth + (padding * 2);
        int boxHeight = (lines.size() * this.font.lineHeight) + padding;

        if (boxX + boxWidth > graphics.guiWidth()) {
            boxX = mouseX - boxWidth - 12;
        }
        if (boxX < 5) boxX = 5; // Left-side safety

        // Vertical Clamping: Ensure the tooltip doesn't bleed off the bottom or top of the screen
        if (boxY + boxHeight > graphics.guiHeight()) {
            boxY = graphics.guiHeight() - boxHeight - 5;
        }
        if (boxY < 5) boxY = 5;

        // 1. Background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, QuestScreen.COL_TOOLTIP_BG);

        // 2. Border
        int borderColor = QuestScreen.COL_PANEL_DIVIDER;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        // 3. Render Lines
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(this.font, lines.get(i), boxX + padding, boxY + 3 + (i * this.font.lineHeight), QuestScreen.COL_TEXT);
        }
    }

    public List<Quest> getFilteredDependencies(List<Quest> allQuests, Quest questToModify) {
        String q = searchQuery.toLowerCase();

        // Predicate to check if a quest matches the query
        java.util.function.Predicate<Quest> matchesQuery = quest -> {
            String title = quest.getTitle().toLowerCase();
            String id = quest.getId().toString().toLowerCase(); // Assuming getId() returns a UUID or String
            return title.contains(q) || id.contains(q);
        };

        if (isRemoveDependencyMode) {
            return allQuests.stream()
                    .filter(quest -> questToModify.getDependencies().contains(quest.getId()))
                    .filter(matchesQuery)
                    .toList();
        } else {
            return allQuests.stream()
                    .filter(quest -> !quest.getId().equals(questToModify.getId()))
                    .filter(quest -> !questToModify.getDependencies().contains(quest.getId()))
                    .filter(matchesQuery)
                    .toList();
        }
    }

    // Inside QuestEditorUI.java
    public void renderPickerFrame(GuiGraphicsExtractor graphics, PickerBounds b, boolean showHeader, String searchQuery, Runnable contentDrawer) {
        // 1. Draw Background
        graphics.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), QuestScreen.COL_UI_BORDER);
        
        // Force background to be opaque to hide elements rendering behind the picker
        int opaqueBG = 0xFF000000 | (QuestScreen.COL_UI_BG & 0x00FFFFFF);
        graphics.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, opaqueBG);

        if (showHeader ) {
            // Only render the search input box if a searchQuery is provided (not null)
            if (searchQuery != null) {
                int searchX = b.x() + 2;
                int searchY = b.y() + 2;
                int searchW = b.w() - 4;
                int searchH = 12;
                graphics.fill(searchX, searchY, searchX + searchW, searchY + searchH, QuestScreen.COL_INPUT_BG);

                // 3. Render Query with Cursor
                int boxWidth = searchW - 4; // Width of the text box minus some padding
                String beforeCursor = this.searchQuery.substring(0, Math.max(0, Math.min(cursorIndex, this.searchQuery.length())));
                int cursorPixelX = font.width(beforeCursor);

                // Auto-scroll the text camera
                updateScrollOffset(boxWidth);

                // Scissor the text so it doesn't bleed out of the search box
                graphics.enableScissor(searchX, searchY, searchX + searchW, searchY + searchH);

                if (selectionStart != -1 && selectionEnd != -1) {
                    String selectedText = this.searchQuery.substring(selectionStart, selectionEnd);
                    int selX = font.width(this.searchQuery.substring(0, selectionStart));
                    int selWidth = font.width(selectedText);

                    // Draw highlight background
                    graphics.fill(searchX + 2 + selX - textScrollOffset, searchY + 2,
                            searchX + 2 + selX + selWidth - textScrollOffset, searchY + 10, QuestScreen.COL_SELECTION);
                }

                // Draw the text, shifted by the scroll offset
                graphics.text(font, this.searchQuery, searchX + 2 - textScrollOffset, searchY + 2, QuestScreen.COL_TEXT);

                // Draw the blinking cursor
                if (Minecraft.getInstance().level.getGameTime() % 20 < 10) {
                    graphics.fill(searchX + 2 + cursorPixelX - textScrollOffset, searchY + 2,
                            searchX + 3 + cursorPixelX - textScrollOffset, searchY + 10, QuestScreen.COL_TEXT);
                }
                graphics.disableScissor();
            }

            // Draw a single clean separator line for the drag handle
            graphics.fill(b.x(), b.y() + 16, b.x() + b.w(), b.y() + 17, QuestScreen.COL_UI_BORDER);
        }

        // 4. Draw Content
        // This calls the specific logic for Items or Quests
        contentDrawer.run();
    }

    public void drawButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String text, int color) {
        int btnColor = getButtonColor(mouseX, mouseY, x, y, w, h, color);
        graphics.fill(x, y, x + w, y + h, btnColor);

        int textWidth = font.width(text);
        // Align text to x + (width/2) - (text/2) and y + 3 for consistent vertical padding
        graphics.text(font, text, x + (w / 2) - (textWidth / 2), y + 3, QuestScreen.COL_TEXT);
    }

    public void closePicker() {
        this.isDependencyPickerOpen = false;
        this.isIconPickerOpen = false;
        this.isShapePickerOpen = false;
        this.isTitleOpen = false;
        this.isSubTitleOpen = false;
        this.isDescriptionOpen = false;
        this.isQuantityOpen = false;
        this.isNameOpen = false;
        this.isXOpen = false;
        this.isYOpen = false;
        this.isZOpen = false;
        this.isTypePickerOpen = false;
        this.isTargetPickerOpen = false;
        // Note: isTaskMode and isRewardModeOpen are now managed by QuestScreen to prevent hitbox desync
        this.isHexEditing = false;

        this.hexQuery = "";
        this.hexCursorIndex = 0;
        this.hexTextScrollOffset = 0;
        this.scrollOffset = 0;
        this.searchQuery = "";
        this.cursorIndex = 0;
        this.textScrollOffset = 0;
        this.selectionStart = -1;
        this.selectionEnd = -1;
        QuestScreen.playClickSound();
    }

    public QuestShape[] getAvailableShapes() {
        return QuestShape.values();
    }

    public void drawEditableText(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, boolean isHex) {
        if (text.isEmpty()) {
            textScrollOffset = 0;
        }
        int cIdx = isHex ? hexCursorIndex : cursorIndex;
        int sOff = isHex ? hexTextScrollOffset : textScrollOffset;

        int cursorPixelX = font.width(text.substring(0, Math.min(cIdx, text.length())));

        if (cursorPixelX - sOff > w - 2) {
            sOff = cursorPixelX - (w - 2);
        }
        else if (cursorPixelX - sOff < 0) {
            sOff = cursorPixelX;
        }
        if (font.width(text) < w - 2) {
            sOff = 0;
        }

        if (isHex) hexTextScrollOffset = sOff; else textScrollOffset = sOff;

        graphics.fill(x, y, x + w, y + h, QuestScreen.COL_INPUT_BG);
        graphics.outline(x, y, w, h, QuestScreen.COL_UI_BORDER);
        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        // --- NEW: Draw Selection Highlight ---
        if (selectionStart != -1 && selectionEnd != -1) {
            int length = text.length();
            int selStart = Math.max(0, Math.min(selectionStart, length));
            int selEnd = Math.max(0, Math.min(selectionEnd, length));

            if (selStart != selEnd) {
                int startIdx = Math.min(selStart, selEnd);
                int endIdx = Math.max(selStart, selEnd);

                int selectX1 = font.width(text.substring(0, startIdx));
                int selectX2 = font.width(text.substring(0, endIdx));

                int drawX1 = x + 3 + selectX1 - sOff;
                int drawX2 = x + 3 + selectX2 - sOff;

                int renderX = Math.max(drawX1, x + 2);
                int renderW = Math.min(drawX2, x + w - 2) - renderX;

                if (renderW > 0) {
                    graphics.fill(renderX, y + 3, renderX + renderW, y + h - 3, QuestScreen.COL_SELECTION);
                }
            }
        }
        // ------------------------------------

        graphics.text(font, text, x + 3 - sOff, y + 3, QuestScreen.COL_TEXT);

        // 4. Draw the cursor
        if (System.currentTimeMillis() % 1000 < 500) {
            int cursorX = x + 3 + cursorPixelX - sOff;

            // Draw only if cursor is within the scissored area
            if (cursorX >= x + 2 && cursorX <= x + w - 2) {
                graphics.fill(cursorX, y + 3, cursorX + 1, y + h - 3, QuestScreen.COL_TEXT);
            }
        }
        graphics.disableScissor();
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        // Add "..." and shrink until it fits
        String ellipsis = "...";
        String result = text;
        while (font.width(result + ellipsis) > maxWidth && result.length() > 0) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    public void updateScrollOffset(int boxWidth) {
        int cursorPixelX = font.width(searchQuery.substring(0, Math.min(cursorIndex, searchQuery.length())));
        int w = boxWidth;

        // If cursor moves off-screen to the right
        if (cursorPixelX - textScrollOffset > w - 2) {
            textScrollOffset = cursorPixelX - (w - 2);
        }
        // If cursor moves off-screen to the left
        else if (cursorPixelX - textScrollOffset < 0) {
            textScrollOffset = cursorPixelX;
        }
        // If text is short, reset offset
        if (font.width(searchQuery) < w - 2) {
            textScrollOffset = 0;
        }
    }

    public static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void renderLargeTextEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int boxW = 240;
        int boxH = 140;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = (screenHeight - boxH) / 2;

        // 1. Dim the background
        graphics.fill(0, 0, screenWidth, screenHeight, QuestScreen.COL_DIM);

        // 2. Main Box
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, QuestScreen.COL_UI_BG);
        // Draw border (using the helper logic from QuestScreen concept)
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, QuestScreen.COL_UI_BORDER);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, QuestScreen.COL_UI_BORDER);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, QuestScreen.COL_UI_BORDER);
        graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, QuestScreen.COL_UI_BORDER);

        String title = isSubTitleOpen ? "Edit Sub-Title" : "Edit Description";
        graphics.text(this.font, Component.literal(title), boxX + 6, boxY + 6, QuestScreen.COL_TEXT);

        // 3. Inner text area
        int contentY = boxY + 20;
        graphics.fill(boxX + 5, contentY, boxX + boxW - 5, boxY + boxH - 25, QuestScreen.COL_UI_INNER_BG);

        // 4. Split text into lines to handle vertical overflow/scrolling
        var lines = this.font.split(Component.literal(this.searchQuery), boxW - 20);
        int maxVisibleLines = (boxH - 45) / this.font.lineHeight;
        int startLine = Math.max(0, lines.size() - maxVisibleLines);

        // Scissor the text area to prevent highlight/text overflow
        graphics.enableScissor(boxX + 5, contentY, boxX + boxW - 5, boxY + boxH - 25);

        // FIX: Handle cursor rendering when the editor is completely empty
        if (this.searchQuery.isEmpty()) {
            if (Util.getMillis() / 500 % 2 == 0) {
                graphics.fill(boxX + 10, contentY + 5, boxX + 11, contentY + 5 + this.font.lineHeight, QuestScreen.COL_TEXT);
            }
        }

        int nextSearchIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            String lineStr = "";
            
            // Extract the raw string from the sequence to calculate widths
            StringBuilder sb = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                sb.append((char)codePoint);
                return true;
            });
            lineStr = sb.toString();

            int lineY = contentY + 5 + ((i - startLine) * this.font.lineHeight);
            
            // Find where this line actually starts in the original string to account for wrapped spaces
            int lineStartIdx = this.searchQuery.indexOf(lineStr, nextSearchIndex);
            if (lineStartIdx == -1) lineStartIdx = nextSearchIndex;
            int lineEndIdx = lineStartIdx + lineStr.length();

            if (i < startLine) {
                nextSearchIndex = lineEndIdx;
                continue;
            }

            // Render Selection Highlight for this specific line
            if (selectionStart != -1 && selectionEnd != -1) {
                int selMin = Math.min(selectionStart, selectionEnd);
                int selMax = Math.max(selectionStart, selectionEnd);
                int lineStart = lineStartIdx;
                int lineEnd = lineEndIdx;

                // Check if the selection overlaps with this line
                int intersectStart = Math.max(selMin, lineStart);
                int intersectEnd = Math.min(selMax, lineEnd);

                if (intersectStart < intersectEnd) {
                    int x1 = font.width(lineStr.substring(0, intersectStart - lineStart));
                    int x2 = font.width(lineStr.substring(0, intersectEnd - lineStart));
                    graphics.fill(boxX + 10 + x1, lineY, boxX + 10 + x2, lineY + this.font.lineHeight, QuestScreen.COL_SELECTION);
                }
            }

            graphics.text(this.font, line, boxX + 10, lineY, QuestScreen.COL_TEXT);

            // Render Blinking Vertical Cursor
            // Check if logical cursorIndex falls within the character range of this specific line
            if (cursorIndex >= lineStartIdx && cursorIndex <= lineEndIdx) {
                // We draw the cursor on this line if it's not at the very end of a wrapped line,
                // or if it IS the very last line of the entire text.
                if (cursorIndex < lineEndIdx || i == lines.size() - 1) {
                    int relativeIdx = cursorIndex - lineStartIdx;
                    int cursorPixelX = font.width(lineStr.substring(0, Math.max(0, relativeIdx)));
                    
                    if (Util.getMillis() / 500 % 2 == 0) {
                        graphics.fill(boxX + 10 + cursorPixelX, lineY, boxX + 10 + cursorPixelX + 1, lineY + this.font.lineHeight, QuestScreen.COL_TEXT);
                    }
                }
            }
            nextSearchIndex = lineEndIdx;
        }

        graphics.disableScissor();

        // 5. Buttons
        int btnY = boxY + boxH - 20;

        // Save Button
        int saveColor = getButtonColor(mouseX, mouseY, boxX + boxW - 55, btnY, 50, 14, QuestScreen.COL_BUTTON_BASE);
        graphics.fill(boxX + boxW - 55, btnY, boxX + boxW - 5, btnY + 14, saveColor);
        graphics.text(this.font, Component.literal("Save"), boxX + boxW - 30 - (font.width("Save")/2), btnY + 3, QuestScreen.COL_TEXT);

        // Cancel Button
        int cancelColor = getButtonColor(mouseX, mouseY, boxX + boxW - 110, btnY, 50, 14, QuestScreen.COL_BUTTON_BASE);
        graphics.fill(boxX + boxW - 110, btnY, boxX + boxW - 60, btnY + 14, cancelColor);
        graphics.text(this.font, Component.literal("Cancel"), boxX + boxW - 85 - (font.width("Cancel")/2), btnY + 3, QuestScreen.COL_TEXT);
    }

    public void renderColorWheel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, PickerBounds b, int currentARGB, java.util.function.Consumer<Integer> onSelect) {
        int pickerX = b.x() + 5;
        int pickerY = b.y() + b.barHeight() + 5;

        // 2. Convert current ARGB to HSB
        float[] hsb = RGBtoHSB((currentARGB >> 16) & 0xFF, (currentARGB >> 8) & 0xFF, currentARGB & 0xFF, null);
        float alpha = ((currentARGB >> 24) & 0xFF) / 255.0f;

        int sqSize = 70;
        for (int j = 0; j < sqSize; j += 2) {
            for (int i = 0; i < sqSize; i += 2) {
                float s = i / (float) sqSize;
                float v = 1.0f - (j / (float) sqSize);
                int color = HSBtoRGB(hsb[0], s, v);
                graphics.fill(pickerX + i, pickerY + j, pickerX + i + 2, pickerY + j + 2, 0xFF000000 | color);
            }
        }
        graphics.outline(pickerX, pickerY, sqSize, sqSize, QuestScreen.COL_UI_BORDER);

        // 3. Hue Slider (Vertical)
        int hueX = pickerX + sqSize + 10;
        int hueW = 12;
        for (int j = 0; j < sqSize; j++) {
            float h = j / (float) sqSize;
            graphics.fill(hueX, pickerY + j, hueX + hueW, pickerY + j + 1, 0xFF000000 | HSBtoRGB(h, 1.0f, 1.0f));
        }
        graphics.outline(hueX, pickerY, hueW, sqSize, QuestScreen.COL_UI_BORDER);

        // 3. Hex Input Field (Now below the square)
        int hexY = pickerY + sqSize + 8;
        String hex = String.format("#%06X", (currentARGB & 0xFFFFFF));
        graphics.text(font, "Hex:", pickerX, hexY + 2, QuestScreen.COL_TEXT);

        if (isHexEditing) {
            drawEditableText(graphics, hexQuery, pickerX + 30, hexY, 65, 12, true);
        } else {
            graphics.fill(pickerX + 30, hexY, pickerX + 95, hexY + 12, QuestScreen.COL_INPUT_BG);
            graphics.outline(pickerX + 30, hexY, 65, 12, QuestScreen.COL_PANEL_DIVIDER);
            graphics.text(font, hex, pickerX + 35, hexY + 2, QuestScreen.COL_TEXT);
        }

        // 4. Alpha Slider (Horizontal)
        int alphaLabelY = hexY + 16;
        graphics.text(font, "Alpha:", pickerX, alphaLabelY + 2, QuestScreen.COL_TEXT);

        int alphaY = alphaLabelY + 12;
        int sliderW = b.w() - 10; // Extend to full window width (minus padding)

        graphics.fill(pickerX, alphaY, pickerX + sliderW, alphaY + 10, 0xFF000000); // BG
        for (int i = 0; i < sliderW; i++) {
            float a = i / (float) sliderW;
            int color = ((int)(a * 255) << 24) | (HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00FFFFFF);
            graphics.fill(pickerX + i, alphaY, pickerX + i + 1, alphaY + 10, color);
        }
        graphics.outline(pickerX, alphaY, sliderW, 10, QuestScreen.COL_UI_BORDER);

        // 5. Indicators
        int selX = pickerX + (int)(hsb[1] * sqSize);
        int selY = pickerY + (int)((1.0f - hsb[2]) * sqSize);
        graphics.outline(selX - 2, selY - 2, 4, 4, QuestScreen.COL_UI_BORDER); // SB marker
        int hMarkerY = pickerY + (int)(hsb[0] * sqSize);
        graphics.fill(hueX - 2, hMarkerY - 1, hueX + hueW + 2, hMarkerY + 1, QuestScreen.COL_UI_BORDER); // Hue marker
        int aMarkerX = pickerX + (int)(alpha * sliderW);
        graphics.fill(aMarkerX - 1, alphaY - 2, aMarkerX + 1, alphaY + 12, QuestScreen.COL_UI_BORDER); // Alpha marker
    }

    /**
     * Logic to map mouse position within the picker bounds back to a color.
     */
    public int getColorAt(double mouseX, double mouseY, PickerBounds b, int currentARGB, boolean draggingSB, boolean draggingHue, boolean draggingAlpha) {
        int pickerX = b.x() + 5;
        int pickerY = b.y() + b.barHeight() + 5;
        int sqSize = 70;
        int hueX = pickerX + sqSize + 10;
        int hexY = pickerY + sqSize + 8;
        
        int alphaLabelY = hexY + 16;
        int alphaY = alphaLabelY + 12;
        int alphaSliderW = b.w() - 10;

        int a = (currentARGB >> 24) & 0xFF;
        float[] hsb = RGBtoHSB((currentARGB >> 16) & 0xFF, (currentARGB >> 8) & 0xFF, currentARGB & 0xFF, null);

        if (draggingSB) {
            // Clamp S and V between 0.0 and 1.0 based on mouse position relative to the square
            float s = Math.max(0, Math.min(1, (float) (mouseX - pickerX) / sqSize));
            float v = Math.max(0, Math.min(1, 1.0f - (float) (mouseY - pickerY) / sqSize));
            return (a << 24) | (HSBtoRGB(hsb[0], s, v) & 0x00FFFFFF);
        } else if (draggingHue) {
            // Clamp H between 0.0 and 1.0 based on mouse position relative to the slider height
            float h = Math.max(0, Math.min(1, (float) (mouseY - pickerY) / sqSize));
            return (a << 24) | (HSBtoRGB(h, hsb[1], hsb[2]) & 0x00FFFFFF);
        } else if (draggingAlpha) {
            float alphaPercent = Math.max(0, Math.min(1, (float) (mouseX - pickerX) / alphaSliderW));
            return ((int)(alphaPercent * 255) << 24) | (HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00FFFFFF);
        }

        return currentARGB;
    }

    /**
     * Moves the cursor up or down in a multi-line context.
     * @param direction -1 for Up, 1 for Down.
     */
    public void moveCursorVertical(QuestScreen screen, int direction, boolean shift) {
        int boxWidth = 240 - 20; // Matches the width used in renderLargeTextEditor
        var wrappedLines = this.font.split(Component.literal(this.searchQuery), boxWidth);
        if (wrappedLines.isEmpty()) return;

        List<String> lineTexts = new ArrayList<>();
        List<Integer> lineStarts = new ArrayList<>();
        int nextSearchIndex = 0;
        int currentLineIdx = -1;

        // 1. Reconstruct line strings and track their start indices in the source searchQuery
        for (var line : wrappedLines) {
            StringBuilder sb = new StringBuilder();
            line.accept((idx, style, cp) -> { sb.append((char)cp); return true; });
            String s = sb.toString();

            int lineStartIdx = this.searchQuery.indexOf(s, nextSearchIndex);
            if (lineStartIdx == -1) lineStartIdx = nextSearchIndex;
            int lineEndIdx = lineStartIdx + s.length();

            lineTexts.add(s);
            lineStarts.add(lineStartIdx);

            // Identify which line the cursor is currently on
            if (currentLineIdx == -1 && this.cursorIndex <= lineEndIdx) {
                currentLineIdx = lineTexts.size() - 1;
            }
            nextSearchIndex = lineEndIdx;
        }

        if (currentLineIdx == -1) currentLineIdx = lineTexts.size() - 1;

        int targetLineIdx = currentLineIdx + direction;
        int newGlobalIndex;

        if (direction == -1 && currentLineIdx == 0) {
            // Case: Pressing UP on the first line -> Move to start
            newGlobalIndex = 0;
        } else if (direction == 1 && currentLineIdx == lineTexts.size() - 1) {
            // Case: Pressing DOWN on the last line -> Move to end
            newGlobalIndex = this.searchQuery.length();
        } else {
            if (targetLineIdx < 0 || targetLineIdx >= lineTexts.size()) return;

            // 2. Calculate current horizontal pixel offset
            String curLineText = lineTexts.get(currentLineIdx);
            int relIdx = this.cursorIndex - lineStarts.get(currentLineIdx);
            int currentX = font.width(curLineText.substring(0, Math.max(0, Math.min(relIdx, curLineText.length()))));

            // 3. Find the closest character index on the target line matching that pixel offset
            String targetLineText = lineTexts.get(targetLineIdx);
            int bestRelIdx = 0;
            int minDiff = Integer.MAX_VALUE;
            for (int i = 0; i <= targetLineText.length(); i++) {
                int targetX = font.width(targetLineText.substring(0, i));
                int diff = Math.abs(targetX - currentX);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestRelIdx = i;
                }
            }
            newGlobalIndex = lineStarts.get(targetLineIdx) + bestRelIdx;
        }

        // 4. Update cursor and selection
        if (shift) {
            if (this.selectionStart == -1) this.selectionStart = this.cursorIndex;
            this.cursorIndex = newGlobalIndex;
            this.selectionEnd = this.cursorIndex;
        } else {
            this.cursorIndex = newGlobalIndex;
            this.selectionStart = -1;
            this.selectionEnd = -1;
        }
    }

    private void renderEntityIcon(GuiGraphicsExtractor graphics, EntityType<?> type, int x, int y, int iconSize) {
        if (type == null) return;
        LivingEntity living = entityCache.get(type);
        if (living == null) {
            try {
                Level level = Minecraft.getInstance().level;
                if (level == null) return;
                Entity created = type.create(level, EntitySpawnReason.COMMAND);
                if (created instanceof LivingEntity le) {
                    entityCache.put(type, le);
                    living = le;
                }
            } catch (Exception ignored) {}
        }

        if (living != null) {
            float maxDim = Math.max(living.getBbHeight(), living.getBbWidth());

            // THREE-ZONE PIECEWISE SCALING
            float multiplier;
            if (maxDim <= 2.0f) {
                float progress = Math.max(0, (maxDim - 0.1f) / (2.0f - 0.1f));
                multiplier = 0.48f + (progress * (0.28f - 0.48f));
            } else if (maxDim <= 4.0f) {
                float progress = (maxDim - 2.0f) / (4.0f - 2.0f);
                multiplier = 0.28f + (progress * (0.40f - 0.28f));
            } else {
                float progress = Math.min(1.0f, (maxDim - 4.0f) / (16.0f - 4.0f));
                multiplier = 0.40f + (progress * (1.45f - 0.40f));
            }

            int scale = Math.max(1, (int) ((iconSize * multiplier) / Math.max(0.1f, maxDim)));

            SimplyQuests.LOGGER.info("[TASK RENDER] Type: {} | Grid: ({}, {}) | Size: {} | Scale: {}", type.getDescription().getString(), x, y, iconSize, scale);

            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, x, y, x + iconSize, y + iconSize, scale, 0, 0, 0, living
            );
        }
    }

    /**
     * Dedicated renderer for Quest Nodes on the main canvas.
     * Uses Local Origin translation to bypass Scissor Box coordinate conflicts.
     */
    private void renderQuestEntityIcon(GuiGraphicsExtractor graphics, EntityType<?> type, int x, int y, int iconSize) {
        if (type == null) return;
        LivingEntity living = entityCache.get(type);
        if (living == null) {
            try {
                Level level = Minecraft.getInstance().level;
                if (level == null) return;
                Entity created = type.create(level, EntitySpawnReason.COMMAND);
                if (created instanceof LivingEntity le) {
                    entityCache.put(type, le);
                    living = le;
                }
            } catch (Exception ignored) {}
        }

        if (living != null) {
            float maxDim = Math.max(living.getBbHeight(), living.getBbWidth());
            if (maxDim <= 0) maxDim = 1.0f;

            // 1. Piecewise Scaling (Your exact logic)
            float multiplier;
            if (maxDim <= 2.0f) multiplier = 0.48f + (Math.max(0, (maxDim - 0.1f) / (2.0f - 0.1f)) * (0.28f - 0.48f));
            else if (maxDim <= 4.0f) multiplier = 0.28f + (((maxDim - 2.0f) / (4.0f - 2.0f)) * (0.40f - 0.28f));
            else multiplier = 0.40f + (Math.min(1.0f, (maxDim - 4.0f) / (16.0f - 4.0f)) * (1.45f - 0.40f));

            // 2. Sample Screen Position
            // We translate to the node's position and immediately sample the matrix to find the screen pixels.
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            Matrix3x2f matrix = graphics.pose();
            float screenX = matrix.m20();
            float screenY = matrix.m21();
            float zoom = matrix.m00();
            graphics.pose().popMatrix(); // Immediately return to the main canvas stack

            // 3. Absolute Projection
            // We calculate the physical pixel coordinates for the renderer.
            float sSize = iconSize * zoom;
            int feetY = (int) (screenY + (sSize * 0.85f));
            int centerX = (int) (screenX + (sSize / 2f));
            int centerY = (int) (screenY + (sSize / 2f));
            int scaledEntity = (int) (Math.max(1, (iconSize * multiplier) / Math.max(0.1f, maxDim)) * zoom);

            // 4. Clean Render Pass
            // We push and reset to Identity so the InventoryRenderer works in screen space.
            graphics.pose().pushMatrix();
            graphics.pose().identity();
            
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, 
                    (int)screenX, (int)screenY, (int)(screenX + sSize), feetY,
                    scaledEntity, 
                    centerX, centerY, centerX, 
                    living
            );
            
            graphics.pose().popMatrix();
        }
    }
}