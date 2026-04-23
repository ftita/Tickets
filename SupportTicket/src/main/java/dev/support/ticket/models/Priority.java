package dev.support.ticket.models;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Livelli di priorità dei ticket.
 * Ogni priorità ha un colore Adventure, un materiale per la GUI e un colore decimale per Discord.
 */
public enum Priority {

    LOW(NamedTextColor.BLUE, Material.LIGHT_BLUE_WOOL, 3447003),
    NORMAL(NamedTextColor.GREEN, Material.LIME_WOOL, 3066993),
    HIGH(NamedTextColor.GOLD, Material.ORANGE_WOOL, 15844367),
    URGENT(NamedTextColor.RED, Material.RED_WOOL, 15158332);

    private final NamedTextColor color;
    private final Material icon;
    private final int discordColor;

    Priority(NamedTextColor color, Material icon, int discordColor) {
        this.color = color;
        this.icon = icon;
        this.discordColor = discordColor;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Material getIcon() {
        return icon;
    }

    public int getDiscordColor() {
        return discordColor;
    }

    /**
     * Parser case-insensitive che ritorna NORMAL come default.
     */
    public static Priority fromString(String input) {
        if (input == null) return NORMAL;
        try {
            return Priority.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    /**
     * Usato per ordinare i ticket: URGENT prima, LOW per ultimo.
     */
    public int weight() {
        return switch (this) {
            case URGENT -> 4;
            case HIGH -> 3;
            case NORMAL -> 2;
            case LOW -> 1;
        };
    }
}
