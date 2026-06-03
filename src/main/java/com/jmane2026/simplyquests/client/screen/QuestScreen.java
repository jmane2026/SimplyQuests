package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.client.ClientQuestEvents;
import com.jmane2026.simplyquests.client.SimplyQuestsClientPacketHandler;
import com.jmane2026.simplyquests.client.screen.input.CanvasHandler;
import com.jmane2026.simplyquests.client.screen.input.EditorHandler;
import com.jmane2026.simplyquests.client.screen.input.PickerHandler;
import com.jmane2026.simplyquests.config.SimplyQuestsConfig;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.network.*;
import com.jmane2026.simplyquests.quest.*;
import com.jmane2026.simplyquests.util.QuestClientData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector2f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class QuestScreen extends Screen {
    public final List<CanvasText> allCanvasTexts = new ArrayList<>();
    public final List<QuestCanvasImage> allCanvasImages = new ArrayList<>();
    private static final Map<String, Identifier> DYNAMIC_IMAGES = new HashMap<>();
    private static final Set<String> PENDING_REQUESTS = new HashSet<>();

    public QuestCanvasImage selectedCanvasImage = null;

    public enum ManipulationMode {NONE, SCALE, ROTATE, ALPHA}

    public ManipulationMode currentImageMode = ManipulationMode.NONE;
    public double initialRotateAngle = 0;
    public double initialImageRotation = 0;
    public boolean isDraggingAlphaSlider = false;
    public boolean isDraggingScaleHandle = false;
    public boolean isDraggingRotateHandle = false;
    public int scaleHandleIndex = -1;
    private double startX, startY, startW, startH;

    public double offsetX = 0;
    public double offsetY = 0;
    public double zoom = 1.0;
    private String pendingChapterName = null;

    public SidebarGroup editingGroup = null;
    public SidebarChapter editingChapter = null;
    public String sidebarSearchQuery = "";
    public int sidebarTextScrollOffset = 0;
    public double sidebarScrollOffset = 0;
    private int totalSidebarContentHeight = 0;
    public boolean needsSidebarScrollToBottom = false;

    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean wasDraggingLastFrame = false;

    private static final int MIN_SIDEBAR_WIDTH = 10;
    public static final int MAX_SIDEBAR_WIDTH = 125;
    public double currentSidebarWidth = MIN_SIDEBAR_WIDTH;

    private long lastTimeMillis = Util.getMillis();

    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();

    public SidebarChapter selectedChapter = null;
    public Quest selectedQuest = null;
    private double descScrollOffset = 0;

    public static int COL_UI_BG = SimplyQuestsConfig.UI_BG.get();
    public static int COL_UI_INNER_BG = SimplyQuestsConfig.UI_INNER_BG.get();
    public static int COL_UI_BORDER = SimplyQuestsConfig.UI_BORDER.get();
    public static int COL_SIDEBAR_BG = SimplyQuestsConfig.SIDEBAR_BG.get();
    public static int COL_SIDEBAR_BORDER = SimplyQuestsConfig.SIDEBAR_BORDER.get();
    public static int COL_PANEL_HEADER = SimplyQuestsConfig.PANEL_HEADER.get();
    public static int COL_PANEL_DIVIDER = SimplyQuestsConfig.PANEL_DIVIDER.get();
    public static int COL_BUTTON_BASE = SimplyQuestsConfig.BUTTON_BASE.get();
    public static int COL_BUTTON_HOVER_DELTA = 0x222222;
    public static int COL_INPUT_BG = SimplyQuestsConfig.INPUT_BG.get();
    public static int COL_TEXT = SimplyQuestsConfig.TEXT.get();

    public static int COL_STATE_LOCKED = SimplyQuestsConfig.STATE_LOCKED.get();
    public static int COL_STATE_AVAILABLE = SimplyQuestsConfig.STATE_AVAILABLE.get();
    public static int COL_STATE_PARTIAL = SimplyQuestsConfig.STATE_PARTIAL.get();
    public static int COL_STATE_COMPLETED = SimplyQuestsConfig.STATE_COMPLETED.get();

    public static int COL_HOVER_UI = SimplyQuestsConfig.HOVER_UI.get();
    public static int COL_HOVER_MENU = SimplyQuestsConfig.HOVER_MENU.get();
    public static int COL_SELECTION = SimplyQuestsConfig.SELECTION.get();
    public static int COL_TEXT_GOLD = SimplyQuestsConfig.TEXT_GOLD.get();
    public static int COL_TEXT_SELECTED = SimplyQuestsConfig.TEXT_SELECTED.get();
    public static int COL_ERROR = SimplyQuestsConfig.ERROR.get();
    public static int COL_TOOLTIP_BG = SimplyQuestsConfig.TOOLTIP_BG.get();
    public static int COL_GRID = SimplyQuestsConfig.GRID.get();
    public static int COL_DIM = SimplyQuestsConfig.DIM.get();
    public static int COL_SLIDER_TRACK = SimplyQuestsConfig.SLIDER_TRACK.get();
    public static int COL_GHOST_BORDER = SimplyQuestsConfig.GHOST_BORDER.get();
    public static int COL_GHOST_FILL = SimplyQuestsConfig.GHOST_FILL.get();

    public int taskPage = 0;
    private long lastTaskPageFlip = 0;
    public int rewardPage = 0;
    public long lastRewardPageFlip = 0;

    public double popupX = -1;
    public double popupY = -1;
    public boolean isDraggingPopup = false;
    public double dragOffsetX = 0;
    public double dragOffsetY = 0;

    public boolean isTaskContextMenu = false;
    public boolean isContextMenuOpen = false;
    public boolean isSideBarContextMenu = false;
    public boolean isSideBarEntryMenu = false;
    public boolean isImageContextMenu = false;
    public boolean isTextContextMenu = false;
    public boolean isTextEditorOpen = false;
    public boolean isSettingsOpen = false;
    public String editingConfigColor = null;
    public int settingsScrollOffset = 0;
    public int pendingPickerColor = 0xFFFFFFFF;
    public static boolean anyClaimableCache = false;
    public static boolean anyUnclaimedGeneralCache = false;
    public boolean isRewardContextMenu = false;
    public boolean isRewardSummaryOpen = false;
    public List<QuestReward> rewardsToShow = new ArrayList<>();
    public int summaryPage = 0;

    public boolean isChoiceModalOpen = false;
    public QuestReward activeChoiceBundle = null;
    public QuestReward selectedChoice = null;

    public CanvasText editingCanvasText = null;
    public CanvasText originalCanvasText = null;
    public CanvasText movingCanvasText = null;
    public QuestCanvasImage movingCanvasImage = null;
    public double contextMenuX;
    public double contextMenuY;

    public Quest originalQuest;
    public SidebarEntry sidebarTargetEntry = null;
    public QuestTask taskToModify = null;
    public QuestTask originalTask = null;
    public QuestTask sidebarTargetTask = null;
    public boolean tempUseAsIcon = false;
    public QuestReward rewardToModify = null;
    public QuestReward originalReward = null;
    public QuestReward movingReward = null;
    public QuestReward sidebarTargetReward = null;

    public QuestTask movingTask = null;
    public QuestTask submittingTask = null;
    public boolean isItemSubmissionOpen = false;
    public boolean isTaskEditorOpen = false;
    public boolean isRewardEditorOpen = false;
    public SidebarGroup sidebarTargetParentGroup = null;
    public SidebarChapter sidebarTargetChapter = null;
    public SidebarChapter movingSidebarChapter = null;
    public SidebarGroup movingSidebarGroup = null;
    public Quest questToModify = null;
    public Quest movingQuest = null;
    public static double GRID_SNAP = SimplyQuestsConfig.GRID_SIZE.get();
    public boolean isDraggingTextSizeSlider = false;
    public boolean isDraggingHue = false;
    public boolean isDraggingSB = false;
    public boolean isDraggingAlpha = false;
    public boolean isDraggingSizeSlider = false;

    public int pickerX = 0;
    public boolean isDraggingGridSlider = false;
    public int pickerY = 0;
    public boolean isDraggingPickerWindow = false;

    public boolean suppressPanning = false;
    public boolean isEditorOpen = false;
    public boolean isEditingChapterIcon = false;
    public final QuestEditorUI editorUI = new QuestEditorUI();

    private static final String[][] CONFIG_ITEM_MAP = {
            {"Main Window BG", "COL_UI_BG"}, {"Editor Area BG", "COL_UI_INNER_BG"}, {"Window Border", "COL_UI_BORDER"},
            {"Sidebar BG", "COL_SIDEBAR_BG"}, {"Sidebar Border", "COL_SIDEBAR_BORDER"},
            {"Header BG", "COL_PANEL_HEADER"}, {"Lines & Dividers", "COL_PANEL_DIVIDER"},
            {"Button Base", "COL_BUTTON_BASE"}, {"Text Input BG", "COL_INPUT_BG"},
            {"Primary Text", "COL_TEXT"}, {"Hover Accent Text", "COL_TEXT_GOLD"}, {"Selected Node Text", "COL_TEXT_SELECTED"},
            {"Error / Delete", "COL_ERROR"}, {"Locked State", "COL_STATE_LOCKED"}, {"Available State", "COL_STATE_AVAILABLE"},
            {"In Progress State", "COL_STATE_PARTIAL"}, {"Completed State", "COL_STATE_COMPLETED"},
            {"Sidebar Hover", "COL_HOVER_UI"}, {"Menu Item Hover", "COL_HOVER_MENU"},
            {"Selection", "COL_SELECTION"}, {"Grid Lines", "COL_GRID"},
            {"Tooltip BG", "COL_TOOLTIP_BG"}, {"Slider Track BG", "COL_SLIDER_TRACK"},
            {"Screen Dimming", "COL_DIM"},
            {"Ghost Border", "COL_GHOST_BORDER"},
            {"Ghost Fill", "COL_GHOST_FILL"}
    };
    private static final int ROW_HEIGHT = 30;

    public QuestScreen() {
        super(Component.literal("SimplyQuests Tree"));
    }

    public final List<Quest> allQuests = new ArrayList<>();
    public final Map<String, Quest> questLookup = new HashMap<>();

    public void registerQuest(Quest quest) {
        if (questLookup.containsKey(quest.getId())) {
            allQuests.remove(questLookup.get(quest.getId()));
        }

        this.allQuests.add(quest);
        this.questLookup.put(quest.getId(), quest);
    }

    public static int getStateColor(QuestState state) {
        return switch (state) {
            case LOCKED -> COL_STATE_LOCKED;
            case AVAILABLE -> COL_STATE_AVAILABLE;
            case PARTIAL -> COL_STATE_PARTIAL;
            case COMPLETED -> COL_STATE_COMPLETED;
        };
    }

    private void updateQuestStates() {
        var completedIds = SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS;
        var taskProgress = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS;

        for (Quest quest : this.allQuests) {
            if (completedIds.contains(quest.getId())) {
                quest.setState(QuestState.COMPLETED);
                continue;
            }

            boolean allDependenciesCompleted = true;
            for (String depId : quest.getDependencies()) {
                if (!completedIds.contains(depId)) {
                    allDependenciesCompleted = false;
                    break;
                }
            }

            if (!allDependenciesCompleted) {
                quest.setState(QuestState.LOCKED);
            } else {
                boolean anyStarted = false;
                for (QuestTask t : quest.getTasks()) {
                    if (taskProgress.getOrDefault(t.getId(), 0) > 0) anyStarted = true;
                }
                quest.setState(anyStarted ? QuestState.PARTIAL : QuestState.AVAILABLE);
            }
        }

        for (SidebarEntry entry : this.sidebarEntries) {
            if (entry instanceof SidebarChapter ch) {
                updateSidebarChapterState(ch);
            } else if (entry instanceof SidebarGroup group) {
                for (SidebarChapter ch : group.getChapters()) {
                    updateSidebarChapterState(ch);
                }
            }
        }
    }

    private void updateSidebarChapterState(SidebarChapter ch) {
        List<Quest> chapterQuests = allQuests.stream().filter(q -> q.getChapterName().equals(ch.getId())).toList();
        if (chapterQuests.isEmpty()) return;

        boolean anyCompleted = false;
        boolean allNonOptionalCompleted = true;
        int nonOptionalCount = 0;

        for (Quest q : chapterQuests) {
            if (q.getState() == QuestState.COMPLETED) anyCompleted = true;
            if (!q.isOptional()) {
                nonOptionalCount++;
                if (q.getState() != QuestState.COMPLETED) allNonOptionalCompleted = false;
            }
        }

        if (nonOptionalCount > 0 ? allNonOptionalCompleted : anyCompleted) ch.setState(QuestState.COMPLETED);
        else if (anyCompleted) ch.setState(QuestState.PARTIAL);
        else ch.setState(QuestState.AVAILABLE);
    }

    @Override
    public void init() {
        String previousId = (this.selectedChapter != null) ? this.selectedChapter.getId() : null;
        double liveX = this.offsetX;
        double liveY = this.offsetY;
        double liveZoom = this.zoom;

        super.init();

        if (checkPermissions()) {
            QuestGlobalState.isEditModeEnabled = SimplyQuestsClientPacketHandler.IS_EDIT_MODE_ALLOWED;
        } else {
            QuestGlobalState.isEditModeEnabled = false;
        }

        this.sidebarEntries.clear();
        this.allQuests.clear();
        this.questLookup.clear();
        this.allCanvasImages.clear();
        this.allCanvasTexts.clear();

        if (this.sidebarEntries.isEmpty()) {
            Map<Identifier, QuestChapter> chapterMap = SimplyQuestsClientPacketHandler.getChapters();
            List<QuestGroup> groupDefinitions = SimplyQuestsClientPacketHandler.getGroups();

            Map<SidebarEntry, Integer> rootOrderMap = new HashMap<>();
            Map<String, SidebarGroup> groupMap = new HashMap<>();

            for (QuestGroup gDef : groupDefinitions) {
                String displayName = (gDef.getTitle() != null && !gDef.getTitle().isEmpty()) ? gDef.getTitle() : gDef.getName();
                SidebarGroup g = new SidebarGroup(displayName, gDef.getColor());
                g.setName(gDef.getName());

                Boolean localExpanded = QuestClientData.isGroupExpanded(gDef.getName());
                boolean targetExpanded = (localExpanded != null) ? localExpanded : gDef.isExpanded();
                if (g.isExpanded() != targetExpanded) g.toggleExpanded();

                groupMap.put(gDef.getName(), g);
                rootOrderMap.put(g, gDef.getOrder());
            }

            List<QuestChapter> sortedChapters = new ArrayList<>(chapterMap.values());
            sortedChapters.sort(Comparator
                    .comparingInt(QuestChapter::getGroupOrder)
                    .thenComparingInt(QuestChapter::getChapterOrder));

            for (QuestChapter chapter : sortedChapters) {
                String groupName = chapter.getGroupName();
                String displayName = (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) ? chapter.getTitle() : chapter.getName();
                SidebarChapter sideChapter = new SidebarChapter(displayName);
                sideChapter.setId(chapter.getName());
                sideChapter.setIconStack(new ItemStack(chapter.getIcon()));
                sideChapter.setState(chapter.getState());
                sideChapter.setOffsetX(chapter.getOffsetX());
                sideChapter.setOffsetY(chapter.getOffsetY());
                sideChapter.setZoom(chapter.getZoom());

                if (groupName == null || groupName.isEmpty() || groupName.equals("Ungrouped")) {
                    rootOrderMap.put(sideChapter, chapter.getGroupOrder());
                } else {
                    SidebarGroup group = groupMap.get(Quest.sanitizePath(groupName));
                    if (group == null) {
                        String sanitizedGroupName = Quest.sanitizePath(groupName);
                        group = new SidebarGroup(groupName, COL_TEXT);
                        groupMap.put(sanitizedGroupName, group);
                        rootOrderMap.put(group, chapter.getGroupOrder());
                    }
                    if (!group.getChapters().contains(sideChapter)) {
                        group.addChapter(sideChapter);
                    }
                }

                for (Quest q : chapter.getQuests()) {
                    registerQuest(q);
                }
                for (CanvasText ct : chapter.getCanvasTexts()) {
                    ct.setChapterName(chapter.getName());
                    this.allCanvasTexts.add(ct);
                }
                for (QuestCanvasImage ci : chapter.getCanvasImages()) {
                    ci.setChapterName(chapter.getName());
                    this.allCanvasImages.add(ci);
                }
            }

            List<SidebarEntry> sortedRoot = new ArrayList<>(rootOrderMap.keySet());
            sortedRoot.sort(Comparator.comparingInt(rootOrderMap::get));
            this.sidebarEntries.addAll(sortedRoot);

            updateQuestStates();
        }

        if (this.selectedChapter != null) {
            this.pendingChapterName = this.selectedChapter.getId();
        } else {
            this.pendingChapterName = QuestClientData.getLastChapter();
        }

        this.lastTimeMillis = Util.getMillis();
        this.zoom = QuestClientData.getZoom();

        boolean found = false;
        if (this.pendingChapterName != null) {
            for (SidebarEntry entry : this.sidebarEntries) {
                if (entry instanceof SidebarGroup group) {
                    for (SidebarChapter chapter : group.getChapters()) {
                        if (chapter.getId().equals(this.pendingChapterName)) {
                            this.selectedChapter = chapter;
                            found = true;
                            break;
                        }
                    }
                } else if (entry instanceof SidebarChapter chapter) {
                    if (chapter.getId().equals(this.pendingChapterName)) {
                        this.selectedChapter = chapter;
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        }

        if (!found && !this.sidebarEntries.isEmpty()) {
            for (SidebarEntry entry : this.sidebarEntries) {
                if (entry instanceof SidebarGroup group && !group.getChapters().isEmpty()) {
                    this.selectedChapter = group.getChapters().get(0);
                    break;
                } else if (entry instanceof SidebarChapter chapter) {
                    this.selectedChapter = chapter;
                    break;
                }
            }
        }

        if (this.selectedChapter != null) {
            if (this.selectedChapter.getId().equals(previousId)) {
                this.offsetX = liveX;
                this.offsetY = liveY;
                this.zoom = liveZoom;
            } else {
                QuestClientData.ViewState localView = QuestClientData.getChapterViewState(this.selectedChapter.getId());
                if (localView != null) {
                    this.offsetX = localView.x();
                    this.offsetY = localView.y();
                    this.zoom = localView.zoom();
                } else {
                    this.offsetX = this.selectedChapter.getOffsetX();
                    this.offsetY = this.selectedChapter.getOffsetY();
                    this.zoom = this.selectedChapter.getZoom();
                }
            }
        } else {
            this.offsetX = 0;
            this.offsetY = 0;
            this.zoom = QuestClientData.getZoom();
        }

        this.pendingChapterName = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        checkPendingRefresh();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        float absoluteCenterX = (float) (this.width / 2.0);
        float absoluteCenterY = (float) (this.height / 2.0);

        boolean hoveringClaimAll = false;
        boolean isSubEditorOpen = isTaskEditorOpen || isRewardEditorOpen;

        Minecraft mc = Minecraft.getInstance();
        long windowHandle = GLFW.glfwGetCurrentContext();
        boolean isLeftButtonDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (!isLeftButtonDown) {

            if (this.isDraggingAlphaSlider || this.isDraggingScaleHandle || this.isDraggingRotateHandle) {
                if (selectedCanvasImage != null) {
                    saveChapterData(selectedCanvasImage.getChapterName());
                }
            }
            this.isDraggingAlphaSlider = false;
            this.isDraggingScaleHandle = false;
            this.isDraggingRotateHandle = false;
            this.scaleHandleIndex = -1;
        }

        if (this.isDraggingSizeSlider && this.isEditorOpen && this.questToModify != null) {
            if (!isLeftButtonDown) {
                this.isDraggingSizeSlider = false;
            } else {
                int windowWidth = 300;
                int panelX = (this.width - windowWidth) / 2;
                int sliderX = panelX + 100;
                int editBtnWidth = 45;
                int editBtnLeft = panelX + windowWidth - 15 - editBtnWidth;
                int sliderW = editBtnLeft - sliderX - 10;

                float mouseRelX = (float) mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                float newSize = 24f + (progress * (240f - 24f));
                this.questToModify.setSize(newSize);
            }
        }

        if (this.isDraggingTextSizeSlider && this.isTextEditorOpen && this.editingCanvasText != null) {
            if (!isLeftButtonDown) {
                this.isDraggingTextSizeSlider = false;
            } else {
                int windowWidth = 200;
                int panelX = (this.width - windowWidth) / 2;
                int sliderX = panelX + 90;
                int sliderW = 95;

                float mouseRelX = (float) mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                float newScale = 0.5f + (progress * (5.0f - 0.5f));
                this.editingCanvasText.setScale(newScale);
            }
        }

        if (this.isDraggingGridSlider && this.isSettingsOpen) {
            if (!isLeftButtonDown) {
                this.isDraggingGridSlider = false;
            } else {
                int windowWidth = 400;
                int x = (this.width - windowWidth) / 2;
                int colW = 195;
                int nextIdx = CONFIG_ITEM_MAP.length;
                int col = nextIdx % 2;
                int rowX = x + 10 + (col * colW);
                int sliderX = rowX + 70;
                int sliderW = 80;

                float mouseRelX = (float) mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                GRID_SNAP = 4.0 + (progress * (32.0 - 4.0));
                GRID_SNAP = Math.round(GRID_SNAP * 2) / 2.0;
            }
        }

        if (this.isDraggingPickerWindow && editorUI.isColorPickerOpen) {
            if (!isLeftButtonDown) {
                this.isDraggingPickerWindow = false;
            } else {
                this.pickerX = (int) (mouseX - dragOffsetX);
                this.pickerY = (int) (mouseY - dragOffsetY);
            }
        }

        if (editorUI.isColorPickerOpen && (this.isTextEditorOpen || this.isSettingsOpen)) {
            int windowWidth = this.isSettingsOpen ? 400 : 200;
            int windowHeight = this.isSettingsOpen ? 240 : 145;
            int x = (this.width - windowWidth) / 2;
            int y = (this.height - windowHeight) / 2;
            int pickerWidth = 135;
            int pickerHeight = 168;

            QuestEditorUI.PickerBounds b = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, pickerWidth, pickerHeight, 16);

            clampPickerPosition(pickerWidth, pickerHeight);

            if (!isLeftButtonDown) {
                this.isDraggingHue = false;
                this.isDraggingSB = false;
                this.isDraggingAlpha = false;
            } else if (this.isDraggingHue || this.isDraggingSB || this.isDraggingAlpha) {

                this.pendingPickerColor = editorUI.getColorAt(mouseX, mouseY, b, this.pendingPickerColor, isDraggingSB, isDraggingHue, isDraggingAlpha);
                editorUI.hexQuery = String.format("%06X", (this.pendingPickerColor & 0xFFFFFF));
            }
        }

        if (isLeftButtonDown && selectedCanvasImage != null) {
            double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
            double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);
            double angle = -selectedCanvasImage.getRotation();

            if (isDraggingScaleHandle) {

                double dx = worldMouseX - startX;
                double dy = worldMouseY - startY;
                double localMouseX = dx * Math.cos(angle) - dy * Math.sin(angle);
                double localMouseY = dx * Math.sin(angle) + dy * Math.cos(angle);

                double newW = startW;
                double newH = startH;
                double localShiftX = 0;
                double localShiftY = 0;

                double snappedLocalX = Math.round(localMouseX / GRID_SNAP) * GRID_SNAP;
                double snappedLocalY = Math.round(localMouseY / GRID_SNAP) * GRID_SNAP;

                if (scaleHandleIndex == 0 || scaleHandleIndex == 7 || scaleHandleIndex == 6) {
                    newW = Math.max(GRID_SNAP, startW - snappedLocalX);
                    localShiftX = startW - newW;
                } else if (scaleHandleIndex == 2 || scaleHandleIndex == 3 || scaleHandleIndex == 4) {
                    newW = Math.max(GRID_SNAP, snappedLocalX);
                }

                if (scaleHandleIndex == 0 || scaleHandleIndex == 1 || scaleHandleIndex == 2) {
                    newH = Math.max(GRID_SNAP, startH - snappedLocalY);
                    localShiftY = startH - newH;
                } else if (scaleHandleIndex == 4 || scaleHandleIndex == 5 || scaleHandleIndex == 6) {
                    newH = Math.max(GRID_SNAP, snappedLocalY);
                }

                double worldShiftX = localShiftX * Math.cos(-angle) - localShiftY * Math.sin(-angle);
                double worldShiftY = localShiftX * Math.sin(-angle) + localShiftY * Math.cos(-angle);

                selectedCanvasImage.setX(startX + worldShiftX);
                selectedCanvasImage.setY(startY + worldShiftY);
                selectedCanvasImage.setWidth(newW);
                selectedCanvasImage.setHeight(newH);

            } else if (isDraggingRotateHandle) {

                double centerX = selectedCanvasImage.getX() + selectedCanvasImage.getWidth() / 2.0;
                double centerY = selectedCanvasImage.getY() + selectedCanvasImage.getHeight() / 2.0;

                double currentAngle = Math.atan2(worldMouseY - centerY, worldMouseX - centerX);

                float finalRotation = (float) (initialImageRotation + (currentAngle - initialRotateAngle));
                selectedCanvasImage.setRotation(finalRotation);

            } else if (isDraggingAlphaSlider && currentImageMode == ManipulationMode.ALPHA) {

                double dx = worldMouseX - selectedCanvasImage.getX();
                double dy = worldMouseY - selectedCanvasImage.getY();
                double localX = dx * Math.cos(angle) - dy * Math.sin(angle);

                double relX = worldMouseX - selectedCanvasImage.getX();
                double btnScale = 0.8;

                double hitX = (relX + 25) / btnScale;

                float newAlpha = (float) ((hitX + 110) / 100.0);
                selectedCanvasImage.setAlpha(Math.max(0.05f, Math.min(1.0f, newAlpha)));
            }
        }

        if (this.isDraggingPopup) {
            if (isLeftButtonDown
                    && !this.isContextMenuOpen
                    && this.selectedQuest != null
                    && !this.isEditorOpen) {
                this.popupX = mouseX - this.dragOffsetX;
                this.popupY = mouseY - this.dragOffsetY;
            } else {
                this.isDraggingPopup = false;
            }
        }

        long currentTimeMillis = Util.getMillis();
        float deltaTime = (currentTimeMillis - this.lastTimeMillis) / 1000.0f;
        this.lastTimeMillis = currentTimeMillis;

        boolean isModalOpen = isEditorOpen || isTaskEditorOpen || isRewardEditorOpen || isChoiceModalOpen || isRewardSummaryOpen || isSettingsOpen || isItemSubmissionOpen;
        boolean isHoveringSidebar = (mouseX <= (this.currentSidebarWidth + 2) && !isModalOpen) ||
                isSideBarContextMenu || isSideBarEntryMenu || isSidebarEditing() || isEditingChapterIcon;
        double targetWidth = isHoveringSidebar ? MAX_SIDEBAR_WIDTH : MIN_SIDEBAR_WIDTH;

        float interpolationSpeed = 12.0f;
        this.currentSidebarWidth += (targetWidth - this.currentSidebarWidth) * (1.0 - Math.exp(-interpolationSpeed * deltaTime));

        if (!isLeftButtonDown) {
            this.suppressPanning = false;
        }

        if (isLeftButtonDown
                && mouseX > this.currentSidebarWidth
                && !this.isDraggingPopup
                && !this.isContextMenuOpen
                && this.selectedQuest == null
                && !this.suppressPanning
                && !this.isEditorOpen
                && !this.isTextEditorOpen
                && !this.isSettingsOpen
                && !this.isDraggingScaleHandle
                && this.movingCanvasImage == null
                && !this.isDraggingRotateHandle
                && !this.isDraggingAlphaSlider) {

            double currentX = mc.mouseHandler.xpos();
            double currentY = mc.mouseHandler.ypos();

            if (!this.wasDraggingLastFrame) {
                this.lastMouseX = currentX;
                this.lastMouseY = currentY;
                this.wasDraggingLastFrame = true;
            }

            double deltaX = currentX - this.lastMouseX;
            double deltaY = currentY - this.lastMouseY;
            double guiScale = mc.getWindow().getGuiScale();

            this.offsetX -= (deltaX / guiScale) / this.zoom;
            this.offsetY -= (deltaY / guiScale) / this.zoom;

            this.lastMouseX = currentX;
            this.lastMouseY = currentY;
        } else {

            if (this.wasDraggingLastFrame && this.selectedChapter != null) {
                QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);
            }
            this.wasDraggingLastFrame = false;
        }

        graphics.pose().pushMatrix();

        graphics.pose().translation(new Vector2f(absoluteCenterX, absoluteCenterY));

        graphics.pose().scale((float) this.zoom, (float) this.zoom);

        graphics.pose().translate(new Vector2f((float) -this.offsetX, (float) -this.offsetY));

        drawGrid(graphics);

        renderQuestTree(graphics, mouseX, mouseY, partialTick);

        if (this.movingTask != null && this.selectedQuest != null) {
            long now = Util.getMillis();
            if (now - lastTaskPageFlip > 800) {
                int panelWidth = 300;
                int px = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int) this.popupX;
                int py = (this.popupY == -1) ? (this.height - 200) / 2 : (int) this.popupY;
                int tasksAreaX = px + 10;
                int tasksAreaY = py + 45;
                int slotSize = 20;
                int taskSpacing = 4;
                int slotTotalWidth = slotSize + taskSpacing;
                int maxVisibleTasks = 4;
                List<QuestTask> tasks = this.selectedQuest.getTasks();

                if (tasks.size() > maxVisibleTasks) {

                    if (mouseX >= tasksAreaX && mouseX <= tasksAreaX + 12 &&
                            mouseY >= tasksAreaY && mouseY <= tasksAreaY + slotSize) {
                        if (taskPage > 0) {
                            taskPage--;
                            lastTaskPageFlip = now;
                            playClickSound();
                        }
                    }

                    int rightTriggerX = tasksAreaX + 12 + (maxVisibleTasks * (slotSize + 4));
                    if (mouseX >= rightTriggerX && mouseX <= rightTriggerX + 12 &&
                            mouseY >= tasksAreaY && mouseY <= tasksAreaY + slotSize) {
                        if ((taskPage + 1) * maxVisibleTasks < tasks.size()) {
                            taskPage++;
                            lastTaskPageFlip = now;
                            playClickSound();
                        }
                    }
                }
            }
        }

        if (this.movingReward != null && this.selectedQuest != null) {
            long now = Util.getMillis();
            if (now - lastRewardPageFlip > 800) {
                Rectangle bounds = getDetailsBounds();
                int rewardsAreaX = bounds.x + (bounds.width / 2) + 10;
                int rewardsAreaY = bounds.y + 45;
                int slotSize = 20;
                List<QuestReward> rewards = this.selectedQuest.getRewards();
                int rMaxVisible = 4;

                if (rewards.size() > rMaxVisible) {
                    if (mouseX >= rewardsAreaX && mouseX <= rewardsAreaX + 12 &&
                            mouseY >= rewardsAreaY && mouseY <= rewardsAreaY + slotSize) {
                        if (rewardPage > 0) {
                            rewardPage--;
                            lastRewardPageFlip = now;
                            playClickSound();
                        }
                    } else if (mouseX >= rewardsAreaX + 12 + (rMaxVisible * 24) && mouseX <= rewardsAreaX + 24 + (rMaxVisible * 24) &&
                            mouseY >= rewardsAreaY && mouseY <= rewardsAreaY + slotSize) {
                        if ((rewardPage + 1) * rMaxVisible < rewards.size()) {
                            rewardPage++;
                            lastRewardPageFlip = now;
                            playClickSound();
                        }
                    }
                }
            }
        }

        graphics.pose().popMatrix();

        int sidebarW = (int) this.currentSidebarWidth;

        graphics.fill(0, 0, sidebarW, this.height, COL_SIDEBAR_BG);
        graphics.fill(sidebarW - 1, 0, sidebarW, this.height, COL_SIDEBAR_BORDER);

        if (sidebarW > MIN_SIDEBAR_WIDTH + 2) {
            float textScale = 0.75f;
            double localMouseY = mouseY / textScale;
            double scrollableMouseY = localMouseY + sidebarScrollOffset;
            double localMouseX = mouseX / textScale;

            String pendingOverlayText = null;
            int pendingOverlayX = 0;
            int pendingOverlayY = 0;

            graphics.pose().pushMatrix();
            graphics.pose().scale(textScale, textScale);

            int scaledMaxSidebarW = (int) Math.ceil(MAX_SIDEBAR_WIDTH / textScale);
            int currentScaledW = (int) Math.ceil(sidebarW / textScale);

            graphics.enableScissor(0, 0, currentScaledW, (int) (this.height / textScale));

            if (sidebarW > MIN_SIDEBAR_WIDTH + 15) {
                int gearX = currentScaledW - 15;
                boolean hoveringGear = localMouseX >= gearX && localMouseX <= gearX + 10 && localMouseY >= 3 && localMouseY <= 13;
                int gearColor = hoveringGear ? COL_TEXT_GOLD : COL_TEXT;

                graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.SETTINGS_ICON, gearX, 4, 0.0f, 0.0f, 10, 10, 10, 10, gearColor);
            }

            if (QuestGlobalState.isEditModeEnabled && sidebarW > MIN_SIDEBAR_WIDTH + 15) {
                int plusX = currentScaledW - 30;
                int plusY = 5;
                boolean hoveringPlus = localMouseX >= plusX - 4 && localMouseX <= plusX + 12 && localMouseY >= plusY - 2 && localMouseY <= plusY + 12;
                int plusColor = hoveringPlus ? COL_TEXT_GOLD : COL_TEXT;
                graphics.text(this.font, Component.literal("+"), plusX, plusY, plusColor);
            }

            graphics.enableScissor(0, 15, currentScaledW, (int) (this.height / textScale));

            graphics.pose().pushMatrix();

            graphics.pose().translate(new Vector2f(0f, -(float) sidebarScrollOffset));

            int currentYPosition = (int) (15 / textScale);
            int localX = (int) (6 / textScale);
            int localChapterX = (int) (14 / textScale);
            int logicalBottom = (int) (this.height / textScale);
            int headerHeight = (int) (15 / textScale);

            for (SidebarEntry entry : this.sidebarEntries) {
                if (entry instanceof SidebarGroup group) {
                    String prefix = group.isExpanded() ? "▼ " : "► ";
                    int solidGroupColor = COL_TEXT;

                    int groupPlusSpace = (QuestGlobalState.isEditModeEnabled && group.isExpanded() && group != editingGroup) ? 15 : 0;
                    int maxGroupWidth = currentScaledW - localX - groupPlusSpace;
                    String fullGroupTitle = prefix + group.getTitle();
                    String displayedGroupTitle = truncate(fullGroupTitle, maxGroupWidth);

                    boolean isGroupTitleHovered = localMouseX >= 0 && localMouseX < currentScaledW &&
                            localMouseY >= headerHeight &&
                            scrollableMouseY >= currentYPosition && scrollableMouseY < currentYPosition + 14;

                    boolean nearGroupPlus = false;
                    if (QuestGlobalState.isEditModeEnabled && group.isExpanded() && group != editingGroup) {
                        nearGroupPlus = localMouseX >= currentScaledW - 20;
                    }

                    boolean showGroupOverlay = isGroupTitleHovered && !nearGroupPlus && !fullGroupTitle.equals(displayedGroupTitle) && !isEditorOpen && currentSidebarWidth > (MAX_SIDEBAR_WIDTH - 5);

                    if (showGroupOverlay) {
                        pendingOverlayText = fullGroupTitle;
                        pendingOverlayX = (int) (localX * textScale);
                        pendingOverlayY = (int) ((currentYPosition - sidebarScrollOffset) * textScale);
                    }

                    boolean isGroupMoving = movingSidebarGroup != null;
                    if (!showGroupOverlay) {
                        if (group == editingGroup) {
                            int editorWidth = currentScaledW - localX;
                            updateSidebarScrollOffset(editorWidth);
                            String displayText = sidebarSearchQuery;

                            graphics.enableScissor(localX - 2, currentYPosition - 2, currentScaledW, currentYPosition + 14);
                            graphics.text(this.font, Component.literal(displayText), localX - sidebarTextScrollOffset, currentYPosition, COL_TEXT);

                            if (Util.getMillis() / 500 % 2 == 0) {
                                int cursorX = localX + this.font.width(displayText) - sidebarTextScrollOffset;
                                graphics.fill(cursorX, currentYPosition, cursorX + 1, currentYPosition + 10, COL_TEXT);
                            }
                            graphics.disableScissor();

                        } else {
                            if (isGroupTitleHovered && !isContextMenuOpen && !isGroupMoving && movingSidebarChapter == null && !isEditingChapterIcon) {

                                graphics.fill(0, currentYPosition - 2, currentScaledW - 1, currentYPosition + 14, COL_HOVER_UI);
                            }
                            graphics.text(this.font, Component.literal(displayedGroupTitle), localX, currentYPosition, solidGroupColor);
                        }
                    }

                    if (QuestGlobalState.isEditModeEnabled && group.isExpanded() && group != editingGroup) {
                        int groupPlusX = currentScaledW - 12;
                        boolean hoveringGroupPlus = localMouseX >= groupPlusX - 4 && localMouseX <= groupPlusX + 12 &&
                                scrollableMouseY >= currentYPosition - 2 && scrollableMouseY <= currentYPosition + 12;
                        graphics.text(this.font, Component.literal("+"), groupPlusX, currentYPosition, hoveringGroupPlus ? COL_TEXT_GOLD : COL_TEXT);
                    }

                    currentYPosition += 18;

                    if (group.isExpanded()) {
                        for (SidebarChapter chapter : group.getChapters()) {
                            int chapterRowHeight = 16;
                            boolean isHovered = localMouseX >= 0 && localMouseX < currentScaledW &&
                                    localMouseY >= headerHeight &&
                                    scrollableMouseY >= currentYPosition && scrollableMouseY < currentYPosition + chapterRowHeight;

                            boolean hasIcon = !chapter.getIconStack().isEmpty();
                            int textXOffset = hasIcon ? localChapterX + 18 : localChapterX;
                            int maxChapterWidth = currentScaledW - textXOffset;
                            String fullChapterName = chapter.getName();
                            String displayedChapterName = truncate(fullChapterName, maxChapterWidth);

                            boolean showChapterOverlay = isHovered && !fullChapterName.equals(displayedChapterName) && !isEditorOpen && currentSidebarWidth > (MAX_SIDEBAR_WIDTH - 5);

                            if (!isEditingChapterIcon && !showChapterOverlay && (chapter == this.selectedChapter || (isHovered && !isContextMenuOpen && movingSidebarChapter == null))) {

                                graphics.fill(0, currentYPosition - 2, currentScaledW - 1, currentYPosition + 14, COL_HOVER_UI);
                            }

                            if (hasIcon) {
                                graphics.item(chapter.getIconStack(), localChapterX, currentYPosition - 1);
                                if (hasClaimableRewards(chapter.getId())) {
                                    graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, localChapterX + 10, currentYPosition - 4, 0.0f, 0.0f, 8, 8, 8, 8);
                                }
                            }

                            if (showChapterOverlay) {
                                pendingOverlayText = fullChapterName;
                                pendingOverlayX = (int) (textXOffset * textScale);
                                pendingOverlayY = (int) ((currentYPosition + 3 - sidebarScrollOffset) * textScale);
                            } else if (chapter == editingChapter) {
                                int editorWidth = currentScaledW - textXOffset;
                                updateSidebarScrollOffset(editorWidth);
                                String displayText = sidebarSearchQuery;

                                graphics.enableScissor(textXOffset - 2, currentYPosition - 2, currentScaledW, currentYPosition + 16);
                                graphics.text(this.font, Component.literal(displayText), textXOffset - sidebarTextScrollOffset, currentYPosition + 3, COL_TEXT);

                                if (Util.getMillis() / 500 % 2 == 0) {
                                    int cursorX = textXOffset + this.font.width(displayText) - sidebarTextScrollOffset;
                                    graphics.fill(cursorX, currentYPosition + 3, cursorX + 1, currentYPosition + 13, COL_TEXT);
                                }
                                graphics.disableScissor();

                            } else {
                                graphics.text(this.font, Component.literal(displayedChapterName), textXOffset, currentYPosition + 3, getStateColor(chapter.getState()));
                            }
                            currentYPosition += chapterRowHeight;
                        }
                    }
                } else if (entry instanceof SidebarChapter chapter) {
                    int chapterRowHeight = 16;
                    boolean isHovered = localMouseX >= 0 && localMouseX < currentScaledW &&
                            localMouseY >= headerHeight &&
                            scrollableMouseY >= currentYPosition && scrollableMouseY < currentYPosition + chapterRowHeight;

                    boolean hasIcon = !chapter.getIconStack().isEmpty();
                    int textXoffset = hasIcon ? localX + 18 : localX;
                    int maxStandaloneWidth = currentScaledW - textXoffset;
                    String fullStandaloneName = chapter.getName();
                    String displayedStandaloneName = truncate(fullStandaloneName, maxStandaloneWidth);

                    boolean showStandaloneOverlay = isHovered && !fullStandaloneName.equals(displayedStandaloneName) && !isEditorOpen && currentSidebarWidth > (MAX_SIDEBAR_WIDTH - 5);

                    if (!isEditingChapterIcon && !showStandaloneOverlay && (chapter == this.selectedChapter || (isHovered && !isContextMenuOpen && movingSidebarChapter == null))) {

                        graphics.fill(0, currentYPosition - 2, currentScaledW - 1, currentYPosition + 14, COL_HOVER_UI);
                    }

                    if (hasIcon) {
                        graphics.item(chapter.getIconStack(), localX, currentYPosition - 1);
                        if (hasClaimableRewards(chapter.getId())) {
                            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, localX + 10, currentYPosition - 4, 0.0f, 0.0f, 8, 8, 8, 8);
                        }
                    }

                    if (showStandaloneOverlay) {
                        pendingOverlayText = fullStandaloneName;
                        pendingOverlayX = (int) (textXoffset * textScale);
                        pendingOverlayY = (int) ((currentYPosition + 3 - sidebarScrollOffset) * textScale);
                    } else if (chapter == editingChapter) {
                        int editorWidth = currentScaledW - textXoffset;
                        updateSidebarScrollOffset(editorWidth);
                        String displayText = sidebarSearchQuery;

                        graphics.enableScissor(textXoffset - 2, currentYPosition - 2, currentScaledW, currentYPosition + 16);
                        graphics.text(this.font, Component.literal(displayText), textXoffset - sidebarTextScrollOffset, currentYPosition + 3, COL_TEXT);

                        if (Util.getMillis() / 500 % 2 == 0) {
                            int cursorX = textXoffset + this.font.width(displayText) - sidebarTextScrollOffset;
                            graphics.fill(cursorX, currentYPosition + 3, cursorX + 1, currentYPosition + 13, COL_TEXT);
                        }
                        graphics.disableScissor();

                    } else {
                        graphics.text(this.font, Component.literal(displayedStandaloneName), textXoffset, currentYPosition + 3, getStateColor(chapter.getState()));
                    }
                    currentYPosition += chapterRowHeight;
                }
                currentYPosition += 6;
            }

            if (this.movingSidebarChapter != null) {
                int tempY = (int) (15 / textScale);
                int snappedY = -1;

                for (SidebarEntry entry : this.sidebarEntries) {
                    if (entry == movingSidebarChapter) continue;

                    if (scrollableMouseY < tempY + 4) {
                        snappedY = tempY - 3;
                        break;
                    }

                    if (entry instanceof SidebarGroup group) {
                        tempY += 18;
                        if (group.isExpanded()) {

                            if (group.getChapters().isEmpty()) {
                                if (scrollableMouseY < tempY + 16) {
                                    snappedY = tempY - 3;
                                    break;
                                }
                            }
                            for (SidebarChapter chapter : group.getChapters()) {
                                if (chapter == movingSidebarChapter) continue;

                                if (scrollableMouseY < tempY + 16) {

                                    snappedY = (scrollableMouseY < tempY + 8) ? tempY - 3 : tempY + 16 - 3;
                                    break;
                                }
                                tempY += 16;
                            }
                        }
                    } else {

                        if (scrollableMouseY < tempY + 16) {
                            snappedY = (scrollableMouseY < tempY + 8) ? tempY - 3 : tempY + 16 - 3;
                        } else {
                            tempY += 16;
                        }
                    }

                    if (snappedY != -1) break;
                    tempY += 6;
                }

                if (snappedY == -1) snappedY = tempY - 3;

                graphics.fill(0, snappedY, currentScaledW - 1, snappedY + 1, COL_UI_BORDER);
            }

            if (this.movingSidebarGroup != null) {
                int tempY = (int) (15 / textScale);
                int snappedY = -1;

                for (SidebarEntry entry : this.sidebarEntries) {
                    if (entry == movingSidebarGroup) continue;

                    if (scrollableMouseY < tempY + 4) {
                        snappedY = tempY - 3;
                        break;
                    }

                    if (entry instanceof SidebarGroup group) {
                        tempY += 18;
                        if (group.isExpanded()) {

                            tempY += group.getChapters().size() * 16;
                        }
                    } else {
                        tempY += 16;
                    }

                    if (snappedY != -1) break;
                    tempY += 6;
                }

                if (snappedY == -1) snappedY = tempY - 3;

                graphics.fill(0, snappedY, currentScaledW - 1, snappedY + 1, COL_UI_BORDER);
            }

            this.totalSidebarContentHeight = currentYPosition;

            int visibleHeight = (int) (this.height / textScale) - 20;
            int maxScroll = Math.max(0, this.totalSidebarContentHeight - visibleHeight);
            if (this.needsSidebarScrollToBottom) {
                this.sidebarScrollOffset = maxScroll;
                this.needsSidebarScrollToBottom = false;
            }
            this.sidebarScrollOffset = Math.max(0, Math.min(maxScroll, this.sidebarScrollOffset));

            graphics.pose().popMatrix();
            graphics.pose().popMatrix();
            graphics.disableScissor();
            graphics.disableScissor();

            if (pendingOverlayText != null) {
                renderStaticTextOverlay(graphics, pendingOverlayText, pendingOverlayX, pendingOverlayY, this.width);
            }
        }

        if (this.isEditorOpen) {
            int windowWidth = 300;
            int windowHeight = 250;
            int windowX = (this.width - windowWidth) / 2;
            int windowY = (this.height - windowHeight) / 2;
            graphics.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, COL_UI_BG);
            drawBorder(graphics, windowX, windowY, windowWidth, windowHeight, COL_UI_BORDER);
            Quest displayQuest = (this.questToModify != null) ? this.questToModify : new Quest("new_quest_id", "Default Chapter", "New Quest", 0.0, 0.0);
            this.editorUI.render(graphics, mouseX, mouseY, windowX, windowY, 300, 250, displayQuest, this.allQuests);
            if (this.editorUI.isSubTitleOpen || this.editorUI.isDescriptionOpen) {
                this.editorUI.renderLargeTextEditor(graphics, mouseX, mouseY, this.width, this.height);
            }
        }

        if (this.selectedQuest != null && !this.isEditorOpen) {
            renderQuestDetails(graphics, mouseX, mouseY);
        }

        if (this.isTaskEditorOpen && this.taskToModify != null) {
            int windowWidth = 300;
            int windowHeight = 250;
            int windowX = (this.width - windowWidth) / 2;
            int windowY = (this.height - windowHeight) / 2;

            graphics.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, COL_UI_BG);
            drawBorder(graphics, windowX, windowY, windowWidth, windowHeight, COL_UI_BORDER);

            this.editorUI.renderTaskEditor(graphics, mouseX, mouseY, windowX, windowY, 300, 250, this.taskToModify, this.selectedQuest, this.tempUseAsIcon);
        }

        if (this.isRewardEditorOpen && this.rewardToModify != null) {
            int windowW = 300, windowH = 200;
            int windowX = (this.width - windowW) / 2;
            int windowY = (this.height - windowH) / 2;
            graphics.fill(windowX, windowY, windowX + windowW, windowY + windowH, COL_UI_BG);
            drawBorder(graphics, windowX, windowY, windowW, windowH, COL_UI_BORDER);
            this.editorUI.renderRewardEditor(graphics, mouseX, mouseY, windowX, windowY, windowW, windowH, this.rewardToModify);
        }

        if (this.isTextEditorOpen && this.editingCanvasText != null) {
            renderCanvasTextEditor(graphics, mouseX, mouseY);
        }

        if (this.isSettingsOpen) {
            renderSettingsModal(graphics, mouseX, mouseY);
        }

        if (this.isItemSubmissionOpen && this.submittingTask != null) {
            renderItemSubmissionModal(graphics, mouseX, mouseY);
        }

        if (editorUI.isIconPickerOpen) {
            int ph = (this.isRewardEditorOpen) ? 200 : 250;
            int panelX = isEditingChapterIcon ? (int) currentSidebarWidth - 300 : (this.width - 300) / 2;
            int panelY = isEditingChapterIcon ? 20 : (this.height - ph) / 2;
            QuestEditorUI.PickerBounds b = editorUI.getPickerBounds(panelX, panelY, ph);
            editorUI.renderPickerFrame(graphics, b, true, editorUI.searchQuery, () -> {
                List<Item> filteredItems = editorUI.getCachedIcons();
                int columns = QuestEditorUI.PICKER_COLUMNS;
                int cellSize = 18;
                int totalContentHeight = (int) Math.ceil(filteredItems.size() / (double) columns) * cellSize;
                int visibleHeight = b.h() - b.barHeight() - 10;
                if (editorUI.scrollOffset > Math.max(0, totalContentHeight - visibleHeight)) {
                    editorUI.scrollOffset = Math.max(0, totalContentHeight - visibleHeight);
                }

                graphics.enableScissor(b.x() + 1, b.y() + b.barHeight(), b.x() + b.w() - 1, b.y() + b.h() - 1);
                String hoveredName = null;
                boolean showCheckmark = editorUI.searchQuery.isEmpty() || "checkmark".contains(editorUI.searchQuery.toLowerCase());
                int totalEntries = filteredItems.size() + (showCheckmark ? 1 : 0);

                for (int i = 0; i < totalEntries; i++) {
                    int ix = b.x() + 5 + (i % columns) * cellSize;
                    int iy = (b.y() + 16 + 5) + (i / columns) * cellSize - (int) editorUI.scrollOffset;

                    if (mouseX > ix && mouseX < ix + 16 && mouseY > iy && mouseY < iy + 16) {
                        graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, COL_HOVER_MENU);
                        if (showCheckmark && i == 0) hoveredName = "Checkmark";
                        else {
                            Item item = filteredItems.get(showCheckmark ? i - 1 : i);
                            hoveredName = item.getDefaultInstance().getHoverName().getString();
                        }
                    }

                    if (iy >= b.y() + b.barHeight() && iy < b.y() + b.h() - 1) {
                        if (showCheckmark && i == 0) {
                            editorUI.drawCheckmark(graphics, ix, iy, 16);
                        } else {
                            Item item = filteredItems.get(showCheckmark ? i - 1 : i);
                            graphics.item(new ItemStack(item), ix, iy);
                        }
                    }
                }
                graphics.disableScissor();
                if (hoveredName != null) {
                    renderSimpleTooltip(graphics, hoveredName, mouseX, mouseY);
                }
            });
        }

        if (hasAnyClaimableRewards() && !isEditorOpen && !isTaskEditorOpen && !isRewardEditorOpen && !isTextEditorOpen && !isSettingsOpen && !isItemSubmissionOpen) {
            int btnSize = 16;
            int btnX = this.width - btnSize - 5;
            int btnY = 5;
            hoveringClaimAll = mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
            int color = hoveringClaimAll ? COL_TEXT_GOLD : 0xFFFFFFFF;

            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ALL_ICON, btnX, btnY, 0.0f, 0.0f, btnSize, btnSize, btnSize, btnSize, color);

            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, btnX + btnSize - 7, btnY - 1, 0.0f, 0.0f, 8, 8, 8, 8);
        }

        if (this.isRewardSummaryOpen) {
            renderRewardSummary(graphics, mouseX, mouseY);
        }

        if (this.isChoiceModalOpen) {
            renderRewardChoiceModal(graphics, mouseX, mouseY);
        }

        if (isContextMenuOpen) {
            renderContextMenu(graphics);
        }

        if (this.selectedChapter != null && mouseX > this.currentSidebarWidth) {
            Quest hoveredQuest = null;
            for (Quest quest : this.allQuests) {

                if (quest.getChapterName().equals(this.selectedChapter.getId()) && isMouseOverNode(mouseX, mouseY, quest)) {
                    hoveredQuest = quest;
                    break;
                }
            }

            if (hoveredQuest != null && !isInputBlocked() && this.selectedQuest == null && !editorUI.isPickerOpen()) {
                String tooltipText = "";

                if (!hoveredQuest.getLockedBy().isEmpty()) {
                    tooltipText = "§cLocked for editing by: " + hoveredQuest.getLockedBy();
                } else if (isInputBlocked()) {
                    return;
                } else {
                    tooltipText = hoveredQuest.getTitle();
                    Map<Identifier, QuestChapter> chapterMap = SimplyQuestsClientPacketHandler.getChapters();

                    Identifier chId = Identifier.fromNamespaceAndPath("simplyquests", Quest.sanitizePath(hoveredQuest.getChapterName()));

                    QuestChapter chapter = chapterMap.get(chId);

                    if (chapter != null) {
                        String chTitle = (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) ? chapter.getTitle() : chapter.getName();
                        String grpTitle = "";

                        if (chapter.getGroupName() != null && !chapter.getGroupName().isEmpty()) {
                            String gId = Quest.sanitizePath(chapter.getGroupName());
                            grpTitle = SimplyQuestsClientPacketHandler.getGroups().stream()
                                    .filter(g -> g.getName().equals(gId))
                                    .map(QuestGroup::getTitle)
                                    .findFirst().orElse("");
                        }
                        tooltipText = grpTitle.isEmpty() ? chTitle + " > " + hoveredQuest.getTitle() : grpTitle + " > " + chTitle + " > " + hoveredQuest.getTitle();
                    }
                }
                renderAnchoredQuestTooltip(graphics, tooltipText);
            }
        }

        if (!isModalOpen && !editorUI.isPickerOpen() && mouseX > (int) this.currentSidebarWidth) {
            double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
            double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

            double displayX, displayY;

            if (movingQuest != null) {
                displayX = Math.round(worldMouseX / GRID_SNAP) * GRID_SNAP;
                displayY = Math.round(worldMouseY / GRID_SNAP) * GRID_SNAP;
            } else if (movingCanvasImage != null || movingCanvasText != null) {
                displayX = Math.round((worldMouseX - dragOffsetX) / GRID_SNAP) * GRID_SNAP;
                displayY = Math.round((worldMouseY - dragOffsetY) / GRID_SNAP) * GRID_SNAP;
            } else {
                displayX = worldMouseX;
                displayY = worldMouseY;
            }

            double unitX = displayX / GRID_SNAP;
            double unitY = -displayY / GRID_SNAP;

            if (unitY == 0.0) unitY = 0.0;

            String coords = String.format("§7[ %.1f, %.1f ]", unitX, unitY);
            float subScale = 0.7f;

            graphics.pose().pushMatrix();

            graphics.pose().translate(this.width - (font.width(coords) * subScale) - 5, this.height - (font.lineHeight * subScale) - 5);
            graphics.pose().scale(subScale, subScale);
            graphics.text(font, coords, 0, 0, 0x88FFFFFF, false);
            graphics.pose().popMatrix();
        }

        if (hoveringClaimAll) {
            renderSimpleTooltip(graphics, "Claim All", mouseX, mouseY);
        }
    }

    private void renderRewardSummary(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, COL_DIM);

        int panelW = 220;
        int panelH = 180;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, COL_UI_BG);
        drawBorder(graphics, x, y, panelW, panelH, COL_UI_BORDER);

        graphics.centeredText(font, Component.literal("Rewards Claimed"), x + panelW / 2, y + 10, COL_TEXT_GOLD);

        int itemsPerPage = 20;
        String hoveredLabel = null;
        int startIdx = summaryPage * itemsPerPage;
        int displayCount = Math.min(itemsPerPage, rewardsToShow.size() - startIdx);

        int gridX = x + 15;
        int gridY = y + 30;
        int cols = 5;

        for (int i = 0; i < displayCount; i++) {
            QuestReward reward = rewardsToShow.get(startIdx + i);
            int ix = gridX + (i % cols) * 40;
            int iy = gridY + (i / cols) * 32;

            boolean isHovered = mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20;
            int innerColor = isHovered ? COL_PANEL_HEADER : COL_UI_BG;

            editorUI.drawRewardIcon(graphics, reward, COL_STATE_COMPLETED, innerColor, ix, iy, 20, false);

            String amt = reward.getType() == QuestReward.RewardType.COMMAND ? "CMD" : "x" + reward.getCount();
            float scale = 0.5f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(ix + 10 - (font.width(amt) * scale / 2), iy + 22);
            graphics.pose().scale(scale, scale);
            graphics.text(font, amt, 0, 0, COL_TEXT);
            graphics.pose().popMatrix();

            if (mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20) {
                hoveredLabel = switch (reward.getType()) {
                    case ITEM ->
                            reward.getCount() + "x " + reward.getItem().getDefaultInstance().getHoverName().getString();
                    case XP -> reward.getCount() + " XP";
                    case COMMAND -> "Execute Command";
                };
            }
        }

        if (rewardsToShow.size() > itemsPerPage) {
            String pageText = (summaryPage + 1) + " / " + ((rewardsToShow.size() + itemsPerPage - 1) / itemsPerPage);
            graphics.centeredText(font, Component.literal(pageText), x + panelW / 2, y + panelH - 40, COL_TEXT);

            if (summaryPage > 0)
                graphics.text(font, "<", x + 15, y + panelH - 40, mouseX >= x + 15 && mouseX <= x + 25 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 ? COL_TEXT_GOLD : COL_TEXT);
            if ((summaryPage + 1) * itemsPerPage < rewardsToShow.size())
                graphics.text(font, ">", x + panelW - 25, y + panelH - 40, mouseX >= x + panelW - 25 && mouseX <= x + panelW - 15 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 ? COL_TEXT_GOLD : COL_TEXT);
        }

        int btnW = 60;
        int btnH = 16;
        int btnX = x + (panelW - btnW) / 2;
        int btnY = y + panelH - 22;
        editorUI.drawButton(graphics, mouseX, mouseY, btnX, btnY, btnW, btnH, "Confirm", COL_BUTTON_BASE);

        if (hoveredLabel != null) {
            renderSimpleTooltip(graphics, hoveredLabel, mouseX, mouseY);
        }
    }

    private void renderRewardChoiceModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, COL_DIM);

        int panelW = 220;
        int panelH = 160;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, COL_UI_BG);
        drawBorder(graphics, x, y, panelW, panelH, COL_UI_BORDER);
        graphics.centeredText(font, Component.literal("Choose a Reward"), x + panelW / 2, y + 10, COL_TEXT_GOLD);

        List<QuestReward> choices = new ArrayList<>();
        choices.add(activeChoiceBundle);
        choices.addAll(activeChoiceBundle.getSubRewards());
        int gridX = x + 15;
        int gridY = y + 30;
        int cols = 5;
        String hoveredLabel = null;

        for (int i = 0; i < choices.size(); i++) {
            QuestReward reward = choices.get(i);
            int ix = gridX + (i % cols) * 40;
            int iy = gridY + (i / cols) * 32;

            boolean isHovered = mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20;
            int outerColor = (reward == selectedChoice) ? COL_STATE_COMPLETED : COL_STATE_AVAILABLE;
            int innerColor = isHovered ? COL_PANEL_HEADER : COL_UI_BG;

            editorUI.drawRewardIcon(graphics, reward, outerColor, innerColor, ix, iy, 20, false);
            renderRewardLabel(graphics, reward, ix, iy, 20);

            if (isHovered) {
                hoveredLabel = getRewardTooltip(reward);
            }
        }

        int btnW = 50;
        int btnH = 14;
        int btnY = y + panelH - 22;

        editorUI.drawButton(graphics, mouseX, mouseY, x + 15, btnY, btnW, btnH, "Cancel", COL_BUTTON_BASE);

        int submitColor = (selectedChoice != null) ? COL_BUTTON_BASE : COL_STATE_LOCKED;
        editorUI.drawButton(graphics, mouseX, mouseY, x + panelW - 15 - btnW, btnY, btnW, btnH, "Submit", submitColor);

        if (hoveredLabel != null) {
            renderSimpleTooltip(graphics, hoveredLabel, mouseX, mouseY);
        }
    }

    private void handleRewardChoiceClicks(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int panelW = 220, panelH = 160;
        int x = (this.width - panelW) / 2, y = (this.height - panelH) / 2;
        int btnY = y + panelH - 22;

        List<QuestReward> choices = new ArrayList<>();
        choices.add(activeChoiceBundle);
        choices.addAll(activeChoiceBundle.getSubRewards());
        int gridX = x + 15, gridY = y + 30;
        for (int i = 0; i < choices.size(); i++) {
            int ix = gridX + (i % 5) * 40;
            int iy = gridY + (i / 5) * 32;
            if (mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20) {
                selectedChoice = choices.get(i);
                playClickSound();
                return;
            }
        }

        if (mouseX >= x + 15 && mouseX <= x + 15 + 50 && mouseY >= btnY && mouseY <= btnY + 14) {
            isChoiceModalOpen = false;
            playClickSound();
        } else if (selectedChoice != null && mouseX >= x + panelW - 65 && mouseX <= x + panelW - 15 && mouseY >= btnY && mouseY <= btnY + 14) {

            ClientPacketDistributor.sendToServer(new ClaimRewardPayload(selectedChoice.getId()));
            isChoiceModalOpen = false;
            playClickSound();
        }
    }

    private void renderQuestTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.selectedChapter == null) return;

        float globalArrowPhase = (Util.getMillis() % 1000000) * 0.01f % 8.0f;

        String activeChapterId = this.selectedChapter.getId();
        if (activeChapterId == null) return;

        float absoluteCenterX = (float) (this.width / 2.0);
        float absoluteCenterY = (float) (this.height / 2.0);
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        float ghostX = 0, ghostY = 0;
        float imgGhostX = 0, imgGhostY = 0;
        float textGhostX = 0, textGhostY = 0;

        if (this.movingQuest != null || this.movingCanvasImage != null || this.movingCanvasText != null) {
            if (this.movingQuest != null) {
                ghostX = (float) (Math.round(worldMouseX / GRID_SNAP) * GRID_SNAP - (this.movingQuest.getSize() / 2.0));
                ghostY = (float) (Math.round(worldMouseY / GRID_SNAP) * GRID_SNAP - (this.movingQuest.getSize() / 2.0));
            }
            if (this.movingCanvasImage != null) {
                imgGhostX = (float) (Math.round((worldMouseX - this.dragOffsetX) / GRID_SNAP) * GRID_SNAP);
                imgGhostY = (float) (Math.round((worldMouseY - this.dragOffsetY) / GRID_SNAP) * GRID_SNAP);
            }
            if (this.movingCanvasText != null) {
                textGhostX = (float) (Math.round((worldMouseX - this.dragOffsetX) / GRID_SNAP) * GRID_SNAP);
                textGhostY = (float) (Math.round((worldMouseY - this.dragOffsetY) / GRID_SNAP) * GRID_SNAP);
            }
        }

        for (QuestCanvasImage ci : this.allCanvasImages) {
            if (!ci.getChapterName().equals(activeChapterId)) continue;

            Identifier tex = getOrRequestImage(ci.getImageId());
            if (tex == null) continue;

            float halfW = (float) ci.getWidth() / 2f;
            float halfH = (float) ci.getHeight() / 2f;

            float alphaMod = (ci == this.movingCanvasImage) ? 0.3f : 1.0f;

            graphics.pose().pushMatrix();

            graphics.pose().translate((float) ci.getX() + halfW, (float) ci.getY() + halfH);

            graphics.pose().rotate(ci.getRotation());

            graphics.pose().translate(-halfW, -halfH);

            int alphaTint = ((int) (ci.getAlpha() * 255 * alphaMod) << 24) | 0xFFFFFF;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, (int) ci.getWidth(), (int) ci.getHeight(), (int) ci.getWidth(), (int) ci.getHeight(), alphaTint);

            if (ci == selectedCanvasImage) {

                graphics.outline(-1, -1, (int) ci.getWidth() + 2, (int) ci.getHeight() + 2, COL_TEXT_GOLD);

                int s = 6;
                int w = (int) ci.getWidth();
                int h = (int) ci.getHeight();

                double dx = worldMouseX - (ci.getX() + ci.getWidth() / 2.0);
                double dy = worldMouseY - (ci.getY() + ci.getHeight() / 2.0);
                double angle = -ci.getRotation();
                double localMouseX = (dx * Math.cos(angle) - dy * Math.sin(angle)) + (ci.getWidth() / 2.0);
                double localMouseY = (dx * Math.sin(angle) + dy * Math.cos(angle)) + (ci.getHeight() / 2.0);

                if (currentImageMode == ManipulationMode.SCALE) {
                    int[][] hBoxes = {
                            {-s, -s, s, s},
                            {0, -s, w, s},
                            {w, -s, s, s},
                            {w, 0, s, h},
                            {w, h, s, s},
                            {0, h, w, s},
                            {-s, h, s, s},
                            {-s, 0, s, h}
                    };

                    for (int i = 0; i < hBoxes.length; i++) {
                        int[] b = hBoxes[i];

                        boolean isHovered = localMouseX >= b[0] - 1 && localMouseX <= b[0] + b[2] + 1 &&
                                localMouseY >= b[1] - 1 && localMouseY <= b[1] + b[3] + 1;
                        boolean isActive = isHovered || (i == scaleHandleIndex);

                        int color = isActive ? COL_TEXT_GOLD : COL_UI_BORDER;
                        graphics.outline(b[0], b[1], b[2], b[3], color);
                    }
                } else if (currentImageMode == ManipulationMode.ROTATE) {
                    int handleSize = 8;

                    int[][] hBoxes = {
                            {-handleSize, -handleSize, handleSize, handleSize},
                            {w, -handleSize, handleSize, handleSize},
                            {w, h, handleSize, handleSize},
                            {-handleSize, h, handleSize, handleSize}
                    };

                    for (int i = 0; i < hBoxes.length; i++) {
                        int[] b = hBoxes[i];
                        boolean isHovered = localMouseX >= b[0] - 1 && localMouseX <= b[0] + b[2] + 1 &&
                                localMouseY >= b[1] - 1 && localMouseY <= b[1] + b[3] + 1;
                        boolean isActive = isHovered || isDraggingRotateHandle;

                        int color = isActive ? COL_TEXT_GOLD : COL_UI_BORDER;
                        graphics.outline(b[0], b[1], b[2], b[3], color);
                    }
                }
            }
            graphics.pose().popMatrix();

            if (ci == selectedCanvasImage) {
                graphics.pose().pushMatrix();
                graphics.pose().translate((float) ci.getX(), (float) ci.getY());

                graphics.pose().pushMatrix();
                graphics.pose().translate(-25, 0);
                graphics.pose().scale(0.8f, 0.8f);

                renderManipulationButton(graphics, 0, 0, QuestEditorUI.SCALE_ICON, currentImageMode == ManipulationMode.SCALE);
                renderManipulationButton(graphics, 0, 20, QuestEditorUI.ROTATE_ICON, currentImageMode == ManipulationMode.ROTATE);
                renderManipulationButton(graphics, 0, 40, QuestEditorUI.ALPHA_ICON, currentImageMode == ManipulationMode.ALPHA);

                if (currentImageMode == ManipulationMode.ALPHA) {
                    renderStandaloneAlphaSlider(graphics, ci);
                }
                graphics.pose().popMatrix();
                graphics.pose().popMatrix();
            }
        }

        for (CanvasText ct : this.allCanvasTexts) {
            if (!ct.getChapterName().equals(activeChapterId)) continue;

            float alphaMod = (ct == this.movingCanvasText) ? 0.3f : 1.0f;

            graphics.pose().pushMatrix();
            graphics.pose().translate((float) ct.getX(), (float) ct.getY());
            graphics.pose().scale(ct.getScale(), ct.getScale());

            int color = (ct.getColor() & 0x00FFFFFF) | ((int) (((ct.getColor() >> 24) & 0xFF) * alphaMod) << 24);
            graphics.text(this.font, Component.literal(ct.getText()), 0, 0, color);

            graphics.pose().popMatrix();
        }

        for (Quest quest : this.allQuests) {

            if (!quest.getChapterName().equals(activeChapterId)) continue;

            for (String depId : quest.getDependencies()) {
                Quest dependency = this.questLookup.get(depId);

                if (dependency != null && dependency.getChapterName().equals(activeChapterId)) {

                    float startX = (dependency == this.movingQuest ? ghostX : (float) dependency.getX()) + (dependency.getSize() / 2.0f);
                    float startY = (dependency == this.movingQuest ? ghostY : (float) dependency.getY()) + (dependency.getSize() / 2.0f);
                    float endX = (quest == this.movingQuest ? ghostX : (float) quest.getX()) + (quest.getSize() / 2.0f);
                    float endY = (quest == this.movingQuest ? ghostY : (float) quest.getY()) + (quest.getSize() / 2.0f);

                    int parentColor = getStateColor(dependency.getState());
                    int childColor = getStateColor(quest.getState());

                    float r1 = dependency.getSize() * 0.25f;
                    float r2 = quest.getSize() * 0.25f;

                    float arrowPhase = 0;
                    boolean inputBlocked = isInputBlocked();
                    boolean isParentHovered = !inputBlocked && isMouseOverNode(mouseX, mouseY, dependency);
                    boolean isChildHovered = !inputBlocked && isMouseOverNode(mouseX, mouseY, quest);

                    if (isParentHovered || isChildHovered || dependency == this.movingQuest || quest == this.movingQuest) {
                        arrowPhase = globalArrowPhase;
                    }

                    drawVectorLine(graphics, startX, startY, endX, endY, 4.0f, parentColor, childColor, r1, r2, arrowPhase);
                }
            }
        }

        for (Quest quest : this.allQuests) {

            if (!quest.getChapterName().equals(activeChapterId)) continue;

            int x = (int) quest.getX();
            int y = (int) quest.getY();
            int size = (int) quest.getSize();

            boolean isSelected = (quest == this.selectedQuest);
            boolean isHovered = isMouseOverNode(mouseX, mouseY, quest);

            int stateColor = getStateColor(quest.getState());

            drawQuestNode(graphics, x, y, size, isSelected, isHovered, stateColor, quest);

            editorUI.drawQuestIcon(graphics, quest, x, y, size);

            boolean canClaimAny = quest.getState() == QuestState.COMPLETED &&
                    !quest.getRewards().isEmpty() &&
                    quest.getRewards().stream().anyMatch(r -> !SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(r.getId()));

            if (canClaimAny) {

                int badgeSize = Math.max(6, (int) (size * 0.35f));
                int bx = x + size - (badgeSize / 2) - 2;
                int by = y - (badgeSize / 2) + 2;

                graphics.pose().pushMatrix();
                graphics.pose().translate(bx, by);
                float badgeScale = badgeSize / 8.0f;
                graphics.pose().scale(badgeScale, badgeScale);
                graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, 0, 0, 0.0f, 0.0f, 8, 8, 8, 8);
                graphics.pose().popMatrix();
            }
        }

        if (this.movingQuest != null) {
            QuestShapeRenderer.render(this.movingQuest.getShape(), graphics, (int) ghostX, (int) ghostY, (int) this.movingQuest.getSize(), COL_GHOST_BORDER, COL_GHOST_FILL);
        }

        if (this.movingCanvasImage != null) {
            Identifier tex = getOrRequestImage(this.movingCanvasImage.getImageId());
            if (tex != null) {
                float halfW = (float) this.movingCanvasImage.getWidth() / 2f;
                float halfH = (float) this.movingCanvasImage.getHeight() / 2f;
                graphics.pose().pushMatrix();
                graphics.pose().translate(imgGhostX + halfW, imgGhostY + halfH);
                graphics.pose().rotate(this.movingCanvasImage.getRotation());
                graphics.pose().translate(-halfW, -halfH);

                int ghostAlpha = (int) (this.movingCanvasImage.getAlpha() * 255 * 0.5f);
                int alphaTint = (ghostAlpha << 24) | 0xFFFFFF;
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f,
                        (int) this.movingCanvasImage.getWidth(), (int) this.movingCanvasImage.getHeight(),
                        (int) this.movingCanvasImage.getWidth(), (int) this.movingCanvasImage.getHeight(), alphaTint);
                graphics.pose().popMatrix();
            }
        }

        if (this.movingCanvasText != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(textGhostX, textGhostY);
            graphics.pose().scale(this.movingCanvasText.getScale(), this.movingCanvasText.getScale());

            int ghostColor = (this.movingCanvasText.getColor() & 0x00FFFFFF) | 0x80000000;
            graphics.text(this.font, Component.literal(this.movingCanvasText.getText()), 0, 0, ghostColor);
            graphics.pose().popMatrix();
        }
    }

    public boolean handleImageInteraction(double mouseX, double mouseY, int button) {
        if (selectedCanvasImage == null || (button != 0 && button != 1)) return false;

        float absoluteCenterX = (float) (this.width / 2.0f);
        float absoluteCenterY = (float) (this.height / 2.0);
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        double relX = worldMouseX - selectedCanvasImage.getX();
        double relY = worldMouseY - selectedCanvasImage.getY();
        double btnScale = 0.8;
        double hitX = (relX + 25) / btnScale;
        double hitY = relY / btnScale;

        if (hitX >= 0 && hitX <= 15 && hitY >= 0 && hitY <= 15) {
            currentImageMode = (currentImageMode == ManipulationMode.SCALE) ? ManipulationMode.NONE : ManipulationMode.SCALE;
            playClickSound();
            return true;
        }

        if (hitX >= 0 && hitX <= 15 && hitY >= 20 && hitY <= 35) {
            currentImageMode = (currentImageMode == ManipulationMode.ROTATE) ? ManipulationMode.NONE : ManipulationMode.ROTATE;
            playClickSound();
            return true;
        }

        if (hitX >= 0 && hitX <= 15 && hitY >= 40 && hitY <= 55) {
            currentImageMode = (currentImageMode == ManipulationMode.ALPHA) ? ManipulationMode.NONE : ManipulationMode.ALPHA;
            playClickSound();
            return true;
        }

        double iw = selectedCanvasImage.getWidth();
        double ih = selectedCanvasImage.getHeight();
        double centerX = selectedCanvasImage.getX() + iw / 2.0;
        double centerY = selectedCanvasImage.getY() + ih / 2.0;

        double dx = worldMouseX - centerX;
        double dy = worldMouseY - centerY;
        double angle = -selectedCanvasImage.getRotation();
        double localX = (dx * Math.cos(angle) - dy * Math.sin(angle)) + (iw / 2.0);
        double localY = (dx * Math.sin(angle) + dy * Math.cos(angle)) + (ih / 2.0);

        if (currentImageMode == ManipulationMode.SCALE) {
            int s = 6;

            int[][] hBoxes = {
                    {-s, -s, s, s}, {0, -s, (int) iw, s}, {(int) iw, -s, s, s},
                    {(int) iw, 0, s, (int) ih}, {(int) iw, (int) ih, s, s},
                    {0, (int) ih, (int) iw, s}, {-s, (int) ih, s, s}, {-s, 0, s, (int) ih}
            };

            for (int i = 0; i < hBoxes.length; i++) {
                if (localX >= hBoxes[i][0] - 1 && localX <= hBoxes[i][0] + hBoxes[i][2] + 1 &&
                        localY >= hBoxes[i][1] - 1 && localY <= hBoxes[i][1] + hBoxes[i][3] + 1) {

                    this.scaleHandleIndex = i;
                    this.isDraggingScaleHandle = true;

                    this.startX = selectedCanvasImage.getX();
                    this.startY = selectedCanvasImage.getY();
                    this.startW = selectedCanvasImage.getWidth();
                    this.startH = selectedCanvasImage.getHeight();

                    return true;
                }
            }
        } else if (currentImageMode == ManipulationMode.ROTATE) {
            int hs = 8;

            boolean tl = localX >= -hs && localX <= 0 && localY >= -hs && localY <= 0;
            boolean tr = localX >= iw && localX <= iw + hs && localY >= -hs && localY <= 0;
            boolean bl = localX >= -hs && localX <= 0 && localY >= ih && localY <= ih + hs;
            boolean br = localX >= iw && localX <= iw + hs && localY >= ih && localY <= ih + hs;
            if (tl || tr || bl || br) {
                initialRotateAngle = Math.atan2(worldMouseY - centerY, worldMouseX - centerX);
                initialImageRotation = selectedCanvasImage.getRotation();
                this.isDraggingRotateHandle = true;
                return true;
            }
        } else if (currentImageMode == ManipulationMode.ALPHA) {
            if (hitX >= -110 && hitX <= -10 && hitY >= 42 && hitY <= 52) {
                isDraggingAlphaSlider = true;
                return true;
            }
        }
        return false;
    }

    public boolean isMouseOverImage(double mouseX, double mouseY, QuestCanvasImage ci) {
        float absoluteCenterX = (float) (this.width / 2.0);
        float absoluteCenterY = (float) (this.height / 2.0);
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        double iw = ci.getWidth();
        double ih = ci.getHeight();
        double centerX = ci.getX() + iw / 2.0;
        double centerY = ci.getY() + ih / 2.0;

        double dx = worldMouseX - centerX;
        double dy = worldMouseY - centerY;
        double angle = -ci.getRotation();
        double localX = (dx * Math.cos(angle) - dy * Math.sin(angle)) + (iw / 2.0);
        double localY = (dx * Math.sin(angle) + dy * Math.cos(angle)) + (ih / 2.0);

        return localX >= 0 && localX <= iw && localY >= 0 && localY <= ih;
    }

    public void openImagePicker(double snappedX, double snappedY) {
        Thread pickerThread = new Thread(() -> {
            String path = null;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog("Select Quest Image", "", filters, "PNG files (*.png)", false);
            }

            if (path != null) {
                File file = new File(path);
                String fileName = file.getName();

                if (!fileName.toLowerCase().endsWith(".png")) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§cOnly PNG files can be used as quest decorations!"));
                        }
                    });
                    return;
                }

                try {

                    byte[] data = Files.readAllBytes(file.toPath());
                    Minecraft.getInstance().execute(() -> {
                        ClientPacketDistributor.sendToServer(new UploadImagePayload(fileName, data));

                        QuestCanvasImage ci = new QuestCanvasImage("img_" + System.currentTimeMillis(), fileName, snappedX, snappedY, 64.0, 64.0, 0f, 1.0f);
                        ci.setChapterName(this.selectedChapter.getId());
                        this.allCanvasImages.add(ci);
                        saveChapterData(this.selectedChapter.getId());
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private Identifier getOrRequestImage(String imageId) {
        if (imageId == null || imageId.isEmpty()) return null;

        if (DYNAMIC_IMAGES.containsKey(imageId)) return DYNAMIC_IMAGES.get(imageId);

        if (PENDING_REQUESTS.contains(imageId)) return null;

        PENDING_REQUESTS.add(imageId);

        File cacheFile = Minecraft.getInstance().gameDirectory.toPath().resolve("simplyquests_cache").resolve(imageId).toFile();
        if (cacheFile.exists()) {
            try {
                loadTextureFromFile(imageId, Files.readAllBytes(cacheFile.toPath()));
                return null;
            } catch (Exception ignored) {
            }
        }

        ClientPacketDistributor.sendToServer(new RequestImagePayload(imageId));

        return null;
    }

    public static void loadTextureFromFile(String imageId, byte[] data) {
        Minecraft.getInstance().execute(() -> {
            try {

                PENDING_REQUESTS.remove(imageId);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(data));
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "simplyquests/dynamic/" + imageId, nativeImage);

                Identifier id = Identifier.fromNamespaceAndPath("simplyquests", ("dynamic/" + imageId).toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
                Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
                DYNAMIC_IMAGES.put(imageId, id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void deleteLocalImageCache(String imageId) {

        DYNAMIC_IMAGES.remove(imageId);
        PENDING_REQUESTS.remove(imageId);

        File cacheFile = Minecraft.getInstance().gameDirectory.toPath().resolve("simplyquests_cache").resolve(imageId).toFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    private void drawQuestNode(GuiGraphicsExtractor graphics, int x, int y, int size, boolean isSelected, boolean isHovered, int stateColor, Quest quest) {
        boolean isLocked = !quest.getLockedBy().isEmpty();

        int backgroundColor = (isHovered && !isInputBlocked() && !isContextMenuOpen && !isLocked) ? COL_PANEL_HEADER : COL_UI_BG;

        int borderColor = isLocked ? COL_ERROR : (isSelected ? COL_TEXT_SELECTED : stateColor);

        QuestShapeRenderer.render(quest.getShape(), graphics, x, y, size, borderColor, backgroundColor);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean isMouseOverSidebar = mouseX <= this.currentSidebarWidth;

        if (this.isRewardSummaryOpen && rewardsToShow.size() > 20) {
            if (scrollY > 0 && summaryPage > 0) {
                summaryPage--;
                playClickSound();
            } else if (scrollY < 0 && (summaryPage + 1) * 20 < rewardsToShow.size()) {
                summaryPage++;
                playClickSound();
            }
            return true;
        }

        if (this.isSettingsOpen) {
            int panelH = 240;
            int y = (this.height - panelH) / 2;
            if (mouseY >= y + 30 && mouseY <= y + panelH - 30) {
                int totalRows = (CONFIG_ITEM_MAP.length + 2) / 2;
                int contentHeight = totalRows * 22;
                int maxScroll = Math.max(0, contentHeight - (panelH - 60));

                this.settingsScrollOffset = (int) Math.max(0, Math.min(maxScroll, this.settingsScrollOffset - scrollY * 12));
            }
            return true;
        }

        if (this.selectedQuest != null && !this.isEditorOpen && getDetailsBounds().contains(mouseX, mouseY)) {
            this.descScrollOffset -= scrollY * 10;
            return true;
        }

        if (editorUI.isPickerOpen()) {
            editorUI.scrollOffset -= scrollY * 10;
            editorUI.scrollOffset = Math.max(0, editorUI.scrollOffset);
            return true;
        }

        if (isMouseOverSidebar) {
            float textScale = 0.75f;
            int visibleHeight = (int) (this.height / textScale) - 20;
            int maxScroll = Math.max(0, totalSidebarContentHeight - visibleHeight);
            this.sidebarScrollOffset = Math.max(0, Math.min(maxScroll, this.sidebarScrollOffset - scrollY * 12));
            return true;
        }

        if ((isEditorOpen || this.selectedQuest != null || this.suppressPanning) && !isMouseOverSidebar) {
            return true;
        }

        double zoomFactor = 1.1;
        double oldZoom = this.zoom;

        if (scrollY > 0) this.zoom *= zoomFactor;
        else if (scrollY < 0) this.zoom /= zoomFactor;

        this.zoom = Math.max(0.3, Math.min(this.zoom, 2.0));
        QuestClientData.setZoom(this.zoom);

        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;
        double mouseFromCenterX = mouseX - absoluteCenterX;
        double mouseFromCenterY = mouseY - absoluteCenterY;

        this.offsetX += (mouseFromCenterX / oldZoom) - (mouseFromCenterX / this.zoom);
        this.offsetY += (mouseFromCenterY / oldZoom) - (mouseFromCenterY / this.zoom);

        QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);

        return true;
    }

    private void setContextMenuPos(double x, double y, int optionCount) {
        int w = 110;
        int h = optionCount * 20;

        if (x + w > this.width) x = this.width - w;
        if (y + h > this.height) y = this.height - h;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        this.contextMenuX = x;
        this.contextMenuY = y;
    }

    public void openImageContextMenu(double x, double y, QuestCanvasImage ci) {
        setContextMenuPos(x, y, 2);
        this.isContextMenuOpen = true;
        this.isImageContextMenu = true;
        this.selectedCanvasImage = ci;
        this.questToModify = null;
        playClickSound();
    }

    public void openCanvasContextMenu(double x, double y) {
        setContextMenuPos(x, y, 3);
        this.isContextMenuOpen = true;
        this.questToModify = null;
        this.isTaskContextMenu = false;
        this.isTextContextMenu = false;
        this.isRewardContextMenu = false;
        this.isSideBarContextMenu = false;
        this.isSideBarEntryMenu = false;
        this.isImageContextMenu = false;
        this.editingCanvasText = null;
        playClickSound();
    }

    public void openQuestContextMenu(double x, double y, Quest quest) {
        setContextMenuPos(x, y, 5);
        this.isContextMenuOpen = true;
        this.questToModify = quest;
        this.isTaskContextMenu = false;
        playClickSound();
    }

    public void openTextContextMenu(double x, double y, CanvasText text) {
        setContextMenuPos(x, y, 3);
        this.isContextMenuOpen = true;
        this.isTextContextMenu = true;
        this.editingCanvasText = text;
        playClickSound();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (this.isRewardSummaryOpen) {
            handleRewardSummaryClicks(mouseX, mouseY, button);
            return true;
        }

        if (this.isChoiceModalOpen) {
            handleRewardChoiceClicks(mouseX, mouseY, button);
            return true;
        }

        if (PickerHandler.handle(this, mouseX, mouseY, button)) return true;

        if (selectedCanvasImage != null && handleImageInteraction(mouseX, mouseY, button)) {
            return true;
        }

        if (hasAnyClaimableRewards() && button == 0 && !isEditorOpen && !isTaskEditorOpen && !isRewardEditorOpen && !isTextEditorOpen && !isSettingsOpen && !isItemSubmissionOpen) {
            int btnSize = 16;
            int btnX = this.width - btnSize - 5;
            int btnY = 5;
            if (mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                claimAllRewards();
                return true;
            }
        }

        if (this.isContextMenuOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1 && isClickingContextMenu(mouseX, mouseY)) {
                handleContextMenuClick(mouseX, mouseY);
            } else {
                this.isContextMenuOpen = false;

                this.isSideBarContextMenu = false;
                this.isSideBarEntryMenu = false;
                this.isTaskContextMenu = false;
                this.isRewardContextMenu = false;
                this.isImageContextMenu = false;
                this.isTextContextMenu = false;
                playClickSound();
            }
            return true;
        }

        if (CanvasHandler.handleMoveModes(this, mouseX, mouseY, button)) return true;

        if (EditorHandler.handle(this, mouseX, mouseY, button)) return true;

        if (this.isSettingsOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) handleSettingsClicks(mouseX, mouseY);
            return true;
        }
        if (this.isItemSubmissionOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) handleItemSubmissionClicks(mouseX, mouseY);
            return true;
        }

        if (CanvasHandler.handleDetailsWindow(this, mouseX, mouseY, button)) return true;

        if (CanvasHandler.handleSidebar(this, mouseX, mouseY, button)) return true;
        if (CanvasHandler.handleCanvas(this, mouseX, mouseY, button)) return true;

        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public void onClose() {

        if (this.selectedChapter != null) {
            QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);
            QuestClientData.setLastChapter(this.selectedChapter.getId());
        }

        if (this.originalQuest != null && (isEditorOpen || isTaskEditorOpen || isRewardEditorOpen)) {
            ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), false));
        }
        if (this.editorUI != null) {
            this.editorUI.closePicker();
        }

        if (!DYNAMIC_IMAGES.isEmpty()) {

            PENDING_REQUESTS.clear();
        }

        super.onClose();
    }

    private void handleRewardSummaryClicks(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        int panelW = 220;
        int panelH = 180;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        int btnW = 60;
        int btnH = 16;
        int btnX = x + (panelW - btnW) / 2;
        int btnY = y + panelH - 22;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.isRewardSummaryOpen = false;
            this.rewardsToShow.clear();
            playClickSound();
            return;
        }

        if (rewardsToShow.size() > 20) {
            if (mouseX >= x + 15 && mouseX <= x + 25 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 && summaryPage > 0) {
                summaryPage--;
                playClickSound();
            }
            if (mouseX >= x + panelW - 25 && mouseX <= x + panelW - 15 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 && (summaryPage + 1) * 20 < rewardsToShow.size()) {
                summaryPage++;
                playClickSound();
            }
        }
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 1.0F
                )
        );
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {

            if (editorUI.isColorPickerOpen) {
                editorUI.isColorPickerOpen = false;
                playClickSound();
                return true;
            }

            if (editorUI.isPickerOpen()) {
                editorUI.closePicker();
                return true;
            }

            if (editorUI.isTitleOpen || editorUI.isSubTitleOpen || editorUI.isDescriptionOpen ||
                    editorUI.isNameOpen || editorUI.isQuantityOpen ||
                    editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen || editorUI.isHexEditing) {
                editorUI.closePicker();
                return true;
            }

            if (this.isTaskEditorOpen || this.isRewardEditorOpen || this.isEditorOpen || this.isTextEditorOpen) {
                this.editorUI.closePicker();

                if (this.originalQuest != null) {
                    this.originalQuest.setLockedBy("");
                    ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), false));
                }

                this.isTaskEditorOpen = false;
                this.isRewardEditorOpen = false;
                this.isEditorOpen = false;
                this.isTextEditorOpen = false;
                this.editorUI.isTaskMode = false;
                this.editorUI.isRewardModeOpen = false;
                playClickSound();
                return true;
            }

            if (this.selectedQuest != null) {
                this.selectedQuest = null;
                playClickSound();
                return true;
            }
        }

        if (isSidebarEditing()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                stopSidebarEditing(true);
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                stopSidebarEditing(false);
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !sidebarSearchQuery.isEmpty()) {
                sidebarSearchQuery = sidebarSearchQuery.substring(0, sidebarSearchQuery.length() - 1);
            } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!sidebarSearchQuery.isEmpty()) {
                    sidebarSearchQuery = sidebarSearchQuery.substring(0, sidebarSearchQuery.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (this.movingQuest != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingQuest = null;
            playClickSound();
            return true;
        }

        if (this.movingSidebarChapter != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingSidebarChapter = null;
            playClickSound();
            return true;
        }

        if (this.movingSidebarGroup != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingSidebarGroup = null;
            playClickSound();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (editorUI.isHexEditing) {
                editorUI.isHexEditing = false;
                return true;
            }
            if (editorUI.isColorPickerOpen) {
                commitPickerColor();
                return true;
            }
            if (this.isEditorOpen) {
                if (editorUI.isTitleOpen) {
                    questToModify.setTitle(editorUI.searchQuery);
                    editorUI.closePicker();
                } else if (editorUI.isSubTitleOpen) {
                    questToModify.setSubTitle(editorUI.searchQuery);
                    editorUI.closePicker();
                } else if (editorUI.isDescriptionOpen) {
                    questToModify.setDescription(editorUI.searchQuery);
                    editorUI.closePicker();
                } else {
                    saveChanges();
                    playClickSound();
                }
                return true;
            } else if (this.isTaskEditorOpen) {
                if (editorUI.isQuantityOpen) {
                    try {
                        taskToModify.setRequiredAmount(Integer.parseInt(editorUI.searchQuery));
                    } catch (NumberFormatException ignored) {
                    }
                    editorUI.closePicker();
                } else if (editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen) {
                    try {
                        int val = Integer.parseInt(editorUI.searchQuery);
                        if (editorUI.isXOpen) taskToModify.setTargetX(val);
                        else if (editorUI.isYOpen) taskToModify.setTargetY(val);
                        else if (editorUI.isZOpen) taskToModify.setTargetZ(val);
                    } catch (NumberFormatException ignored) {
                    }
                    editorUI.closePicker();
                } else if (editorUI.isNameOpen) {
                    updateTaskNameAndId(editorUI.searchQuery);
                    editorUI.closePicker();
                } else {

                    if (this.originalQuest != null) {
                        this.originalQuest.setLockedBy("");
                        ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), false));
                    }

                    if (this.originalTask == null) {
                        this.selectedQuest.getTasks().add(this.taskToModify);
                    } else {

                        int index = this.selectedQuest.getTasks().indexOf(this.originalTask);

                        if (index == -1) this.selectedQuest.getTasks().add(this.taskToModify);
                        else {
                            this.selectedQuest.getTasks().set(index, this.taskToModify);
                        }
                    }

                    if (this.tempUseAsIcon) {
                        for (QuestTask t : this.selectedQuest.getTasks()) {
                            t.setIcon(false);
                        }
                        this.taskToModify.setIcon(true);
                        this.selectedQuest.setUseTaskIcon(true);
                        this.selectedQuest.setLogo(this.taskToModify.getIconStack().getItem());
                    } else {
                        this.taskToModify.setIcon(false);

                        boolean anyIcons = this.selectedQuest.getTasks().stream().anyMatch(QuestTask::isIcon);
                        if (!anyIcons) this.selectedQuest.setUseTaskIcon(false);
                    }
                    updateQuestStates();
                    saveChapterData(this.selectedQuest.getChapterName());
                    playClickSound();
                    this.isTaskEditorOpen = false;
                    this.taskToModify = null;
                    this.originalTask = null;
                    this.editorUI.isTaskMode = false;
                }
                return true;
            } else if (this.isRewardEditorOpen) {
                if (editorUI.isQuantityOpen || editorUI.isNameOpen) {

                    if (this.originalQuest != null) {
                        this.originalQuest.setLockedBy("");
                        ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), false));
                    }

                    QuestReward activeTarget = (editorUI.selectedRewardChoiceIndex == -1)
                            ? rewardToModify
                            : rewardToModify.getSubRewards().get(editorUI.selectedRewardChoiceIndex);

                    if (editorUI.isQuantityOpen && activeTarget != null) {
                        try {
                            int amount = Integer.parseInt(editorUI.searchQuery.trim());
                            activeTarget.setCount(amount);
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (editorUI.isNameOpen && activeTarget != null) {
                        activeTarget.setCommand(editorUI.searchQuery);
                    }
                    editorUI.closePicker();
                } else {

                    if (this.originalQuest != null) {
                        this.originalQuest.setLockedBy("");
                        ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), false));
                    }

                    this.rewardToModify.getSubRewards().removeIf(r -> (r.getType() == QuestReward.RewardType.ITEM && r.getItem() == Items.AIR) ||
                            (r.getType() == QuestReward.RewardType.COMMAND && r.getCommand().trim().isEmpty()));

                    if (this.originalReward == null) {
                        this.selectedQuest.getRewards().add(this.rewardToModify);
                    } else {
                        int index = this.selectedQuest.getRewards().indexOf(this.originalReward);

                        if (index == -1) this.selectedQuest.getRewards().add(this.rewardToModify);
                        else this.selectedQuest.getRewards().set(index, this.rewardToModify);
                    }
                    saveChapterData(this.selectedQuest.getChapterName());
                    playClickSound();
                    this.isRewardEditorOpen = false;
                    this.rewardToModify = null;
                    this.originalReward = null;
                    this.editorUI.isRewardModeOpen = false;
                    this.editorUI.closePicker();
                }
                return true;
            } else if (this.isTextEditorOpen) {

                editingCanvasText.setText(editorUI.searchQuery);
                if (allCanvasTexts.contains(originalCanvasText)) {
                    int index = allCanvasTexts.indexOf(originalCanvasText);
                    allCanvasTexts.set(index, editingCanvasText);
                } else {
                    allCanvasTexts.add(editingCanvasText);
                }
                saveChapterData(editingCanvasText.getChapterName());
                editorUI.closePicker();
                isTextEditorOpen = false;
                editingCanvasText = null;
                originalCanvasText = null;
                return true;
            }
        }

        if (editorUI.isPickerOpen() || isTextEditorOpen || editorUI.isHexEditing || editorUI.isSubTitleOpen || editorUI.isDescriptionOpen || editorUI.isQuantityOpen || editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen) {
            if (event.key() == GLFW.GLFW_KEY_A && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (editorUI.isHexEditing) {
                    editorUI.selectionStart = 0;
                    editorUI.selectionEnd = editorUI.hexQuery.length();
                    editorUI.hexCursorIndex = editorUI.hexQuery.length();
                } else {
                    editorUI.selectionStart = 0;
                    editorUI.selectionEnd = editorUI.searchQuery.length();
                    editorUI.cursorIndex = editorUI.searchQuery.length();
                }
                return true;
            }

            if (editorUI.isHexEditing) {
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (editorUI.selectionStart != -1) {
                        int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                        int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                        editorUI.hexQuery = editorUI.hexQuery.substring(0, start) + editorUI.hexQuery.substring(Math.min(end, editorUI.hexQuery.length()));
                        editorUI.hexCursorIndex = start;
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                    } else if (editorUI.hexCursorIndex > 0) {
                        editorUI.hexQuery = editorUI.hexQuery.substring(0, editorUI.hexCursorIndex - 1) + editorUI.hexQuery.substring(editorUI.hexCursorIndex);
                        editorUI.hexCursorIndex--;
                    }
                    applyHexToCurrentTarget();
                } else if (key == GLFW.GLFW_KEY_LEFT && editorUI.hexCursorIndex > 0) {
                    editorUI.hexCursorIndex--;
                } else if (key == GLFW.GLFW_KEY_RIGHT && editorUI.hexCursorIndex < editorUI.hexQuery.length()) {
                    editorUI.hexCursorIndex++;
                }
                return true;
            }

            boolean isShiftDown = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

            if (key == GLFW.GLFW_KEY_LEFT) {
                if (isShiftDown) {

                    if (editorUI.selectionStart == -1) {
                        editorUI.selectionStart = editorUI.cursorIndex;
                    }
                    if (editorUI.cursorIndex > 0) {
                        editorUI.cursorIndex--;
                    }
                    editorUI.selectionEnd = editorUI.cursorIndex;

                    if (editorUI.selectionStart == editorUI.selectionEnd) {
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                    }
                } else {
                    if (editorUI.selectionStart != -1) {
                        editorUI.cursorIndex = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                    } else if (editorUI.cursorIndex > 0) {
                        editorUI.cursorIndex--;
                    }
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                if (isShiftDown) {
                    if (editorUI.selectionStart == -1) {
                        editorUI.selectionStart = editorUI.cursorIndex;
                    }
                    if (editorUI.cursorIndex < editorUI.searchQuery.length()) {
                        editorUI.cursorIndex++;
                    }
                    editorUI.selectionEnd = editorUI.cursorIndex;
                    if (editorUI.selectionStart == editorUI.selectionEnd) {
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                    }
                } else {
                    if (editorUI.selectionStart != -1) {
                        editorUI.cursorIndex = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                    } else if (editorUI.cursorIndex < editorUI.searchQuery.length()) {
                        editorUI.cursorIndex++;
                    }
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_UP && (editorUI.isSubTitleOpen || editorUI.isDescriptionOpen)) {
                editorUI.moveCursorVertical(this, -1, isShiftDown);
                return true;
            } else if (key == GLFW.GLFW_KEY_DOWN && (editorUI.isSubTitleOpen || editorUI.isDescriptionOpen)) {
                editorUI.moveCursorVertical(this, 1, isShiftDown);
                return true;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (editorUI.selectionStart != -1) {

                    String query = editorUI.searchQuery;
                    int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                    int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                    editorUI.searchQuery = query.substring(0, start) + query.substring(end);
                    editorUI.cursorIndex = start;
                    editorUI.selectionStart = -1;
                    editorUI.selectionEnd = -1;
                } else if (editorUI.cursorIndex > 0) {

                    String query = editorUI.searchQuery;
                    editorUI.searchQuery = query.substring(0, editorUI.cursorIndex - 1) + query.substring(editorUI.cursorIndex);
                    editorUI.cursorIndex--;
                }
                return true;
            }
            return true;
        }

        if (ClientQuestEvents.OPEN_QUEST_KEY.matches(event) || key == GLFW.GLFW_KEY_E) {
            if (this.isRewardSummaryOpen) {
                this.isRewardSummaryOpen = false;
                this.rewardsToShow.clear();
                playClickSound();
                return true;
            }

            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    public List<SidebarEntry> getSidebarEntries() {
        return this.sidebarEntries;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawVectorLine(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, float thickness, int color1, int color2, float r1, float r2, float arrowPhase) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance == 0) return;

        float angle = (float) Math.atan2(dy, dx);

        graphics.pose().pushMatrix();

        graphics.pose().translate(new Vector2f(x1, y1));

        graphics.pose().rotate(angle - (float) Math.PI / 2.0f);

        if (distance <= r1 + r2) {
            graphics.pose().popMatrix();
            return;
        }

        int iThick = (int) thickness;
        int lineXStart = -(iThick / 2);
        int lineXEnd = lineXStart + iThick;
        float visibleLength = distance - r1 - r2;
        float arrowSpacing = 8.0f;

        float blendStart = r1 + (visibleLength * 0.3f);
        float blendEnd = r1 + (visibleLength * 0.7f);

        graphics.fill(lineXStart, (int) r1, lineXEnd, (int) blendStart, color1);

        graphics.fillGradient(lineXStart, (int) blendStart, lineXEnd, (int) blendEnd, color1, color2);

        graphics.fill(lineXStart, (int) blendEnd, lineXEnd, (int) (distance - r2), color2);

        float lineStart = r1;
        float lineEnd = distance - r2;

        graphics.pose().rotate((float) Math.PI / 2.0f);

        for (float d = lineStart + (arrowPhase - arrowSpacing); d <= lineEnd + arrowSpacing; d += arrowSpacing) {

            float ratio = Math.max(0, Math.min(1, (d - r1) / visibleLength));
            int baseColor = interpolateColor(color1, color2, ratio);

            int r = (int) (((baseColor >> 16) & 0xFF) * 0.3f);
            int g = (int) (((baseColor >> 8) & 0xFF) * 0.3f);
            int b = (int) ((baseColor & 0xFF) * 0.3f);
            int etchedColor = (0x99 << 24) | (r << 16) | (g << 8) | b;

            graphics.pose().pushMatrix();
            float tx = d - iThick / 2.0f;
            graphics.pose().translate(tx, (float) lineXStart);
            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.FLOW_ARROW,
                    0, 0, 0, 0, iThick, iThick, iThick, iThick, etchedColor);
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();
    }

    private int interpolateColor(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public boolean isMouseOverNode(double mouseX, double mouseY, Quest quest) {

        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;

        double screenX = absoluteCenterX + (quest.getX() - this.offsetX) * this.zoom;
        double screenY = absoluteCenterY + (quest.getY() - this.offsetY) * this.zoom;
        double screenSize = quest.getSize() * this.zoom;

        return mouseX >= screenX && mouseX <= screenX + screenSize && mouseY >= screenY && mouseY <= screenY + screenSize;
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        float baseSize = (float) GRID_SNAP;

        float worldWidth = (float) this.width / (float) this.zoom;
        float worldHeight = (float) this.height / (float) this.zoom;

        float minX = (float) this.offsetX - (worldWidth / 2.0f);
        float maxX = (float) this.offsetX + (worldWidth / 2.0f);
        float minY = (float) this.offsetY - (worldHeight / 2.0f);
        float maxY = (float) this.offsetY + (worldHeight / 2.0f);

        float startX = (float) Math.floor(minX / baseSize) * baseSize;
        float startY = (float) Math.floor(minY / baseSize) * baseSize;

        int gridColor = COL_GRID;

        for (float x = startX; x <= maxX; x += baseSize) {
            graphics.fill((int) x, (int) minY, (int) x + 1, (int) maxY, gridColor);
        }
        for (float y = startY; y <= maxY; y += baseSize) {
            graphics.fill((int) minX, (int) y, (int) maxX, (int) y + 1, gridColor);
        }
    }

    private void renderQuestDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.selectedQuest == null || isEditorOpen) return;

        int panelWidth = 300;
        int panelHeight = 200;
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int) this.popupX;
        int y = (this.popupY == -1) ? (this.height - panelHeight) / 2 : (int) this.popupY;
        int midX = x + (panelWidth / 2);

        String hoveredRewardLabel = null;
        String hoveredTaskName = null;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, COL_UI_BG);
        graphics.fill(x, y, x + panelWidth, y + 20, COL_PANEL_HEADER);
        drawBorder(graphics, x, y, panelWidth, panelHeight, COL_UI_BORDER);

        graphics.centeredText(this.font, Component.literal(this.selectedQuest.getTitle()), midX, y + 6, COL_TEXT);

        int xBtnLeft = x + panelWidth - 18;
        int xBtnRight = x + panelWidth - 5;
        int xBtnTop = y + 3;
        int xBtnBottom = y + 17;

        boolean isHoveringX = mouseX >= xBtnLeft && mouseX <= xBtnRight &&
                mouseY >= xBtnTop && mouseY <= xBtnBottom;

        int xColor = isHoveringX ? COL_ERROR : COL_UI_BORDER;
        int closeBtnSize = 10;
        int closeX = xBtnLeft + (xBtnRight - xBtnLeft - closeBtnSize) / 2;
        int closeY = xBtnTop + (xBtnBottom - xBtnTop - closeBtnSize) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(closeX, closeY);
        graphics.pose().scale(closeBtnSize / 16.0f, closeBtnSize / 16.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLOSE_ICON, 0, 0, 0.0f, 0.0f, 16, 16, 16, 16, xColor);
        graphics.pose().popMatrix();

        graphics.fill(x, y + 20, x + panelWidth, y + 21, COL_UI_BORDER);

        int headerY = y + 30;
        int tasksCenter = x + (panelWidth / 4);
        int rewardsCenter = x + (3 * panelWidth / 4);

        graphics.centeredText(this.font, Component.literal("Tasks"), tasksCenter, headerY, COL_TEXT);
        graphics.centeredText(this.font, Component.literal("Rewards"), rewardsCenter, headerY, COL_TEXT);

        if (QuestGlobalState.isEditModeEnabled) {
            int plusX = tasksCenter + (this.font.width("Tasks") / 2) + 8;
            boolean hoveringPlus = mouseX >= plusX - 2 && mouseX <= plusX + 10 && mouseY >= headerY - 2 && mouseY <= headerY + 12;
            int color = hoveringPlus ? COL_TEXT_GOLD : COL_TEXT;
            graphics.text(this.font, Component.literal("+"), plusX, headerY, color);
        }

        var rewards = this.selectedQuest.getRewards();
        int rewardsAreaX = x + (panelWidth / 2) + 10;
        int rewardsAreaY = y + 45;
        int rSlotSize = 20, rMaxVisible = 4, rSlotTotalWidth = 24;

        if (QuestGlobalState.isEditModeEnabled) {
            int rPlusX = x + (3 * panelWidth / 4) + (this.font.width("Rewards") / 2) + 8;
            boolean hov = !isInputBlocked() && mouseX >= rPlusX - 2 && mouseX <= rPlusX + 12 && mouseY >= headerY - 2 && mouseY <= headerY + 12;
            graphics.text(font, Component.literal("+"), rPlusX, headerY, hov ? COL_TEXT_GOLD : COL_TEXT);
        }

        boolean isSubEditorOpen = isTaskEditorOpen || isRewardEditorOpen || isChoiceModalOpen || isRewardSummaryOpen;
        if (!rewards.isEmpty() && !isSubEditorOpen) {

            int totalVisualItems = rewards.size();

            int startIdx = rewardPage * rMaxVisible;
            int visibleCount = Math.min(rMaxVisible, totalVisualItems - startIdx);
            int rTotalWidth = rMaxVisible * rSlotTotalWidth;
            boolean hasPager = rewards.size() > rMaxVisible;
            int renderX = rewardsAreaX + (hasPager ? 12 : 0);

            if (this.movingReward != null && mouseX >= renderX && mouseX <= renderX + rTotalWidth) {
                int snappedIX = -1;
                for (int i = 0; i < visibleCount; i++) {
                    int ix = renderX + (i * rSlotTotalWidth);
                    if (mouseX < ix + (rSlotTotalWidth / 2)) {
                        snappedIX = ix - 2;
                        break;
                    }
                }
                if (snappedIX == -1) snappedIX = renderX + (visibleCount * rSlotTotalWidth) - 2;
                graphics.fill(snappedIX, rewardsAreaY, snappedIX + 1, rewardsAreaY + rSlotSize, COL_UI_BORDER);
            }

            if (hasPager) {
                if (rewardPage > 0) graphics.text(font, "<", rewardsAreaX, rewardsAreaY + 6, COL_TEXT);
                if (startIdx + rMaxVisible < rewards.size())
                    graphics.text(font, ">", renderX + (rMaxVisible * rSlotTotalWidth), rewardsAreaY + 6, COL_TEXT);
            }

            for (int i = 0; i < visibleCount; i++) {
                QuestReward reward = rewards.get(startIdx + i);
                int ix = renderX + (i * rSlotTotalWidth);
                boolean isBundle = !reward.getSubRewards().isEmpty();
                boolean isRewardHovered = !isSubEditorOpen && mouseX >= ix && mouseX <= ix + rSlotSize && mouseY >= rewardsAreaY && mouseY <= rewardsAreaY + rSlotSize;
                int innerColor = isRewardHovered ? COL_PANEL_HEADER : COL_UI_BG;

                boolean questDone = SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.contains(selectedQuest.getId());
                boolean claimed = SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(reward.getId());
                int circleColor = questDone ? COL_STATE_COMPLETED : COL_STATE_AVAILABLE;

                if (isBundle) {

                    List<QuestReward> allOptions = new ArrayList<>();
                    allOptions.add(reward);
                    allOptions.addAll(reward.getSubRewards());
                    int cycleIdx = (int) ((Util.getMillis() / 1000) % allOptions.size());
                    QuestReward displayReward = allOptions.get(cycleIdx);

                    editorUI.drawRewardIcon(graphics, displayReward, circleColor, innerColor, ix, rewardsAreaY, rSlotSize, questDone && !claimed);
                    renderRewardLabel(graphics, displayReward, ix, rewardsAreaY, rSlotSize);
                } else {
                    if (reward == movingReward)
                        graphics.fill(ix, rewardsAreaY, ix + rSlotSize, rewardsAreaY + rSlotSize, 0x40FFFFFF);
                    editorUI.drawRewardIcon(graphics, reward, circleColor, innerColor, ix, rewardsAreaY, rSlotSize, questDone && !claimed);
                    renderRewardLabel(graphics, reward, ix, rewardsAreaY, rSlotSize);
                }

                if (isRewardHovered) {
                    if (claimed) {
                        hoveredRewardLabel = isBundle ? "Choice Reward (Claimed)" : getRewardTooltip(reward) + " (Claimed)";
                    } else {
                        hoveredRewardLabel = isBundle ? "Reward Choice (Click to open)" : getRewardTooltip(reward);
                    }
                }
            }
        }

        List<QuestTask> tasks = this.selectedQuest.getTasks();
        int tasksAreaX = x + 10;
        int tasksAreaY = y + 45;
        int slotSize = 20;
        int maxVisibleTasks = 4;
        int taskSpacing = 4;
        int slotTotalWidth = slotSize + taskSpacing;

        if (!tasks.isEmpty() && !isSubEditorOpen) {
            int startIdx = taskPage * maxVisibleTasks;
            int visibleCount = Math.min(maxVisibleTasks, tasks.size() - startIdx);
            boolean hasPagination = tasks.size() > maxVisibleTasks;
            int renderX = tasksAreaX + (hasPagination ? 12 : 0);

            if (this.movingTask != null && mouseX >= renderX && mouseX <= renderX + (maxVisibleTasks * slotTotalWidth)) {
                int snappedIX = -1;
                for (int i = 0; i < visibleCount; i++) {
                    int ix = renderX + (i * slotTotalWidth);

                    if (mouseX < ix + (slotTotalWidth / 2)) {
                        snappedIX = ix - 2;
                        break;
                    }
                }

                if (snappedIX == -1) snappedIX = renderX + (visibleCount * slotTotalWidth) - 2;

                graphics.fill(snappedIX, tasksAreaY, snappedIX + 1, tasksAreaY + slotSize, COL_UI_BORDER);
            }

            if (hasPagination) {
                if (taskPage > 0) graphics.text(font, "<", tasksAreaX, tasksAreaY + 6, COL_TEXT);
                if (startIdx + maxVisibleTasks < tasks.size()) {
                    int arrowX = renderX + (maxVisibleTasks * (slotSize + 4));
                    graphics.text(font, ">", arrowX, tasksAreaY + 6, COL_TEXT);
                }
            }

            for (int i = 0; i < visibleCount; i++) {
                QuestTask task = tasks.get(startIdx + i);
                int ix = renderX + (i * slotTotalWidth);

                int currentAmount = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(task.getId(), 0);

                boolean isBeingMoved = (task == this.movingTask);
                float alpha = isBeingMoved ? 0.3f : 1.0f;
                int highlightColor = isBeingMoved ? 0x10FFFFFF : 0x40FFFFFF;

                boolean isTaskHovered = mouseX >= ix && mouseX <= ix + slotSize && mouseY >= tasksAreaY && mouseY <= tasksAreaY + slotSize;
                int innerColor = (isTaskHovered && !isBeingMoved && !isSubEditorOpen) ? COL_PANEL_HEADER : COL_UI_BG;
                if (isTaskHovered && !isBeingMoved && !isSubEditorOpen) {
                    hoveredTaskName = getTaskTooltip(task);
                }

                String info = "";
                if (task.getType() == QuestTask.TaskType.ITEM || task.getType() == QuestTask.TaskType.KILL) {

                    int displayAmount = Math.min(currentAmount, task.getRequiredAmount());
                    info = displayAmount + "/" + task.getRequiredAmount();
                } else if (task.getType() == QuestTask.TaskType.LOCATION) {
                    info = task.getTargetX() + "," + task.getTargetY() + "," + task.getTargetZ();
                }

                if (!info.isEmpty()) {
                    float s = 0.5f;
                    int textW = (int) (this.font.width(info) * s);
                    int tx = ix + (slotSize / 2) - (textW / 2);
                    int ty = tasksAreaY + slotSize + 2;

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(tx, ty);
                    graphics.pose().scale(s, s);
                    graphics.text(this.font, info, 0, 0, COL_TEXT);
                    graphics.pose().popMatrix();
                }

                graphics.pose().pushMatrix();

                editorUI.drawTaskIcon(graphics, task, currentAmount, innerColor, ix + 2, tasksAreaY + 2, mouseX, mouseY, 16);
                graphics.pose().popMatrix();
            }
        }

        int hLine1Y = y + 75;
        graphics.fill(midX, y + 21, midX + 1, hLine1Y, COL_UI_BORDER);

        if (this.movingTask != null) {
            int current = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(movingTask.getId(), 0);
            editorUI.drawTaskIcon(graphics, movingTask, current, COL_PANEL_HEADER, (int) mouseX - 10, (int) mouseY - 10, (int) mouseX, (int) mouseY, 20);
        }

        graphics.fill(x, hLine1Y, x + panelWidth, hLine1Y + 1, COL_UI_BORDER);

        String fullSubTitle = this.selectedQuest.getSubTitle();
        String displayedSubTitle = truncate(fullSubTitle, panelWidth - 20);
        int subTitleY = hLine1Y + 8;

        boolean isSubTitleHovered = mouseX >= x + 10 && mouseX <= x + panelWidth - 10 && mouseY >= subTitleY && mouseY < subTitleY + font.lineHeight;
        boolean showSubTitleOverlay = isSubTitleHovered && !fullSubTitle.equals(displayedSubTitle);

        if (!showSubTitleOverlay) {
            graphics.centeredText(this.font, Component.literal(displayedSubTitle), midX, subTitleY, COL_TEXT);
        }

        int hLine2Y = subTitleY + 14;
        graphics.fill(x, hLine2Y, x + panelWidth, hLine2Y + 1, COL_UI_BORDER);

        int descAreaY = hLine2Y + 8;
        int descAreaHeight = (y + panelHeight) - descAreaY - 10;
        int textWidth = panelWidth - 20;

        var lines = this.font.split(Component.literal(this.selectedQuest.getDescription()), textWidth);
        int totalTextHeight = lines.size() * this.font.lineHeight;

        this.descScrollOffset = Math.max(0, Math.min(this.descScrollOffset, Math.max(0, totalTextHeight - descAreaHeight)));

        graphics.enableScissor(x + 10, descAreaY, x + panelWidth - 10, y + panelHeight - 10);

        for (int i = 0; i < lines.size(); i++) {
            int lineY = descAreaY + (i * this.font.lineHeight) - (int) this.descScrollOffset;

            if (lineY + this.font.lineHeight > descAreaY && lineY < y + panelHeight - 10) {
                graphics.text(this.font, lines.get(i), x + 10, lineY, COL_TEXT);
            }
        }
        graphics.disableScissor();

        if (hoveredTaskName != null && !isContextMenuOpen && !isSubEditorOpen) {
            renderSimpleTooltip(graphics, hoveredTaskName, mouseX, mouseY);
        }

        if (hoveredRewardLabel != null && !isContextMenuOpen && !isSubEditorOpen) {
            renderSimpleTooltip(graphics, hoveredRewardLabel, mouseX, mouseY);
        }

        if (showSubTitleOverlay) {
            renderStaticTextOverlay(graphics, fullSubTitle, x + 10, subTitleY, panelWidth - 20);
        }
    }

    private String getTaskTooltip(QuestTask task) {
        String baseName = task.getTargetDisplayName();

        if (!task.getName().isEmpty() && !task.getName().equals("New Task") &&
                !task.getName().equals("(Default)") && !task.getName().equals(baseName)) {
            return task.getName();
        }

        String qtyPrefix = task.getRequiredAmount() > 1 ? task.getRequiredAmount() + "x " : "";

        return switch (task.getType()) {
            case ITEM -> "Collect " + qtyPrefix + baseName;
            case KILL -> "Kill " + qtyPrefix + baseName;
            case OBSERVE -> "Look at " + baseName;
            case BIOME -> "Explore " + baseName;
            case LOCATION -> "Travel to " + task.getTargetX() + ", " + task.getTargetY() + ", " + task.getTargetZ();
            case CHECKBOX -> task.getName();
        };
    }

    private void renderRewardLabel(GuiGraphicsExtractor graphics, QuestReward reward, int ix, int iy, int size) {

        String val = (reward == null) ? "?" : (reward.getType() == QuestReward.RewardType.COMMAND ? "CMD" : String.valueOf(reward.getCount()));
        float s = 0.5f;
        int tx = ix + (size / 2) - (int) (font.width(val) * s / 2);

        graphics.pose().pushMatrix();
        graphics.pose().translate(tx, iy + size + 2);
        graphics.pose().scale(s, s);
        graphics.text(font, val, 0, 0, COL_TEXT);
        graphics.pose().popMatrix();
    }

    private String getRewardTooltip(QuestReward reward) {
        if (reward == null) return "";
        return switch (reward.getType()) {
            case ITEM -> {
                String name = reward.getItem().getDefaultInstance().getHoverName().getString();
                yield reward.getCount() + "x " + name;
            }
            case XP -> reward.getCount() + " XP";
            case COMMAND -> "Execute Command";
        };
    }

    private void renderContextMenu(GuiGraphicsExtractor graphics) {

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        List<String> options;
        if (isSideBarContextMenu) options = List.of("Add Group", "Add Chapter");
        else if (isSideBarEntryMenu)
            options = (sidebarTargetEntry instanceof SidebarGroup) ? List.of("Move", "Rename", "Reset Progress", "Delete") : List.of("Move", "Rename", "Edit Icon", "Reset Progress", "Delete");
        else if (isTaskContextMenu) options = getTaskOptions();
        else if (isRewardContextMenu) options = List.of("Edit", "Delete", "Move");
        else if (isImageContextMenu) options = List.of("Move", "Delete");
        else if (isTextContextMenu) options = List.of("Edit Text", "Delete", "Move");
        else
            options = (questToModify == null) ? List.of("Create Quest", "Add Text", "Add Image") : List.of("Edit", "Complete", "Delete", "Reset Progress", "Move");

        int w = 110;
        int h = options.size() * 20;
        int x = (int) contextMenuX;
        int y = (int) contextMenuY;

        graphics.fill(x, y, x + w, y + h, COL_PANEL_HEADER);

        graphics.fill(x, y, x + w, y + 1, COL_UI_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COL_UI_BORDER);
        graphics.fill(x, y, x + 1, y + h, COL_UI_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COL_UI_BORDER);

        for (int i = 0; i < options.size(); i++) {
            int optionY = y + (i * 20);

            boolean isOptionHovered = mouseX >= x && mouseX <= x + w && mouseY >= optionY && mouseY <= optionY + 20;

            if (isOptionHovered) {
                graphics.fill(x + 1, optionY + 1, x + w - 1, optionY + 19, COL_HOVER_MENU);
            }

            String opt = options.get(i);

            int textColor = isOptionHovered ? COL_TEXT_GOLD :
                    (opt.contains("Delete") || opt.contains("Reset") ? COL_ERROR : COL_TEXT);

            graphics.text(this.font, Component.literal(options.get(i)), x + 5, optionY + 6, textColor, false);
        }
    }

    public void openRewardContextMenu(double x, double y, QuestReward reward) {
        setContextMenuPos(x, y, 3);
        this.isContextMenuOpen = true;
        this.isRewardContextMenu = true;
        this.sidebarTargetReward = reward;

        this.isTaskContextMenu = false;
        this.questToModify = null;
        this.isSideBarEntryMenu = false;
        this.isSideBarContextMenu = false;
        this.isTextContextMenu = false;
        playClickSound();
    }

    public void openSideBarContextMenu(double x, double y) {
        if (isSidebarEditing()) stopSidebarEditing(true);
        setContextMenuPos(x, y, 2);
        this.isContextMenuOpen = true;
        this.isSideBarContextMenu = true;
        this.isTaskContextMenu = false;
        this.isSideBarEntryMenu = false;
        this.questToModify = null;
        playClickSound();
    }

    public void openSidebarEntryContextMenu(double x, double y, SidebarEntry entry, SidebarGroup parent) {
        if (isSidebarEditing()) stopSidebarEditing(true);
        setContextMenuPos(x, y, entry instanceof SidebarGroup ? 4 : 5);
        this.isContextMenuOpen = true;
        this.isTaskContextMenu = false;
        this.isSideBarEntryMenu = true;
        this.isSideBarContextMenu = false;
        this.sidebarTargetEntry = entry;
        this.sidebarTargetParentGroup = parent;
        this.sidebarTargetChapter = (entry instanceof SidebarChapter chapter) ? chapter : null;
        this.questToModify = null;
        playClickSound();
    }

    public void openTaskContextMenu(double x, double y, QuestTask task) {
        setContextMenuPos(x, y, getTaskOptions().size());
        this.isContextMenuOpen = true;
        this.isTaskContextMenu = true;
        this.isSideBarEntryMenu = false;
        this.isSideBarContextMenu = false;
        this.sidebarTargetTask = task;
        playClickSound();
    }

    private boolean isClickingContextMenu(double mouseX, double mouseY) {

        int optionCount;
        if (isSideBarContextMenu) optionCount = 2;
        else if (isSideBarEntryMenu) optionCount = (sidebarTargetEntry instanceof SidebarGroup) ? 4 : 5;
        else if (isTaskContextMenu) optionCount = getTaskOptions().size();
        else if (isRewardContextMenu) optionCount = 3;
        else if (isTextContextMenu) optionCount = 3;

        else optionCount = (questToModify == null) ? 3 : 5;

        int w = 110;
        int h = optionCount * 20;

        return mouseX >= contextMenuX && mouseX <= contextMenuX + w &&
                mouseY >= contextMenuY && mouseY <= contextMenuY + h;
    }

    public void completeQuest(Quest quest) {

        ClientPacketDistributor.sendToServer(new AdminCompletePayload(quest.getId(), Optional.empty(), true));

        playClickSound();
        updateQuestStates();

    }

    public void claimAllRewards() {
        List<QuestReward> rewardsToClaim = new ArrayList<>();

        for (Quest quest : this.allQuests) {

            if (quest.getState() == QuestState.COMPLETED) {
                for (QuestReward reward : quest.getRewards()) {

                    if (reward.getSubRewards().isEmpty() && !SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(reward.getId())) {
                        ClientPacketDistributor.sendToServer(new ClaimRewardPayload(reward.getId()));
                        rewardsToClaim.add(reward);
                    }
                }
            }
        }

        if (!rewardsToClaim.isEmpty()) {
            openRewardSummary(rewardsToClaim);
        }
    }

    public void openRewardSummary(List<QuestReward> rewards) {
        this.rewardsToShow = new ArrayList<>(rewards);
        this.isRewardSummaryOpen = true;
        this.summaryPage = 0;
        playClickSound();
    }

    public void deleteQuest(Quest quest) {

        SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.remove(quest.getId());

        this.allQuests.remove(quest);

        this.questLookup.remove(quest.getId());

        if (this.selectedQuest == quest) this.selectedQuest = null;

        playClickSound();
        updateQuestStates();
        ClientPacketDistributor.sendToServer(new DeleteQuestPayload(quest.getId(), quest.getChapterName()));
    }

    private void handleContextMenuClick(double mouseX, double mouseY) {
        playClickSound();
        this.isContextMenuOpen = false;

        if (isRewardContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;

            if (optionIndex == 0) {
                this.originalReward = this.sidebarTargetReward;
                QuestReward r = this.originalReward;

                this.rewardToModify = new QuestReward(r);

                this.editorUI.selectedRewardChoiceIndex = -1;
                this.editorUI.rewardChoicePage = 0;

                this.isRewardEditorOpen = true;
                this.editorUI.isRewardModeOpen = true;
            } else if (optionIndex == 1) {
                this.selectedQuest.getRewards().remove(this.sidebarTargetReward);

                int maxRewardPages = (this.selectedQuest.getRewards().size() + 3) / 4;
                if (this.rewardPage >= maxRewardPages && this.rewardPage > 0) this.rewardPage = maxRewardPages - 1;

                saveChapterData(this.selectedQuest.getChapterName());
            } else if (optionIndex == 2) {
                this.movingReward = this.sidebarTargetReward;
            }

            this.isRewardContextMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        if (isImageContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;
            if (optionIndex == 0) {
                this.movingCanvasImage = this.selectedCanvasImage;
                float absoluteCenterX = (float) (this.width / 2.0);
                float absoluteCenterY = (float) (this.height / 2.0);
                double worldMouseX = this.offsetX + ((contextMenuX - absoluteCenterX) / this.zoom);
                double worldMouseY = this.offsetY + ((contextMenuY - absoluteCenterY) / this.zoom);
                this.dragOffsetX = worldMouseX - this.movingCanvasImage.getX();
                this.dragOffsetY = worldMouseY - this.movingCanvasImage.getY();
            } else if (optionIndex == 1) {

                String imgId = this.selectedCanvasImage.getImageId();
                this.allCanvasImages.remove(this.selectedCanvasImage);
                saveChapterData(this.selectedCanvasImage.getChapterName());

                ClientPacketDistributor.sendToServer(new DeleteImagePayload(imgId));

                deleteLocalImageCache(imgId);
                this.selectedCanvasImage = null;
            }
            return;
        }

        if (isTaskContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;
            List<String> options = getTaskOptions();

            if (optionIndex >= 0 && optionIndex < options.size()) {
                String action = options.get(optionIndex);
                if (action.equals("Edit")) {
                    this.originalTask = this.sidebarTargetTask;
                    QuestTask t = this.originalTask;
                    this.taskToModify = new QuestTask(t.getId(), t.getType(), t.getTargetId(), t.getName(), t.getRequiredAmount(), t.getCurrentAmount(), t.isOptional(), t.isRepeatable(), t.isConsume(), t.getState(), t.getTargetX(), t.getTargetY(), t.getTargetZ(), t.isIcon());
                    this.isTaskEditorOpen = true;
                    this.editorUI.isTaskMode = true;
                    this.editorUI.currentTaskType = this.taskToModify.getType();

                    this.tempUseAsIcon = t.isIcon();
                } else if (action.equals("Move")) {
                    this.movingTask = this.sidebarTargetTask;
                } else if (action.equals("Complete")) {
                    ClientPacketDistributor.sendToServer(new AdminCompletePayload(selectedQuest.getId(), Optional.of(sidebarTargetTask.getId()), true));
                } else if (action.equals("Reset Progress")) {
                    ClientPacketDistributor.sendToServer(new AdminCompletePayload(selectedQuest.getId(), Optional.of(sidebarTargetTask.getId()), false));
                } else if (action.equals("Delete")) {
                    this.selectedQuest.getTasks().remove(this.sidebarTargetTask);

                    int maxTaskPages = (this.selectedQuest.getTasks().size() + 3) / 4;
                    if (this.taskPage >= maxTaskPages && this.taskPage > 0) this.taskPage = maxTaskPages - 1;

                    updateQuestStates();
                    saveChapterData(this.selectedQuest.getChapterName());
                }
            }

            this.isTaskContextMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        if (isTextContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;
            if (optionIndex == 0) {
                openTextEditor(editingCanvasText);
            } else if (optionIndex == 1) {
                ClientPacketDistributor.sendToServer(new DeleteCanvasTextPayload(editingCanvasText.getText(), editingCanvasText.getX(), editingCanvasText.getY(), editingCanvasText.getChapterName()));
                allCanvasTexts.remove(editingCanvasText);
                editingCanvasText = null;
            } else if (optionIndex == 2) {
                this.movingCanvasText = this.editingCanvasText;

                float absoluteCenterX = (float) (this.width / 2.0);
                float absoluteCenterY = (float) (this.height / 2.0);
                double worldMenuX = this.offsetX + ((contextMenuX - absoluteCenterX) / this.zoom);
                double worldMenuY = this.offsetY + ((contextMenuY - absoluteCenterY) / this.zoom);
                this.dragOffsetX = worldMenuX - this.movingCanvasText.getX();
                this.dragOffsetY = worldMenuY - this.movingCanvasText.getY();
            }
            this.isTextContextMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        if (isSideBarEntryMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;

            if (sidebarTargetEntry instanceof SidebarGroup group) {
                switch (optionIndex) {
                    case 0 -> this.movingSidebarGroup = group;
                    case 1 -> {
                        this.editingGroup = group;
                        this.sidebarSearchQuery = group.getTitle();
                        this.sidebarTextScrollOffset = 0;
                    }
                    case 2 -> {

                        ClientPacketDistributor.sendToServer(new AdminResetPayload(Optional.of(Quest.sanitizePath(group.getTitle())), Optional.empty()));
                    }
                    case 3 -> {

                        for (SidebarChapter ch : group.getChapters()) {
                            String chId = ch.getId();

                            ClientPacketDistributor.sendToServer(new DeleteChapterPayload(chId));

                            allQuests.removeIf(q -> {
                                if (q.getChapterName().equals(chId)) {
                                    questLookup.remove(q.getId());
                                    if (this.selectedQuest == q) this.selectedQuest = null;
                                    return true;
                                }
                                return false;
                            });
                        }
                        sidebarEntries.remove(group);

                        ClientPacketDistributor.sendToServer(new DeleteGroupPayload(group.getName()));
                        updateQuestStates();
                        saveGroupManifest();
                    }
                }
            } else if (sidebarTargetEntry instanceof SidebarChapter chapter) {
                switch (optionIndex) {
                    case 0 -> this.movingSidebarChapter = chapter;
                    case 1 -> {
                        this.editingChapter = chapter;
                        this.sidebarSearchQuery = chapter.getName();
                        this.sidebarTextScrollOffset = 0;
                    }
                    case 2 -> {
                        this.isEditingChapterIcon = true;
                        this.editorUI.isIconPickerOpen = true;
                        this.editorUI.searchQuery = "";
                        this.editorUI.scrollOffset = 0;
                    }
                    case 3 -> {

                        ClientPacketDistributor.sendToServer(new AdminResetPayload(Optional.empty(), Optional.of(chapter.getId())));
                    }
                    case 4 -> {
                        String chapterId = chapter.getId();

                        ClientPacketDistributor.sendToServer(new DeleteChapterPayload(chapterId));

                        allQuests.removeIf(q -> {
                            if (q.getChapterName().equals(chapterId)) {
                                questLookup.remove(q.getId());
                                if (this.selectedQuest != null && this.selectedQuest.getId().equals(q.getId()))
                                    this.selectedQuest = null;
                                return true;
                            }
                            return false;
                        });

                        if (sidebarTargetParentGroup != null) {
                            sidebarTargetParentGroup.getChapters().remove(chapter);
                        } else {
                            sidebarEntries.remove(chapter);
                        }
                        if (selectedChapter == chapter) {
                            selectedChapter = null;
                        }

                        updateQuestStates();
                        saveGroupManifest();
                    }
                }
            }
            this.isSideBarEntryMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        if (isSideBarContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;

            if (optionIndex == 0) {
                SidebarGroup newGroup = new SidebarGroup("New Group", COL_TEXT);

                String uniqueId = "group_" + Long.toHexString(System.currentTimeMillis()).substring(8);
                newGroup.setName(uniqueId);
                this.sidebarEntries.add(newGroup);
                this.editingGroup = newGroup;
                this.sidebarSearchQuery = "";
                this.sidebarTextScrollOffset = 0;

                this.needsSidebarScrollToBottom = true;
                if (!newGroup.isExpanded()) newGroup.toggleExpanded();
            } else if (optionIndex == 1) {
                SidebarChapter newChapter = new SidebarChapter("New Chapter");

                String uniqueId = "chapter_" + Long.toHexString(System.currentTimeMillis()).substring(8);
                newChapter.setId(uniqueId);
                this.sidebarEntries.add(newChapter);
                this.editingChapter = newChapter;
                this.sidebarTextScrollOffset = 0;
                this.sidebarSearchQuery = "";
                this.needsSidebarScrollToBottom = true;
            }
            this.isSideBarContextMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        int relativeY = (int) (mouseY - contextMenuY);
        int optionIndex = relativeY / 20;

        if (questToModify == null) {
            String currentChapter = (this.selectedChapter != null) ? this.selectedChapter.getId() : "default_chapter";
            String currentGroup = findGroupNameForChapter(currentChapter);

            String newId = Quest.generateQuestId(currentGroup, currentChapter, "New Quest", this.allQuests);

            double absoluteCenterX = this.width / 2.0;
            double absoluteCenterY = this.height / 2.0;
            double worldMouseX = this.offsetX + ((contextMenuX - absoluteCenterX) / this.zoom);
            double worldMouseY = this.offsetY + ((contextMenuY - absoluteCenterY) / this.zoom);

            double snappedX = Math.round(worldMouseX / GRID_SNAP) * GRID_SNAP;
            double snappedY = Math.round(worldMouseY / GRID_SNAP) * GRID_SNAP;

            if (optionIndex == 0) {
                double gridX = snappedX - 12.0;
                double gridY = snappedY - 12.0;

                String uniqueId = newId + "_" + Long.toHexString(System.currentTimeMillis()).substring(8);
                this.questToModify = new Quest(uniqueId, currentChapter, "New Quest", gridX, gridY);
                this.originalQuest = null;
                this.isEditorOpen = true;
            } else if (optionIndex == 1) {
                CanvasText ct = new CanvasText("New Text", snappedX, snappedY, 1.0f, COL_TEXT);
                ct.setChapterName(this.selectedChapter.getName());
                openTextEditor(ct);
            } else if (optionIndex == 2) {
                openImagePicker(snappedX, snappedY);
            }
        } else {

            switch (optionIndex) {
                case 0 -> {

                    this.originalQuest = this.questLookup.get(this.questToModify.getId());
                    if (this.originalQuest != null) {
                        this.originalQuest.setLockedBy(Minecraft.getInstance().player.getName().getString());
                        ClientPacketDistributor.sendToServer(new QuestLockPayload(this.originalQuest.getId(), true));
                    }
                    startEditing(questToModify);
                    this.popupX = this.width / 2.0 - 150;
                    this.popupY = this.height / 2.0 - 100;
                }
                case 1 -> completeQuest(questToModify);
                case 2 -> deleteQuest(questToModify);
                case 3 -> {
                    ClientPacketDistributor.sendToServer(new AdminCompletePayload(questToModify.getId(), Optional.empty(), false));
                }
                case 4 -> this.movingQuest = this.questToModify;
            }
        }
    }

    private void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        int thickness = 1;

        graphics.fill(x, y, x + w, y + thickness, color);

        graphics.fill(x, y + h - thickness, x + w, y + h, color);

        graphics.fill(x, y, x + thickness, y + h, color);

        graphics.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private void updateSidebarScrollOffset(int availableWidth) {

        int textWidth = this.font.width(sidebarSearchQuery) + 2;
        if (textWidth > availableWidth) {
            sidebarTextScrollOffset = textWidth - availableWidth;
        } else {
            sidebarTextScrollOffset = 0;
        }
    }

    private void renderSimpleTooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        int maxWidth = 200;
        var lines = this.font.split(Component.literal(text), maxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 6;
        int boxX = mouseX + 12;
        int boxY = mouseY - 12;
        int boxWidth = textWidth + (padding * 2);
        int boxHeight = (lines.size() * this.font.lineHeight) + (padding);

        if (boxY < 5) boxY = 5;
        if (boxY + boxHeight > this.height) boxY = this.height - boxHeight - 5;
        if (boxX + boxWidth > this.width - 5) {
            boxX = mouseX - boxWidth - 12;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, COL_TOOLTIP_BG);

        int borderColor = COL_UI_BORDER;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        for (int i = 0; i < lines.size(); i++) {
            graphics.text(this.font, lines.get(i), boxX + padding, boxY + 3 + (i * this.font.lineHeight), COL_TEXT);
        }
    }

    /**
     * Renders a tooltip anchored to the bottom-center of the active canvas area.
     * This prevents mouse-relative tooltips from obscuring dependency lines.
     */
    private void renderAnchoredQuestTooltip(GuiGraphicsExtractor graphics, String text) {

        int canvasStartX = (int) this.currentSidebarWidth;
        int canvasWidth = this.width - canvasStartX;

        int maxWidth = canvasWidth - 40;
        var lines = this.font.split(Component.literal(text), maxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 6;
        int boxWidth = textWidth + (padding * 2);
        int boxHeight = (lines.size() * this.font.lineHeight) + 4;

        int boxX = canvasStartX + (canvasWidth - boxWidth) / 2;
        int boxY = this.height - boxHeight - 8;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, COL_TOOLTIP_BG);

        drawBorder(graphics, boxX, boxY, boxWidth, boxHeight, COL_UI_BORDER);

        for (int i = 0; i < lines.size(); i++) {
            graphics.centeredText(this.font, lines.get(i), boxX + (boxWidth / 2), boxY + 2 + (i * this.font.lineHeight), COL_TEXT);
        }
    }

    public String truncate(String text, int maxWidth) {
        if (maxWidth < 10) return "";
        if (this.font.width(text) <= maxWidth) return text;

        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
    }

    private void renderStaticTextOverlay(GuiGraphicsExtractor graphics, String fullText, int x, int y, int containerMaxWidth) {

        var lines = this.font.split(Component.literal(fullText), containerMaxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 4;

        int boxWidth = Math.min(textWidth + (padding * 2), this.width - x - padding);
        int boxHeight = (lines.size() * this.font.lineHeight) + (padding * 2);

        int finalX = x - padding;
        int finalY = y - padding;

        graphics.fill(finalX, finalY, finalX + boxWidth, finalY + boxHeight, COL_UI_INNER_BG);

        int borderColor = COL_UI_BORDER;
        graphics.fill(finalX, finalY, finalX + boxWidth, finalY + 1, borderColor);
        graphics.fill(finalX, finalY + boxHeight - 1, finalX + boxWidth, finalY + boxHeight, borderColor);
        graphics.fill(finalX, finalY, finalX + 1, finalY + boxHeight, borderColor);
        graphics.fill(finalX + boxWidth - 1, finalY, finalX + boxWidth, finalY + boxHeight, borderColor);

        for (int i = 0; i < lines.size(); i++) {
            int lineY = finalY + padding + (i * this.font.lineHeight);
            graphics.text(this.font, lines.get(i), finalX + padding, lineY, COL_TEXT);
        }
    }

    private boolean isInputBlocked() {
        return this.isEditorOpen ||
                this.isTaskEditorOpen ||
                this.isRewardEditorOpen ||
                this.selectedQuest != null ||
                this.currentSidebarWidth > (MAX_SIDEBAR_WIDTH - 5) ||
                this.suppressPanning ||
                this.movingQuest != null ||
                this.movingSidebarChapter != null ||
                this.movingTask != null ||
                this.movingSidebarGroup != null ||
                this.isContextMenuOpen ||
                this.isSettingsOpen ||
                this.editorUI.isColorPickerOpen ||
                this.isDraggingPickerWindow ||
                this.isTextEditorOpen ||
                this.isItemSubmissionOpen ||
                this.isRewardSummaryOpen ||
                this.isChoiceModalOpen ||
                isSidebarEditing() ||
                this.isDraggingTextSizeSlider ||
                this.isDraggingSizeSlider ||
                this.isDraggingScaleHandle ||
                this.movingCanvasImage != null ||
                this.isDraggingRotateHandle ||
                this.isDraggingAlphaSlider;
    }

    public boolean isSidebarEditing() {
        return editingGroup != null || editingChapter != null;
    }

    public Rectangle getDetailsBounds() {
        int panelWidth = 300;
        int panelHeight = 200;
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int) this.popupX;
        int y = (this.popupY == -1) ? (this.height - panelHeight) / 2 : (int) this.popupY;
        return new Rectangle(x, y, panelWidth, panelHeight);
    }

    public void selectChapter(SidebarChapter chapter) {
        this.selectedChapter = chapter;
        this.offsetX = chapter.getOffsetX();
        this.offsetY = chapter.getOffsetY();
        QuestClientData.setLastChapter(chapter.getId());
        playClickSound();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (isSidebarEditing()) {
            sidebarSearchQuery += event.codepointAsString();
            return true;
        }

        if (editorUI.isQuantityOpen || editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen) {
            String typed = event.codepointAsString();
            char firstChar = typed.charAt(0);

            boolean isDigit = Character.isDigit(firstChar);

            boolean isMinus = firstChar == '-' && !editorUI.isQuantityOpen && (editorUI.cursorIndex == 0 || editorUI.selectionStart == 0);

            if (isDigit || isMinus) {
                if (editorUI.isQuantityOpen) {
                    int potentialLength = (editorUI.selectionStart != -1) ?
                            (editorUI.searchQuery.length() - Math.abs(editorUI.selectionEnd - editorUI.selectionStart) + 1) :
                            (editorUI.searchQuery.length() + 1);

                    if (potentialLength <= 4) {
                        charTypedInPicker(typed);
                    }
                } else {

                    charTypedInPicker(typed);
                }
            }
            return true;
        }

        if (editorUI.isColorPickerOpen) {
            String typed = event.codepointAsString();

            if ("0123456789ABCDEFabcdef".contains(typed)) {
                if (editorUI.selectionStart != -1) {
                    int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                    int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                    String head = editorUI.hexQuery.substring(0, Math.max(0, Math.min(start, editorUI.hexQuery.length())));
                    String tail = editorUI.hexQuery.substring(Math.min(end, editorUI.hexQuery.length()));
                    String next = head + typed.toUpperCase() + tail;
                    if (next.length() <= 6) {
                        editorUI.hexQuery = next;
                        editorUI.hexCursorIndex = start + 1;
                        editorUI.selectionStart = -1;
                        editorUI.selectionEnd = -1;
                        applyHexToCurrentTarget();
                    }
                } else if (editorUI.hexQuery.length() < 6) {
                    String head = editorUI.hexQuery.substring(0, editorUI.hexCursorIndex);
                    String tail = editorUI.hexQuery.substring(editorUI.hexCursorIndex);
                    editorUI.hexQuery = head + typed.toUpperCase() + tail;
                    editorUI.hexCursorIndex++;
                    applyHexToCurrentTarget();
                }
            }
            return true;
        }

        if (editorUI.isPickerOpen() || isTextEditorOpen) {
            charTypedInPicker(event.codepointAsString());
        }
        return super.charTyped(event);
    }

    public void startEditing(Quest selectedQuest) {
        this.originalQuest = selectedQuest;
        this.questToModify = new Quest(selectedQuest);
        this.isEditorOpen = true;
    }

    public void saveChanges() {
        if (questToModify != null) {
            if (originalQuest != null) {

                String oldId = originalQuest.getId();
                String chapter = originalQuest.getChapterName();
                String group = findGroupNameForChapter(chapter);

                originalQuest.setLockedBy("");
                ClientPacketDistributor.sendToServer(new QuestLockPayload(oldId, false));

                String newId = oldId;
                if (!Quest.sanitizePath(originalQuest.getTitle()).equals(Quest.sanitizePath(questToModify.getTitle()))) {
                    List<Quest> otherQuests = allQuests.stream().filter(q -> q != originalQuest).toList();
                    newId = Quest.generateQuestId(group, chapter, questToModify.getTitle(), otherQuests);
                }

                if (!oldId.equals(newId)) {

                    ClientPacketDistributor.sendToServer(new DeleteQuestPayload(oldId, chapter));

                    for (Quest q : allQuests) {
                        List<String> deps = q.getDependencies();
                        for (int i = 0; i < deps.size(); i++) {
                            if (deps.get(i).equals(oldId)) {
                                deps.set(i, newId);
                            }
                        }
                    }

                    questLookup.remove(oldId);
                    questLookup.put(newId, originalQuest);

                    for (QuestTask task : originalQuest.getTasks()) {
                        task.setId(QuestTask.generateTaskId(newId, task.getName(), originalQuest.getTasks()));
                    }

                    for (QuestReward reward : originalQuest.getRewards()) {
                        reward.setId(QuestReward.generateRewardId(newId, reward.getType().name().toLowerCase(), originalQuest.getRewards()));
                    }
                }

                originalQuest.setId(newId);
                originalQuest.setTitle(questToModify.getTitle());
                originalQuest.setSubTitle(questToModify.getSubTitle());
                originalQuest.setDescription(questToModify.getDescription());
                originalQuest.setX(questToModify.getX());
                originalQuest.setY(questToModify.getY());
                originalQuest.setLogo(questToModify.getLogo());
                originalQuest.setShape(questToModify.getShape());
                originalQuest.setSize(questToModify.getSize());
                originalQuest.setState(questToModify.getState());
                originalQuest.setOptional(questToModify.isOptional());
                originalQuest.setRepeatable(questToModify.isRepeatable());
                originalQuest.setDependencies(questToModify.getDependencies());

                originalQuest.setUseTaskIcon(questToModify.isUseTaskIcon());

                if (!oldId.equals(newId)) {
                    saveAllChapters();
                } else {
                    saveChapterData(chapter);
                }
            } else {

                String chapter = questToModify.getChapterName();
                String group = findGroupNameForChapter(chapter);
                String finalId = Quest.generateQuestId(group, chapter, questToModify.getTitle(), allQuests);
                questToModify.setId(finalId);

                registerQuest(questToModify);
                saveChapterData(chapter);
            }
            updateQuestStates();
            updateClaimableCache();
            checkPendingRefresh();
        }
        this.isEditorOpen = false;
        this.questToModify = null;
        this.originalQuest = null;
    }

    public void checkPendingRefresh() {
        if (SimplyQuestsClientPacketHandler.NEEDS_REFRESH) {
            SimplyQuestsClientPacketHandler.NEEDS_REFRESH = false;
            this.init();
        }
    }

    private String findGroupNameForChapter(String chapterId) {
        for (SidebarEntry entry : this.sidebarEntries) {
            if (entry instanceof SidebarGroup g) {
                for (SidebarChapter chapter : g.getChapters()) {
                    if (Objects.equals(chapter.getId(), chapterId)) {
                        return Quest.sanitizePath(g.getTitle());
                    }
                }
            }
        }
        return "";
    }

    public void stopSidebarEditing(boolean save) {
        if (save) {
            if (editingGroup != null && !sidebarSearchQuery.isEmpty()) {

                editingGroup.setTitle(sidebarSearchQuery);
                saveGroupManifest();
            } else if (editingChapter != null && !sidebarSearchQuery.isEmpty()) {

                editingChapter.setName(sidebarSearchQuery);
                saveGroupManifest();
                saveChapterData(editingChapter.getId());
            }
        } else {

            if (editingGroup != null && editingGroup.getTitle().isEmpty()) {
                sidebarEntries.remove(editingGroup);
            } else if (editingChapter != null && (editingChapter.getName().isEmpty() || editingChapter.getId() == null)) {
                sidebarEntries.remove(editingChapter);
                for (SidebarEntry entry : sidebarEntries) {
                    if (entry instanceof SidebarGroup g) g.getChapters().remove(editingChapter);
                }
            }
        }
        this.editingGroup = null;
        this.editingChapter = null;
        this.sidebarSearchQuery = "";
        this.sidebarTextScrollOffset = 0;
        playClickSound();
    }

    private void updateAllDependencies(Map<String, String> idChanges) {
        if (idChanges.isEmpty()) return;
        for (Quest q : allQuests) {
            List<String> deps = q.getDependencies();
            for (int i = 0; i < deps.size(); i++) {
                String oldDep = deps.get(i);
                if (idChanges.containsKey(oldDep)) {
                    deps.set(i, idChanges.get(oldDep));
                }
            }
        }
        updateQuestStates();
    }

    public void dropSidebarChapter(double mouseX, double mouseY) {
        float textScale = 0.75f;
        double localMouseY = mouseY / textScale;
        double scrollableMouseY = localMouseY + sidebarScrollOffset;
        if (movingSidebarChapter == null) return;

        sidebarEntries.remove(movingSidebarChapter);
        sidebarEntries.forEach(e -> {
            if (e instanceof SidebarGroup g) g.getChapters().remove(movingSidebarChapter);
        });

        int currentYPosition = (int) (15 / textScale);
        int insertIndex = -1;
        SidebarGroup targetGroup = null;

        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);

            if (scrollableMouseY < currentYPosition + 4) {
                insertIndex = i;
                break;
            }

            if (entry instanceof SidebarGroup group) {
                currentYPosition += 18;
                if (group.isExpanded()) {

                    if (group.getChapters().isEmpty()) {
                        if (scrollableMouseY < currentYPosition + 16) {
                            targetGroup = group;
                            insertIndex = 0;
                            break;
                        }
                    }
                    for (SidebarChapter chapter : group.getChapters()) {

                        if (scrollableMouseY < currentYPosition + 16) {
                            targetGroup = group;
                            int idx = group.getChapters().indexOf(chapter);

                            insertIndex = (scrollableMouseY < currentYPosition + 8) ? idx : idx + 1;
                            break;
                        }
                        currentYPosition += 16;
                    }
                    if (targetGroup != null) break;
                }
            } else {

                if (scrollableMouseY < currentYPosition + 16) {

                    insertIndex = (scrollableMouseY < currentYPosition + 8) ? i : i + 1;
                    break;
                } else {
                    currentYPosition += 16;
                }
            }
            currentYPosition += 6;
        }

        if (targetGroup != null) {
            targetGroup.getChapters().add(insertIndex, movingSidebarChapter);
        } else {

            if (insertIndex == -1) sidebarEntries.add(movingSidebarChapter);
            else sidebarEntries.add(insertIndex, movingSidebarChapter);
        }

        saveGroupManifest();
    }

    public void dropSidebarGroup(double mouseX, double mouseY) {
        float textScale = 0.75f;
        double localMouseY = mouseY / textScale;
        double scrollableMouseY = localMouseY + sidebarScrollOffset;
        if (movingSidebarGroup == null) return;

        sidebarEntries.remove(movingSidebarGroup);

        int currentYPosition = (int) (15 / textScale);
        int insertIndex = -1;

        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);

            if (scrollableMouseY < currentYPosition + 4) {
                insertIndex = i;
                break;
            }

            if (entry instanceof SidebarGroup group) {
                currentYPosition += 18;
                if (group.isExpanded()) currentYPosition += group.getChapters().size() * 16;
            } else {
                currentYPosition += 16;
            }
            currentYPosition += 6;
        }

        if (insertIndex == -1) sidebarEntries.add(movingSidebarGroup);
        else sidebarEntries.add(insertIndex, movingSidebarGroup);

        saveGroupManifest();
    }

    public void handleTaskFieldClick(String field) {
        if (field.equals("Cancel")) {
            this.isTaskEditorOpen = false;
            this.taskToModify = null;
            this.originalTask = null;
            this.editorUI.isTaskMode = false;
            this.editorUI.closePicker();
            playClickSound();
        }
        if (field.equals("Optional")) {
            taskToModify.setOptional(!taskToModify.isOptional());
            playClickSound();
        } else if (field.equals("Repeatable")) {
            taskToModify.setRepeatable(!taskToModify.isRepeatable());
            playClickSound();
        } else if (field.equals("Consume")) {
            taskToModify.setConsume(!taskToModify.isConsume());
            playClickSound();
        } else if (field.equals("Quantity")) {
            editorUI.closePicker();
            editorUI.searchQuery = String.valueOf(taskToModify.getRequiredAmount());
            editorUI.isQuantityOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();

            editorUI.selectionStart = 0;
            editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("Name")) {
            editorUI.closePicker();
            editorUI.searchQuery = taskToModify.getName();
            editorUI.isNameOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0;
            editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("X") || field.equals("Y") || field.equals("Z")) {
            editorUI.closePicker();
            editorUI.searchQuery = String.valueOf(field.equals("X") ? taskToModify.getTargetX() : (field.equals("Y") ? taskToModify.getTargetY() : taskToModify.getTargetZ()));
            editorUI.isXOpen = field.equals("X");
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0;
            editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("Type")) {
            editorUI.isTypePickerOpen = true;
            playClickSound();
        } else if (field.equals("Target")) {
            editorUI.searchQuery = "";
            editorUI.isTargetPickerOpen = true;
            playClickSound();
        }
    }

    private void charTypedInPicker(String newChar) {

        if (editorUI.selectionStart != -1 && editorUI.selectionEnd != -1) {
            int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
            int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);

            start = Math.max(0, Math.min(start, editorUI.searchQuery.length()));
            end = Math.max(0, Math.min(end, editorUI.searchQuery.length()));

            editorUI.searchQuery = editorUI.searchQuery.substring(0, start) + newChar + editorUI.searchQuery.substring(end);
            editorUI.cursorIndex = start + 1;
            editorUI.selectionStart = -1;
            editorUI.selectionEnd = -1;
        } else {

            if (editorUI.cursorIndex > editorUI.searchQuery.length())
                editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.searchQuery = editorUI.searchQuery.substring(0, editorUI.cursorIndex) + newChar + editorUI.searchQuery.substring(editorUI.cursorIndex);
            editorUI.cursorIndex++;
        }
    }

    public static void renderStaticOverlayFromUI(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.renderStaticTextOverlay(graphics, text, x, y, maxWidth);
            }
        });
    }

    public void updateTaskNameAndId(String newName) {
        if (this.selectedQuest != null && this.taskToModify != null) {
            this.taskToModify.setName(newName);
            String newId = QuestTask.generateTaskId(this.selectedQuest.getId(), newName, this.selectedQuest.getTasks());
            this.taskToModify.setId(newId);
        }
    }

    public void updateTaskTargetAndName(QuestTask.TaskType type, String targetId) {
        this.taskToModify.setTargetId(targetId);
        updateTaskNameAndId(this.taskToModify.getTargetDisplayName());
    }

    public void dropTask(double mouseX, double mouseY) {
        if (this.selectedQuest == null || this.movingTask == null) return;

        int panelWidth = 300;
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int) this.popupX;
        int tasksAreaX = x + 10;
        int slotSize = 20;
        int maxVisibleTasks = 4;

        List<QuestTask> tasks = this.selectedQuest.getTasks();
        tasks.remove(movingTask);

        int startIdx = taskPage * maxVisibleTasks;
        boolean hasPagination = tasks.size() + 1 > maxVisibleTasks;
        int renderX = tasksAreaX + (hasPagination ? 12 : 0);

        int insertIndex = -1;
        for (int i = 0; i < Math.min(maxVisibleTasks, tasks.size() + 1); i++) {
            int ix = renderX + (i * (slotSize + 4));
            if (mouseX < ix + (slotSize / 2)) {
                insertIndex = startIdx + i;
                break;
            }
        }

        if (insertIndex == -1 || insertIndex > tasks.size()) tasks.add(movingTask);
        else tasks.add(insertIndex, movingTask);
    }

    public void dropQuest(double mouseX, double mouseY) {
        if (this.movingQuest == null) return;
        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);
        double snappedX = Math.round(worldMouseX / GRID_SNAP) * GRID_SNAP;
        double snappedY = Math.round(worldMouseY / GRID_SNAP) * GRID_SNAP;
        this.movingQuest.setX(snappedX - (this.movingQuest.getSize() / 2.0));
        this.movingQuest.setY(snappedY - (this.movingQuest.getSize() / 2.0));

        saveChapterData(this.movingQuest.getChapterName());
    }

    public void dropText(double mouseX, double mouseY) {
        if (this.movingCanvasText == null) return;
        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        double targetX = worldMouseX - this.dragOffsetX;
        double targetY = worldMouseY - this.dragOffsetY;

        this.movingCanvasText.setX(Math.round(targetX / GRID_SNAP) * GRID_SNAP);
        this.movingCanvasText.setY(Math.round(targetY / GRID_SNAP) * GRID_SNAP);
        saveChapterData(this.movingCanvasText.getChapterName());
    }

    public void dropImage(double mouseX, double mouseY) {
        if (this.movingCanvasImage == null) return;
        float absoluteCenterX = (float) (this.width / 2.0);
        float absoluteCenterY = (float) (this.height / 2.0);
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        double targetX = worldMouseX - this.dragOffsetX;
        double targetY = worldMouseY - this.dragOffsetY;
        this.movingCanvasImage.setX(Math.round(targetX / GRID_SNAP) * GRID_SNAP);
        this.movingCanvasImage.setY(Math.round(targetY / GRID_SNAP) * GRID_SNAP);
        saveChapterData(this.movingCanvasImage.getChapterName());
    }

    private List<String> getTaskOptions() {
        if (this.selectedQuest == null) return List.of();
        boolean canMove = this.selectedQuest.getTasks().size() > 1;
        if (canMove) {
            return List.of("Edit", "Complete", "Reset Progress", "Move", "Delete");
        }
        return List.of("Edit", "Complete", "Reset Progress", "Delete");
    }

    /**
     * Helper to find and save a chapter by name.
     */
    public void saveChapterData(String chapterName) {
        SidebarChapter targetChapter = null;

        for (SidebarEntry entry : this.sidebarEntries) {
            if (entry instanceof SidebarChapter ch && ch.getId().equals(chapterName)) {
                targetChapter = ch;
                break;
            } else if (entry instanceof SidebarGroup group) {
                targetChapter = group.getChapters().stream()
                        .filter(ch -> ch.getId().equals(chapterName))
                        .findFirst().orElse(null);
            }
            if (targetChapter != null) break;
        }

        if (targetChapter != null) {
            final String finalChapterId = targetChapter.getId();

            int groupOrder = -1;
            int chapterOrder = 0;
            int groupColor = COL_TEXT;
            String groupName = "";

            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry == targetChapter) {
                    groupOrder = i;
                    break;
                } else if (entry instanceof SidebarGroup group) {
                    int chIdx = group.getChapters().indexOf(targetChapter);
                    if (chIdx != -1) {
                        groupOrder = i;
                        chapterOrder = chIdx;

                        groupName = group.getName();
                        break;
                    }
                }
            }

            String prettyTitle = targetChapter.getName();
            String sanitizedId = targetChapter.getId();
            QuestChapter data = new QuestChapter(groupName, groupOrder, groupColor, sanitizedId, prettyTitle, chapterOrder,
                    targetChapter.getIconStack().getItem(),
                    new ArrayList<>(allQuests.stream().filter(q -> q.getChapterName().equals(finalChapterId)).toList()),
                    new ArrayList<>(allCanvasTexts.stream().filter(ct -> ct.getChapterName().equals(finalChapterId)).toList()),
                    new ArrayList<>(allCanvasImages.stream().filter(ci -> ci.getChapterName().equals(finalChapterId)).toList()),
                    targetChapter.getOffsetX(),
                    targetChapter.getOffsetY(),
                    targetChapter.getZoom());

            ClientPacketDistributor.sendToServer(new SaveChapterPayload(data));
        }
    }

    /**
     * Performs a full database save.
     * Mandatory after renaming Chapters or Groups to fix global dependency IDs.
     */
    public void saveAllChapters() {

        for (SidebarEntry entry : sidebarEntries) {
            if (entry instanceof SidebarChapter ch) {
                saveChapterData(ch.getId());
            } else if (entry instanceof SidebarGroup group) {
                for (SidebarChapter ch : group.getChapters()) {
                    saveChapterData(ch.getId());
                }
            }
        }

        saveGroupManifest();
    }

    /**
     * Lightweight save that only updates the sidebar structure (order, expansion, groups)
     * without re-saving every individual chapter file.
     */
    public void saveGroupManifest() {
        List<QuestGroup> groupData = new ArrayList<>();
        List<SaveGroupsPayload.StandaloneChapterInfo> rootChapters = new ArrayList<>();

        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);
            if (entry instanceof SidebarGroup group) {
                List<String> chaptersInGroup = group.getChapters().stream()
                        .map(SidebarChapter::getId)
                        .toList();

                groupData.add(new QuestGroup(
                        group.getName(),
                        group.getTitle(),
                        group.getTitleColor(),
                        i,
                        group.isExpanded(),
                        chaptersInGroup
                ));
            } else if (entry instanceof SidebarChapter chapter) {
                rootChapters.add(new SaveGroupsPayload.StandaloneChapterInfo(chapter.getId(), i));
            }
        }

        ClientPacketDistributor.sendToServer(new SaveGroupsPayload(groupData, rootChapters));
    }

    public void refreshTaskProgress(String questId, String taskId, int amount, QuestTask.TaskState state) {
        Quest quest = questLookup.get(questId);
        if (quest != null) {
            for (QuestTask task : quest.getTasks()) {
                if (task.getId().equals(taskId)) {
                    task.setCurrentAmount(amount);
                    task.setState(state);
                    break;
                }
            }
            updateQuestStates();
        }
    }

    public void refreshGlobalProgress() {
        var taskMap = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS;
        var completedIds = SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS;

        for (Quest quest : allQuests) {

            for (QuestTask task : quest.getTasks()) {
                int current = taskMap.getOrDefault(task.getId(), 0);
                task.setCurrentAmount(current);

                if (current >= task.getRequiredAmount()) task.setState(QuestTask.TaskState.COMPLETE);
                else if (current > 0) task.setState(QuestTask.TaskState.PARTIAL);
                else task.setState(QuestTask.TaskState.INCOMPLETE);
            }

            if (completedIds.contains(quest.getId())) {
                quest.setState(QuestState.COMPLETED);
            }
        }
        updateQuestStates();
        updateClaimableCache();
    }

    private void renderItemSubmissionModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int boxW = 160;
        int boxH = 100;
        int x = (this.width - boxW) / 2;
        int y = (this.height - boxH) / 2;

        graphics.fill(0, 0, this.width, this.height, COL_DIM);

        graphics.fill(x, y, x + boxW, y + boxH, COL_UI_BG);
        drawBorder(graphics, x, y, boxW, boxH, COL_UI_BORDER);

        graphics.centeredText(font, Component.literal("Submit Items"), x + boxW / 2, y + 8, COL_TEXT);

        int currentAmount = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(submittingTask.getId(), 0);
        boolean isHovered = mouseX >= x + (boxW / 2) - 10 && mouseX <= x + (boxW / 2) + 10 && mouseY >= y + 25 && mouseY <= y + 45;
        int innerColor = isHovered ? COL_PANEL_HEADER : COL_UI_BG;

        editorUI.drawTaskIcon(graphics, submittingTask, currentAmount, innerColor, x + (boxW / 2) - 10, y + 25, mouseX, mouseY, 20);

        String progress = currentAmount + " / " + submittingTask.getRequiredAmount();
        graphics.centeredText(font, Component.literal(progress), x + boxW / 2, y + 50, COL_TEXT);

        int btnW = 50;
        int btnH = 16;
        int btnY = y + boxH - 25;

        int cancelX = x + 20;
        int cancelColor = editorUI.getButtonColor(mouseX, mouseY, cancelX, btnY, btnW, btnH, COL_BUTTON_BASE);
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelColor);
        graphics.centeredText(font, Component.literal("Cancel"), cancelX + btnW / 2, btnY + 4, COL_TEXT);

        int submitX = x + boxW - 20 - btnW;
        int submitColor = editorUI.getButtonColor(mouseX, mouseY, submitX, btnY, btnW, btnH, COL_BUTTON_BASE);
        graphics.fill(submitX, btnY, submitX + btnW, btnY + btnH, submitColor);
        graphics.centeredText(font, Component.literal("Submit"), submitX + btnW / 2, btnY + 4, COL_TEXT);
    }

    public void handleItemSubmissionClicks(double mouseX, double mouseY) {
        int boxW = 160;
        int boxH = 100;
        int x = (this.width - boxW) / 2;
        int y = (this.height - boxH) / 2;
        int btnW = 50;
        int btnH = 16;
        int btnY = y + boxH - 25;

        if (mouseX >= x + 20 && mouseX <= x + 20 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.isItemSubmissionOpen = false;
            this.submittingTask = null;
            playClickSound();
        } else if (mouseX >= x + boxW - 20 - btnW && mouseX <= x + boxW - 20 && mouseY >= btnY && mouseY <= btnY + btnH) {

            ClientPacketDistributor.sendToServer(new SubmitItemTaskPayload(selectedQuest.getId(), submittingTask.getId()));
            this.isItemSubmissionOpen = false;
            this.submittingTask = null;
            playClickSound();
        }
    }

    private void openTextEditor(CanvasText ct) {
        this.originalCanvasText = ct;

        this.editingCanvasText = new CanvasText(ct.getText(), ct.getX(), ct.getY(), ct.getScale(), ct.getColor());
        this.editingCanvasText.setChapterName(ct.getChapterName());
        this.editorUI.searchQuery = ct.getText();
        this.editorUI.cursorIndex = this.editorUI.searchQuery.length();
        this.editorUI.selectionStart = 0;
        this.editorUI.selectionEnd = this.editorUI.searchQuery.length();
        this.isTextEditorOpen = true;
    }

    private void renderCanvasTextEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int windowWidth = 200;
        int windowHeight = 145;
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        graphics.fill(x, y, x + windowWidth, y + windowHeight, COL_UI_BG);
        drawBorder(graphics, x, y, windowWidth, windowHeight, COL_UI_BORDER);
        graphics.centeredText(font, Component.literal("Edit Canvas Text"), x + windowWidth / 2, y + 10, COL_TEXT);

        graphics.text(font, "Text:", x + 15, y + 35, COL_TEXT);
        editorUI.drawEditableText(graphics, editorUI.searchQuery, x + 50, y + 33, 135, 14, false);

        graphics.text(font, "Scale:", x + 15, y + 60, COL_TEXT);
        String scaleText = String.format("%.1fx", editingCanvasText.getScale());
        graphics.text(font, scaleText, x + 55, y + 60, COL_TEXT);

        int sliderX = x + 90;
        int sliderW = 95;
        int sliderY = y + 64;

        float progress = (editingCanvasText.getScale() - 0.5f) / (5.0f - 0.5f);

        graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 2, COL_INPUT_BG);
        graphics.fill(sliderX, sliderY, sliderX + (int) (progress * sliderW), sliderY + 2, COL_SLIDER_TRACK);

        int handleX = sliderX + (int) (progress * sliderW);
        graphics.fill(handleX - 2, sliderY - 3, handleX + 2, sliderY + 5, COL_UI_BORDER);

        graphics.text(font, "Color:", x + 15, y + 85, COL_TEXT);

        graphics.fill(x + 55, y + 83, x + 55 + 35, y + 83 + 12, COL_INPUT_BG);
        graphics.fill(x + 56, y + 84, x + 55 + 34, y + 83 + 11, editingCanvasText.getColor());
        graphics.outline(x + 55, y + 83, 35, 12, COL_UI_BORDER);

        editorUI.drawButton(graphics, mouseX, mouseY, x + 100, y + 82, 70, 14, "Change", COL_BUTTON_BASE);

        int btnY = y + windowHeight - 20;
        int btnW = 45;
        int cancelX = x + windowWidth - 110;
        int saveX = x + windowWidth - 60;

        int cancelColor = editorUI.getButtonColor(mouseX, mouseY, cancelX, btnY, btnW, 14, COL_BUTTON_BASE);
        int saveColor = editorUI.getButtonColor(mouseX, mouseY, saveX, btnY, btnW, 14, COL_BUTTON_BASE);

        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + 14, cancelColor);
        graphics.centeredText(font, Component.literal("Cancel"), cancelX + btnW / 2, btnY + 3, COL_TEXT);

        graphics.fill(saveX, btnY, saveX + btnW, btnY + 14, saveColor);
        graphics.centeredText(font, Component.literal("Save"), saveX + btnW / 2, btnY + 3, COL_TEXT);

        if (editorUI.isColorPickerOpen) {
            int pickerWidth = 135;
            QuestEditorUI.PickerBounds b = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, pickerWidth, 168, 16);

            editorUI.renderPickerFrame(graphics, b, true, null, () -> {
                graphics.text(font, "Pick Color", b.x() + 5, b.y() + 4, COL_TEXT);
                editorUI.renderColorWheel(graphics, mouseX, mouseY, b, this.pendingPickerColor, color -> {
                    this.pendingPickerColor = color;
                });
                editorUI.drawButton(graphics, mouseX, mouseY, b.x() + 5, b.y() + b.h() - 18, b.w() - 10, 14, "Submit", COL_BUTTON_BASE);
            });
        }
    }

    public void handleCanvasTextEditorClicks(double mouseX, double mouseY) {
        int windowWidth = 200;
        int windowHeight = 145;
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        QuestEditorUI.PickerBounds pBounds = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, 135, 168, 16);
        boolean insidePicker = editorUI.isColorPickerOpen && mouseX >= pBounds.x() && mouseX <= pBounds.x() + pBounds.w() && mouseY >= pBounds.y() && mouseY <= pBounds.y() + pBounds.h();

        if (editorUI.isColorPickerOpen) {
            if (insidePicker) {

                if (mouseX >= pBounds.x() + 5 && mouseX <= pBounds.x() + pBounds.w() - 5 && mouseY >= pBounds.y() + pBounds.h() - 18 && mouseY <= pBounds.y() + pBounds.h() - 4) {
                    commitPickerColor();
                    return;
                }

                if (mouseY < pBounds.y() + 16) {
                    this.isDraggingPickerWindow = true;
                    this.dragOffsetX = mouseX - pBounds.x();
                    this.dragOffsetY = mouseY - pBounds.y();
                    return;
                }

                handlePickerColorChange(mouseX, mouseY, pBounds, null);
                return;
            } else {

                boolean insideMain = mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight;
                if (insideMain) {
                    editorUI.isColorPickerOpen = false;
                    playClickSound();
                }
            }
        }

        boolean insideMain = mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight;
        if (!insideMain) {
            editorUI.closePicker();
            isTextEditorOpen = false;
            editingCanvasText = null;
            originalCanvasText = null;
            return;
        }

        if (mouseX >= x + 50 && mouseX <= x + 185 && mouseY >= y + 33 && mouseY <= y + 47) {
            editorUI.selectionStart = -1;
            editorUI.selectionEnd = -1;
            return;
        }

        int sliderX = x + 90;
        int sliderW = 95;
        if (mouseX >= sliderX - 2 && mouseX <= sliderX + sliderW + 2 && mouseY >= y + 60 && mouseY <= y + 70) {
            this.isDraggingTextSizeSlider = true;
            return;
        }

        if (mouseX >= x + 100 && mouseX <= x + 170 && mouseY >= y + 82 && mouseY <= y + 96) {
            editorUI.isColorPickerOpen = !editorUI.isColorPickerOpen;
            if (editorUI.isColorPickerOpen) {
                this.pendingPickerColor = editingCanvasText.getColor();

                int pW = 135;
                int pH = 168;
                this.pickerX = x + 180;
                this.pickerY = y + 70;
                clampPickerPosition(pW, pH);
            }
            playClickSound();
            return;
        }

        int btnY = y + windowHeight - 20;

        if (mouseX >= x + windowWidth - 110 && mouseX <= x + windowWidth - 110 + 45 && mouseY >= btnY && mouseY <= btnY + 14) {
            editorUI.closePicker();
            isTextEditorOpen = false;
            editingCanvasText = null;
            originalCanvasText = null;
        } else if (mouseX >= x + windowWidth - 60 && mouseX <= x + windowWidth - 60 + 45 && mouseY >= btnY && mouseY <= btnY + 14) {
            editingCanvasText.setText(editorUI.searchQuery);
            if (allCanvasTexts.contains(originalCanvasText)) {
                int index = allCanvasTexts.indexOf(originalCanvasText);
                allCanvasTexts.set(index, editingCanvasText);
            } else {
                allCanvasTexts.add(editingCanvasText);
            }
            saveChapterData(editingCanvasText.getChapterName());
            editorUI.closePicker();
            isTextEditorOpen = false;
            editingCanvasText = null;
            originalCanvasText = null;
        }
    }

    private void handlePickerColorChange(double mouseX, double mouseY, QuestEditorUI.PickerBounds b, String configKey) {
        int pickerY = b.y() + b.barHeight() + 5;
        int topY = b.y() + b.barHeight() + 5;
        int hexY = pickerY + 70 + 8;
        int alphaLabelY = hexY + 16;
        int alphaY = alphaLabelY + 12;

        if (mouseX >= b.x() + 35 && mouseX <= b.x() + 100 && mouseY >= pickerY + 70 + 8 && mouseY <= pickerY + 70 + 8 + 12) {
            editorUI.isHexEditing = true;
            editorUI.hexQuery = String.format("%06X", (this.pendingPickerColor & 0xFFFFFF));
            editorUI.hexCursorIndex = editorUI.hexQuery.length();
            playClickSound();
        } else {

            boolean clickingSB = mouseX >= b.x() + 5 && mouseX <= b.x() + 75 && mouseY >= pickerY && mouseY <= pickerY + 70;
            boolean clickingHue = mouseX >= b.x() + 80 && mouseX <= b.x() + 92 && mouseY >= pickerY && mouseY <= pickerY + 70;

            int sliderW = b.w() - 10;
            boolean clickingAlpha = mouseX >= b.x() + 5 && mouseX <= b.x() + 5 + sliderW && mouseY >= alphaY && mouseY <= alphaY + 10;

            if (clickingSB) {
                this.isDraggingSB = true;
            } else if (clickingHue) {
                this.isDraggingHue = true;
            } else if (clickingAlpha) {
                this.isDraggingAlpha = true;
            }

            this.pendingPickerColor = editorUI.getColorAt(mouseX, mouseY, b, this.pendingPickerColor, clickingSB, clickingHue, clickingAlpha);
            editorUI.hexQuery = String.format("%06X", (this.pendingPickerColor & 0xFFFFFF));

            editorUI.isHexEditing = false;
        }
    }

    private void applyHexToCurrentTarget() {
        try {
            if (editorUI.hexQuery.length() == 6) {
                int newRGB = Integer.parseInt(editorUI.hexQuery, 16);
                int alpha = (this.pendingPickerColor >> 24) & 0xFF;
                this.pendingPickerColor = (alpha << 24) | newRGB;
            }
        } catch (Exception ignored) {
        }
    }

    private void commitPickerColor() {
        if (this.isSettingsOpen && editingConfigColor != null) {
            setConfigValueByName(editingConfigColor, this.pendingPickerColor);
        } else if (this.isTextEditorOpen && editingCanvasText != null) {
            editingCanvasText.setColor(this.pendingPickerColor);
        }
        editorUI.isColorPickerOpen = false;
        editorUI.isHexEditing = false;
        playClickSound();
    }

    private void renderSettingsModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int windowWidth = 400;
        int windowHeight = 240;
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        graphics.fill(0, 0, this.width, this.height, COL_DIM);
        graphics.fill(x, y, x + windowWidth, y + windowHeight, COL_UI_BG);
        drawBorder(graphics, x, y, windowWidth, windowHeight, COL_UI_BORDER);
        graphics.centeredText(font, Component.literal("UI Settings"), x + windowWidth / 2, y + 10, COL_TEXT);

        int rowH = 22;
        int colW = 195;
        int listY = y + 30;
        int listH = windowHeight - 60;
        graphics.enableScissor(x, listY, x + windowWidth, listY + listH);

        for (int i = 0; i < CONFIG_ITEM_MAP.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int rowX = x + 10 + (col * colW);
            int rowY = listY + (row * rowH) - settingsScrollOffset;

            if (rowY < listY - rowH || rowY > listY + listH) continue;

            String label = truncate(CONFIG_ITEM_MAP[i][0], 105);
            graphics.text(font, label, rowX, rowY + 5, COL_TEXT);
            int colorValue = getConfigValueByName(CONFIG_ITEM_MAP[i][1]);
            graphics.fill(rowX + 110, rowY + 3, rowX + 130, rowY + 15, colorValue);
            graphics.outline(rowX + 110, rowY + 3, 20, 12, COL_UI_BORDER);
            editorUI.drawButton(graphics, mouseX, mouseY, rowX + 140, rowY + 2, 40, 14, "Edit", COL_BUTTON_BASE);
        }

        int nextIdx = CONFIG_ITEM_MAP.length;
        int col = nextIdx % 2;
        int row = nextIdx / 2;
        int rowX = x + 10 + (col * colW);
        int rowY = listY + (row * rowH) - settingsScrollOffset;

        if (rowY >= listY - rowH && rowY <= listY + listH) {
            graphics.text(font, "Grid Size:", rowX, rowY + 5, COL_TEXT);

            int sliderX = rowX + 70;
            int sliderW = 80;
            int sliderY = rowY + 8;

            float progress = (float) ((GRID_SNAP - 4.0) / (32.0 - 4.0));

            graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 2, COL_INPUT_BG);
            graphics.fill(sliderX, sliderY, sliderX + (int) (progress * sliderW), sliderY + 2, COL_SLIDER_TRACK);

            int handleX = sliderX + (int) (progress * sliderW);
            graphics.fill(handleX - 2, sliderY - 3, handleX + 2, sliderY + 5, COL_UI_BORDER);

            String valText = String.format("%.1f", GRID_SNAP);
            graphics.text(font, valText, sliderX + sliderW + 5, rowY + 5, COL_TEXT);
        }

        graphics.disableScissor();

        int footerY = y + windowHeight - 20;

        editorUI.drawButton(graphics, mouseX, mouseY, x + 10, footerY, 100, 14, "Reset Defaults", COL_BUTTON_BASE);

        int saveX = x + windowWidth - 130;
        editorUI.drawButton(graphics, mouseX, mouseY, saveX, footerY, 120, 14, "Save & Close", COL_BUTTON_BASE);

        if (editorUI.isColorPickerOpen) {
            int pickerWidth = 135;
            QuestEditorUI.PickerBounds b = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, pickerWidth, 168, 16);

            editorUI.renderPickerFrame(graphics, b, true, null, () -> {
                graphics.text(font, "Pick Color", b.x() + 5, b.y() + 4, COL_TEXT);
                editorUI.renderColorWheel(graphics, mouseX, mouseY, b, this.pendingPickerColor, color -> {
                    this.pendingPickerColor = color;
                });
                editorUI.drawButton(graphics, mouseX, mouseY, b.x() + 5, b.y() + b.h() - 18, b.w() - 10, 14, "Submit", COL_BUTTON_BASE);
            });
        }
    }

    public void handleSettingsClicks(double mouseX, double mouseY) {
        int windowWidth = 400;
        int windowHeight = 240;
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        QuestEditorUI.PickerBounds pBounds = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, 135, 168, 16);
        boolean insidePicker = editorUI.isColorPickerOpen && mouseX >= pBounds.x() && mouseX <= pBounds.x() + pBounds.w() && mouseY >= pBounds.y() && mouseY <= pBounds.y() + pBounds.h();

        if (editorUI.isColorPickerOpen) {
            if (insidePicker) {

                if (mouseY < pBounds.y() + 16) {
                    this.isDraggingPickerWindow = true;
                    this.dragOffsetX = mouseX - pBounds.x();
                    this.dragOffsetY = mouseY - pBounds.y();
                    return;
                }

                if (mouseX >= pBounds.x() + 5 && mouseX <= pBounds.x() + pBounds.w() - 5 && mouseY >= pBounds.y() + pBounds.h() - 18 && mouseY <= pBounds.y() + pBounds.h() - 4) {
                    commitPickerColor();
                    return;
                }
                handlePickerColorChange(mouseX, mouseY, pBounds, editingConfigColor);
                return;
            } else {

                if (mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight) {
                    editorUI.isColorPickerOpen = false;
                    playClickSound();
                }
            }
        }

        boolean insideMain = mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight;
        if (!insideMain) {
            isSettingsOpen = false;
            editorUI.closePicker();
            saveThemeToConfig();
            return;
        }

        for (int i = 0; i < CONFIG_ITEM_MAP.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int rowX = x + 10 + (col * 195);
            int rowY = y + 30 + (row * 22) - settingsScrollOffset;
            if (rowY < y + 30 || rowY > y + windowHeight - 30) continue;

            if (mouseX >= rowX + 140 && mouseX <= rowX + 180 && mouseY >= rowY + 2 && mouseY <= rowY + 16) {
                editingConfigColor = CONFIG_ITEM_MAP[i][1];
                this.pendingPickerColor = getConfigValueByName(editingConfigColor);
                editorUI.isColorPickerOpen = true;

                int pW = 135;
                int pH = 168;
                this.pickerX = rowX + 190;
                this.pickerY = rowY - 50;

                clampPickerPosition(pW, pH);

                playClickSound();
                return;
            }
        }

        int nextIdx = CONFIG_ITEM_MAP.length;
        int col = nextIdx % 2;
        int row = nextIdx / 2;
        int rowX = x + 10 + (col * 195);
        int rowY = y + 30 + (row * 22) - settingsScrollOffset;
        if (mouseX >= rowX + 70 && mouseX <= rowX + 150 && mouseY >= rowY && mouseY <= rowY + 22) {
            this.isDraggingGridSlider = true;
            return;
        }

        int footerY = y + windowHeight - 20;
        if (mouseX >= x + 10 && mouseX <= x + 110 && mouseY >= footerY && mouseY <= footerY + 14) {
            resetThemeToDefaults();
            playClickSound();
        } else if (mouseX >= x + windowWidth - 130 && mouseX <= x + windowWidth - 10 && mouseY >= footerY && mouseY <= footerY + 14) {
            isSettingsOpen = false;
            editorUI.closePicker();
            saveThemeToConfig();
        }
    }

    private void clampPickerPosition(int pW, int pH) {

        this.pickerX = (int) Math.max(0, Math.min(this.width - pW, this.pickerX));
        this.pickerY = (int) Math.max(0, Math.min(this.height - pH, this.pickerY));
    }

    public void handleRewardFieldClick(String field) {
        if (field.equals("Type")) {
            editorUI.isTypePickerOpen = true;
            playClickSound();
        } else if (field.equals("Target")) {
            editorUI.scrollOffset = 0;
            editorUI.searchQuery = "";
            editorUI.isIconPickerOpen = true;
            playClickSound();
        } else if (field.equals("Quantity")) {
            editorUI.searchQuery = String.valueOf(rewardToModify.getCount());
            editorUI.isQuantityOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0;
            editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("Command")) {
            editorUI.searchQuery = rewardToModify.getCommand();
            editorUI.isNameOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0;
            editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        }
    }

    public boolean isMouseOverText(double mouseX, double mouseY, CanvasText ct) {
        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;
        double screenX = absoluteCenterX + (ct.getX() - this.offsetX) * this.zoom;
        double screenY = absoluteCenterY + (ct.getY() - this.offsetY) * this.zoom;
        int w = (int) (font.width(ct.getText()) * ct.getScale() * this.zoom);
        int h = (int) (font.lineHeight * ct.getScale() * this.zoom);
        return mouseX >= screenX && mouseX <= screenX + w && mouseY >= screenY && mouseY <= screenY + h;
    }

    private int getConfigValueByName(String name) {
        return switch (name) {
            case "COL_UI_BG" -> COL_UI_BG;
            case "COL_UI_BORDER" -> COL_UI_BORDER;
            case "COL_SIDEBAR_BG" -> COL_SIDEBAR_BG;
            case "COL_SIDEBAR_BORDER" -> COL_SIDEBAR_BORDER;
            case "COL_PANEL_HEADER" -> COL_PANEL_HEADER;
            case "COL_PANEL_DIVIDER" -> COL_PANEL_DIVIDER;
            case "COL_BUTTON_BASE" -> COL_BUTTON_BASE;
            case "COL_INPUT_BG" -> COL_INPUT_BG;
            case "COL_TEXT" -> COL_TEXT;
            case "COL_TEXT_GOLD" -> COL_TEXT_GOLD;
            case "COL_TEXT_SELECTED" -> COL_TEXT_SELECTED;
            case "COL_ERROR" -> COL_ERROR;
            case "COL_STATE_LOCKED" -> COL_STATE_LOCKED;
            case "COL_STATE_AVAILABLE" -> COL_STATE_AVAILABLE;
            case "COL_STATE_PARTIAL" -> COL_STATE_PARTIAL;
            case "COL_STATE_COMPLETED" -> COL_STATE_COMPLETED;
            case "COL_HOVER_UI" -> COL_HOVER_UI;
            case "COL_HOVER_MENU" -> COL_HOVER_MENU;
            case "COL_SELECTION" -> COL_SELECTION;
            case "COL_GRID" -> COL_GRID;
            case "COL_DIM" -> COL_DIM;
            case "COL_GHOST_BORDER" -> COL_GHOST_BORDER;
            case "COL_GHOST_FILL" -> COL_GHOST_FILL;
            default -> 0xFFFFFFFF;
        };
    }

    private void setConfigValueByName(String name, int val) {
        switch (name) {
            case "COL_UI_BG" -> COL_UI_BG = val;
            case "COL_UI_BORDER" -> COL_UI_BORDER = val;
            case "COL_SIDEBAR_BG" -> COL_SIDEBAR_BG = val;
            case "COL_SIDEBAR_BORDER" -> COL_SIDEBAR_BORDER = val;
            case "COL_PANEL_HEADER" -> COL_PANEL_HEADER = val;
            case "COL_PANEL_DIVIDER" -> COL_PANEL_DIVIDER = val;
            case "COL_BUTTON_BASE" -> COL_BUTTON_BASE = val;
            case "COL_INPUT_BG" -> COL_INPUT_BG = val;
            case "COL_TEXT" -> COL_TEXT = val;
            case "COL_TEXT_GOLD" -> COL_TEXT_GOLD = val;
            case "COL_TEXT_SELECTED" -> COL_TEXT_SELECTED = val;
            case "COL_ERROR" -> COL_ERROR = val;
            case "COL_STATE_LOCKED" -> COL_STATE_LOCKED = val;
            case "COL_STATE_AVAILABLE" -> COL_STATE_AVAILABLE = val;
            case "COL_STATE_PARTIAL" -> COL_STATE_PARTIAL = val;
            case "COL_STATE_COMPLETED" -> COL_STATE_COMPLETED = val;
            case "COL_HOVER_UI" -> COL_HOVER_UI = val;
            case "COL_HOVER_MENU" -> COL_HOVER_MENU = val;
            case "COL_SELECTION" -> COL_SELECTION = val;
            case "COL_GRID" -> COL_GRID = val;
            case "COL_DIM" -> COL_DIM = val;
            case "COL_GHOST_BORDER" -> COL_GHOST_BORDER = val;
            case "COL_GHOST_FILL" -> COL_GHOST_FILL = val;
        }
    }

    private void saveThemeToConfig() {
        SimplyQuestsConfig.UI_BG.set(COL_UI_BG);
        SimplyQuestsConfig.UI_BORDER.set(COL_UI_BORDER);
        SimplyQuestsConfig.SIDEBAR_BG.set(COL_SIDEBAR_BG);
        SimplyQuestsConfig.SIDEBAR_BORDER.set(COL_SIDEBAR_BORDER);
        SimplyQuestsConfig.PANEL_HEADER.set(COL_PANEL_HEADER);
        SimplyQuestsConfig.PANEL_DIVIDER.set(COL_PANEL_DIVIDER);
        SimplyQuestsConfig.BUTTON_BASE.set(COL_BUTTON_BASE);
        SimplyQuestsConfig.INPUT_BG.set(COL_INPUT_BG);
        SimplyQuestsConfig.TEXT.set(COL_TEXT);
        SimplyQuestsConfig.TEXT_GOLD.set(COL_TEXT_GOLD);
        SimplyQuestsConfig.TEXT_SELECTED.set(COL_TEXT_SELECTED);
        SimplyQuestsConfig.ERROR.set(COL_ERROR);
        SimplyQuestsConfig.STATE_LOCKED.set(COL_STATE_LOCKED);
        SimplyQuestsConfig.STATE_AVAILABLE.set(COL_STATE_AVAILABLE);
        SimplyQuestsConfig.STATE_PARTIAL.set(COL_STATE_PARTIAL);
        SimplyQuestsConfig.STATE_COMPLETED.set(COL_STATE_COMPLETED);
        SimplyQuestsConfig.HOVER_UI.set(COL_HOVER_UI);
        SimplyQuestsConfig.HOVER_MENU.set(COL_HOVER_MENU);
        SimplyQuestsConfig.SELECTION.set(COL_SELECTION);
        SimplyQuestsConfig.TOOLTIP_BG.set(COL_TOOLTIP_BG);
        SimplyQuestsConfig.GRID.set(COL_GRID);
        SimplyQuestsConfig.DIM.set(COL_DIM);
        SimplyQuestsConfig.GRID_SIZE.set(GRID_SNAP);
        SimplyQuestsConfig.SLIDER_TRACK.set(COL_SLIDER_TRACK);
        SimplyQuestsConfig.GHOST_BORDER.set(COL_GHOST_BORDER);
        SimplyQuestsConfig.GHOST_FILL.set(COL_GHOST_FILL);
        SimplyQuestsConfig.SPEC.save();
    }

    private void resetThemeToDefaults() {
        COL_UI_BG = 0xFF222222;
        COL_UI_INNER_BG = 0xFF111111;
        COL_UI_BORDER = 0xFFFFFFFF;
        COL_SIDEBAR_BG = 0xDD111115;
        COL_SIDEBAR_BORDER = 0xFF3A3A43;
        COL_PANEL_HEADER = 0xFF333333;
        COL_PANEL_DIVIDER = 0xFF555555;
        COL_BUTTON_BASE = 0xFF555555;
        COL_INPUT_BG = 0xFF000000;
        COL_TEXT = 0xFFFFFFFF;
        COL_TEXT_GOLD = 0xFFFFFF00;
        COL_TEXT_SELECTED = 0xFFFFFF55;
        COL_ERROR = 0xFFFF5555;
        COL_STATE_LOCKED = 0xFF505050;
        COL_STATE_AVAILABLE = 0xFFB0B0B0;
        COL_STATE_PARTIAL = 0xFF55FFFF;
        COL_STATE_COMPLETED = 0xFF55FF55;
        COL_SELECTION = 0x883399FF;
        COL_GRID = 0x0DFFFFFF;
        COL_DIM = 0xAA000000;
        COL_HOVER_UI = 0x28FFFFFF;
        COL_HOVER_MENU = 0x55FFFFFF;
        COL_TOOLTIP_BG = 0xF0101015;
        COL_SLIDER_TRACK = 0xFFAAAAAA;
        COL_GHOST_BORDER = 0x80505050;
        COL_GHOST_FILL = 0x40FFFFFF;
        GRID_SNAP = 16.0;
    }

    public void dropReward(double mouseX, double mouseY) {
        if (this.selectedQuest == null || this.movingReward == null) return;
        Rectangle bounds = getDetailsBounds();
        int rewardsAreaX = bounds.x + (bounds.width / 2) + 10;
        int rSlotTotalWidth = 24;
        int rMaxVisible = 4;

        List<QuestReward> rewards = this.selectedQuest.getRewards();
        rewards.remove(movingReward);

        int startIdx = rewardPage * rMaxVisible;
        boolean hasPager = rewards.size() + 1 > rMaxVisible;
        int renderX = rewardsAreaX + (hasPager ? 12 : 0);

        int insertIndex = -1;
        for (int i = 0; i < Math.min(rMaxVisible, rewards.size() + 1); i++) {
            int ix = renderX + (i * rSlotTotalWidth);
            if (mouseX < ix + (rSlotTotalWidth / 2)) {
                insertIndex = startIdx + i;
                break;
            }
        }

        if (insertIndex == -1 || insertIndex > rewards.size()) rewards.add(movingReward);
        else rewards.add(insertIndex, movingReward);

        saveChapterData(this.selectedQuest.getChapterName());
    }

    /**
     * Checks if a specific chapter contains any completed quests with unclaimed rewards.
     */
    private boolean hasClaimableRewards(String chapterName) {
        for (Quest q : this.allQuests) {
            if (q.getChapterName().equals(chapterName) && q.getState() == QuestState.COMPLETED) {
                for (QuestReward r : q.getRewards()) {

                    if (!SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(r.getId())) return true;
                }
            }
        }
        return false;
    }

    /**
     * Global check to see if the player has any rewards waiting to be claimed across all chapters.
     */
    public static boolean hasAnyClaimableRewards() {
        return anyClaimableCache;
    }

    /**
     * Global check for the Inventory button badge. Includes choice rewards.
     */
    public static boolean hasAnyUnclaimedRewards() {
        return anyUnclaimedGeneralCache;
    }

    /**
     * Internal helper to refresh the claimable status cache.
     * Called only when player data actually changes.
     */
    public static void updateClaimableCache() {
        Collection<Quest> questsToProcess;
        Minecraft mc = Minecraft.getInstance();

        boolean foundClaimable = false;
        boolean foundUnclaimed = false;

        if (mc.screen instanceof QuestScreen screen && screen.allQuests != null && !screen.allQuests.isEmpty()) {
            questsToProcess = screen.allQuests;
        } else {

            questsToProcess = new ArrayList<>(SimplyQuestsClientPacketHandler.getChapters().values()).stream()
                    .flatMap(c -> c.getQuests().stream()).toList();
        }

        for (Quest quest : questsToProcess) {
            if (SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.contains(quest.getId())) {
                for (QuestReward reward : quest.getRewards()) {
                    boolean claimed = SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(reward.getId());
                    if (!claimed) {
                        foundUnclaimed = true;

                        if (reward.getSubRewards().isEmpty()) {
                            foundClaimable = true;
                        }
                    }
                }
            }
            if (foundClaimable && foundUnclaimed) break;
        }

        anyClaimableCache = foundClaimable;
        anyUnclaimedGeneralCache = foundUnclaimed;
    }

    private boolean checkPermissions() {
        if (this.minecraft.player == null) return false;

        var localServer = this.minecraft.getSingleplayerServer();
        if (localServer != null) {
            NameAndId identity = new NameAndId(this.minecraft.player.getGameProfile().id(), this.minecraft.player.getGameProfile().name());
            return localServer.getPlayerList().isOp(identity);
        }

        return SimplyQuestsClientPacketHandler.IS_CLIENT_OP;
    }

    private void renderManipulationButton(GuiGraphicsExtractor graphics, int x, int y, Identifier icon, boolean active) {
        int bg = active ? COL_TEXT_GOLD : COL_PANEL_HEADER;
        graphics.fill(x, y, x + 15, y + 15, bg);
        graphics.outline(x, y, 15, 15, COL_UI_BORDER);

        int tint = active ? COL_UI_BG : COL_TEXT;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 2, y + 2);

        float iconScale = 11.0f / 16.0f;
        graphics.pose().scale(iconScale, iconScale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0f, 0.0f, 16, 16, 16, 16, tint);
        graphics.pose().popMatrix();
    }

    private void renderStandaloneAlphaSlider(GuiGraphicsExtractor graphics, QuestCanvasImage ci) {
        int sw = 100, sh = 10;
        int sx = -110, sy = 42;

        graphics.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);

        for (int i = 0; i < sw; i++) {
            float a = i / (float) sw;
            int color = ((int) (a * 255) << 24) | 0xFFFFFF;
            graphics.fill(sx + i, sy, sx + i + 1, sy + sh, color);
        }

        graphics.outline(sx, sy, sw, sh, COL_UI_BORDER);
        int markerX = sx + (int) (ci.getAlpha() * sw);
        graphics.fill(markerX - 1, sy - 2, markerX + 1, sy + sh + 2, COL_UI_BORDER);

        graphics.centeredText(font, Component.literal("Alpha: " + (int) (ci.getAlpha() * 100) + "%"), sx + sw / 2, sy - 10, COL_TEXT);
    }
}