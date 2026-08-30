package com.server.economy.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formatting helpers for timestamps shown in transaction history and logs.
 */
public final class TimeUtil {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    public static String formatForDisplay(Instant instant) {
        if (instant == null) {
            return "Unknown";
        }
        return DISPLAY_FORMAT.format(instant);
    }

    public static String formatRelative(Instant instant) {
        if (instant == null) {
            return "Unknown";
        }
        long seconds = Instant.now().getEpochSecond() - instant.getEpochSecond();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days + "d ago";
    }
}
