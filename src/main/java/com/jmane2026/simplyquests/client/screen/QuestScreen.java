package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.client.SimplyQuestsClientPacketHandler;
import com.jmane2026.simplyquests.client.ClientQuestEvents;
import com.jmane2026.simplyquests.client.screen.input.CanvasHandler;
import com.jmane2026.simplyquests.client.screen.input.EditorHandler;
import com.jmane2026.simplyquests.client.screen.input.PickerHandler;
import com.jmane2026.simplyquests.config.SimplyQuestsConfig;
import com.jmane2026.simplyquests.data.QuestChapter;
import com.jmane2026.simplyquests.data.QuestGroup;
import com.jmane2026.simplyquests.network.*;
import com.jmane2026.simplyquests.quest.*;
import com.jmane2026.simplyquests.util.QuestClientData;
import net.minecraft.client.Minecraft;
import com.jmane2026.simplyquests.events.QuestServerEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;

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
    
    // --- IMAGE MANIPULATION STATE ---
    public QuestCanvasImage selectedCanvasImage = null;
    public enum ManipulationMode { NONE, SCALE, ROTATE, ALPHA }
    public ManipulationMode currentImageMode = ManipulationMode.NONE;
    public double initialRotateAngle = 0;
    public double initialImageRotation = 0;
    public boolean isDraggingAlphaSlider = false;
    public boolean isDraggingScaleHandle = false;
    public boolean isDraggingRotateHandle = false;
    public int scaleHandleIndex = -1; // 0-7: TL, T, TR, R, BR, B, BL, L
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

    // --- Centralized UI Colors (Ready for Config) ---
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

    // State Colors
    public static int COL_STATE_LOCKED = SimplyQuestsConfig.STATE_LOCKED.get();
    public static int COL_STATE_AVAILABLE = SimplyQuestsConfig.STATE_AVAILABLE.get();
    public static int COL_STATE_PARTIAL = SimplyQuestsConfig.STATE_PARTIAL.get(); // Brighter default set in Config
    public static int COL_STATE_COMPLETED = SimplyQuestsConfig.STATE_COMPLETED.get();

    // Interaction Colors
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

    // A constant for row height
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
        // FIX: Prevent duplicate rendering by checking if the quest is already registered.
        // This solves the "multiple badges" issue caused by redundant server syncs.
        if (this.questLookup.containsKey(quest.getId())) return;

        this.allQuests.add(quest);
        this.questLookup.put(quest.getId(), quest);
    }

    /**
     * Helper to get the current color for a quest state based on config variables.
     */
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
                // Check task progress from the synced client map
                boolean anyStarted = false;
                for (QuestTask t : quest.getTasks()) {
                    if (taskProgress.getOrDefault(t.getId(), 0) > 0) anyStarted = true;
                }
                quest.setState(anyStarted ? QuestState.PARTIAL : QuestState.AVAILABLE);
            }
        }

        // Update Sidebar Chapter States
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
        // FIX: Filter quests using the sanitized internal ID, not the display name
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
        // 0. Store current live state to prevent "jumps" during data refreshes (like group toggles)
        String previousId = (this.selectedChapter != null) ? this.selectedChapter.getId() : null;
        double liveX = this.offsetX;
        double liveY = this.offsetY;
        double liveZoom = this.zoom;

        super.init();

        if (checkPermissions())
        {
            QuestGlobalState.isEditModeEnabled = SimplyQuestsClientPacketHandler.IS_EDIT_MODE_ALLOWED;
        } else {
            QuestGlobalState.isEditModeEnabled = false;
        }

        // Clear local caches to force a rebuild from the QuestManager data
        this.sidebarEntries.clear();
        this.allQuests.clear();
        this.questLookup.clear();
        this.allCanvasImages.clear();
        this.allCanvasTexts.clear();

        // --- LOAD REAL DATA FROM MANAGER ---
        if (this.sidebarEntries.isEmpty()) {
            // FIX: Pull from the Client Cache (SimplyQuestsClientPacketHandler) instead of the Server Manager.
            // This ensures data is visible when playing on a dedicated server.
            Map<Identifier, QuestChapter> chapterMap = SimplyQuestsClientPacketHandler.getChapters();
            List<QuestGroup> groupDefinitions = SimplyQuestsClientPacketHandler.getGroups();

            // Use a map to collect root entries and their intended order to avoid TreeMap collisions
            Map<SidebarEntry, Integer> rootOrderMap = new HashMap<>();
            Map<String, SidebarGroup> groupMap = new HashMap<>();

            // 1. Pre-populate Groups from the manifest
            for (QuestGroup gDef : groupDefinitions) {
                String displayName = (gDef.getTitle() != null && !gDef.getTitle().isEmpty()) ? gDef.getTitle() : gDef.getName();
                SidebarGroup g = new SidebarGroup(displayName, gDef.getColor());
                
                // LOCAL OVERRIDE: Prioritize local expansion preferences over server defaults
                Boolean localExpanded = QuestClientData.isGroupExpanded(gDef.getName());
                boolean targetExpanded = (localExpanded != null) ? localExpanded : gDef.isExpanded();
                if (g.isExpanded() != targetExpanded) g.toggleExpanded();
                
                groupMap.put(gDef.getName(), g);
                rootOrderMap.put(g, gDef.getOrder());
            }

            // 1. Convert to list and sort by Group Order, then Chapter Order
            List<QuestChapter> sortedChapters = new ArrayList<>(chapterMap.values());
            sortedChapters.sort(Comparator
                    .comparingInt(QuestChapter::getGroupOrder)
                    .thenComparingInt(QuestChapter::getChapterOrder));

            for (QuestChapter chapter : sortedChapters) {
                String groupName = chapter.getGroupName();
                String displayName = (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) ? chapter.getTitle() : chapter.getName();
                SidebarChapter sideChapter = new SidebarChapter(displayName);
                sideChapter.setId(chapter.getName()); // Capture the sanitized internal ID
                sideChapter.setIconStack(new ItemStack(chapter.getIcon()));
                sideChapter.setState(chapter.getState()); // Sync initial state
                sideChapter.setOffsetX(chapter.getOffsetX()); // FIX: Restore camera X
                sideChapter.setOffsetY(chapter.getOffsetY()); // FIX: Restore camera Y
                sideChapter.setZoom(chapter.getZoom());

                // If group is empty or "Ungrouped", add directly to root, otherwise add to group
                if (groupName == null || groupName.isEmpty() || groupName.equals("Ungrouped")) {
                    rootOrderMap.put(sideChapter, chapter.getGroupOrder());
                } else {
                    // FIX: Sanitize the groupName from the chapter file during lookup
                    // to ensure it matches the ID stored in the groupMap manifest
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

                // 3. Register all Quests in this chapter
                for (Quest q : chapter.getQuests()) {
                    registerQuest(q);
                }
                // 4. Load Canvas Texts
                for (CanvasText ct : chapter.getCanvasTexts()) {
                    ct.setChapterName(chapter.getName());
                    this.allCanvasTexts.add(ct);
                }
                // 5. Load Canvas Images
                for (QuestCanvasImage ci : chapter.getCanvasImages()) {
                    ci.setChapterName(chapter.getName());
                    this.allCanvasImages.add(ci);
                }
            }

            // 4. Sort and build final sidebar list
            List<SidebarEntry> sortedRoot = new ArrayList<>(rootOrderMap.keySet());
            sortedRoot.sort(Comparator.comparingInt(rootOrderMap::get));
            this.sidebarEntries.addAll(sortedRoot);

            updateQuestStates();
        }
        // ------------------------------------

        // 1. Save the name of the currently selected chapter if it exists
        if (this.selectedChapter != null) {
            this.pendingChapterName = this.selectedChapter.getId();
        } else {
            this.pendingChapterName = QuestClientData.getLastChapter();
        }

        this.lastTimeMillis = Util.getMillis();
        this.zoom = QuestClientData.getZoom();

        // 3. Try to re-select the chapter by name
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

        // 4. Fallback: If not found (or first time), pick the first one
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

        // 5. Restore camera state
        if (this.selectedChapter != null) {
            // If we are refreshing data for the same chapter, keep the live camera!
            if (this.selectedChapter.getId().equals(previousId)) {
                this.offsetX = liveX;
                this.offsetY = liveY;
                this.zoom = liveZoom;
            } else {
                // FIX: Prioritize Local View Preferences over Server Master Defaults
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

        this.pendingChapterName = null; // Clear the temporary storage
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Define screen center early so it's available for all manipulation and matrix math
        float absoluteCenterX = (float) (this.width / 2.0);
        float absoluteCenterY = (float) (this.height / 2.0);

        boolean hoveringClaimAll = false;
        boolean isSubEditorOpen = isTaskEditorOpen || isRewardEditorOpen;

        Minecraft mc = Minecraft.getInstance();
        long windowHandle = GLFW.glfwGetCurrentContext();
        boolean isLeftButtonDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (!isLeftButtonDown) {
            this.isDraggingAlphaSlider = false;
            this.isDraggingScaleHandle = false;
            this.isDraggingRotateHandle = false;
            this.scaleHandleIndex = -1;
        }

        // Handle Slider Dragging logic
        if (this.isDraggingSizeSlider && this.isEditorOpen && this.questToModify != null) {
            if (!isLeftButtonDown) {
                this.isDraggingSizeSlider = false;
            } else {
                int windowWidth = 300;
                int panelX = (this.width - windowWidth) / 2;
                int sliderX = panelX + 100; // Matching valueX (panelX + 90 + 10)
                int editBtnWidth = 45;
                int editBtnLeft = panelX + windowWidth - 15 - editBtnWidth;
                int sliderW = editBtnLeft - sliderX - 10;

                float mouseRelX = (float)mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                // Scale: 1.0 (24px) to 10.0 (240px)
                float newSize = 24f + (progress * (240f - 24f));
                this.questToModify.setSize(newSize);
            }
        }

        // Handle Text Scale Slider Dragging
        if (this.isDraggingTextSizeSlider && this.isTextEditorOpen && this.editingCanvasText != null) {
            if (!isLeftButtonDown) {
                this.isDraggingTextSizeSlider = false;
            } else {
                int windowWidth = 200;
                int panelX = (this.width - windowWidth) / 2;
                int sliderX = panelX + 90;
                int sliderW = 95;

                float mouseRelX = (float)mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                float newScale = 0.5f + (progress * (5.0f - 0.5f));
                this.editingCanvasText.setScale(newScale);
            }
        }

        // Handle Grid Size Slider Dragging
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

                float mouseRelX = (float)mouseX - sliderX;
                float progress = Math.max(0, Math.min(1, mouseRelX / sliderW));

                GRID_SNAP = 4.0 + (progress * (32.0 - 4.0));
                GRID_SNAP = Math.round(GRID_SNAP * 2) / 2.0;
            }
        }

        // Handle Color Picker Window Dragging
        if (this.isDraggingPickerWindow && editorUI.isColorPickerOpen) {
            if (!isLeftButtonDown) {
                this.isDraggingPickerWindow = false;
            } else {
                this.pickerX = (int)(mouseX - dragOffsetX);
                this.pickerY = (int)(mouseY - dragOffsetY);
            }
        }

        // 0. Unified Color Picker Dragging logic (Canvas Text and UI Settings)
        if (editorUI.isColorPickerOpen && (this.isTextEditorOpen || this.isSettingsOpen)) {
            int windowWidth = this.isSettingsOpen ? 400 : 200;
            int windowHeight = this.isSettingsOpen ? 240 : 145;
            int x = (this.width - windowWidth) / 2;
            int y = (this.height - windowHeight) / 2;
            int pickerWidth = 135;
            int pickerHeight = 168;

            // Use the instance variables (this.pickerX/Y) so dragging is actually reflected!
            QuestEditorUI.PickerBounds b = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, pickerWidth, pickerHeight, 16);

            clampPickerPosition(pickerWidth, pickerHeight); // Continuous safety clamp while dragging

            if (!isLeftButtonDown) {
                this.isDraggingHue = false;
                this.isDraggingSB = false;
                this.isDraggingAlpha = false;
            } else if (this.isDraggingHue || this.isDraggingSB || this.isDraggingAlpha) {
                // Only update the pending color while dragging, don't apply to target yet
                this.pendingPickerColor = editorUI.getColorAt(mouseX, mouseY, b, this.pendingPickerColor, isDraggingSB, isDraggingHue, isDraggingAlpha);
                editorUI.hexQuery = String.format("%06X", (this.pendingPickerColor & 0xFFFFFF));
            }
        }

        // --- LIVE IMAGE MANIPULATION ---
        if (isLeftButtonDown && selectedCanvasImage != null) {
            double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
            double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);
            double angle = -selectedCanvasImage.getRotation();

            if (isDraggingScaleHandle) {
                // FIX: Calculate mouse position relative to the FIXED startX/startY captured at the beginning of the drag.
                // This breaks the feedback loop that was causing the "jumping" behavior.
                double dx = worldMouseX - startX;
                double dy = worldMouseY - startY;
                double localMouseX = dx * Math.cos(angle) - dy * Math.sin(angle);
                double localMouseY = dx * Math.sin(angle) + dy * Math.cos(angle);

                double newW = startW;
                double newH = startH;
                double localShiftX = 0;
                double localShiftY = 0;

                // Snapping local mouse coordinates to grid
                double snappedLocalX = Math.round(localMouseX / GRID_SNAP) * GRID_SNAP;
                double snappedLocalY = Math.round(localMouseY / GRID_SNAP) * GRID_SNAP;

                // 1. Handle Width and Horizontal origin shift
                if (scaleHandleIndex == 0 || scaleHandleIndex == 7 || scaleHandleIndex == 6) { // Left-side handles (TL, L, BL)
                    newW = Math.max(GRID_SNAP, startW - snappedLocalX);
                    localShiftX = startW - newW;
                } else if (scaleHandleIndex == 2 || scaleHandleIndex == 3 || scaleHandleIndex == 4) { // Right-side handles (TR, R, BR)
                    newW = Math.max(GRID_SNAP, snappedLocalX);
                }

                // 2. Handle Height and Vertical origin shift
                if (scaleHandleIndex == 0 || scaleHandleIndex == 1 || scaleHandleIndex == 2) { // Top-side handles (TL, T, TR)
                    newH = Math.max(GRID_SNAP, startH - snappedLocalY);
                    localShiftY = startH - newH;
                } else if (scaleHandleIndex == 4 || scaleHandleIndex == 5 || scaleHandleIndex == 6) { // Bottom-side handles (BR, B, BL)
                    newH = Math.max(GRID_SNAP, snappedLocalY);
                }

                // 3. Apply local shifts back to World Space X/Y
                // Since the image is rotated, a "local left" shift must be rotated by the image's angle to find the new world X/Y
                double worldShiftX = localShiftX * Math.cos(-angle) - localShiftY * Math.sin(-angle);
                double worldShiftY = localShiftX * Math.sin(-angle) + localShiftY * Math.cos(-angle);

                selectedCanvasImage.setX(startX + worldShiftX);
                selectedCanvasImage.setY(startY + worldShiftY);
                selectedCanvasImage.setWidth(newW);
                selectedCanvasImage.setHeight(newH);

            } else if (isDraggingRotateHandle) {
                // Rotation around the center of the image
                double centerX = selectedCanvasImage.getX() + selectedCanvasImage.getWidth() / 2.0;
                double centerY = selectedCanvasImage.getY() + selectedCanvasImage.getHeight() / 2.0;
                
                double currentAngle = Math.atan2(worldMouseY - centerY, worldMouseX - centerX);

                // Normalize to 0-360 steps if desired, otherwise free rotate
                float finalRotation = (float)(initialImageRotation + (currentAngle - initialRotateAngle));
                selectedCanvasImage.setRotation(finalRotation);

            } else if (isDraggingAlphaSlider && currentImageMode == ManipulationMode.ALPHA) {
                // Transform world mouse into Image-Local Space for alpha (relative to image origin)
                double dx = worldMouseX - selectedCanvasImage.getX();
                double dy = worldMouseY - selectedCanvasImage.getY();
                double localX = dx * Math.cos(angle) - dy * Math.sin(angle);

                // FIX: Use a fixed 0.8 scale so dragging matches the visual scale on the grid
                double relX = worldMouseX - selectedCanvasImage.getX();
                double btnScale = 0.8;
                // Buttons are at -25 offset. We translate the coordinate back to the local button space.
                double hitX = (relX + 25) / btnScale;

                float newAlpha = (float)((hitX + 110) / 100.0);
                selectedCanvasImage.setAlpha(Math.max(0.05f, Math.min(1.0f, newAlpha)));
            }
            
            // Save changes to server frequently while dragging (throttled by the payload handler)
            if (Util.getMillis() % 200 < 20) {
                saveChapterData(selectedCanvasImage.getChapterName());
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

        //Keep the sidebar open if hovering, if the sidebar context menu is open, or if we are editing a name.
        // FIX: Block expansion via mouse position if a modal window is open
        boolean isModalOpen = isEditorOpen || isTaskEditorOpen || isRewardEditorOpen || isChoiceModalOpen || isRewardSummaryOpen || isSettingsOpen || isItemSubmissionOpen;
        boolean isHoveringSidebar = (mouseX <= (this.currentSidebarWidth + 2) && !isModalOpen) || 
                                     isSideBarContextMenu || isSideBarEntryMenu || isSidebarEditing() || isEditingChapterIcon;
        double targetWidth = isHoveringSidebar ? MAX_SIDEBAR_WIDTH : MIN_SIDEBAR_WIDTH;

        float interpolationSpeed = 12.0f;
        this.currentSidebarWidth += (targetWidth - this.currentSidebarWidth) * (1.0 - Math.exp(-interpolationSpeed * deltaTime));

        // --- DIRECT HARDWARE DELTA TRACKING ---
        if (!isLeftButtonDown) {
            this.suppressPanning = false;
        }

// 2. Updated condition: Added !this.suppressPanning
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
            // FIX: If we were dragging last frame and just stopped, trigger a local disk save.
            // This ensures progress is kept even if Minecraft is closed without closing the UI.
            if (this.wasDraggingLastFrame && this.selectedChapter != null) {
                QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);
            }
            this.wasDraggingLastFrame = false;
        }

        // 2. BEGIN THE MATRIX SCALE PASS
        graphics.pose().pushMatrix();

        // 2. Move the matrix origin to the absolute middle of the screen
        graphics.pose().translation(new Vector2f(absoluteCenterX, absoluteCenterY));

        // 3. Zoom from the center of the screen
        graphics.pose().scale((float) this.zoom, (float) this.zoom);

        // 4. Translate by the local grid coordinates
        graphics.pose().translate(new Vector2f((float) -this.offsetX, (float) -this.offsetY));

        drawGrid(graphics);

        // 5. Render your quest tree nodes onto the grid
        renderQuestTree(graphics, mouseX, mouseY, partialTick);

        // --- AUTO-PAGINATION WHILE MOVING TASK ---
        if (this.movingTask != null && this.selectedQuest != null) {
            long now = Util.getMillis();
            if (now - lastTaskPageFlip > 800) { // 800ms cooldown to prevent rapid flipping
                int panelWidth = 300;
                int px = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int)this.popupX;
                int py = (this.popupY == -1) ? (this.height - 200) / 2 : (int)this.popupY;
                int tasksAreaX = px + 10;
                int tasksAreaY = py + 45;
                int slotSize = 20;
                int taskSpacing = 4;
                int slotTotalWidth = slotSize + taskSpacing;
                int maxVisibleTasks = 4;
                List<QuestTask> tasks = this.selectedQuest.getTasks();

                if (tasks.size() > maxVisibleTasks) {
                    // Over Left Boundary?
                    if (mouseX >= tasksAreaX && mouseX <= tasksAreaX + 12 &&
                            mouseY >= tasksAreaY && mouseY <= tasksAreaY + slotSize) {
                        if (taskPage > 0) {
                            taskPage--;
                            lastTaskPageFlip = now;
                            playClickSound();
                        }
                    }
                    // Over Right Boundary?
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

        // --- AUTO-PAGINATION WHILE MOVING REWARD ---
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

        // 1. Draw Sidebar Background Pane
        graphics.fill(0, 0, sidebarW, this.height, COL_SIDEBAR_BG);
        graphics.fill(sidebarW - 1, 0, sidebarW, this.height, COL_SIDEBAR_BORDER);

        // 2. DYNAMIC MINIMIZED ARROW
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

            // FIX: Use Math.ceil to ensure logical width fully covers physical pixels (167 instead of 166)
            int scaledMaxSidebarW = (int) Math.ceil(MAX_SIDEBAR_WIDTH / textScale);
            int currentScaledW = (int) Math.ceil(sidebarW / textScale);

            // FIX: Use logical coordinates (currentScaledW) so the scissor matches our 0.75 scale
            graphics.enableScissor(0, 0, currentScaledW, (int)(this.height / textScale));

            // Render Settings Button (Gear)
            if (sidebarW > MIN_SIDEBAR_WIDTH + 15) {
                int gearX = currentScaledW - 15;
                boolean hoveringGear = localMouseX >= gearX && localMouseX <= gearX + 10 && localMouseY >= 3 && localMouseY <= 13;
                int gearColor = hoveringGear ? COL_TEXT_GOLD : COL_TEXT;

                // Tint the texture directly using the color integer at the end of the blit call
                graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.SETTINGS_ICON, gearX, 4, 0.0f, 0.0f, 10, 10, 10, 10, gearColor);
            }

            // 1b. Draw the main "+" Add Button at the top right of the sidebar, scaled to match others
            if (QuestGlobalState.isEditModeEnabled && sidebarW > MIN_SIDEBAR_WIDTH + 15) {
                int plusX = currentScaledW - 30;
                int plusY = 5;
                boolean hoveringPlus = localMouseX >= plusX - 4 && localMouseX <= plusX + 12 && localMouseY >= plusY - 2 && localMouseY <= plusY + 12;
                int plusColor = hoveringPlus ? COL_TEXT_GOLD : COL_TEXT;
                graphics.text(this.font, Component.literal("+"), plusX, plusY, plusColor);
            }

            // 2. Enable List Scissor: This starts at Y=15 (logical) to protect the header bar
            // FIX: Convert logical Y (15) to physical pixels so it aligns with the header
            graphics.enableScissor(0, 15, currentScaledW, (int)(this.height / textScale));

            graphics.pose().pushMatrix();
            // Translate the grid downwards based on how far we've scrolled
            graphics.pose().translate(new Vector2f(0f, -(float)sidebarScrollOffset));

            // 3. Sidebar Entry Loop
            int currentYPosition = (int) (15 / textScale);
            int localX = (int) (6 / textScale);
            int localChapterX = (int) (14 / textScale);
            int logicalBottom = (int) (this.height / textScale); // Logical height for the PoseStack
            int headerHeight = (int)(15 / textScale);

            for (SidebarEntry entry : this.sidebarEntries) {
                if (entry instanceof SidebarGroup group) {
                    String prefix = group.isExpanded() ? "▼ " : "► ";
                    int solidGroupColor = COL_TEXT;

                    // 1. Calculate Truncation for Group Title
                    int groupPlusSpace = (QuestGlobalState.isEditModeEnabled && group.isExpanded() && group != editingGroup) ? 15 : 0;
                    int maxGroupWidth = currentScaledW - localX - groupPlusSpace;
                    String fullGroupTitle = prefix + group.getTitle();
                    String displayedGroupTitle = truncate(fullGroupTitle, maxGroupWidth);

                    boolean isGroupTitleHovered = localMouseX >= 0 && localMouseX < currentScaledW &&
                            localMouseY >= headerHeight &&
                            scrollableMouseY >= currentYPosition && scrollableMouseY < currentYPosition + 14;

                    // Suppress tooltip if mouse is within 20 logical pixels of the right edge (where the + button lives)
                    boolean nearGroupPlus = false;
                    if (QuestGlobalState.isEditModeEnabled && group.isExpanded() && group != editingGroup) {
                        nearGroupPlus = localMouseX >= currentScaledW - 20;
                    }

                    boolean showGroupOverlay = isGroupTitleHovered && !nearGroupPlus && !fullGroupTitle.equals(displayedGroupTitle) && !isEditorOpen && currentSidebarWidth > (MAX_SIDEBAR_WIDTH - 5);

                    if (showGroupOverlay) {
                        pendingOverlayText = fullGroupTitle;
                        pendingOverlayX = (int)(localX * textScale);
                        pendingOverlayY = (int)((currentYPosition - sidebarScrollOffset) * textScale);
                    }

                    boolean isGroupMoving = movingSidebarGroup != null;
                    if (!showGroupOverlay) {
                        if (group == editingGroup) {
                            int editorWidth = currentScaledW - localX;
                            updateSidebarScrollOffset(editorWidth);
                            String displayText = sidebarSearchQuery;

                            // FIX: Pass logical coordinates. The 0.75 scale will handle the conversion.
                            // We start at localX - 2 to give the first character anti-aliasing room.
                            // FIX: Use row-relative Y coordinates so the scissor follows the entry while scrolling
                            graphics.enableScissor(localX - 2, currentYPosition - 2, currentScaledW, currentYPosition + 14);
                            graphics.text(this.font, Component.literal(displayText), localX - sidebarTextScrollOffset, currentYPosition, COL_TEXT);

                            if (Util.getMillis() / 500 % 2 == 0) {
                                int cursorX = localX + this.font.width(displayText) - sidebarTextScrollOffset;
                                graphics.fill(cursorX, currentYPosition, cursorX + 1, currentYPosition + 10, COL_TEXT);
                            }
                            graphics.disableScissor();
                            // graphics.enableScissor(0, 0, currentScaledW, logicalBottom);
                        } else {
                            if (isGroupTitleHovered && !isContextMenuOpen && !isGroupMoving && movingSidebarChapter == null && !isEditingChapterIcon) {
                                // FIX: Anchor highlight to the absolute left (0) and stop 1px before the border
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
                                // FIX: Anchor highlight to the absolute left (0) and stop 1px before the border
                                graphics.fill(0, currentYPosition - 2, currentScaledW - 1, currentYPosition + 14, COL_HOVER_UI);
                            }

                            // 1. Render Icon and Claim Badge regardless of editing/overlay state
                            if (hasIcon) {
                                graphics.item(chapter.getIconStack(), localChapterX, currentYPosition - 1);
                                if (hasClaimableRewards(chapter.getId())) {
                                    graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, localChapterX + 10, currentYPosition - 4, 0.0f, 0.0f, 8, 8, 8, 8);
                                }
                            }

                            if (showChapterOverlay) {
                                pendingOverlayText = fullChapterName;
                                pendingOverlayX = (int)(textXOffset * textScale);
                                pendingOverlayY = (int)((currentYPosition + 3 - sidebarScrollOffset) * textScale);
                            } else if (chapter == editingChapter) {
                                int editorWidth = currentScaledW - textXOffset;
                                updateSidebarScrollOffset(editorWidth);
                                String displayText = sidebarSearchQuery;

                                // FIX: Use row-relative Y coordinates so the scissor follows the entry while scrolling
                                graphics.enableScissor(textXOffset - 2, currentYPosition - 2, currentScaledW, currentYPosition + 16);
                                graphics.text(this.font, Component.literal(displayText), textXOffset - sidebarTextScrollOffset, currentYPosition + 3, COL_TEXT);

                                if (Util.getMillis() / 500 % 2 == 0) {
                                    int cursorX = textXOffset + this.font.width(displayText) - sidebarTextScrollOffset;
                                    graphics.fill(cursorX, currentYPosition + 3, cursorX + 1, currentYPosition + 13, COL_TEXT);
                                }
                                graphics.disableScissor();
                                // graphics.enableScissor(0, 0, currentScaledW, logicalBottom);
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
                        // FIX: Anchor highlight to the absolute left (0) and stop 1px before the border
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
                        pendingOverlayX = (int)(textXoffset * textScale);
                        pendingOverlayY = (int)((currentYPosition + 3 - sidebarScrollOffset) * textScale);
                    } else if (chapter == editingChapter) {
                        int editorWidth = currentScaledW - textXoffset;
                        updateSidebarScrollOffset(editorWidth);
                        String displayText = sidebarSearchQuery;

                        // FIX: Use row-relative Y coordinates so the scissor follows the entry while scrolling
                        graphics.enableScissor(textXoffset - 2, currentYPosition - 2, currentScaledW, currentYPosition + 16);
                        graphics.text(this.font, Component.literal(displayText), textXoffset - sidebarTextScrollOffset, currentYPosition + 3, COL_TEXT);

                        if (Util.getMillis() / 500 % 2 == 0) {
                            int cursorX = textXoffset + this.font.width(displayText) - sidebarTextScrollOffset;
                            graphics.fill(cursorX, currentYPosition + 3, cursorX + 1, currentYPosition + 13, COL_TEXT);
                        }
                        graphics.disableScissor();
                        // graphics.enableScissor(0, 0, currentScaledW, logicalBottom);
                    } else {
                        graphics.text(this.font, Component.literal(displayedStandaloneName), textXoffset, currentYPosition + 3, getStateColor(chapter.getState()));
                    }
                    currentYPosition += chapterRowHeight;
                }
                currentYPosition += 6;
            }

            // --- GHOST INSERTION LINE ---
            if (this.movingSidebarChapter != null) {
                int tempY = (int) (15 / textScale);
                int snappedY = -1;

                for (SidebarEntry entry : this.sidebarEntries) {
                    if (entry == movingSidebarChapter) continue;

                    // Check if mouse is in the gap above this root entry
                    if (scrollableMouseY < tempY + 4) {
                        snappedY = tempY - 3;
                        break;
                    }

                    if (entry instanceof SidebarGroup group) {
                        tempY += 18;
                        if (group.isExpanded()) {
                            // Allow dropping into an empty expanded group
                            if (group.getChapters().isEmpty()) {
                                if (scrollableMouseY < tempY + 16) {
                                    snappedY = tempY - 3;
                                    break;
                                }
                            }
                            for (SidebarChapter chapter : group.getChapters()) {
                                if (chapter == movingSidebarChapter) continue;

                                // If mouse is over this chapter row
                                if (scrollableMouseY < tempY + 16) {
                                    // Top half = before, Bottom half = after
                                    snappedY = (scrollableMouseY < tempY + 8) ? tempY - 3 : tempY + 16 - 3;
                                    break;
                                }
                                tempY += 16;
                            }
                        }
                    } else {
                        // Standalone chapter root check
                        if (scrollableMouseY < tempY + 16) {
                            snappedY = (scrollableMouseY < tempY + 8) ? tempY - 3 : tempY + 16 - 3;
                        } else {
                            tempY += 16;
                        }
                    }

                    if (snappedY != -1) break;
                    tempY += 6;
                }

                // If no insertion point found yet, it defaults to the very bottom of the list
                if (snappedY == -1) snappedY = tempY - 3;

                // Draw a 1-pixel high white line across the sidebar width
                // FIX: Match the highlight width logic to prevent line bleed
                graphics.fill(0, snappedY, currentScaledW - 1, snappedY + 1, COL_UI_BORDER);
            }

            // --- GROUP GHOST INSERTION LINE ---
            if (this.movingSidebarGroup != null) {
                int tempY = (int) (15 / textScale);
                int snappedY = -1;

                for (SidebarEntry entry : this.sidebarEntries) {
                    if (entry == movingSidebarGroup) continue;

                    // Groups can only be root entries, so we only check gaps between root items
                    if (scrollableMouseY < tempY + 4) {
                        snappedY = tempY - 3;
                        break;
                    }

                    if (entry instanceof SidebarGroup group) {
                        tempY += 18;
                        if (group.isExpanded()) {
                            // If moving a group, we skip over its internal chapters
                            tempY += group.getChapters().size() * 16;
                        }
                    } else {
                        tempY += 16;
                    }

                    if (snappedY != -1) break;
                    tempY += 6;
                }

                // Default to bottom
                if (snappedY == -1) snappedY = tempY - 3;

                // Draw a 1-pixel high white line across the sidebar width
                // FIX: Match the highlight width logic to prevent line bleed
                graphics.fill(0, snappedY, currentScaledW - 1, snappedY + 1, COL_UI_BORDER);
            }

            this.totalSidebarContentHeight = currentYPosition;

            // FIX: Keep scroll within valid bounds and handle auto-scroll to bottom
            int visibleHeight = (int) (this.height / textScale) - 20;
            int maxScroll = Math.max(0, this.totalSidebarContentHeight - visibleHeight);
            if (this.needsSidebarScrollToBottom) {
                this.sidebarScrollOffset = maxScroll;
                this.needsSidebarScrollToBottom = false;
            }
            this.sidebarScrollOffset = Math.max(0, Math.min(maxScroll, this.sidebarScrollOffset));

            graphics.pose().popMatrix();
            graphics.pose().popMatrix();
            graphics.disableScissor(); // Clears the List (Header protection) scissor
            graphics.disableScissor(); // Clears the Sidebar (Width restriction) scissor

            // Final Pass: Draw the expansion overlay on top of everything and escaping the sidebar scissor
            if (pendingOverlayText != null) {
                renderStaticTextOverlay(graphics, pendingOverlayText, pendingOverlayX, pendingOverlayY, this.width);
            }
        }

        // --- UI OVERLAY RENDERING (Z-ORDER) ---

        // 1. Main Editor Window
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

        // 2. Quest Details Popup
        if (this.selectedQuest != null && !this.isEditorOpen) {
            renderQuestDetails(graphics, mouseX, mouseY);
        }

        // 6. Task Editor Window (Rendered last to be on top)
        if (this.isTaskEditorOpen && this.taskToModify != null) {
            int windowWidth = 300;
            int windowHeight = 250;
            int windowX = (this.width - windowWidth) / 2;
            int windowY = (this.height - windowHeight) / 2;

            graphics.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, COL_UI_BG);
            drawBorder(graphics, windowX, windowY, windowWidth, windowHeight, COL_UI_BORDER);

            this.editorUI.renderTaskEditor(graphics, mouseX, mouseY, windowX, windowY, 300, 250, this.taskToModify, this.selectedQuest, this.tempUseAsIcon);
        }

        // 6b. Reward Editor Window
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

        // 6.5 Settings Modal
        if (this.isSettingsOpen) {
            renderSettingsModal(graphics, mouseX, mouseY);
        }

        // 7. Item Submission Modal (Top level)
        if (this.isItemSubmissionOpen && this.submittingTask != null) {
            renderItemSubmissionModal(graphics, mouseX, mouseY);
        }

        // --- TOP-LEVEL OVERLAYS (Must render last to ensure they are on top of all windows) ---

        // 8. Icon Picker
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

        // 8.5 Claim All Button (Renders in top-right corner if rewards are pending)
        if (hasAnyClaimableRewards() && !isEditorOpen && !isTaskEditorOpen && !isRewardEditorOpen && !isTextEditorOpen && !isSettingsOpen && !isItemSubmissionOpen) {
            int btnSize = 16;
            int btnX = this.width - btnSize - 5;
            int btnY = 5;
            hoveringClaimAll = mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
            int color = hoveringClaimAll ? COL_TEXT_GOLD : 0xFFFFFFFF;

            // Draw the Claim All Icon
            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ALL_ICON, btnX, btnY, 0.0f, 0.0f, btnSize, btnSize, btnSize, btnSize, color);
            
            // Red ! badge at the top-right corner of the icon
            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, btnX + btnSize - 7, btnY - 1, 0.0f, 0.0f, 8, 8, 8, 8);
        }

        if (this.isRewardSummaryOpen) {
            renderRewardSummary(graphics, mouseX, mouseY);
        }

        if (this.isChoiceModalOpen) {
            renderRewardChoiceModal(graphics, mouseX, mouseY);
        }

        // 9. Context Menu
        if (isContextMenuOpen) {
            renderContextMenu(graphics);
        }

        // 10. Quest Tooltips
        if (this.selectedChapter != null && mouseX > this.currentSidebarWidth) {
            Quest hoveredQuest = null;
            for (Quest quest : this.allQuests) {
                // FIX: Check isInputBlocked to prevent tooltips rendering behind editors
                if (!isInputBlocked() && quest.getChapterName().equals(this.selectedChapter.getId()) && isMouseOverNode(mouseX, mouseY, quest)) {
                    hoveredQuest = quest;
                    break;
                }
            }
            // FIX: Hide tooltips if input is blocked by a window or picker
            if (hoveredQuest != null && !isInputBlocked() && this.selectedQuest == null && !editorUI.isPickerOpen()) {
                // Build detailed path: Group > Chapter > Quest
                String tooltipText = hoveredQuest.getTitle();

                // FIX: Pull from the Client Cache
                Map<Identifier, QuestChapter> chapterMap = SimplyQuestsClientPacketHandler.getChapters();
                
                // Look up the actual Chapter data using the sanitized ID
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
                renderAnchoredQuestTooltip(graphics, tooltipText);
            }
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
                hoveredLabel = switch(reward.getType()) {
                    case ITEM -> reward.getCount() + "x " + reward.getItem().getDefaultInstance().getHoverName().getString();
                    case XP -> reward.getCount() + " XP";
                    case COMMAND -> "Execute Command";
                };
            }
        }

        if (rewardsToShow.size() > itemsPerPage) {
            String pageText = (summaryPage + 1) + " / " + ((rewardsToShow.size() + itemsPerPage - 1) / itemsPerPage);
            graphics.centeredText(font, Component.literal(pageText), x + panelW / 2, y + panelH - 40, COL_TEXT);

            if (summaryPage > 0) graphics.text(font, "<", x + 15, y + panelH - 40, mouseX >= x + 15 && mouseX <= x + 25 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 ? COL_TEXT_GOLD : COL_TEXT);
            if ((summaryPage + 1) * itemsPerPage < rewardsToShow.size()) graphics.text(font, ">", x + panelW - 25, y + panelH - 40, mouseX >= x + panelW - 25 && mouseX <= x + panelW - 15 && mouseY >= y + panelH - 42 && mouseY <= y + panelH - 32 ? COL_TEXT_GOLD : COL_TEXT);
        }

        int btnW = 60;
        int btnH = 16;
        int btnX = x + (panelW - btnW) / 2;
        int btnY = y + panelH - 22;
        editorUI.drawButton(graphics, mouseX, mouseY, btnX, btnY, btnW, btnH, "Confirm", COL_BUTTON_BASE);

        // Final Pass: Render the tooltip last so it stays on top of all icons and buttons
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

        List<QuestReward> choices = activeChoiceBundle.getSubRewards();
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

        // Cancel Button
        editorUI.drawButton(graphics, mouseX, mouseY, x + 15, btnY, btnW, btnH, "Cancel", COL_BUTTON_BASE);

        // Submit Button (Only active if a choice is selected)
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

        // 1. Grid Interaction
        List<QuestReward> choices = activeChoiceBundle.getSubRewards();
        int gridX = x + 15, gridY = y + 30;
        for (int i = 0; i < choices.size(); i++) {
            int ix = gridX + (i % 5) * 40;
            int iy = gridY + (i / 5) * 32;
            if (mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20) {
                selectedChoice = choices.get(i);
                playClickSound(); return;
            }
        }

        // 2. Buttons
        if (mouseX >= x + 15 && mouseX <= x + 15 + 50 && mouseY >= btnY && mouseY <= btnY + 14) {
            isChoiceModalOpen = false; playClickSound();
        } else if (selectedChoice != null && mouseX >= x + panelW - 65 && mouseX <= x + panelW - 15 && mouseY >= btnY && mouseY <= btnY + 14) {
            // Send the specific sub-reward ID to the server
            ClientPacketDistributor.sendToServer(new ClaimRewardPayload(selectedChoice.getId()));
            isChoiceModalOpen = false;
            playClickSound();
        }
    }

    private void renderQuestTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.selectedChapter == null) return;

        // 0. Calculate GLOBAL animation phase for high-precision smoothness
        // We do this at the top so it is accessible to all rendering passes
        float globalAnimTime = (float) ((System.nanoTime() / 1_000_000.0) % 10000.0) + partialTick;
        float globalArrowPhase = (globalAnimTime * 0.015f) % 8.0f;

        // PASS 0: BLOCK RENDERING IF EDITOR OPEN
        String activeChapterId = this.selectedChapter.getId();
        if (activeChapterId == null) return;

        // 1. Calculate World Space Mouse and Shadow/Ghost coordinates
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

        // =========================================================================
        // PASS 1: DRAW CANVAS IMAGES (Background)
        // =========================================================================
        for (QuestCanvasImage ci : this.allCanvasImages) {
            if (!ci.getChapterName().equals(activeChapterId)) continue;

            Identifier tex = getOrRequestImage(ci.getImageId());
            if (tex == null) continue;

            float halfW = (float) ci.getWidth() / 2f;
            float halfH = (float) ci.getHeight() / 2f;
            
            // Dim the original stationary image while it is being moved
            float alphaMod = (ci == this.movingCanvasImage) ? 0.3f : 1.0f;

            graphics.pose().pushMatrix();
            // 1. Move origin to image center
            graphics.pose().translate((float)ci.getX() + halfW, (float)ci.getY() + halfH);
            // 2. Rotate around center
            graphics.pose().rotate(ci.getRotation());
            // 3. Move origin back to top-left for drawing
            graphics.pose().translate(-halfW, -halfH);

            // FIX: Use only ONE blit call with the alpha tint (multiplied by ghost status).
            int alphaTint = ((int)(ci.getAlpha() * 255 * alphaMod) << 24) | 0xFFFFFF;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, (int)ci.getWidth(), (int)ci.getHeight(), (int)ci.getWidth(), (int)ci.getHeight(), alphaTint);

            // --- SELECTION UI: TRANSFORM BOX (Must be inside rotation matrix) ---
            if (ci == selectedCanvasImage) {
                // Draw basic selection outline slightly outside the image
                graphics.outline(-1, -1, (int)ci.getWidth() + 2, (int)ci.getHeight() + 2, COL_TEXT_GOLD);

                int s = 6; // Handle thickness
                int w = (int)ci.getWidth();
                int h = (int)ci.getHeight();

                // Hit detection for handle highlighting (Local Space)
                double dx = worldMouseX - (ci.getX() + ci.getWidth() / 2.0);
                double dy = worldMouseY - (ci.getY() + ci.getHeight() / 2.0);
                double angle = -ci.getRotation();
                double localMouseX = (dx * Math.cos(angle) - dy * Math.sin(angle)) + (ci.getWidth() / 2.0);
                double localMouseY = (dx * Math.sin(angle) + dy * Math.cos(angle)) + (ci.getHeight() / 2.0);

                if (currentImageMode == ManipulationMode.SCALE) {
                    int[][] hBoxes = {
                            {-s, -s, s, s},   // 0: TL Square
                            {0, -s, w, s},    // 1: Top Bar
                            {w, -s, s, s},    // 2: TR Square
                            {w, 0, s, h},     // 3: Right Bar
                            {w, h, s, s},     // 4: BR Square
                            {0, h, w, s},     // 5: Bottom Bar
                            {-s, h, s, s},    // 6: BL Square
                            {-s, 0, s, h}     // 7: Left Bar
                    };

                    for (int i = 0; i < hBoxes.length; i++) {
                        int[] b = hBoxes[i];
                        // Check if mouse is over this handle box (with 1px buffer)
                        boolean isHovered = localMouseX >= b[0] - 1 && localMouseX <= b[0] + b[2] + 1 &&
                                           localMouseY >= b[1] - 1 && localMouseY <= b[1] + b[3] + 1;
                        boolean isActive = isHovered || (i == scaleHandleIndex);
                        
                        int color = isActive ? COL_TEXT_GOLD : COL_UI_BORDER;
                        graphics.outline(b[0], b[1], b[2], b[3], color);
                    }
                } else if (currentImageMode == ManipulationMode.ROTATE) {
                    int handleSize = 8;
                    // Position rotation handles outside corners for consistency
                    int[][] hBoxes = {
                            {-handleSize, -handleSize, handleSize, handleSize}, // TL
                            {w, -handleSize, handleSize, handleSize},           // TR
                            {w, h, handleSize, handleSize},                     // BR
                            {-handleSize, h, handleSize, handleSize}            // BL
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
            graphics.pose().popMatrix(); // End Image Rotation Matrix

            // --- SELECTION UI: BUTTONS (Must be OUTSIDE rotation matrix so they don't orbit) ---
            if (ci == selectedCanvasImage) {
                graphics.pose().pushMatrix();
                graphics.pose().translate((float)ci.getX(), (float)ci.getY());

                graphics.pose().pushMatrix();
                graphics.pose().translate(-25, 0); // Position buttons to the left of the image origin
                graphics.pose().scale(0.8f, 0.8f); // Buttons now scale with zoom (canvas standard)
                
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

        // =========================================================================
        // PASS 2: DRAW CANVAS TEXT
        // =========================================================================
        for (CanvasText ct : this.allCanvasTexts) {
            if (!ct.getChapterName().equals(activeChapterId)) continue;
            
            // Dim the original text while it is being moved
            float alphaMod = (ct == this.movingCanvasText) ? 0.3f : 1.0f;

            graphics.pose().pushMatrix();
            graphics.pose().translate((float)ct.getX(), (float)ct.getY());
            graphics.pose().scale(ct.getScale(), ct.getScale());

            int color = (ct.getColor() & 0x00FFFFFF) | ((int)(((ct.getColor() >> 24) & 0xFF) * alphaMod) << 24);
            graphics.text(this.font, Component.literal(ct.getText()), 0, 0, color);

            graphics.pose().popMatrix();
        }

        // =========================================================================
        // PASS 3: DRAW THE LINES (Layered on top of decorations)
        // =========================================================================
        for (Quest quest : this.allQuests) {

            if (!quest.getChapterName().equals(activeChapterId)) continue;

            for (String depId : quest.getDependencies()) {
                Quest dependency = this.questLookup.get(depId);

                if (dependency != null && dependency.getChapterName().equals(activeChapterId)) {
                    // FIX: Calculate midpoints using the quest's dynamic float size
                    float startX = (dependency == this.movingQuest ? ghostX : (float) dependency.getX()) + (dependency.getSize() / 2.0f);
                    float startY = (dependency == this.movingQuest ? ghostY : (float) dependency.getY()) + (dependency.getSize() / 2.0f);
                    float endX = (quest == this.movingQuest ? ghostX : (float) quest.getX()) + (quest.getSize() / 2.0f);
                    float endY = (quest == this.movingQuest ? ghostY : (float) quest.getY()) + (quest.getSize() / 2.0f);

                    // FANCY: Blend colors between parent and child quest states
                    int parentColor = getStateColor(dependency.getState());
                    int childColor = getStateColor(quest.getState());

                    // Use a proportional inset (25% of size) instead of a fixed 3px.
                    // This ensures that lines always connect and tuck behind borders even on massive nodes.
                    float r1 = dependency.getSize() * 0.25f;
                    float r2 = quest.getSize() * 0.25f;

                    // Calculate scroll phase based on the global high-precision timer
                    float arrowPhase = 0;
                    boolean inputBlocked = isInputBlocked();
                    boolean isParentHovered = !inputBlocked && isMouseOverNode(mouseX, mouseY, dependency);
                    boolean isChildHovered = !inputBlocked && isMouseOverNode(mouseX, mouseY, quest);

                    if (isParentHovered || isChildHovered || dependency == this.movingQuest || quest == this.movingQuest) {
                        arrowPhase = globalArrowPhase;
                    }
                    
                    // Increased thickness to 4.0f to provide enough resolution for the arrow shape
                    // and prevents it from appearing as a single blurry pixel.
                    drawVectorLine(graphics, startX, startY, endX, endY, 4.0f, parentColor, childColor, r1, r2, arrowPhase);
                }
            }
        }

        // =========================================================================
        // PASS 4: DRAW THE NODES (Layered cleanly on top of lines)
        // =========================================================================
        for (Quest quest : this.allQuests) {

            if (!quest.getChapterName().equals(activeChapterId)) continue;

            int x = (int) quest.getX();
            int y = (int) quest.getY();
            int size = (int) quest.getSize();

            boolean isSelected = (quest == this.selectedQuest);
            boolean isHovered = isMouseOverNode(mouseX, mouseY, quest);

            int stateColor = getStateColor(quest.getState());

            // Pass isHovered down to the drawing helper
            drawQuestNode(graphics, x, y, size, isSelected, isHovered, stateColor, quest);

            // FIX: Use the centralized helper with grid coordinates and full size.
            // This avoids coordinate mismatches that were causing 3D entities to be scissored out.
            editorUI.drawQuestIcon(graphics, quest, x, y, size);

            // PASS 2b: DRAW CLAIM NOTIFICATION BADGE
            // Only show if the quest is completed and has at least one unclaimed reward
            boolean canClaimAny = quest.getState() == QuestState.COMPLETED &&
                    !quest.getRewards().isEmpty() &&
                    quest.getRewards().stream().anyMatch(r -> !SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(r.getId()));

            if (canClaimAny) {
                // Position badge at the top-right corner of the node
                int badgeSize = Math.max(6, (int)(size * 0.35f));
                int bx = x + size - (badgeSize / 2) - 2;
                int by = y - (badgeSize / 2) + 2;

                // FIX: Use matrix scaling to prevent the "top-left crop" error.
                // We sample exactly 8x8 from the texture and scale it to badgeSize.
                graphics.pose().pushMatrix();
                graphics.pose().translate(bx, by);
                float badgeScale = badgeSize / 8.0f;
                graphics.pose().scale(badgeScale, badgeScale);
                graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLAIM_ICON, 0, 0, 0.0f, 0.0f, 8, 8, 8, 8);
                graphics.pose().popMatrix();
            }
        }

        // PASS 5: RENDER GHOST NODE (Shared scope with ghostX/ghostY)
        if (this.movingQuest != null) {
            QuestShapeRenderer.render(this.movingQuest.getShape(), graphics, (int)ghostX, (int)ghostY, (int)this.movingQuest.getSize(), COL_GHOST_BORDER, COL_GHOST_FILL);
        }

        // PASS 6: RENDER GHOST IMAGE
        if (this.movingCanvasImage != null) {
            Identifier tex = getOrRequestImage(this.movingCanvasImage.getImageId());
            if (tex != null) {
                float halfW = (float) this.movingCanvasImage.getWidth() / 2f;
                float halfH = (float) this.movingCanvasImage.getHeight() / 2f;
                graphics.pose().pushMatrix();
                graphics.pose().translate(imgGhostX + halfW, imgGhostY + halfH);
                graphics.pose().rotate(this.movingCanvasImage.getRotation());
                graphics.pose().translate(-halfW, -halfH);

                // Semi-transparent ghost color (50% of current image alpha)
                int ghostAlpha = (int)(this.movingCanvasImage.getAlpha() * 255 * 0.5f);
                int alphaTint = (ghostAlpha << 24) | 0xFFFFFF;
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 
                        (int)this.movingCanvasImage.getWidth(), (int)this.movingCanvasImage.getHeight(), 
                        (int)this.movingCanvasImage.getWidth(), (int)this.movingCanvasImage.getHeight(), alphaTint);
                graphics.pose().popMatrix();
            }
        }

        // PASS 7: RENDER GHOST TEXT
        if (this.movingCanvasText != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(textGhostX, textGhostY);
            graphics.pose().scale(this.movingCanvasText.getScale(), this.movingCanvasText.getScale());
            // Use semi-transparent version of current color
            int ghostColor = (this.movingCanvasText.getColor() & 0x00FFFFFF) | 0x80000000;
            graphics.text(this.font, Component.literal(this.movingCanvasText.getText()), 0, 0, ghostColor);
            graphics.pose().popMatrix();
        }
    }

    public boolean handleImageInteraction(double mouseX, double mouseY, int button) {
        if (selectedCanvasImage == null || (button != 0 && button != 1)) return false;

        // 1. Convert mouse to World Space
        float absoluteCenterX = (float) (this.width / 2.0f);
        float absoluteCenterY = (float) (this.height / 2.0);
        double worldMouseX = this.offsetX + ((mouseX - absoluteCenterX) / this.zoom);
        double worldMouseY = this.offsetY + ((mouseY - absoluteCenterY) / this.zoom);

        // 2. Tool Toggle Check (Buttons at -25 relative to image origin)
        double relX = worldMouseX - selectedCanvasImage.getX();
        double relY = worldMouseY - selectedCanvasImage.getY();
        double btnScale = 0.8;
        double hitX = (relX + 25) / btnScale;
        double hitY = relY / btnScale;

        // S Button
        if (hitX >= 0 && hitX <= 15 && hitY >= 0 && hitY <= 15) {
            currentImageMode = (currentImageMode == ManipulationMode.SCALE) ? ManipulationMode.NONE : ManipulationMode.SCALE;
            playClickSound(); return true;
        }
        // R Button
        if (hitX >= 0 && hitX <= 15 && hitY >= 20 && hitY <= 35) {
            currentImageMode = (currentImageMode == ManipulationMode.ROTATE) ? ManipulationMode.NONE : ManipulationMode.ROTATE;
            playClickSound(); return true;
        }
        // A Button
        if (hitX >= 0 && hitX <= 15 && hitY >= 40 && hitY <= 55) {
            currentImageMode = (currentImageMode == ManipulationMode.ALPHA) ? ManipulationMode.NONE : ManipulationMode.ALPHA;
            playClickSound(); return true;
        }

        // 3. Mode-Specific Interaction (Handles/Sliders)
        // FIX: Transform world mouse into Image-Local Space relative to the CENTER pivot
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
            int s = 6; // Matching thickness in renderer
            // Hitboxes for the external frame (slightly inflated for easier clicking)
            int[][] hBoxes = {
                    {-s, -s, s, s}, {0, -s, (int)iw, s}, {(int)iw, -s, s, s}, // TL, T, TR
                    {(int)iw, 0, s, (int)ih}, {(int)iw, (int)ih, s, s},       // R, BR
                    {0, (int)ih, (int)iw, s}, {-s, (int)ih, s, s}, {-s, 0, s, (int)ih} // B, BL, L
            };

            for (int i = 0; i < hBoxes.length; i++) {
                if (localX >= hBoxes[i][0] - 1 && localX <= hBoxes[i][0] + hBoxes[i][2] + 1 &&
                        localY >= hBoxes[i][1] - 1 && localY <= hBoxes[i][1] + hBoxes[i][3] + 1) {

                    this.scaleHandleIndex = i;
                    this.isDraggingScaleHandle = true;

                    // Capture starting state for delta calculations
                    this.startX = selectedCanvasImage.getX();
                    this.startY = selectedCanvasImage.getY();
                    this.startW = selectedCanvasImage.getWidth();
                    this.startH = selectedCanvasImage.getHeight();

                    return true;
                }
            }
        } else if (currentImageMode == ManipulationMode.ROTATE) {
            int hs = 8;
            // Updated hit detection to match external handle positions
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

        // FIX: Re-calculate local mouse position relative to the center pivot
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
        new Thread(() -> {
            String path = null;
            
            // Use MemoryStack to handle the low-level pointers for file filters
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog("Select Quest Image", "", filters, "PNG files (*.png)", false);
            }

            if (path != null) {
                File file = new File(path);
                String fileName = file.getName();

                // LOCAL VALIDATION: Catch the "All Files" fallback immediately
                if (!fileName.toLowerCase().endsWith(".png")) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§cOnly PNG files can be used as quest decorations!"));
                        }
                    });
                    return;
                }

                try {
                    // Read bytes in the background thread to prevent game stutter
                    byte[] data = Files.readAllBytes(file.toPath());
                Minecraft.getInstance().execute(() -> {
                    ClientPacketDistributor.sendToServer(new UploadImagePayload(fileName, data));

                    // Create and add the image to the canvas immediately so it appears without a restart
                    QuestCanvasImage ci = new QuestCanvasImage("img_" + System.currentTimeMillis(), fileName, snappedX, snappedY, 64.0, 64.0, 0f, 1.0f);
                    ci.setChapterName(this.selectedChapter.getId());
                    this.allCanvasImages.add(ci);
                    saveChapterData(this.selectedChapter.getId());
                });
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    private Identifier getOrRequestImage(String imageId) {
        if (imageId == null || imageId.isEmpty()) return null;

        // 1. Check if already loaded in memory
        if (DYNAMIC_IMAGES.containsKey(imageId)) return DYNAMIC_IMAGES.get(imageId);

        // 2. Check if we are already waiting for this image (prevents spam)
        if (PENDING_REQUESTS.contains(imageId)) return null;

        // Mark as pending immediately
        PENDING_REQUESTS.add(imageId);

        // 3. Check local disk cache folder
        File cacheFile = Minecraft.getInstance().gameDirectory.toPath().resolve("simplyquests_cache").resolve(imageId).toFile();
        if (cacheFile.exists()) {
            try {
                loadTextureFromFile(imageId, Files.readAllBytes(cacheFile.toPath()));
                return null; // Will be available next frame once loadTextureFromFile finishes
            } catch (Exception ignored) {}
        }

        // 4. Not on disk? Request from server immediately
        ClientPacketDistributor.sendToServer(new RequestImagePayload(imageId));

        return null;
    }

    public static void loadTextureFromFile(String imageId, byte[] data) {
        Minecraft.getInstance().execute(() -> {
            try {
                // Once loaded, we are no longer "pending"
                PENDING_REQUESTS.remove(imageId);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(data));
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "simplyquests/dynamic/" + imageId, nativeImage);
                // Use regex sanitization for dynamic image paths
                Identifier id = Identifier.fromNamespaceAndPath("simplyquests", ("dynamic/" + imageId).toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
                Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
                DYNAMIC_IMAGES.put(imageId, id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void deleteLocalImageCache(String imageId) {
        // 1. Remove from memory map so the UI stops trying to use the old Identifier
        DYNAMIC_IMAGES.remove(imageId);
        PENDING_REQUESTS.remove(imageId);

        // 2. Physically delete the file from the local machine's cache folder
        File cacheFile = Minecraft.getInstance().gameDirectory.toPath().resolve("simplyquests_cache").resolve(imageId).toFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    // Quick helper method to keep code clean
    private void drawQuestNode(GuiGraphicsExtractor graphics, int x, int y, int size, boolean isSelected, boolean isHovered, int stateColor, Quest quest) {
        // Lighten the background if hovered
        int backgroundColor = (isHovered && !isInputBlocked() && !isContextMenuOpen) ? COL_PANEL_HEADER : COL_UI_BG;
        int borderColor = isSelected ? COL_TEXT_SELECTED : stateColor;

        QuestShapeRenderer.render(quest.getShape(), graphics, x, y, size, borderColor, backgroundColor);
    }

    // --- ZOOM CENTERING SCROLL MATH ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean isMouseOverSidebar = mouseX <= this.currentSidebarWidth;

        if (this.isRewardSummaryOpen && rewardsToShow.size() > 20) {
            if (scrollY > 0 && summaryPage > 0) { summaryPage--; playClickSound(); }
            else if (scrollY < 0 && (summaryPage + 1) * 20 < rewardsToShow.size()) { summaryPage++; playClickSound(); }
            return true;
        }

        if (this.isSettingsOpen) {
            int panelH = 240;
            int y = (this.height - panelH) / 2;
            if (mouseY >= y + 30 && mouseY <= y + panelH - 30) {
                int totalRows = (CONFIG_ITEM_MAP.length + 2) / 2; // +2 to account for the extra Slider row
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

        // 2. Perform the zoom-to-mouse math
        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;
        double mouseFromCenterX = mouseX - absoluteCenterX;
        double mouseFromCenterY = mouseY - absoluteCenterY;

        this.offsetX += (mouseFromCenterX / oldZoom) - (mouseFromCenterX / this.zoom);
        this.offsetY += (mouseFromCenterY / oldZoom) - (mouseFromCenterY / this.zoom);

        // 3. Trigger a local disk save immediately
        QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);

        return true;
    }

    private void setContextMenuPos(double x, double y, int optionCount) {
        int w = 110;
        int h = optionCount * 20;

        // Clamp X and Y so the menu stays fully on screen
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

// 1. GLOBAL PICKERS (Icon, Type, Target) - Highest Priority
        if (PickerHandler.handle(this, mouseX, mouseY, button)) return true;

        // 1.5 Claim All Button Check (Only if not in a modal and rewards are available)
        
        // --- NEW: Image Manipulation Check ---
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

        // 2. CONTEXT MENU INPUT
        if (this.isContextMenuOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1 && isClickingContextMenu(mouseX, mouseY)) {
                handleContextMenuClick(mouseX, mouseY);
            } else {
                this.isContextMenuOpen = false;
                // FIX: Reset all specific context menu flags when clicking outside
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

        // 3. MOVE MODES (Dragging something on canvas)
        if (CanvasHandler.handleMoveModes(this, mouseX, mouseY, button)) return true;

        // 4. EDITOR WINDOWS (Task, Reward, Text, Quest)
        if (EditorHandler.handle(this, mouseX, mouseY, button)) return true;

        // 5. MODAL SCREENS (Settings, Submission)
        if (this.isSettingsOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) handleSettingsClicks(mouseX, mouseY);
            return true;
        }
        if (this.isItemSubmissionOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) handleItemSubmissionClicks(mouseX, mouseY);
            return true;
        }

        // 6. QUEST DETAILS WINDOW
        if (CanvasHandler.handleDetailsWindow(this, mouseX, mouseY, button)) return true;

        // 7. SIDEBAR & CANVAS
        if (CanvasHandler.handleSidebar(this, mouseX, mouseY, button)) return true;
        if (CanvasHandler.handleCanvas(this, mouseX, mouseY, button)) return true;

        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public void onClose() {
        // LOCAL SAVE: Store current camera state to the client's machine only
        if (this.selectedChapter != null) {
            QuestClientData.saveChapterViewState(this.selectedChapter.getId(), this.offsetX, this.offsetY, this.zoom);
            QuestClientData.setLastChapter(this.selectedChapter.getId());
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

        // --- PRIORITY ESCAPE HANDLING ---
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.isRewardSummaryOpen) {
                this.isRewardSummaryOpen = false;
                this.rewardsToShow.clear();
                playClickSound();
                return true;
            }

            if (this.isChoiceModalOpen) {
                this.isChoiceModalOpen = false;
                playClickSound();
                return true;
            }

            // 1. Color Picker has absolute top priority
            if (editorUI.isColorPickerOpen) {
                editorUI.isColorPickerOpen = false;
                playClickSound();
                return true;
            }

            // 2. Pickers (Icon, Dependency, Shape, etc.)
            if (editorUI.isPickerOpen()) {
                editorUI.closePicker();
                return true;
            }

            // 3. Active Typing Fields inside a window (Quantity, Name, etc.)
            if (editorUI.isTitleOpen || editorUI.isSubTitleOpen || editorUI.isDescriptionOpen ||
                editorUI.isNameOpen || editorUI.isQuantityOpen ||
                editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen || editorUI.isHexEditing) {
                editorUI.closePicker();
                return true;
            }

            // 4. Modal Windows
            if (this.isTaskEditorOpen || this.isRewardEditorOpen || this.isEditorOpen || this.isTextEditorOpen) {
                this.isTaskEditorOpen = false;
                this.isRewardEditorOpen = false;
                this.isEditorOpen = false;
                this.isTextEditorOpen = false;
                this.editorUI.isTaskMode = false;
                this.editorUI.isRewardModeOpen = false;
                playClickSound();
                return true;
            }

            // 5. Quest Details Window
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
            return true; //Consume all keys to prevent 'o' etc from closing the screen.
        }
        // Cancel Move Mode with Escape
        if (this.movingQuest != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingQuest = null;
            playClickSound();
            return true;
        }
        // Cancel Sidebar Move with Escape
        if (this.movingSidebarChapter != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingSidebarChapter = null;
            playClickSound();
            return true;
        }

        // Cancel Group Move with Escape
        if (this.movingSidebarGroup != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.movingSidebarGroup = null;
            playClickSound();
            return true;
        }

        // Handle Enter key for Editor Saving (Quest and Task)
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
                // If a text sub-editor is open, commit that specific field first
                if (editorUI.isTitleOpen) {
                    questToModify.setTitle(editorUI.searchQuery);
                    editorUI.closePicker();
                    return true;
                } else if (editorUI.isSubTitleOpen) {
                    questToModify.setSubTitle(editorUI.searchQuery);
                    editorUI.closePicker();
                    return true;
                } else if (editorUI.isDescriptionOpen) {
                    questToModify.setDescription(editorUI.searchQuery);
                    editorUI.closePicker();
                    return true;
                }
                // General editor save
                saveChanges();
                playClickSound();
                return true;
            } else if (this.isTaskEditorOpen) {
                if (editorUI.isQuantityOpen) {
                    try {
                        taskToModify.setRequiredAmount(Integer.parseInt(editorUI.searchQuery));
                    } catch (NumberFormatException ignored) {}
                    editorUI.closePicker();
                } else if (editorUI.isXOpen || editorUI.isYOpen || editorUI.isZOpen) {
                    try {
                        int val = Integer.parseInt(editorUI.searchQuery);
                        if (editorUI.isXOpen) taskToModify.setTargetX(val);
                        else if (editorUI.isYOpen) taskToModify.setTargetY(val);
                        else if (editorUI.isZOpen) taskToModify.setTargetZ(val);
                    } catch (NumberFormatException ignored) {}
                    editorUI.closePicker();
                } else if (editorUI.isNameOpen) {
                    updateTaskNameAndId(editorUI.searchQuery);
                    editorUI.closePicker();
                } else {
                    // Act like the Save button for the Task Editor
                    if (this.originalTask == null) {
                        this.selectedQuest.getTasks().add(this.taskToModify);
                    } else {
                        // Update existing task in the list
                        int index = this.selectedQuest.getTasks().indexOf(this.originalTask);
                        if (index != -1) {
                            this.selectedQuest.getTasks().set(index, this.taskToModify);
                        }
                    }
                
                // FIX: Enforce exclusivity (Radio Button behavior)
                // If this task is being set as the icon, clear the flag from all other tasks
                if (this.tempUseAsIcon) {
                    for (QuestTask t : this.selectedQuest.getTasks()) {
                        t.setIcon(false);
                    }
                    this.taskToModify.setIcon(true);
                    this.selectedQuest.setUseTaskIcon(true);
                    this.selectedQuest.setLogo(this.taskToModify.getIconStack().getItem());
                } else {
                    this.taskToModify.setIcon(false);
                    // Only disable the quest's task icon mode if NO tasks are providers anymore
                    boolean anyIcons = this.selectedQuest.getTasks().stream().anyMatch(QuestTask::isIcon);
                    if (!anyIcons) this.selectedQuest.setUseTaskIcon(false);
                }
                    updateQuestStates();
                    saveChapterData(this.selectedQuest.getChapterName()); // SAVE TRIGGER: Task added/updated
                    playClickSound();
                    this.isTaskEditorOpen = false;
                    this.taskToModify = null;
                    this.originalTask = null;
                    this.editorUI.isTaskMode = false;
                }
                return true;
            } else if (this.isRewardEditorOpen) {
                if (editorUI.isQuantityOpen || editorUI.isNameOpen) {
                    if (editorUI.isQuantityOpen && rewardToModify != null) {
                        try {
                            int amount = Integer.parseInt(editorUI.searchQuery.trim());
                            this.rewardToModify.setCount(amount);
                        } catch (NumberFormatException ignored) {}
                    } else if (editorUI.isNameOpen && rewardToModify != null) {
                        this.rewardToModify.setCommand(editorUI.searchQuery);
                    }
                    editorUI.closePicker();
                } else {
                    // Act like the Save button for the Reward Editor
                    if (this.originalReward == null) {
                        this.selectedQuest.getRewards().add(this.rewardToModify);
                    } else {
                        int index = this.selectedQuest.getRewards().indexOf(this.originalReward);
                        if (index != -1) this.selectedQuest.getRewards().set(index, this.rewardToModify);
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
                // Save and close Canvas Text Editor via Enter Key
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

        // 1. Icon Picker Search Logic (Highest Priority)
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

            // 1. Perform the action (Backspace, Left, Right)
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
                    // Initialize selection if not already selecting
                    if (editorUI.selectionStart == -1) {
                        editorUI.selectionStart = editorUI.cursorIndex;
                    }
                    if (editorUI.cursorIndex > 0) {
                        editorUI.cursorIndex--;
                    }
                    editorUI.selectionEnd = editorUI.cursorIndex;
                    // Clear selection if we moved back to the starting point
                    if (editorUI.selectionStart == editorUI.selectionEnd) {
                        editorUI.selectionStart = -1; editorUI.selectionEnd = -1;
                    }
                } else {
                    if (editorUI.selectionStart != -1) {
                        editorUI.cursorIndex = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                        editorUI.selectionStart = -1; editorUI.selectionEnd = -1;
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
                        editorUI.selectionStart = -1; editorUI.selectionEnd = -1;
                    }
                } else {
                    if (editorUI.selectionStart != -1) {
                        editorUI.cursorIndex = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                        editorUI.selectionStart = -1; editorUI.selectionEnd = -1;
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
                    // Delete the selection
                    String query = editorUI.searchQuery;
                    int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
                    int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);
                    editorUI.searchQuery = query.substring(0, start) + query.substring(end);
                    editorUI.cursorIndex = start;
                    editorUI.selectionStart = -1;
                    editorUI.selectionEnd = -1;
                } else if (editorUI.cursorIndex > 0) {
                    // Normal character-by-character backspace
                    String query = editorUI.searchQuery;
                    editorUI.searchQuery = query.substring(0, editorUI.cursorIndex - 1) + query.substring(editorUI.cursorIndex);
                    editorUI.cursorIndex--;
                }
                return true;
            }
            return true; //Consume all other keys while picker/title editor is open
        }

        // 3. Your existing keybind check
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
    public List<SidebarEntry> getSidebarEntries() { return this.sidebarEntries; }

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

        // Calculate the angle between the two nodes in their local grid space
        float angle = (float) Math.atan2(dy, dx);

        // 1. Isolate the matrix state for this line
        graphics.pose().pushMatrix();

        // 2. Move our drawing origin to the center of the parent node
        graphics.pose().translate(new Vector2f(x1, y1));

        // 3. Rotate so the "Forward" direction of the line is on the local Y-axis.
        // This allows us to use the vertical fillGradient helper to blend colors along the line's length.
        graphics.pose().rotate(angle - (float)Math.PI / 2.0f);

        // Safety check: if nodes are overlapping, don't draw a line
        if (distance <= r1 + r2) {
            graphics.pose().popMatrix();
            return;
        }
        
        // 4. INTEGER SNAPPING: Ensure line and arrow widths match exactly to prevent gaps/overflow
        int iThick = (int)thickness;
        int lineXStart = -(iThick / 2);
        int lineXEnd = lineXStart + iThick;
        float visibleLength = distance - r1 - r2;
        float arrowSpacing = 8.0f; // This MUST match the modulo in renderQuestTree

        // 4. PINCHED GRADIENT: Concentrates the color swap in the center 40% of the line
        // This prevents the parent color from dominating the entire line visually.
        float blendStart = r1 + (visibleLength * 0.3f);
        float blendEnd = r1 + (visibleLength * 0.7f);

        // 4a. Draw the Static Gradient Line
        // Segment 1: Solid Parent Color
        graphics.fill(lineXStart, (int) r1, lineXEnd, (int) blendStart, color1);

        // Segment 2: Transition Zone (The actual gradient)
        graphics.fillGradient(lineXStart, (int) blendStart, lineXEnd, (int) blendEnd, color1, color2);

        // Segment 3: Solid Child Color
        graphics.fill(lineXStart, (int) blendEnd, lineXEnd, (int) (distance - r2), color2);

        // 4b. Draw Flow Arrows ('>' symbols) along the visible line gap
        float lineStart = r1; 
        float lineEnd = distance - r2;

        // 4c. Align for Arrow Direction
        graphics.pose().rotate((float)Math.PI / 2.0f);

        // FIX: Remove margins and safety clips. The loop now starts/ends 1 unit early/late
        // so arrows appear to emerge from and submerge into the nodes.
        for (float d = lineStart + (arrowPhase - arrowSpacing); d <= lineEnd + arrowSpacing; d += arrowSpacing) {
            // The arrows still move! We sample the color of the static gradient at 
            // the arrow's current position to keep the "etched" look consistent.
            float ratio = Math.max(0, Math.min(1, (d - r1) / visibleLength));
            int baseColor = interpolateColor(color1, color2, ratio);

            // Create a "Dark Etched" version (Darken RGB to 30% of original, Alpha to 60%)
            int r = (int)(((baseColor >> 16) & 0xFF) * 0.3f);
            int g = (int)(((baseColor >> 8) & 0xFF) * 0.3f);
            int b = (int)((baseColor & 0xFF) * 0.3f);
            int etchedColor = (0x99 << 24) | (r << 16) | (g << 8) | b;

            // FIX: Use the (int, int) translate signature required by the graphics wrapper.
            // We cast the calculated positions to (int) to satisfy the method call.
            graphics.pose().pushMatrix();
            int tx = (int)(d - iThick / 2.0f);
            graphics.pose().translate(tx, lineXStart);
            graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.FLOW_ARROW,
                    0, 0, 0, 0, iThick, iThick, iThick, iThick, etchedColor);
            graphics.pose().popMatrix();
        }

        // 5. Restore the canvas matrix
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
        // 1. Convert the quest's local canvas position to absolute screen position
        // We reverse the matrix transformation math we did during the render pass!
        double absoluteCenterX = this.width / 2.0;
        double absoluteCenterY = this.height / 2.0;

        double screenX = absoluteCenterX + (quest.getX() - this.offsetX) * this.zoom;
        double screenY = absoluteCenterY + (quest.getY() - this.offsetY) * this.zoom;
        double screenSize = quest.getSize() * this.zoom;

        // 2. Check if mouse fits cleanly inside the converted bounding box
        return mouseX >= screenX && mouseX <= screenX + screenSize && mouseY >= screenY && mouseY <= screenY + screenSize;
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        float baseSize = (float) GRID_SNAP; // FIX: Use dynamic snap value for visual grid

        // 1. Calculate the view bounds in World Coordinates
        // This tells us exactly what area of the map is currently visible
        float worldWidth = (float) this.width / (float) this.zoom;
        float worldHeight = (float) this.height / (float) this.zoom;

        float minX = (float) this.offsetX - (worldWidth / 2.0f);
        float maxX = (float) this.offsetX + (worldWidth / 2.0f);
        float minY = (float) this.offsetY - (worldHeight / 2.0f);
        float maxY = (float) this.offsetY + (worldHeight / 2.0f);

        // 2. Align start points so lines snap to the grid (so quests sit perfectly on them)
        float startX = (float) Math.floor(minX / baseSize) * baseSize;
        float startY = (float) Math.floor(minY / baseSize) * baseSize;

        int gridColor = COL_GRID;

        // 3. Draw lines across the entire calculated world bounds
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
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int)this.popupX;
        int y = (this.popupY == -1) ? (this.height - panelHeight) / 2 : (int)this.popupY;
        int midX = x + (panelWidth / 2);

        // 1. Backgrounds
        String hoveredRewardLabel = null;
        String hoveredTaskName = null;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, COL_UI_BG);
        graphics.fill(x, y, x + panelWidth, y + 20, COL_PANEL_HEADER);
        drawBorder(graphics, x, y, panelWidth, panelHeight, COL_UI_BORDER);

        // 2. Title & X Button (Hover Logic)
        graphics.centeredText(this.font, Component.literal(this.selectedQuest.getTitle()), midX, y + 6, COL_TEXT);

        // Recalculated X button bounds for precise hover detection
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
        
        // FIX: Scale the 16x16 close icon to 10x10 screen pixels without cropping
        graphics.pose().pushMatrix();
        graphics.pose().translate(closeX, closeY);
        graphics.pose().scale(closeBtnSize / 16.0f, closeBtnSize / 16.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, QuestEditorUI.CLOSE_ICON, 0, 0, 0.0f, 0.0f, 16, 16, 16, 16, xColor);
        graphics.pose().popMatrix();

        // 3. Header Divider
        graphics.fill(x, y + 20, x + panelWidth, y + 21, COL_UI_BORDER);

        // 4. Tasks & Rewards Section
        int headerY = y + 30;
        int tasksCenter = x + (panelWidth / 4);
        int rewardsCenter = x + (3 * panelWidth / 4);

        graphics.centeredText(this.font, Component.literal("Tasks"), tasksCenter, headerY, COL_TEXT);
        graphics.centeredText(this.font, Component.literal("Rewards"), rewardsCenter, headerY, COL_TEXT);

        // Task Add Button (+) if in edit mode and no tasks exist
        if (QuestGlobalState.isEditModeEnabled) {
            int plusX = tasksCenter + (this.font.width("Tasks") / 2) + 8;
            boolean hoveringPlus = mouseX >= plusX - 2 && mouseX <= plusX + 10 && mouseY >= headerY - 2 && mouseY <= headerY + 12;
            int color = hoveringPlus ? COL_TEXT_GOLD : COL_TEXT;
            graphics.text(this.font, Component.literal("+"), plusX, headerY, color);
        }

        // --- REWARDS SECTION (Unified Task Style) ---
        var rewards = this.selectedQuest.getRewards();
        int rewardsAreaX = x + (panelWidth / 2) + 10;
        int rewardsAreaY = y + 45;
        int rSlotSize = 20, rMaxVisible = 4, rSlotTotalWidth = 24;

        // Add Reward Button (+)
        if (QuestGlobalState.isEditModeEnabled) {
            int rPlusX = x + (3 * panelWidth / 4) + (this.font.width("Rewards") / 2) + 8;
            boolean hov = !isInputBlocked() && mouseX >= rPlusX - 2 && mouseX <= rPlusX + 12 && mouseY >= headerY - 2 && mouseY <= headerY + 12;
            graphics.text(font, Component.literal("+"), rPlusX, headerY, hov ? COL_TEXT_GOLD : COL_TEXT);
        }

        // FIX: Update this flag to include the choice modal and summary so background interactions are blocked
        boolean isSubEditorOpen = isTaskEditorOpen || isRewardEditorOpen || isChoiceModalOpen || isRewardSummaryOpen;
        if (!rewards.isEmpty() && !isSubEditorOpen) {
            // REFINED LOGIC: A reward is a choice bundle if its subRewards list is not empty.
            // We render everything in its own slot, but bundles will cycle their display icons.
            int totalVisualItems = rewards.size();

            int startIdx = rewardPage * rMaxVisible;
            int visibleCount = Math.min(rMaxVisible, totalVisualItems - startIdx);
            int rTotalWidth = rMaxVisible * rSlotTotalWidth;
            boolean hasPager = rewards.size() > rMaxVisible;
            int renderX = rewardsAreaX + (hasPager ? 12 : 0);

            // --- REWARD GHOST LINE (Reordering Indicator) ---
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

            // Pagination Arrows
            if (hasPager) {
                if (rewardPage > 0) graphics.text(font, "<", rewardsAreaX, rewardsAreaY + 6, COL_TEXT);
                if (startIdx + rMaxVisible < rewards.size()) graphics.text(font, ">", renderX + (rMaxVisible * rSlotTotalWidth), rewardsAreaY + 6, COL_TEXT);
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
                    // Bundle rendering (Cycling icons)
                    int cycleIdx = (int)((Util.getMillis() / 1000) % reward.getSubRewards().size());
                    QuestReward displayReward = reward.getSubRewards().get(cycleIdx);

                    editorUI.drawRewardIcon(graphics, displayReward, circleColor, innerColor, ix, rewardsAreaY, rSlotSize, questDone && !claimed);
                    renderRewardLabel(graphics, displayReward, ix, rewardsAreaY, rSlotSize);
                } else {
                    if (reward == movingReward) graphics.fill(ix, rewardsAreaY, ix + rSlotSize, rewardsAreaY + rSlotSize, 0x40FFFFFF);
                    editorUI.drawRewardIcon(graphics, reward, circleColor, innerColor, ix, rewardsAreaY, rSlotSize, questDone && !claimed);
                    renderRewardLabel(graphics, reward, ix, rewardsAreaY, rSlotSize);
                }

                // Hover Tooltip
                if (isRewardHovered) {
                    hoveredRewardLabel = isBundle ? "Reward Choice (Click to open)" : getRewardTooltip(reward);
                }
            }
        }

        // 4b. Task List Rendering
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

            // --- TASK GHOST LINE (Reordering Indicator) ---
            if (this.movingTask != null && mouseX >= renderX && mouseX <= renderX + (maxVisibleTasks * slotTotalWidth)) {
                int snappedIX = -1;
                for (int i = 0; i < visibleCount; i++) {
                    int ix = renderX + (i * slotTotalWidth);
                    // If mouse is in the left half of the slot, snap to the left edge
                    if (mouseX < ix + (slotTotalWidth / 2)) {
                        snappedIX = ix - 2;
                        break;
                    }
                }
                // If not snapped before any item, snap after the last visible item
                if (snappedIX == -1) snappedIX = renderX + (visibleCount * slotTotalWidth) - 2;

                graphics.fill(snappedIX, tasksAreaY, snappedIX + 1, tasksAreaY + slotSize, COL_UI_BORDER);
            }

            // Draw cycling arrows
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

                // Fetch the synced current amount for this task immediately
                int currentAmount = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(task.getId(), 0);

                // Dim the task being moved
                boolean isBeingMoved = (task == this.movingTask);
                float alpha = isBeingMoved ? 0.3f : 1.0f;
                int highlightColor = isBeingMoved ? 0x10FFFFFF : 0x40FFFFFF;

                // Remove dark background (make transparent) and add hover highlight
                boolean isTaskHovered = mouseX >= ix && mouseX <= ix + slotSize && mouseY >= tasksAreaY && mouseY <= tasksAreaY + slotSize;
                int innerColor = (isTaskHovered && !isBeingMoved && !isSubEditorOpen) ? COL_PANEL_HEADER : COL_UI_BG;
                if (isTaskHovered && !isBeingMoved && !isSubEditorOpen) {
                    hoveredTaskName = getTaskTooltip(task);
                }

                // --- RENDER TASK INFO TEXT ---
                String info = "";
                if (task.getType() == QuestTask.TaskType.ITEM || task.getType() == QuestTask.TaskType.KILL) {
                    // Use the pre-fetched amount for the text display
                    int displayAmount = Math.min(currentAmount, task.getRequiredAmount());
                    info = displayAmount + "/" + task.getRequiredAmount();
                } else if (task.getType() == QuestTask.TaskType.LOCATION) {
                    info = task.getTargetX() + "," + task.getTargetY() + "," + task.getTargetZ();
                }

                if (!info.isEmpty()) {
                    float s = 0.5f; // Small scale to fit coordinates
                    int textW = (int)(this.font.width(info) * s);
                    int tx = ix + (slotSize / 2) - (textW / 2);
                    int ty = tasksAreaY + slotSize + 2;

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(tx, ty);
                    graphics.pose().scale(s, s);
                    graphics.text(this.font, info, 0, 0, COL_TEXT);
                    graphics.pose().popMatrix();
                }

                // Draw Icon with Alpha support (for movement)
                graphics.pose().pushMatrix();
                // If your GuiGraphicsExtractor supports alpha, you can apply it here; otherwise, just render.
                editorUI.drawTaskIcon(graphics, task, currentAmount, innerColor, ix + 2, tasksAreaY + 2, mouseX, mouseY, 16);
                graphics.pose().popMatrix();
            }
        }

        int hLine1Y = y + 75; // Pulled up from 90 to 75
        graphics.fill(midX, y + 21, midX + 1, hLine1Y, COL_UI_BORDER); // Vert line

        // 7. Moving Task Icon (Follows cursor)
        if (this.movingTask != null) {
            int current = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(movingTask.getId(), 0);
            editorUI.drawTaskIcon(graphics, movingTask, current, COL_PANEL_HEADER, (int)mouseX - 10, (int)mouseY - 10, (int)mouseX, (int)mouseY, 20);
        }

        graphics.fill(x, hLine1Y, x + panelWidth, hLine1Y + 1, COL_UI_BORDER); // Horiz line 1

        // Reverted to Truncation Logic for the main window
        String fullSubTitle = this.selectedQuest.getSubTitle();
        String displayedSubTitle = truncate(fullSubTitle, panelWidth - 20);
        int subTitleY = hLine1Y + 8;

        // Hover Overlay for Subtitle (Static Multi-line Expansion)
        boolean isSubTitleHovered = mouseX >= x + 10 && mouseX <= x + panelWidth - 10 && mouseY >= subTitleY && mouseY < subTitleY + font.lineHeight;
        boolean showSubTitleOverlay = isSubTitleHovered && !fullSubTitle.equals(displayedSubTitle);

        // Only draw the base text if the expansion overlay is not active
        if (!showSubTitleOverlay) {
            graphics.centeredText(this.font, Component.literal(displayedSubTitle), midX, subTitleY, COL_TEXT);
        }

        // Restored fixed height for the subtitle bar
        int hLine2Y = subTitleY + 14;
        graphics.fill(x, hLine2Y, x + panelWidth, hLine2Y + 1, COL_UI_BORDER); // Horiz line 2

        // 6. Description with Vertical Scrolling
        int descAreaY = hLine2Y + 8;
        int descAreaHeight = (y + panelHeight) - descAreaY - 10; // 10px padding at bottom
        int textWidth = panelWidth - 20;

        //Split text into lines to calculate total height and handle scrolling.
        var lines = this.font.split(Component.literal(this.selectedQuest.getDescription()), textWidth);
        int totalTextHeight = lines.size() * this.font.lineHeight;

        //Clamp the scroll offset so we don't scroll into the void.
        this.descScrollOffset = Math.max(0, Math.min(this.descScrollOffset, Math.max(0, totalTextHeight - descAreaHeight)));

        graphics.enableScissor(x + 10, descAreaY, x + panelWidth - 10, y + panelHeight - 10);

        for (int i = 0; i < lines.size(); i++) {
            int lineY = descAreaY + (i * this.font.lineHeight) - (int)this.descScrollOffset;
            //Only render if the line is vertically within the scissored area.
            if (lineY + this.font.lineHeight > descAreaY && lineY < y + panelHeight - 10) {
                graphics.text(this.font, lines.get(i), x + 10, lineY, COL_TEXT);
            }
        }
        graphics.disableScissor();

        // Final Pass: Tooltips (Rendered last within the window context to be on top of everything)
        if (hoveredTaskName != null && !isContextMenuOpen && !isSubEditorOpen) {
            renderSimpleTooltip(graphics, hoveredTaskName, mouseX, mouseY);
        }

        if (hoveredRewardLabel != null && !isContextMenuOpen && !isSubEditorOpen) {
            renderSimpleTooltip(graphics, hoveredRewardLabel, mouseX, mouseY);
        }

        // Final Pass for Quest Details: Render the overlay box last so it covers the description and separator line
        if (showSubTitleOverlay) {
            renderStaticTextOverlay(graphics, fullSubTitle, x + 10, subTitleY, panelWidth - 20);
        }
    }

    private String getTaskTooltip(QuestTask task) {
        String baseName = task.getTargetDisplayName();
        
        // FIX: If the name is just the auto-generated target name, ignore it to show the instruction instead
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
        // If reward is null, we treat it as a bundle and show a "?"
        String val = (reward == null) ? "?" : (reward.getType() == QuestReward.RewardType.COMMAND ? "CMD" : String.valueOf(reward.getCount()));
        float s = 0.5f;
        int tx = ix + (size / 2) - (int)(font.width(val) * s / 2);
        
        graphics.pose().pushMatrix();
        graphics.pose().translate(tx, iy + size + 2);
        graphics.pose().scale(s, s);
        graphics.text(font, val, 0, 0, COL_TEXT);
        graphics.pose().popMatrix();
    }

    private String getRewardTooltip(QuestReward reward) {
        if (reward == null) return "";
        return switch(reward.getType()) {
            case ITEM -> {
                String name = reward.getItem().getDefaultInstance().getHoverName().getString();
                yield reward.getCount() + "x " + name;
            }
            case XP -> reward.getCount() + " XP";
            case COMMAND -> "Execute Command";
        };
    }

    private void renderContextMenu(GuiGraphicsExtractor graphics) {
        // 1. Fetch live mouse position directly from hardware
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        List<String> options;
        if (isSideBarContextMenu) options = List.of("Add Group", "Add Chapter");
        else if (isSideBarEntryMenu) options = (sidebarTargetEntry instanceof SidebarGroup) ? List.of("Move", "Rename", "Reset Progress", "Delete") : List.of("Move", "Rename", "Edit Icon", "Reset Progress", "Delete");
        else if (isTaskContextMenu) options = getTaskOptions();
        else if (isRewardContextMenu) options = List.of("Edit", "Delete", "Move");
        else if (isImageContextMenu) options = List.of("Move", "Delete");
        else if (isTextContextMenu) options = List.of("Edit Text", "Delete", "Move");
        else options = (questToModify == null) ? List.of("Create Quest", "Add Text", "Add Image") : List.of("Edit", "Complete", "Delete", "Reset Progress", "Move");

        int w = 110;
        int h = options.size() * 20;
        int x = (int)contextMenuX;
        int y = (int)contextMenuY;

        // 2. Draw background
        graphics.fill(x, y, x + w, y + h, COL_PANEL_HEADER);

        // Draw border
        graphics.fill(x, y, x + w, y + 1, COL_UI_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COL_UI_BORDER);
        graphics.fill(x, y, x + 1, y + h, COL_UI_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COL_UI_BORDER);

        // 3. Render Options with live hover detection
        for (int i = 0; i < options.size(); i++) {
            int optionY = y + (i * 20);

            // Use the live mouse coordinates for check
            boolean isOptionHovered = mouseX >= x && mouseX <= x + w && mouseY >= optionY && mouseY <= optionY + 20;

            // Highlight background
            if (isOptionHovered) {
                graphics.fill(x + 1, optionY + 1, x + w - 1, optionY + 19, COL_HOVER_MENU);
            }

            // Gold if hovered, White if not
            int textColor = isOptionHovered ? COL_TEXT_GOLD : COL_TEXT;

            graphics.text(this.font, Component.literal(options.get(i)), x + 5, optionY + 6, textColor, false);
        }
    }

    public void openRewardContextMenu(double x, double y, QuestReward reward) {
        setContextMenuPos(x, y, 3);
        this.isContextMenuOpen = true;
        this.isRewardContextMenu = true;
        this.sidebarTargetReward = reward;

        this.isTaskContextMenu = false; // Reset other flags
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
        // 1. Calculate how many options we have
        int optionCount;
        if (isSideBarContextMenu) optionCount = 2;
        else if (isSideBarEntryMenu) optionCount = (sidebarTargetEntry instanceof SidebarGroup) ? 4 : 5;
        else if (isTaskContextMenu) optionCount = getTaskOptions().size();
        else if (isRewardContextMenu) optionCount = 3;
        else if (isTextContextMenu) optionCount = 3;
        // FIX: Increased from 2 to 3 to accommodate the "Add Image" option
        else optionCount = (questToModify == null) ? 3 : 5;

        // 2. Define the exact dimensions
        int w = 110;
        int h = optionCount * 20;

        // 3. Return true if inside this rectangle
        return mouseX >= contextMenuX && mouseX <= contextMenuX + w &&
                mouseY >= contextMenuY && mouseY <= contextMenuY + h;
    }

    public void completeQuest(Quest quest) {
        // Notify the server to perform completion.
        // Server will sync back state and trigger Toasts.
        ClientPacketDistributor.sendToServer(new AdminCompletePayload(quest.getId(), Optional.empty(), true));

        playClickSound();
        updateQuestStates();
        // Removed saveChapterData: completion status is handled by the server progress, not the chapter file.
    }

    public void claimAllRewards() {
        List<QuestReward> rewardsToClaim = new ArrayList<>();

        for (Quest quest : this.allQuests) {
            // Only process quests that are completed
            if (quest.getState() == QuestState.COMPLETED) {
                for (QuestReward reward : quest.getRewards()) {
                    // Only claim if not already claimed in the client-side tracker
                    // Skip choice bundles - they must be claimed manually
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
        // FIX: Immediately clear from client-side completion cache
        SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.remove(quest.getId());
        
        // 1. Remove from the primary list
        this.allQuests.remove(quest);

        // 2. Remove from the lookup map
        this.questLookup.remove(quest.getId());

        // 3. Clean up UI references
        if (this.selectedQuest == quest) {
            this.selectedQuest = null;
        }

        playClickSound();
        updateQuestStates();
        saveChapterData(quest.getChapterName());
    }

    private void handleContextMenuClick(double mouseX, double mouseY) {
        playClickSound(); // Play sound for ANY valid context menu selection
        this.isContextMenuOpen = false;

        if (isRewardContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;

            if (optionIndex == 0) { // Edit
                this.originalReward = this.sidebarTargetReward;
                QuestReward r = this.originalReward;
                // Use the Copy Constructor to clone the reward and its sub-rewards
                this.rewardToModify = new QuestReward(r);
                this.isRewardEditorOpen = true;
                this.editorUI.isRewardModeOpen = true;
            } else if (optionIndex == 1) { // Delete
                this.selectedQuest.getRewards().remove(this.sidebarTargetReward);

                // Recalculate reward page bounds to prevent being stuck on an empty page
                int maxRewardPages = (this.selectedQuest.getRewards().size() + 3) / 4;
                if (this.rewardPage >= maxRewardPages && this.rewardPage > 0) this.rewardPage = maxRewardPages - 1;

                saveChapterData(this.selectedQuest.getChapterName());
            } else if (optionIndex == 2) { // Move
                this.movingReward = this.sidebarTargetReward;
            }

            this.isRewardContextMenu = false;
            this.isContextMenuOpen = false;
            return;
        }

        if (isImageContextMenu) {
            int relativeY = (int) (mouseY - contextMenuY);
            int optionIndex = relativeY / 20;
            if (optionIndex == 0) { // Move
                this.movingCanvasImage = this.selectedCanvasImage;
                float absoluteCenterX = (float) (this.width / 2.0);
                float absoluteCenterY = (float) (this.height / 2.0);
                double worldMouseX = this.offsetX + ((contextMenuX - absoluteCenterX) / this.zoom);
                double worldMouseY = this.offsetY + ((contextMenuY - absoluteCenterY) / this.zoom);
                this.dragOffsetX = worldMouseX - this.movingCanvasImage.getX();
                this.dragOffsetY = worldMouseY - this.movingCanvasImage.getY();
            } else if (optionIndex == 1) { // Delete
                // Capture the filename before removing the object from the list
                String imgId = this.selectedCanvasImage.getImageId();
                this.allCanvasImages.remove(this.selectedCanvasImage);
                saveChapterData(this.selectedCanvasImage.getChapterName());
                // Request server to delete the physical file
                ClientPacketDistributor.sendToServer(new DeleteImagePayload(imgId));
                // FIX: Immediately purge the image from the client's memory and disk cache
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
                    // FIX: Initialize state from the explicit boolean field
                    this.tempUseAsIcon = t.isIcon();
                } else if (action.equals("Move")) {
                    this.movingTask = this.sidebarTargetTask;
                } else if (action.equals("Complete")) {
                    ClientPacketDistributor.sendToServer(new AdminCompletePayload(selectedQuest.getId(), Optional.of(sidebarTargetTask.getId()), true));
                } else if (action.equals("Reset Progress")) {
                    ClientPacketDistributor.sendToServer(new AdminCompletePayload(selectedQuest.getId(), Optional.of(sidebarTargetTask.getId()), false));
                } else if (action.equals("Delete")) {
                    this.selectedQuest.getTasks().remove(this.sidebarTargetTask);

                    // Recalculate task page bounds to prevent being stuck on an empty page
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
            if (optionIndex == 0) { // Edit
                openTextEditor(editingCanvasText);
            } else if (optionIndex == 1) { // Delete
                allCanvasTexts.remove(editingCanvasText);
                saveChapterData(editingCanvasText.getChapterName());
                editingCanvasText = null;
            } else if (optionIndex == 2) { // Move
                this.movingCanvasText = this.editingCanvasText;
                // FIX: Calculate world-space offsets relative to where the context menu was opened
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
                    case 2 -> { // Reset Progress
                        // Send the sanitized group name so the server can match it against quest IDs
                        ClientPacketDistributor.sendToServer(new AdminResetPayload(Optional.of(Quest.sanitizePath(group.getTitle())), Optional.empty()));
                    }
                    case 3 -> {
                        // Delete group and all internal quests
                        for (SidebarChapter ch : group.getChapters()) {
                            String chName = ch.getName();
                            // Request server-side file deletion
                            ClientPacketDistributor.sendToServer(new DeleteChapterPayload(chName));

                            allQuests.removeIf(q -> {
                                if (q.getChapterName().equals(chName)) {
                                    questLookup.remove(q.getId());
                                    if (this.selectedQuest == q) this.selectedQuest = null;
                                    return true;
                                }
                                return false;
                            });
                        }
                        sidebarEntries.remove(group);
                        // Tell the server to remove the group from groups.json
                        ClientPacketDistributor.sendToServer(new DeleteGroupPayload(group.getTitle()));
                        updateQuestStates();
                        saveGroupManifest(); // SAVE TRIGGER: Update groups.json manifest
                    }
                }
            } else if (sidebarTargetEntry instanceof SidebarChapter chapter) {
                switch (optionIndex) {
                    case 0 -> this.movingSidebarChapter = chapter; // Move
                    case 1 -> { // Rename
                        this.editingChapter = chapter;
                        this.sidebarSearchQuery = chapter.getName();
                        this.sidebarTextScrollOffset = 0;
                    }
                    case 2 -> { // Edit Icon
                        this.isEditingChapterIcon = true;
                        this.editorUI.isIconPickerOpen = true;
                        this.editorUI.searchQuery = "";
                        this.editorUI.scrollOffset = 0;
                    }
                    case 3 -> { // Reset Progress
                        // Send the internal sanitized ID so the server can find the quests
                        ClientPacketDistributor.sendToServer(new AdminResetPayload(Optional.empty(), Optional.of(chapter.getId())));
                    }
                    case 4 -> { // Delete
                        String chapterName = chapter.getName();

                        // FIX: Request server-side file deletion
                        ClientPacketDistributor.sendToServer(new DeleteChapterPayload(chapterName));

                        // 1. Purge all quests belonging to this chapter from memory and the lookup map
                        allQuests.removeIf(q -> {
                            if (q.getChapterName().equals(chapterName)) {
                                questLookup.remove(q.getId());
                                if (this.selectedQuest != null && this.selectedQuest.getId().equals(q.getId())) this.selectedQuest = null;
                                return true;
                            }
                            return false;
                        });

                        // 2. Remove the chapter entry from the sidebar
                        if (sidebarTargetParentGroup != null) {
                            sidebarTargetParentGroup.getChapters().remove(chapter);
                        } else {
                            sidebarEntries.remove(chapter);
                        }
                        if (selectedChapter == chapter) {
                            selectedChapter = null;
                        }

                        // 3. Refresh states to handle broken dependencies in other chapters
                        updateQuestStates();
                        saveGroupManifest(); // SAVE TRIGGER: Update ordering and manifest
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

            if (optionIndex == 0) { //Add Group
                SidebarGroup newGroup = new SidebarGroup("New Group", COL_TEXT);
                this.sidebarEntries.add(newGroup);
                this.editingGroup = newGroup;
                this.sidebarSearchQuery = "";
                this.sidebarTextScrollOffset = 0;

                this.needsSidebarScrollToBottom = true;
                if (!newGroup.isExpanded()) newGroup.toggleExpanded();
            } else if (optionIndex == 1) { //Add Chapter
                SidebarChapter newChapter = new SidebarChapter("New Chapter");
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

            if (optionIndex == 0) { // Create Quest
                double gridX = snappedX - 12.0;
                double gridY = snappedY - 12.0;
                // currentChapter here is already chapter.getId() thanks to our earlier update
                this.questToModify = new Quest(newId, currentChapter, "New Quest", gridX, gridY);
                this.originalQuest = null;
                this.isEditorOpen = true;
            } else if (optionIndex == 1) { // Add Text
                CanvasText ct = new CanvasText("New Text", snappedX, snappedY, 1.0f, COL_TEXT);
                ct.setChapterName(this.selectedChapter.getName());
                openTextEditor(ct);
            } else if (optionIndex == 2) { // Add Image
                openImagePicker(snappedX, snappedY);
            }
        } else {
            // Handling interactions with an existing quest node
            switch (optionIndex) {
                case 0 -> {
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
        // Top
        graphics.fill(x, y, x + w, y + thickness, color);
        // Bottom
        graphics.fill(x, y + h - thickness, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + thickness, y + h, color);
        // Right
        graphics.fill(x + w - thickness, y, x + w, y + h, color);
    }
    private void updateSidebarScrollOffset(int availableWidth) {
        // FIX: Use 2px buffer for the vertical bar cursor instead of full underscore width
        int textWidth = this.font.width(sidebarSearchQuery) + 2;
        if (textWidth > availableWidth) {
            sidebarTextScrollOffset = textWidth - availableWidth;
        } else {
            sidebarTextScrollOffset = 0;
        }
    }

    private void renderSimpleTooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        int maxWidth = 200; // Maximum width for floating tooltips
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

        // Ensure tooltip doesn't go off-screen
        if (boxY < 5) boxY = 5;
        if (boxY + boxHeight > this.height) boxY = this.height - boxHeight - 5;
        if (boxX + boxWidth > this.width - 5) {
            boxX = mouseX - boxWidth - 12;
        }

        // 1. Background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, COL_TOOLTIP_BG);

        // 2. Border
        int borderColor = COL_UI_BORDER;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        // 3. Render Lines
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(this.font, lines.get(i), boxX + padding, boxY + 3 + (i * this.font.lineHeight), COL_TEXT);
        }
    }

    /**
     * Renders a tooltip anchored to the bottom-center of the active canvas area.
     * This prevents mouse-relative tooltips from obscuring dependency lines.
     */
    private void renderAnchoredQuestTooltip(GuiGraphicsExtractor graphics, String text) {
        // Calculate center of the area between the sidebar and the right edge
        int canvasStartX = (int)this.currentSidebarWidth;
        int canvasWidth = this.width - canvasStartX;

        int maxWidth = canvasWidth - 40; // Allow 20px margin on each side
        var lines = this.font.split(Component.literal(text), maxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 6;
        int boxWidth = textWidth + (padding * 2);
        int boxHeight = (lines.size() * this.font.lineHeight) + 4;

        int boxX = canvasStartX + (canvasWidth - boxWidth) / 2;
        int boxY = this.height - boxHeight - 8; // Closer to the bottom

        // 1. Background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, COL_TOOLTIP_BG);

        // 2. Border
        drawBorder(graphics, boxX, boxY, boxWidth, boxHeight, COL_UI_BORDER);

        // 3. Render Lines
        for (int i = 0; i < lines.size(); i++) {
            graphics.centeredText(this.font, lines.get(i), boxX + (boxWidth / 2), boxY + 2 + (i * this.font.lineHeight), COL_TEXT);
        }
    }

    public String truncate(String text, int maxWidth) {
        if (maxWidth < 10) return ""; // Safety floor for UI animations
        if (this.font.width(text) <= maxWidth) return text;

        // Use Minecraft's built-in efficient truncation helper
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
    }

    private void renderStaticTextOverlay(GuiGraphicsExtractor graphics, String fullText, int x, int y, int containerMaxWidth) {
        // Split text into lines to support vertical expansion in the overlay
        var lines = this.font.split(Component.literal(fullText), containerMaxWidth);

        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }

        int padding = 4;
        // Ensure width is clamped to absolute screen edge minus some padding
        int boxWidth = Math.min(textWidth + (padding * 2), this.width - x - padding);
        int boxHeight = (lines.size() * this.font.lineHeight) + (padding * 2);

        // Clamp within screen/container bounds
        int finalX = x - padding;
        int finalY = y - padding;

        // Thematic Opaque Background
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
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int)this.popupX;
        int y = (this.popupY == -1) ? (this.height - panelHeight) / 2 : (int)this.popupY;
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

            // Allow digits always
            boolean isDigit = Character.isDigit(firstChar);
            // Allow minus sign only at the start and for coordinates, not quantity
            // Fix: Allow '-' if at the start (index 0) or if replacing text starting at the beginning (overwriting '0')
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
                    // Coordinates have a generous 10-character limit
                    charTypedInPicker(typed);
                }
            }
            return true;
        }

        if (editorUI.isColorPickerOpen) {
            String typed = event.codepointAsString();
            // Only allow valid hex characters
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
                // 1. Capture old ID and context
                String oldId = originalQuest.getId();
                String chapter = originalQuest.getChapterName();
                String group = findGroupNameForChapter(chapter);

                // 2. Only regenerate the ID if the sanitized title has actually changed.
                // This prevents "Sanitization Drift" from changing the ID and orphaning player progress
                // when the admin is just editing properties like dependencies or descriptions.
                String newId = oldId;
                if (!Quest.sanitizePath(originalQuest.getTitle()).equals(Quest.sanitizePath(questToModify.getTitle()))) {
                    List<Quest> otherQuests = allQuests.stream().filter(q -> q != originalQuest).toList();
                    newId = Quest.generateQuestId(group, chapter, questToModify.getTitle(), otherQuests);
                }
                
                // 3. Update relational dependencies in other quests
                if (!oldId.equals(newId)) {
                    for (Quest q : allQuests) {
                        List<String> deps = q.getDependencies();
                        for (int i = 0; i < deps.size(); i++) {
                            if (deps.get(i).equals(oldId)) {
                                deps.set(i, newId);
                            }
                        }
                    }
                    // Update registry maps
                    questLookup.remove(oldId);
                    questLookup.put(newId, originalQuest);
                }

                // 4. Apply changes
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
                originalQuest.setRewards(questToModify.getRewards());
                originalQuest.setTasks(questToModify.getTasks());
                originalQuest.setUseTaskIcon(questToModify.isUseTaskIcon());

                // If title changed, dependencies globally might be broken, save all
                if (!oldId.equals(newId)) {
                    saveAllChapters();
                } else {
                    saveChapterData(chapter);
                }
            } else {
                // NEW QUEST: Generate final ID based on the title entered in editor
                String chapter = questToModify.getChapterName();
                String group = findGroupNameForChapter(chapter);
                String finalId = Quest.generateQuestId(group, chapter, questToModify.getTitle(), allQuests);
                questToModify.setId(finalId);

                registerQuest(questToModify);
                saveChapterData(chapter);
            }
            updateQuestStates();
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
        return ""; // Default for standalone chapters to ensure ID consistency
    }

    public void stopSidebarEditing(boolean save) {
        if (save) {
            if (editingGroup != null && !sidebarSearchQuery.isEmpty()) {
                String oldGroup = editingGroup.getTitle();
                String newGroup = sidebarSearchQuery;
                if (!Quest.sanitizePath(oldGroup).equals(Quest.sanitizePath(newGroup))) {
                    // When a group is renamed, all chapters inside it need to be re-saved
                    // because their internal data (the group they belong to) has changed.
                    // (Note: This assumes you add a 'group' field to QuestChapter later)
                    editingGroup.setTitle(newGroup);

                    // IDs contain the group name, so we must update all quests in all chapters of this group
                    Map<String, String> idChanges = new HashMap<>();
                    String newGroupId = Quest.sanitizePath(newGroup);
                    for (SidebarChapter ch : editingGroup.getChapters()) {
                        for (Quest q : allQuests) {
                            if (q.getChapterName().equals(ch.getId())) {
                                String oldId = q.getId();
                                List<Quest> others = allQuests.stream().filter(quest -> quest != q).toList();
                                String newId = Quest.generateQuestId(newGroupId, ch.getId(), q.getTitle(), others);

                                q.setId(newId);
                                idChanges.put(oldId, newId);
                                questLookup.remove(oldId);
                                questLookup.put(newId, q);

                                // Fix Task IDs to match new parent
                                for(QuestTask task : q.getTasks()) {
                                    task.setId(QuestTask.generateTaskId(newId, task.getName(), q.getTasks()));
                                }
                            }
                        }
                    }
                    updateAllDependencies(idChanges);

                    if (idChanges.isEmpty()) {
                        saveGroupManifest();
                        editingGroup.getChapters().forEach(ch -> saveChapterData(ch.getId()));
                    } else {
                        saveAllChapters(); // Save everything to ensure dependency IDs are updated globally
                    }
                } else {
                    saveGroupManifest();
                }
            } else if (editingChapter != null && !sidebarSearchQuery.isEmpty()) {
                String oldChapterId = editingChapter.getId();
                String newChapter = sidebarSearchQuery;
                String newChapterId = Quest.sanitizePath(newChapter);
                if (oldChapterId == null || !Quest.sanitizePath(oldChapterId).equals(newChapterId)) {
                    if (oldChapterId != null) {
                        ClientPacketDistributor.sendToServer(new DeleteChapterPayload(oldChapterId));
                    }

                    String group = findGroupNameForChapter(oldChapterId);
                    editingChapter.setName(newChapter);
                    editingChapter.setId(newChapterId);

                    // Update the saved active chapter name if the current one was renamed
                    if (selectedChapter == editingChapter) QuestClientData.setLastChapter(newChapterId);

                    Map<String, String> idChanges = new HashMap<>();
                    for (Quest q : allQuests) {
                        if (q.getChapterName().equals(oldChapterId)) {
                            String oldId = q.getId();

                            // 1. Update the internal chapter name so the filter doesn't hide it
                            q.setChapterName(newChapterId);

                            // 2. Regenerate ID because chapter name changed
                            List<Quest> others = allQuests.stream().filter(quest -> quest != q).toList();
                            String newId = Quest.generateQuestId(group, newChapterId, q.getTitle(), others);

                            q.setId(newId);
                            idChanges.put(oldId, newId);
                            for(QuestTask task : q.getTasks()) {
                                task.setId(QuestTask.generateTaskId(newId, task.getName(), q.getTasks()));
                            }

                            // 3. Update lookup maps
                            questLookup.remove(oldId);
                            questLookup.put(newId, q);
                        }
                    }
                    updateAllDependencies(idChanges);

                    if (idChanges.isEmpty()) {
                        saveGroupManifest();
                        saveChapterData(newChapterId);
                    } else {
                        saveAllChapters(); // Global save to fix cross-chapter dependencies
                    }
                } else {
                    saveGroupManifest();
                    saveChapterData(oldChapterId);
                }
            }
        } else {
            // If canceled and name was empty, we might want to remove the item
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

        // 1. Fully detach the chapter from its current position
        sidebarEntries.remove(movingSidebarChapter);
        sidebarEntries.forEach(e -> {
            if (e instanceof SidebarGroup g) g.getChapters().remove(movingSidebarChapter);
        });

        // 2. Determine new location (Matching the ghost line logic)
        int currentYPosition = (int) (15 / textScale);
        int insertIndex = -1;
        SidebarGroup targetGroup = null;

        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);

            // Check if dropping into the root gap above this entry
            if (scrollableMouseY < currentYPosition + 4) {
                insertIndex = i;
                break;
            }

            if (entry instanceof SidebarGroup group) {
                currentYPosition += 18;
                if (group.isExpanded()) {
                    // Allow dropping into an empty expanded group
                    if (group.getChapters().isEmpty()) {
                        if (scrollableMouseY < currentYPosition + 16) {
                            targetGroup = group;
                            insertIndex = 0;
                            break;
                        }
                    }
                    for (SidebarChapter chapter : group.getChapters()) {
                        // If mouse is over this chapter row
                        if (scrollableMouseY < currentYPosition + 16) {
                            targetGroup = group;
                            int idx = group.getChapters().indexOf(chapter);
                            // Top half = before, Bottom half = after
                            insertIndex = (scrollableMouseY < currentYPosition + 8) ? idx : idx + 1;
                            break;
                        }
                        currentYPosition += 16;
                    }
                    if (targetGroup != null) break;
                }
            } else {
                // Check if dropping over a standalone chapter
                if (scrollableMouseY < currentYPosition + 16) {
                    // Top half = before, Bottom half = after
                    insertIndex = (scrollableMouseY < currentYPosition + 8) ? i : i + 1;
                    break;
                } else {
                    currentYPosition += 16;
                }
            }
            currentYPosition += 6;
        }

        // 3. Perform insertion
        if (targetGroup != null) {
            targetGroup.getChapters().add(insertIndex, movingSidebarChapter);
        } else {
            // If insertIndex is still -1, it goes to the bottom of the root list
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

            // Groups can only be root items, so we only care about root list indices
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
            this.editorUI.closePicker(); // Ensure sub-pickers are also closed
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
            editorUI.closePicker(); // Close others first
            editorUI.searchQuery = String.valueOf(taskToModify.getRequiredAmount());
            editorUI.isQuantityOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();
            // Highlight the number so it can be overwritten immediately
            editorUI.selectionStart = 0; editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("Name")) {
            editorUI.closePicker();
            editorUI.searchQuery = taskToModify.getName();
            editorUI.isNameOpen = true;
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0; editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("X") || field.equals("Y") || field.equals("Z")) {
            editorUI.closePicker();
            editorUI.searchQuery = String.valueOf(field.equals("X") ? taskToModify.getTargetX() : (field.equals("Y") ? taskToModify.getTargetY() : taskToModify.getTargetZ()));
            editorUI.isXOpen = field.equals("X");
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0; editorUI.selectionEnd = editorUI.searchQuery.length();
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

    // Helper for charTyped to reuse logic
    private void charTypedInPicker(String newChar) {
        // Handle Selection replacement
        if (editorUI.selectionStart != -1 && editorUI.selectionEnd != -1) {
            int start = Math.min(editorUI.selectionStart, editorUI.selectionEnd);
            int end = Math.max(editorUI.selectionStart, editorUI.selectionEnd);

            // Ensure indices are within current string bounds (prevents crashes from stale state)
            start = Math.max(0, Math.min(start, editorUI.searchQuery.length()));
            end = Math.max(0, Math.min(end, editorUI.searchQuery.length()));

            editorUI.searchQuery = editorUI.searchQuery.substring(0, start) + newChar + editorUI.searchQuery.substring(end);
            editorUI.cursorIndex = start + 1;
            editorUI.selectionStart = -1;
            editorUI.selectionEnd = -1;
        } else {
            // Normal character insertion
            if (editorUI.cursorIndex > editorUI.searchQuery.length()) editorUI.cursorIndex = editorUI.searchQuery.length();
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
        int x = (this.popupX == -1) ? (this.width - panelWidth) / 2 : (int)this.popupX;
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

        // FIX: Use offsets during drop calculation to maintain relative position
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

        // 1. Search by internal ID (chapterName here is the sanitized ID passed from Quest objects)
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

        // 2. Perform the save if the chapter was found
        if (targetChapter != null) {
            final String finalChapterId = targetChapter.getId();
            // Calculate live ordering metadata
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
                        groupName = Quest.sanitizePath(group.getTitle());
                        break;
                    }
                }
            }

            String prettyTitle = targetChapter.getName(); // The display title
            String sanitizedId = targetChapter.getId();   // The internal ID
            QuestChapter data = new QuestChapter(groupName, groupOrder, groupColor, sanitizedId, prettyTitle, chapterOrder,
                    targetChapter.getIconStack().getItem(),
                    new ArrayList<>(allQuests.stream().filter(q -> q.getChapterName().equals(finalChapterId)).toList()),
                    new ArrayList<>(allCanvasTexts.stream().filter(ct -> ct.getChapterName().equals(finalChapterId)).toList()),
                    new ArrayList<>(allCanvasImages.stream().filter(ci -> ci.getChapterName().equals(finalChapterId)).toList()),
                    targetChapter.getOffsetX(),
                    targetChapter.getOffsetY(),
                    targetChapter.getZoom());

            // Send the entire chapter object to the server to be saved
            ClientPacketDistributor.sendToServer(new SaveChapterPayload(data));
        }
    }

    /**
     * Performs a full database save. 
     * Mandatory after renaming Chapters or Groups to fix global dependency IDs.
     */
    public void saveAllChapters() {
        // 1. Rewrite every chapter file (updates Quest IDs and Dependencies on disk)
        for (SidebarEntry entry : sidebarEntries) {
            if (entry instanceof SidebarChapter ch) {
                saveChapterData(ch.getId());
            } else if (entry instanceof SidebarGroup group) {
                for (SidebarChapter ch : group.getChapters()) {
                    saveChapterData(ch.getId());
                }
            }
        }
        // 2. Sync the sidebar structure
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
                        .map(SidebarChapter::getId) // IMPORTANT: Send the internal ID
                        .toList();

                groupData.add(new QuestGroup(
                        Quest.sanitizePath(group.getTitle()),
                        group.getTitle(),
                        group.getTitleColor(),
                        i,
                        group.isExpanded(),
                        chaptersInGroup
                ));
            } else if (entry instanceof SidebarChapter chapter) {
                rootChapters.add(new SaveGroupsPayload.StandaloneChapterInfo(chapter.getId(), i)); // IMPORTANT: Send internal ID
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
            // Sync individual task counts into the local objects
            for (QuestTask task : quest.getTasks()) {
                int current = taskMap.getOrDefault(task.getId(), 0);
                task.setCurrentAmount(current);

                // FIX: Update task state based on current progress so icons reflect completion
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

        // Dim Background
        graphics.fill(0, 0, this.width, this.height, COL_DIM);

        graphics.fill(x, y, x + boxW, y + boxH, COL_UI_BG);
        drawBorder(graphics, x, y, boxW, boxH, COL_UI_BORDER);

        graphics.centeredText(font, Component.literal("Submit Items"), x + boxW / 2, y + 8, COL_TEXT);

        int currentAmount = SimplyQuestsClientPacketHandler.CLIENT_TASK_PROGRESS.getOrDefault(submittingTask.getId(), 0);
        boolean isHovered = mouseX >= x + (boxW / 2) - 10 && mouseX <= x + (boxW / 2) + 10 && mouseY >= y + 25 && mouseY <= y + 45;
        int innerColor = isHovered ? COL_PANEL_HEADER : COL_UI_BG;
        // Icon
        editorUI.drawTaskIcon(graphics, submittingTask, currentAmount, innerColor, x + (boxW / 2) - 10, y + 25, mouseX, mouseY, 20);

        String progress = currentAmount + " / " + submittingTask.getRequiredAmount();
        graphics.centeredText(font, Component.literal(progress), x + boxW / 2, y + 50, COL_TEXT);

        int btnW = 50;
        int btnH = 16;
        int btnY = y + boxH - 25;

        // Cancel Button
        int cancelX = x + 20;
        int cancelColor = editorUI.getButtonColor(mouseX, mouseY, cancelX, btnY, btnW, btnH, COL_BUTTON_BASE);
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelColor);
        graphics.centeredText(font, Component.literal("Cancel"), cancelX + btnW / 2, btnY + 4, COL_TEXT);

        // Submit Button
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

        // Cancel
        if (mouseX >= x + 20 && mouseX <= x + 20 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.isItemSubmissionOpen = false;
            this.submittingTask = null;
            playClickSound();
        }
        // Submit
        else if (mouseX >= x + boxW - 20 - btnW && mouseX <= x + boxW - 20 && mouseY >= btnY && mouseY <= btnY + btnH) {
            // Correct NeoForge 26.1 Static API for sending to server
            ClientPacketDistributor.sendToServer(new SubmitItemTaskPayload(selectedQuest.getId(), submittingTask.getId()));
            this.isItemSubmissionOpen = false;
            this.submittingTask = null;
            playClickSound();
        }
    }

    private void openTextEditor(CanvasText ct) {
        this.originalCanvasText = ct;
        // Simple clone for "Cancel" functionality
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
        int windowHeight = 145; // Increased to fit Color row
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        graphics.fill(x, y, x + windowWidth, y + windowHeight, COL_UI_BG);
        drawBorder(graphics, x, y, windowWidth, windowHeight, COL_UI_BORDER);
        graphics.centeredText(font, Component.literal("Edit Canvas Text"), x + windowWidth / 2, y + 10, COL_TEXT);

        // Text Input Row
        graphics.text(font, "Text:", x + 15, y + 35, COL_TEXT);
        editorUI.drawEditableText(graphics, editorUI.searchQuery, x + 50, y + 33, 135, 14, false);

        // Scale Row
        graphics.text(font, "Scale:", x + 15, y + 60, COL_TEXT);
        String scaleText = String.format("%.1fx", editingCanvasText.getScale());
        graphics.text(font, scaleText, x + 55, y + 60, COL_TEXT);

        // --- SLIDER LOGIC ---
        int sliderX = x + 90;
        int sliderW = 95;
        int sliderY = y + 64;

        float progress = (editingCanvasText.getScale() - 0.5f) / (5.0f - 0.5f);

        // Track
        graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 2, COL_INPUT_BG);
        graphics.fill(sliderX, sliderY, sliderX + (int)(progress * sliderW), sliderY + 2, COL_SLIDER_TRACK);

        // Handle
        int handleX = sliderX + (int)(progress * sliderW);
        graphics.fill(handleX - 2, sliderY - 3, handleX + 2, sliderY + 5, COL_UI_BORDER);

        // Color Row
        graphics.text(font, "Color:", x + 15, y + 85, COL_TEXT);
        // Preview box
        graphics.fill(x + 55, y + 83, x + 55 + 35, y + 83 + 12, COL_INPUT_BG);
        graphics.fill(x + 56, y + 84, x + 55 + 34, y + 83 + 11, editingCanvasText.getColor());
        graphics.outline(x + 55, y + 83, 35, 12, COL_UI_BORDER);

        editorUI.drawButton(graphics, mouseX, mouseY, x + 100, y + 82, 70, 14, "Change", COL_BUTTON_BASE);

        // Footer Buttons
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

        // Render Color Picker Popup LAST to ensure it is on top of footer buttons
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

        // --- Color Picker Logic (Priority) ---
        QuestEditorUI.PickerBounds pBounds = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, 135, 168, 16);
        boolean insidePicker = editorUI.isColorPickerOpen && mouseX >= pBounds.x() && mouseX <= pBounds.x() + pBounds.w() && mouseY >= pBounds.y() && mouseY <= pBounds.y() + pBounds.h();

        if (editorUI.isColorPickerOpen) {
            if (insidePicker) {
                // Check for "Submit" button click
                if (mouseX >= pBounds.x() + 5 && mouseX <= pBounds.x() + pBounds.w() - 5 && mouseY >= pBounds.y() + pBounds.h() - 18 && mouseY <= pBounds.y() + pBounds.h() - 4) {
                    commitPickerColor();
                    return;
                }

                // Check for Header Dragging (Top 16 pixels)
                if (mouseY < pBounds.y() + 16) {
                    this.isDraggingPickerWindow = true;
                    this.dragOffsetX = mouseX - pBounds.x();
                    this.dragOffsetY = mouseY - pBounds.y();
                    return;
                }

                // Handle Color Wheel interaction
                handlePickerColorChange(mouseX, mouseY, pBounds, null);
                return;
            } else {
                // Clicked outside the picker, check if we also clicked outside the main editor
                boolean insideMain = mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight;
                if (insideMain) {
                    editorUI.isColorPickerOpen = false;
                    playClickSound();
                }
            }
        }

        // Main Editor Bounds Check
        boolean insideMain = mouseX >= x && mouseX <= x + windowWidth && mouseY >= y && mouseY <= y + windowHeight;
        if (!insideMain) {
            editorUI.closePicker();
            isTextEditorOpen = false;
            editingCanvasText = null;
            originalCanvasText = null;
            return;
        }

        // Click inside the text box of the Canvas Text Editor to clear highlight
        if (mouseX >= x + 50 && mouseX <= x + 185 && mouseY >= y + 33 && mouseY <= y + 47) {
            editorUI.selectionStart = -1;
            editorUI.selectionEnd = -1;
            return;
        }

        // Scale Slider Check
        int sliderX = x + 90;
        int sliderW = 95;
        if (mouseX >= sliderX - 2 && mouseX <= sliderX + sliderW + 2 && mouseY >= y + 60 && mouseY <= y + 70) {
            this.isDraggingTextSizeSlider = true;
            return;
        }

        // Color Picker Toggle Button
        if (mouseX >= x + 100 && mouseX <= x + 170 && mouseY >= y + 82 && mouseY <= y + 96) {
            editorUI.isColorPickerOpen = !editorUI.isColorPickerOpen;
            if (editorUI.isColorPickerOpen) {
                this.pendingPickerColor = editingCanvasText.getColor();
                // Spawn picker next to the button and clamp to screen
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
        // Cancel/Save buttons
        if (mouseX >= x + windowWidth - 110 && mouseX <= x + windowWidth - 110 + 45 && mouseY >= btnY && mouseY <= btnY + 14) {
            editorUI.closePicker();
            isTextEditorOpen = false;
            editingCanvasText = null;
            originalCanvasText = null;
        }
        else if (mouseX >= x + windowWidth - 60 && mouseX <= x + windowWidth - 60 + 45 && mouseY >= btnY && mouseY <= btnY + 14) {
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

        // Check for hex box click using dynamic offsets
        if (mouseX >= b.x() + 35 && mouseX <= b.x() + 100 && mouseY >= pickerY + 70 + 8 && mouseY <= pickerY + 70 + 8 + 12) {
            editorUI.isHexEditing = true;
            editorUI.hexQuery = String.format("%06X", (this.pendingPickerColor & 0xFFFFFF));
            editorUI.hexCursorIndex = editorUI.hexQuery.length();
            playClickSound();
        } else {
            // Use content-aware mouse detection for Square, Hue, and Alpha
            boolean clickingSB = mouseX >= b.x() + 5 && mouseX <= b.x() + 75 && mouseY >= pickerY && mouseY <= pickerY + 70;
            boolean clickingHue = mouseX >= b.x() + 80 && mouseX <= b.x() + 92 && mouseY >= pickerY && mouseY <= pickerY + 70;

            // Use the full width for the slider hitbox
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

        // --- 3. ADD GRID SIZE SLIDER ROW ---
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

            // Track
            graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 2, COL_INPUT_BG);
            graphics.fill(sliderX, sliderY, sliderX + (int) (progress * sliderW), sliderY + 2, COL_SLIDER_TRACK);

            // Handle
            int handleX = sliderX + (int) (progress * sliderW);
            graphics.fill(handleX - 2, sliderY - 3, handleX + 2, sliderY + 5, COL_UI_BORDER);

            String valText = String.format("%.1f", GRID_SNAP);
            graphics.text(font, valText, sliderX + sliderW + 5, rowY + 5, COL_TEXT);
        }

        // FIX: Disable the list scissor AFTER the loop and grid row check, but BEFORE footer/pickers
        graphics.disableScissor();

        int footerY = y + windowHeight - 20;
        // Align Reset button to the left margin
        editorUI.drawButton(graphics, mouseX, mouseY, x + 10, footerY, 100, 14, "Reset Defaults", COL_BUTTON_BASE);

        // Save & Close: Widened to 120 and aligned to the right margin
        int saveX = x + windowWidth - 130;
        editorUI.drawButton(graphics, mouseX, mouseY, saveX, footerY, 120, 14, "Save & Close", COL_BUTTON_BASE);

        // Render Picker LAST in Settings pass
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

        // --- Color Picker Logic ---
        QuestEditorUI.PickerBounds pBounds = new QuestEditorUI.PickerBounds(this.pickerX, this.pickerY, 135, 168, 16);
        boolean insidePicker = editorUI.isColorPickerOpen && mouseX >= pBounds.x() && mouseX <= pBounds.x() + pBounds.w() && mouseY >= pBounds.y() && mouseY <= pBounds.y() + pBounds.h();

        if (editorUI.isColorPickerOpen) {
            if (insidePicker) {
                // Check for Header Dragging
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
                // Clicked outside picker, but check if we are inside main before closing
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

                // Position picker next to the clicked row and clamp to screen
                int pW = 135;
                int pH = 168;
                this.pickerX = rowX + 190;
                this.pickerY = rowY - 50;

                clampPickerPosition(pW, pH);

                playClickSound();
                return;
            }
        }

        // Check for Grid Slider click
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
        }
        else if (mouseX >= x + windowWidth - 130 && mouseX <= x + windowWidth - 10 && mouseY >= footerY && mouseY <= footerY + 14) {
            isSettingsOpen = false;
            editorUI.closePicker();
            saveThemeToConfig();
        }
    }

    private void clampPickerPosition(int pW, int pH) {
        // Ensure picker doesn't go off the left/top or right/bottom edges
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
            editorUI.selectionStart = 0; editorUI.selectionEnd = editorUI.searchQuery.length();
            playClickSound();
        } else if (field.equals("Command")) {
            editorUI.searchQuery = rewardToModify.getCommand();
            editorUI.isNameOpen = true; // Reuse isNameOpen for single-line command editing
            editorUI.cursorIndex = editorUI.searchQuery.length();
            editorUI.selectionStart = 0; editorUI.selectionEnd = editorUI.searchQuery.length();
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
        COL_STATE_PARTIAL = 0xFF55FFFF; // Brighter Sky Blue
        COL_STATE_COMPLETED = 0xFF55FF55;
        COL_SELECTION = 0x883399FF;
        COL_GRID = 0x0DFFFFFF;
        COL_DIM = 0xAA000000;
        COL_HOVER_UI = 0x28FFFFFF;
        COL_HOVER_MENU = 0x55FFFFFF;
        COL_TOOLTIP_BG = 0xF0101015;
        COL_SLIDER_TRACK = 0xFFAAAAAA;
        COL_GHOST_BORDER = 0x80505050; // Replaces previous (LOCKED | 0x80)
        COL_GHOST_FILL = 0x40FFFFFF;   // Replaces previous (BORDER | 0x40)
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
                    // Badge shows for ANY unclaimed reward, including choices
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
        // FIX: Pull from the Client Cache
        Map<Identifier, QuestChapter> chapterMap = SimplyQuestsClientPacketHandler.getChapters();

        if (chapterMap == null || chapterMap.isEmpty()) {
            anyClaimableCache = false;
            anyUnclaimedGeneralCache = false;
            return;
        }

        // Temporary variables to hold calculation
        boolean foundClaimable = false;
        boolean foundUnclaimed = false;

        // Iterate through the values of the Map (the QuestChapter objects)
        for (QuestChapter chapter : chapterMap.values()) {
            for (Quest quest : chapter.getQuests()) {
                if (SimplyQuestsClientPacketHandler.CLIENT_COMPLETED_QUESTS.contains(quest.getId())) {
                    for (QuestReward reward : quest.getRewards()) {
                        boolean claimed = SimplyQuestsClientPacketHandler.CLIENT_CLAIMED_REWARDS.contains(reward.getId());
                        if (!claimed) {
                            foundUnclaimed = true;
                            // "Claim All" logic applies ONLY to non-choice rewards
                            if (reward.getSubRewards().isEmpty()) {
                                foundClaimable = true;
                            }
                        }
                    }
                }
                if (foundClaimable && foundUnclaimed) break;
            }
            if (foundClaimable && foundUnclaimed) break;
        }

        // Apply the results to the static caches
        anyClaimableCache = foundClaimable;
        anyUnclaimedGeneralCache = foundUnclaimed;
    }

    private boolean checkPermissions() {
        if (this.minecraft.player == null) return false;

        // 1. Check the Integrated Server (Works for Single-player and LAN hosts)
        var localServer = this.minecraft.getSingleplayerServer();
        if (localServer != null) {
            NameAndId identity = new NameAndId(this.minecraft.player.getGameProfile().id(), this.minecraft.player.getGameProfile().name());
            return localServer.getPlayerList().isOp(identity);
        }

        // 2. Fallback for Dedicated Servers (Uses the synced status)
        return SimplyQuestsClientPacketHandler.IS_CLIENT_OP;
    }

    private void renderManipulationButton(GuiGraphicsExtractor graphics, int x, int y, Identifier icon, boolean active) {
        int bg = active ? COL_TEXT_GOLD : COL_PANEL_HEADER;
        graphics.fill(x, y, x + 15, y + 15, bg);
        graphics.outline(x, y, 15, 15, COL_UI_BORDER);

        int tint = active ? COL_UI_BG : COL_TEXT;
        
        // Draw icon centered within the 15x15 button with a small 2px padding
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 2, y + 2);
        // Scale the 16x16 texture to 11x11 area
        float iconScale = 11.0f / 16.0f;
        graphics.pose().scale(iconScale, iconScale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0f, 0.0f, 16, 16, 16, 16, tint);
        graphics.pose().popMatrix();
    }

    private void renderStandaloneAlphaSlider(GuiGraphicsExtractor graphics, QuestCanvasImage ci) {
        int sw = 100, sh = 10;
        int sx = -110, sy = 42; // Positioned near the Alpha button

        // 1. Draw black background for contrast
        graphics.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);

        // 2. Draw Alpha Gradient (White to Transparent)
        for (int i = 0; i < sw; i++) {
            float a = i / (float) sw;
            int color = ((int)(a * 255) << 24) | 0xFFFFFF;
            graphics.fill(sx + i, sy, sx + i + 1, sy + sh, color);
        }

        // 3. Draw Outline and Marker
        graphics.outline(sx, sy, sw, sh, COL_UI_BORDER);
        int markerX = sx + (int)(ci.getAlpha() * sw);
        graphics.fill(markerX - 1, sy - 2, markerX + 1, sy + sh + 2, COL_UI_BORDER);

        graphics.centeredText(font, Component.literal("Alpha: " + (int)(ci.getAlpha()*100) + "%"), sx + sw/2, sy - 10, COL_TEXT);
    }
}