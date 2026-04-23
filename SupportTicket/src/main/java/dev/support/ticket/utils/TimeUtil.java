package dev.support.ticket.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility per formattare timestamp in modo human-friendly.
 */
public final class TimeUtil {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TimeUtil() {
        // utility class
    }

    /**
     * Formato assoluto: "22/04/2026 14:30"
     */
    public static String formatAbsolute(long epochMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return FORMATTER.format(dt);
    }

    /**
     * Formato relativo: "5 minuti fa", "2 ore fa", "3 giorni fa".
     */
    public static String formatRelative(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        if (diff < 0) diff = 0;

        long seconds = diff / 1000;
        if (seconds < 60) return seconds + " secondi fa";

        long minutes = seconds / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " minuto fa" : " minuti fa");

        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " ora fa" : " ore fa");

        long days = hours / 24;
        if (days < 30) return days + (days == 1 ? " giorno fa" : " giorni fa");

        long months = days / 30;
        if (months < 12) return months + (months == 1 ? " mese fa" : " mesi fa");

        long years = months / 12;
        return years + (years == 1 ? " anno fa" : " anni fa");
    }
}
