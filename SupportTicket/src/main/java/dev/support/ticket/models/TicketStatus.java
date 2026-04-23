package dev.support.ticket.models;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Stato del ticket nel suo ciclo di vita.
 * OPEN -> CLAIMED -> CLOSED
 */
public enum TicketStatus {

    OPEN(NamedTextColor.YELLOW, "Aperto"),
    CLAIMED(NamedTextColor.AQUA, "In carico"),
    CLOSED(NamedTextColor.GRAY, "Chiuso");

    private final NamedTextColor color;
    private final String label;

    TicketStatus(NamedTextColor color, String label) {
        this.color = color;
        this.label = label;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public String getLabel() {
        return label;
    }
}
