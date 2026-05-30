package com.jmane2026.simplyquests.client.screen;

import com.jmane2026.simplyquests.quest.Quest;

public enum FieldType {
    TOGGLE {
        @Override
        public void execute(Quest quest, String field) {
            if (field.equals("Optional")) quest.setOptional(!quest.isOptional());
            else if (field.equals("Repeatable")) quest.setRepeatable(!quest.isRepeatable());
        }
    },      // For boolean
    ICON_SELECT {
        @Override
        public void execute(Quest quest, String field) {

        }
    }, // For Icon
    QUEST_LIST {
        @Override
        public void execute(Quest quest, String field) {

        }
    },  // For Dependencies
    TEXT_INPUT {
        @Override
        public void execute(Quest quest, String field) {

        }
    };   // For Title, Description, etc.

    public abstract void execute(Quest quest, String field);

    // Map your labels to these types
    public static FieldType getFieldType(String label) {
        return switch (label) {
            case "Optional", "Repeatable" -> FieldType.TOGGLE;
            case "Icon" -> FieldType.ICON_SELECT;
            case "Dependencies" -> FieldType.QUEST_LIST;
            default -> FieldType.TEXT_INPUT;
        };
    }
}