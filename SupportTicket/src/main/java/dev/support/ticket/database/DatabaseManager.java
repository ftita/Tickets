package dev.support.ticket.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.models.Priority;
import dev.support.ticket.models.Ticket;
import dev.support.ticket.models.TicketReply;
import dev.support.ticket.models.TicketStatus;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gestore del database SQLite tramite HikariCP.
 * Tutte le operazioni di I/O vanno eseguite in async (vedi TicketService).
 */
public class DatabaseManager {

    private final SupportTicketPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    // ==============================================================
    // Setup & teardown
    // ==============================================================

    public void init() {
        File dbFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("database.file", "tickets.db"));

        // Assicuriamoci che la cartella esista
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Impossibile creare la cartella del plugin.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 5));
        config.setPoolName("SupportTicketPool");
        // SQLite va trattato con guanti: una sola connessione scrive alla volta
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTables() {
        String ticketsTable = """
                CREATE TABLE IF NOT EXISTS tickets (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    creator_uuid    TEXT    NOT NULL,
                    creator_name    TEXT    NOT NULL,
                    subject         TEXT    NOT NULL,
                    priority        TEXT    NOT NULL,
                    status          TEXT    NOT NULL,
                    claimed_by      TEXT,
                    claimed_by_name TEXT,
                    created_at      INTEGER NOT NULL,
                    closed_at       INTEGER
                );
                """;

        String repliesTable = """
                CREATE TABLE IF NOT EXISTS ticket_replies (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id    INTEGER NOT NULL,
                    author_uuid  TEXT    NOT NULL,
                    author_name  TEXT    NOT NULL,
                    is_staff     INTEGER NOT NULL,
                    message      TEXT    NOT NULL,
                    created_at   INTEGER NOT NULL,
                    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
                );
                """;

        String indexCreator = "CREATE INDEX IF NOT EXISTS idx_tickets_creator ON tickets(creator_uuid);";
        String indexStatus = "CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);";
        String indexReplies = "CREATE INDEX IF NOT EXISTS idx_replies_ticket ON ticket_replies(ticket_id);";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ticketsTable);
            stmt.execute(repliesTable);
            stmt.execute(indexCreator);
            stmt.execute(indexStatus);
            stmt.execute(indexReplies);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore durante la creazione delle tabelle", e);
        }
    }

    // ==============================================================
    // CRUD Ticket
    // ==============================================================

    /**
     * Crea un nuovo ticket e ritorna l'ID generato.
     */
    public int createTicket(UUID creator, String creatorName, String subject, Priority priority) {
        String sql = """
                INSERT INTO tickets (creator_uuid, creator_name, subject, priority, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, creator.toString());
            ps.setString(2, creatorName);
            ps.setString(3, subject);
            ps.setString(4, priority.name());
            ps.setString(5, TicketStatus.OPEN.name());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore createTicket", e);
        }
        return -1;
    }

    public Optional<Ticket> getTicket(int id) {
        String sql = "SELECT * FROM tickets WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ticket ticket = mapTicket(rs);
                    ticket.getReplies().addAll(getReplies(id));
                    return Optional.of(ticket);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore getTicket", e);
        }
        return Optional.empty();
    }

    /**
     * Ritorna tutti i ticket aperti/claimed (non chiusi), ordinati per priorità desc e data asc.
     */
    public List<Ticket> getOpenTickets() {
        String sql = """
                SELECT * FROM tickets
                WHERE status != ?
                ORDER BY
                    CASE priority
                        WHEN 'URGENT' THEN 4
                        WHEN 'HIGH'   THEN 3
                        WHEN 'NORMAL' THEN 2
                        WHEN 'LOW'    THEN 1
                        ELSE 0
                    END DESC,
                    created_at ASC
                """;

        List<Ticket> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TicketStatus.CLOSED.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore getOpenTickets", e);
        }
        return list;
    }

    /**
     * Ritorna tutti i ticket (aperti e non) creati da un certo giocatore.
     */
    public List<Ticket> getTicketsByCreator(UUID creator) {
        String sql = "SELECT * FROM tickets WHERE creator_uuid = ? ORDER BY created_at DESC";
        List<Ticket> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, creator.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore getTicketsByCreator", e);
        }
        return list;
    }

    public int countOpenTicketsByCreator(UUID creator) {
        String sql = "SELECT COUNT(*) FROM tickets WHERE creator_uuid = ? AND status != ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, creator.toString());
            ps.setString(2, TicketStatus.CLOSED.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore countOpenTicketsByCreator", e);
        }
        return 0;
    }

    public void updateStatus(int ticketId, TicketStatus status, Long closedAt) {
        String sql = "UPDATE tickets SET status = ?, closed_at = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            if (closedAt == null) ps.setNull(2, java.sql.Types.BIGINT);
            else ps.setLong(2, closedAt);
            ps.setInt(3, ticketId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore updateStatus", e);
        }
    }

    public void updateClaim(int ticketId, UUID staffUuid, String staffName) {
        String sql = """
                UPDATE tickets
                SET claimed_by = ?, claimed_by_name = ?, status = ?
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (staffUuid == null) {
                ps.setNull(1, java.sql.Types.VARCHAR);
                ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setString(3, TicketStatus.OPEN.name());
            } else {
                ps.setString(1, staffUuid.toString());
                ps.setString(2, staffName);
                ps.setString(3, TicketStatus.CLAIMED.name());
            }
            ps.setInt(4, ticketId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore updateClaim", e);
        }
    }

    public void deleteTicket(int ticketId) {
        // Con ON DELETE CASCADE SQLite pulisce anche le reply
        String sql = "DELETE FROM tickets WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore deleteTicket", e);
        }
    }

    // ==============================================================
    // CRUD Reply
    // ==============================================================

    public int addReply(int ticketId, UUID author, String authorName, boolean staff, String message) {
        String sql = """
                INSERT INTO ticket_replies (ticket_id, author_uuid, author_name, is_staff, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ticketId);
            ps.setString(2, author.toString());
            ps.setString(3, authorName);
            ps.setInt(4, staff ? 1 : 0);
            ps.setString(5, message);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore addReply", e);
        }
        return -1;
    }

    public List<TicketReply> getReplies(int ticketId) {
        String sql = "SELECT * FROM ticket_replies WHERE ticket_id = ? ORDER BY created_at ASC";
        List<TicketReply> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TicketReply(
                            rs.getInt("id"),
                            rs.getInt("ticket_id"),
                            UUID.fromString(rs.getString("author_uuid")),
                            rs.getString("author_name"),
                            rs.getInt("is_staff") == 1,
                            rs.getString("message"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore getReplies", e);
        }
        return list;
    }

    // ==============================================================
    // Helper privati
    // ==============================================================

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        String claimedByRaw = rs.getString("claimed_by");
        UUID claimedBy = claimedByRaw == null ? null : UUID.fromString(claimedByRaw);

        long closed = rs.getLong("closed_at");
        Long closedAt = rs.wasNull() ? null : closed;

        return new Ticket(
                rs.getInt("id"),
                UUID.fromString(rs.getString("creator_uuid")),
                rs.getString("creator_name"),
                rs.getString("subject"),
                Priority.fromString(rs.getString("priority")),
                TicketStatus.valueOf(rs.getString("status")),
                claimedBy,
                rs.getString("claimed_by_name"),
                rs.getLong("created_at"),
                closedAt
        );
    }
}
