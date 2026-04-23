package dev.support.ticket.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rappresenta un ticket di supporto.
 * Campi nullable: claimedBy/claimedByName (se nessuno lo ha preso in carico),
 * closedAt (se non ancora chiuso).
 */
public final class Ticket {

    private final int id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final String subject;
    private Priority priority;
    private TicketStatus status;
    private UUID claimedBy;
    private String claimedByName;
    private final long createdAt;
    private Long closedAt;
    private final List<TicketReply> replies;

    public Ticket(int id, UUID creatorUuid, String creatorName, String subject,
                  Priority priority, TicketStatus status,
                  UUID claimedBy, String claimedByName,
                  long createdAt, Long closedAt) {
        this.id = id;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.subject = subject;
        this.priority = priority;
        this.status = status;
        this.claimedBy = claimedBy;
        this.claimedByName = claimedByName;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.replies = new ArrayList<>();
    }

    // ---------- Getter ----------

    public int getId() {
        return id;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getSubject() {
        return subject;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public UUID getClaimedBy() {
        return claimedBy;
    }

    public String getClaimedByName() {
        return claimedByName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getClosedAt() {
        return closedAt;
    }

    public List<TicketReply> getReplies() {
        return replies;
    }

    // ---------- Setter (solo per i campi mutabili) ----------

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public void setClaimedBy(UUID claimedBy) {
        this.claimedBy = claimedBy;
    }

    public void setClaimedByName(String claimedByName) {
        this.claimedByName = claimedByName;
    }

    public void setClosedAt(Long closedAt) {
        this.closedAt = closedAt;
    }

    // ---------- Helper ----------

    public boolean isOpen() {
        return status != TicketStatus.CLOSED;
    }

    public boolean isClaimed() {
        return claimedBy != null;
    }
}
