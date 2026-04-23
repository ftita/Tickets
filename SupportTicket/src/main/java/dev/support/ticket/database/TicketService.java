package dev.support.ticket.database;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.discord.DiscordNotifier;
import dev.support.ticket.models.Priority;
import dev.support.ticket.models.Ticket;
import dev.support.ticket.models.TicketReply;
import dev.support.ticket.models.TicketStatus;
import dev.support.ticket.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Layer di servizio sopra il DatabaseManager.
 * Si occupa di:
 *  - eseguire le query in async
 *  - gestire cooldown anti-spam
 *  - notificare staff / Discord
 *  - applicare i limiti della config (max ticket aperti per giocatore)
 */
public class TicketService {

    private final SupportTicketPlugin plugin;
    private final DatabaseManager database;
    private final DiscordNotifier discord;
    private final Executor executor;

    // player UUID -> timestamp ultimo ticket creato
    private final ConcurrentHashMap<UUID, Long> createCooldowns = new ConcurrentHashMap<>();

    public TicketService(SupportTicketPlugin plugin, DatabaseManager database, DiscordNotifier discord) {
        this.plugin = plugin;
        this.database = database;
        this.discord = discord;
        // Thread pool dedicato, così non intasiamo il main thread e nemmeno l'async scheduler di Bukkit
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SupportTicket-DB");
            t.setDaemon(true);
            return t;
        });
    }

    // ==============================================================
    // Creazione
    // ==============================================================

    /**
     * Verifica cooldown e limiti, poi crea il ticket in async.
     * Ritorna un CompletableFuture con l'ID o -1 in caso di errore generico,
     * -2 se in cooldown, -3 se ha superato il limite massimo.
     */
    public CompletableFuture<Integer> createTicket(Player creator, Priority priority, String subject) {
        UUID uuid = creator.getUniqueId();

        // Cooldown
        long cooldownSec = plugin.getConfig().getLong("settings.create-cooldown-seconds", 60);
        Long last = createCooldowns.get(uuid);
        if (last != null) {
            long elapsed = (System.currentTimeMillis() - last) / 1000;
            if (elapsed < cooldownSec) {
                return CompletableFuture.completedFuture(-2);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            // Limite ticket aperti
            int max = plugin.getConfig().getInt("settings.max-open-tickets-per-player", 3);
            if (database.countOpenTicketsByCreator(uuid) >= max) {
                return -3;
            }

            int id = database.createTicket(uuid, creator.getName(), subject, priority);
            if (id > 0) {
                createCooldowns.put(uuid, System.currentTimeMillis());
                // Notifica staff + Discord sul main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyStaffNewTicket(id, creator.getName(), priority, subject);
                });
                if (plugin.getConfig().getBoolean("discord.notify.on-create", true)) {
                    discord.notifyTicketCreated(id, creator.getName(), priority, subject);
                }
            }
            return id;
        }, executor);
    }

    // ==============================================================
    // Get & list
    // ==============================================================

    public CompletableFuture<Optional<Ticket>> getTicket(int id) {
        return CompletableFuture.supplyAsync(() -> database.getTicket(id), executor);
    }

    public CompletableFuture<List<Ticket>> getOpenTickets() {
        return CompletableFuture.supplyAsync(database::getOpenTickets, executor);
    }

    public CompletableFuture<List<Ticket>> getTicketsByCreator(UUID creator) {
        return CompletableFuture.supplyAsync(() -> database.getTicketsByCreator(creator), executor);
    }

    // ==============================================================
    // Claim / Close
    // ==============================================================

    public CompletableFuture<Boolean> claimTicket(int ticketId, Player staff) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Ticket> opt = database.getTicket(ticketId);
            if (opt.isEmpty() || !opt.get().isOpen()) return false;

            database.updateClaim(ticketId, staff.getUniqueId(), staff.getName());

            if (plugin.getConfig().getBoolean("discord.notify.on-claim", true)) {
                discord.notifyTicketClaimed(ticketId, staff.getName(), opt.get());
            }

            // Avvisa il creatore se online
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player creator = Bukkit.getPlayer(opt.get().getCreatorUuid());
                if (creator != null && creator.isOnline()) {
                    creator.sendMessage(MessageUtil.format(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    "&aIl tuo ticket &e#%id% &aè stato preso in carico da &b%staff%&a.",
                            "id", String.valueOf(ticketId),
                            "staff", staff.getName()
                    ));
                }
            });
            return true;
        }, executor);
    }

    public CompletableFuture<Boolean> closeTicket(int ticketId, String closedByName) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Ticket> opt = database.getTicket(ticketId);
            if (opt.isEmpty() || !opt.get().isOpen()) return false;

            database.updateStatus(ticketId, TicketStatus.CLOSED, System.currentTimeMillis());

            if (plugin.getConfig().getBoolean("discord.notify.on-close", true)) {
                discord.notifyTicketClosed(ticketId, closedByName, opt.get());
            }

            // Avvisa il creatore se online
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player creator = Bukkit.getPlayer(opt.get().getCreatorUuid());
                if (creator != null && creator.isOnline()) {
                    creator.sendMessage(MessageUtil.format(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    "&7Il tuo ticket &e#%id% &7è stato chiuso da &b%staff%&7.",
                            "id", String.valueOf(ticketId),
                            "staff", closedByName
                    ));
                }
            });
            return true;
        }, executor);
    }

    // ==============================================================
    // Reply
    // ==============================================================

    public CompletableFuture<Boolean> addReply(int ticketId, Player author, boolean staff, String message) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Ticket> opt = database.getTicket(ticketId);
            if (opt.isEmpty() || !opt.get().isOpen()) return false;

            database.addReply(ticketId, author.getUniqueId(), author.getName(), staff, message);

            if (plugin.getConfig().getBoolean("discord.notify.on-reply", false)) {
                discord.notifyReply(ticketId, author.getName(), staff, message);
            }

            // Inoltra la reply al destinatario se online
            Bukkit.getScheduler().runTask(plugin, () -> forwardReply(opt.get(), author, staff, message));
            return true;
        }, executor);
    }

    private void forwardReply(Ticket ticket, Player author, boolean fromStaff, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        if (fromStaff) {
            // Notifica il creatore
            Player creator = Bukkit.getPlayer(ticket.getCreatorUuid());
            if (creator != null && creator.isOnline()) {
                creator.sendMessage(MessageUtil.format(
                        prefix + plugin.getConfig().getString("messages.reply-from-staff", ""),
                        "staff", author.getName(),
                        "message", message
                ));
            }
        } else {
            // Notifica lo staff assegnato (o tutto lo staff online se nessuno è assegnato)
            if (ticket.isClaimed()) {
                Player staff = Bukkit.getPlayer(ticket.getClaimedBy());
                if (staff != null && staff.isOnline()) {
                    staff.sendMessage(MessageUtil.format(
                            prefix + plugin.getConfig().getString("messages.reply-from-player", ""),
                            "player", author.getName(),
                            "message", message
                    ));
                }
            } else {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("supportticket.staff"))
                        .forEach(p -> p.sendMessage(MessageUtil.format(
                                prefix + "&6[Ticket &e#" + ticket.getId() + "&6] " +
                                        plugin.getConfig().getString("messages.reply-from-player", ""),
                                "player", author.getName(),
                                "message", message
                        )));
            }
        }
    }

    // ==============================================================
    // Admin: delete
    // ==============================================================

    public CompletableFuture<Void> deleteTicket(int ticketId) {
        return CompletableFuture.runAsync(() -> database.deleteTicket(ticketId), executor);
    }

    // ==============================================================
    // Helper: notifica staff in game
    // ==============================================================

    private void notifyStaffNewTicket(int id, String playerName, Priority priority, String subject) {
        if (!plugin.getConfig().getBoolean("settings.notify-staff-on-create", true)) return;

        String msg = plugin.getConfig().getString("messages.prefix", "") +
                plugin.getConfig().getString("messages.new-ticket-staff", "");
        boolean sound = plugin.getConfig().getBoolean("settings.play-sound-on-new-ticket", true);
        String soundName = plugin.getConfig().getString("settings.sound-name", "BLOCK_NOTE_BLOCK_PLING");

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("supportticket.staff"))
                .forEach(p -> {
                    p.sendMessage(MessageUtil.format(msg,
                            "id", String.valueOf(id),
                            "player", playerName,
                            "priority", priority.name(),
                            "message", subject));
                    if (sound) {
                        try {
                            p.playSound(p.getLocation(),
                                    Sound.valueOf(soundName), 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {
                            // Suono non valido: ignoriamo senza rompere la notifica
                        }
                    }
                });
    }
}
