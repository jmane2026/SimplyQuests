package com.jmane2026.simplyquests.quest;

public class QuestGlobalState {
    // This boolean is now safe to access from commands on the server
    // and from the GUI on the client.
    public static boolean isEditModeEnabled = false;
}