package com.abyss.orth.core.constant;

import java.util.Arrays;

/**
 * Block strategy enumeration for handling concurrent job triggers.
 *
 * <p>Defines how the executor should handle a new trigger when a previous execution of the same job
 * is still running.
 */
public enum ExecutorBlockStrategyEnum {
    /** Execute triggers serially, queueing new triggers until previous execution completes */
    SERIAL_EXECUTION("Serial execution"),

    /** Discard the new trigger if a previous execution is still running */
    DISCARD_LATER("Discard Later"),

    /** Terminate the previous execution and start the new trigger immediately */
    COVER_EARLY("Cover Early");

    private final String title;

    ExecutorBlockStrategyEnum(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Matches an enum constant by name with a fallback default.
     *
     * @param name the enum constant name to match
     * @param defaultItem the default value to return if no match is found
     * @return the matched enum constant or the default value
     */
    public static ExecutorBlockStrategyEnum match(
            String name, ExecutorBlockStrategyEnum defaultItem) {
        if (name == null) {
            return defaultItem;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(defaultItem);
    }
}
