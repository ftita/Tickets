package dev.support.ticket.models;

import java.util.UUID;

/**
 * Una singola risposta/messaggio all'interno di un ticket.
 * Può essere inviata sia dal giocatore sia da un membro dello staff.
 */
public final class TicketReply {

    private final int id;
    private final int ticketId;
    private final UUID authorUuid;
    private final String authorName;
    private final boolean staff;
    private final String message;
    private final long createdAt;

    public TicketReply(int id, int ticketId, UUID authorUuid, String authorName,
                       boolean staff, String message, long createdAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.staff = staff;
        this.message = message;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getTicketId() {
        return ticketId;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public String getAuthorName() {
        return authorName;
    }

    public boolean isStaff() {
        return staff;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
