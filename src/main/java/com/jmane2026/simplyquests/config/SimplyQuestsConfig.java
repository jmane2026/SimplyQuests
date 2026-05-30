package com.jmane2026.simplyquests.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SimplyQuestsConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<Integer> UI_BG;
    public static final ModConfigSpec.ConfigValue<Integer> UI_INNER_BG;
    public static final ModConfigSpec.ConfigValue<Integer> UI_BORDER;
    public static final ModConfigSpec.ConfigValue<Integer> SIDEBAR_BG;
    public static final ModConfigSpec.ConfigValue<Integer> SIDEBAR_BORDER;
    public static final ModConfigSpec.ConfigValue<Integer> PANEL_HEADER;
    public static final ModConfigSpec.ConfigValue<Integer> PANEL_DIVIDER;
    public static final ModConfigSpec.ConfigValue<Integer> BUTTON_BASE;
    public static final ModConfigSpec.ConfigValue<Integer> INPUT_BG;
    public static final ModConfigSpec.ConfigValue<Integer> TEXT;
    public static final ModConfigSpec.ConfigValue<Integer> TEXT_GOLD;
    public static final ModConfigSpec.ConfigValue<Integer> TEXT_SELECTED;
    public static final ModConfigSpec.ConfigValue<Integer> ERROR;
    public static final ModConfigSpec.ConfigValue<Integer> STATE_LOCKED;
    public static final ModConfigSpec.ConfigValue<Integer> STATE_AVAILABLE;
    public static final ModConfigSpec.ConfigValue<Integer> STATE_PARTIAL;
    public static final ModConfigSpec.ConfigValue<Integer> STATE_COMPLETED;
    public static final ModConfigSpec.ConfigValue<Integer> HOVER_UI;
    public static final ModConfigSpec.ConfigValue<Integer> HOVER_MENU;
    public static final ModConfigSpec.ConfigValue<Integer> SELECTION;
    public static final ModConfigSpec.ConfigValue<Integer> TOOLTIP_BG;
    public static final ModConfigSpec.ConfigValue<Integer> GRID;
    public static final ModConfigSpec.ConfigValue<Integer> DIM;
    public static final ModConfigSpec.ConfigValue<Integer> SLIDER_TRACK;

    public static final ModConfigSpec.ConfigValue<Integer> GHOST_BORDER;
    public static final ModConfigSpec.ConfigValue<Integer> GHOST_FILL;

    public static final ModConfigSpec.DoubleValue GRID_SIZE;

    // Button Position Configs
    public static final ModConfigSpec.IntValue BOOK_X;
    public static final ModConfigSpec.IntValue BOOK_Y;


    static {
        BUILDER.push("Colors");
        UI_BG = BUILDER.comment("Main UI Background Color (ARGB)").define("ui_bg", 0xFF222222);
        UI_INNER_BG = BUILDER.comment("Inner Input Background Color (ARGB)").define("ui_inner_bg", 0xFF111111);
        UI_BORDER = BUILDER.comment("UI Border Color (ARGB)").define("ui_border", 0xFFFFFFFF);
        SIDEBAR_BG = BUILDER.comment("Sidebar Background Color (ARGB)").define("sidebar_bg", 0xDD111115);
        SIDEBAR_BORDER = BUILDER.comment("Sidebar Border Color (ARGB)").define("sidebar_border", 0xFF3A3A43);
        PANEL_HEADER = BUILDER.comment("Panel Header Color (ARGB)").define("panel_header", 0xFF333333);
        PANEL_DIVIDER = BUILDER.comment("Divider and Line Color (ARGB)").define("panel_divider", 0xFF555555);
        BUTTON_BASE = BUILDER.comment("Default Button Color (ARGB)").define("button_base", 0xFF555555);
        INPUT_BG = BUILDER.comment("Text Input Field Background (ARGB)").define("input_bg", 0xFF000000);
        TEXT = BUILDER.comment("Primary Text Color (ARGB)").define("text", 0xFFFFFFFF);
        TEXT_GOLD = BUILDER.comment("Active/Gold Text Tone (ARGB)").define("text_gold", 0xFFFFFF00);
        TEXT_SELECTED = BUILDER.comment("Selection Highlight Text Color (ARGB)").define("text_selected", 0xFFFFFF55);
        ERROR = BUILDER.comment("Error and Delete Color (ARGB)").define("error", 0xFFFF5555);
        STATE_LOCKED = BUILDER.comment("Locked Quest Color (ARGB)").define("state_locked", 0xFF505050);
        STATE_AVAILABLE = BUILDER.comment("Available Quest Color (ARGB)").define("state_available", 0xFFB0B0B0);
        STATE_PARTIAL = BUILDER.comment("In-Progress Quest Color (ARGB)").define("state_partial", 0xFF55FFFF);
        STATE_COMPLETED = BUILDER.comment("Completed Quest Color (ARGB)").define("state_completed", 0xFF55FF55);
        HOVER_UI = BUILDER.comment("Subtle UI Element Hover Color (ARGB)").define("hover_ui", 0x28FFFFFF);
        HOVER_MENU = BUILDER.comment("Context Menu/List Hover Color (ARGB)").define("hover_menu", 0x55FFFFFF);
        SELECTION = BUILDER.comment("Text Selection Highlight Color (ARGB)").define("selection", 0x883399FF);
        TOOLTIP_BG = BUILDER.comment("Tooltip Background Color (ARGB)").define("tooltip_bg", 0xF0101015);
        GRID = BUILDER.comment("Canvas Grid Line Color (ARGB)").define("grid", 0x0DFFFFFF);
        DIM = BUILDER.comment("Modal Background Dimming Color (ARGB)").define("dim", 0xAA000000);
        SLIDER_TRACK = BUILDER.comment("Slider Track Background Color (ARGB)").define("slider_track", 0xFFAAAAAA);
        GHOST_BORDER = BUILDER.comment("Color of the ghost node border during movement").define("ghostBorder", 0x80505050);
        GHOST_FILL = BUILDER.comment("Color of the ghost node background during movement").define("ghostFill", 0x40FFFFFF);

        BUILDER.pop();

        BUILDER.push("Button Position");
        BOOK_X = BUILDER.comment("The X coordinate of the Quest Book button in the inventory (use -5000 for default)")
                .defineInRange("bookX", -5000, Integer.MIN_VALUE, Integer.MAX_VALUE);

        BOOK_Y = BUILDER.comment("The Y coordinate of the Quest Book button in the inventory (use -5000 for default)")
                .defineInRange("bookY", -5000, Integer.MIN_VALUE, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("Canvas");
        GRID_SIZE = BUILDER.comment("The default size of the grid and snapping increments (4.0 to 32.0)")
                .defineInRange("gridSize", 16.0, 4.0, 32.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
