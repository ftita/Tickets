package dev.support.ticket.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility per trasformare stringhe con codici colore legacy (&a, &b, ...)
 * in Component Adventure usabili su Paper 1.21+.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
        // utility class
    }

    /**
     * Converte una stringa con codici '&' in un Component Adventure.
     */
    public static Component colorize(String input) {
        if (input == null) return Component.empty();
        return LEGACY.deserialize(input);
    }

    /**
     * Sostituisce placeholder %key% nella stringa prima della deserializzazione.
     * Esempio: format("Ticket %id% da %player%", "id", "5", "player", "Steve")
     */
    public static Component format(String template, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements deve avere un numero pari di elementi");
        }
        String result = template == null ? "" : template;
        for (int i = 0; i < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return colorize(result);
    }

    /**
     * Versione che restituisce la stringa colorata per contesti che non supportano Component
     * (es. titoli di inventario in alcune versioni o placeholder esterni).
     */
    public static String formatString(String template, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements deve avere un numero pari di elementi");
        }
        String result = template == null ? "" : template;
        for (int i = 0; i < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return result;
    }
}
