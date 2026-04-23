package dev.support.ticket.commands;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.models.Priority;
import dev.support.ticket.models.Ticket;
import dev.support.ticket.models.TicketReply;
import dev.support.ticket.utils.MessageUtil;
import dev.support.ticket.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ticket <create|close|list|claim|info|reply|gui|reload|delete>
 *
 * Tutti i sottocomandi sono instradati qui e la tab-completion è contestuale al permesso.
 */
public class TicketCommand implements CommandExecutor, TabCompleter {

    private final SupportTicketPlugin plugin;

    public TicketCommand(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    // ==============================================================
    // Dispatch
    // ==============================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create", "open", "new" -> handleCreate(sender, args);
            case "close" -> handleClose(sender, args);
            case "list" -> handleList(sender);
            case "claim" -> handleClaim(sender, args);
            case "info", "view" -> handleInfo(sender, args);
            case "reply" -> handleReply(sender, args);
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            case "delete" -> handleDelete(sender, args);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ==============================================================
    // Sottocomandi
    // ==============================================================

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("messages.player-only"));
            return;
        }
        if (!player.hasPermission("supportticket.use")) {
            player.sendMessage(msg("messages.no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(msg("messages.usage-create"));
            return;
        }

        Priority priority = Priority.fromString(args[1]);
        // Tutto dal terzo argomento in poi è il messaggio
        String subject = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        plugin.getTicketService().createTicket(player, priority, subject).thenAccept(result -> {
            if (result == -2) {
                long cooldown = plugin.getConfig().getLong("settings.create-cooldown-seconds", 60);
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.cooldown-active", ""),
                        "seconds", String.valueOf(cooldown)));
            } else if (result == -3) {
                int max = plugin.getConfig().getInt("settings.max-open-tickets-per-player", 3);
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.max-tickets-reached", ""),
                        "max", String.valueOf(max)));
            } else if (result > 0) {
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-created", ""),
                        "id", String.valueOf(result)));
            } else {
                player.sendMessage(Component.text("Errore interno durante la creazione del ticket.",
                        NamedTextColor.RED));
            }
        });
    }

    private void handleClose(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("messages.usage-close"));
            return;
        }
        Integer id = parseInt(args[1]);
        if (id == null) {
            sender.sendMessage(msg("messages.usage-close"));
            return;
        }

        // Il giocatore può chiudere i propri ticket; lo staff qualunque ticket
        plugin.getTicketService().getTicket(id).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-not-found", ""),
                        "id", String.valueOf(id)));
                return;
            }
            Ticket t = opt.get();

            if (sender instanceof Player p) {
                boolean owner = p.getUniqueId().equals(t.getCreatorUuid());
                boolean staff = p.hasPermission("supportticket.staff");
                if (!owner && !staff) {
                    p.sendMessage(msg("messages.ticket-not-yours"));
                    return;
                }
            }

            String closerName = sender instanceof Player p ? p.getName() : "Console";
            plugin.getTicketService().closeTicket(id, closerName).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    sender.sendMessage(MessageUtil.format(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    plugin.getConfig().getString("messages.ticket-closed", ""),
                            "id", String.valueOf(id)));
                }
            });
        });
    }

    private void handleList(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("supportticket.staff")) {
            // Il giocatore vede solo i suoi ticket
            plugin.getTicketService().getTicketsByCreator(p.getUniqueId()).thenAccept(list ->
                    printList(sender, list, false));
        } else {
            // Staff o console: tutti i ticket aperti
            plugin.getTicketService().getOpenTickets().thenAccept(list ->
                    printList(sender, list, true));
        }
    }

    private void handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("messages.player-only"));
            return;
        }
        if (!player.hasPermission("supportticket.staff")) {
            player.sendMessage(msg("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(msg("messages.usage-claim"));
            return;
        }
        Integer id = parseInt(args[1]);
        if (id == null) {
            player.sendMessage(msg("messages.usage-claim"));
            return;
        }

        plugin.getTicketService().claimTicket(id, player).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-claimed", ""),
                        "id", String.valueOf(id)));
            } else {
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-not-found", ""),
                        "id", String.valueOf(id)));
            }
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("messages.usage-info"));
            return;
        }
        Integer id = parseInt(args[1]);
        if (id == null) {
            sender.sendMessage(msg("messages.usage-info"));
            return;
        }

        plugin.getTicketService().getTicket(id).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-not-found", ""),
                        "id", String.valueOf(id)));
                return;
            }
            Ticket t = opt.get();

            // Se giocatore e non staff, può vedere solo i propri
            if (sender instanceof Player p &&
                    !p.hasPermission("supportticket.staff") &&
                    !p.getUniqueId().equals(t.getCreatorUuid())) {
                p.sendMessage(msg("messages.ticket-not-yours"));
                return;
            }

            printTicketInfo(sender, t);
        });
    }

    private void handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("messages.player-only"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(msg("messages.usage-reply"));
            return;
        }
        Integer id = parseInt(args[1]);
        if (id == null) {
            player.sendMessage(msg("messages.usage-reply"));
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        plugin.getTicketService().getTicket(id).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(MessageUtil.format(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.ticket-not-found", ""),
                        "id", String.valueOf(id)));
                return;
            }
            Ticket t = opt.get();
            boolean staff = player.hasPermission("supportticket.staff");
            boolean owner = player.getUniqueId().equals(t.getCreatorUuid());
            if (!staff && !owner) {
                player.sendMessage(msg("messages.ticket-not-yours"));
                return;
            }
            if (!t.isOpen()) {
                player.sendMessage(MessageUtil.colorize(
                        plugin.getConfig().getString("messages.prefix", "") +
                                "&cQuesto ticket è chiuso."));
                return;
            }

            plugin.getTicketService().addReply(id, player, staff, message).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    player.sendMessage(MessageUtil.colorize(
                            plugin.getConfig().getString("messages.prefix", "") +
                                    "&aRisposta inviata al ticket &e#" + id));
                }
            });
        });
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("messages.player-only"));
            return;
        }
        if (player.hasPermission("supportticket.staff")) {
            plugin.getGuiManager().openStaffList(player);
        } else {
            plugin.getGuiManager().openPlayerList(player);
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("supportticket.admin")) {
            sender.sendMessage(msg("messages.no-permission"));
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(msg("messages.reload-success"));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("supportticket.admin")) {
            sender.sendMessage(msg("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.colorize("&cUso: /ticket delete <id>"));
            return;
        }
        Integer id = parseInt(args[1]);
        if (id == null) return;
        plugin.getTicketService().deleteTicket(id).thenRun(() ->
                sender.sendMessage(MessageUtil.colorize(
                        plugin.getConfig().getString("messages.prefix", "") +
                                "&aTicket &e#" + id + " &aeliminato definitivamente.")));
    }

    // ==============================================================
    // Helper output
    // ==============================================================

    private void printList(CommandSender sender, List<Ticket> tickets, boolean staffView) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        if (tickets.isEmpty()) {
            sender.sendMessage(MessageUtil.colorize(prefix +
                    (staffView ? "&7Nessun ticket aperto." : "&7Non hai nessun ticket.")));
            return;
        }
        sender.sendMessage(MessageUtil.colorize("&8&m-----&r " +
                (staffView ? "&cTicket Aperti" : "&aI tuoi Ticket") + " &8&m-----"));
        for (Ticket t : tickets) {
            String line = "&e#" + t.getId() + " &7| &" + priorityColorCode(t.getPriority()) +
                    t.getPriority().name() + " &7| &f" + t.getCreatorName() +
                    " &7| &7" + TimeUtil.formatRelative(t.getCreatedAt()) +
                    "\n  &8» &f" + truncate(t.getSubject(), 60);
            sender.sendMessage(MessageUtil.colorize(line));
        }
    }

    private void printTicketInfo(CommandSender sender, Ticket t) {
        sender.sendMessage(MessageUtil.colorize("&8&m-----&r &eTicket #" + t.getId() + " &8&m-----"));
        sender.sendMessage(MessageUtil.colorize("&7Creatore: &f" + t.getCreatorName()));
        sender.sendMessage(MessageUtil.colorize("&7Priorità: &" +
                priorityColorCode(t.getPriority()) + t.getPriority().name()));
        sender.sendMessage(MessageUtil.colorize("&7Stato: &f" + t.getStatus().getLabel()));
        if (t.isClaimed()) {
            sender.sendMessage(MessageUtil.colorize("&7In carico a: &b" + t.getClaimedByName()));
        }
        sender.sendMessage(MessageUtil.colorize("&7Creato: &f" + TimeUtil.formatAbsolute(t.getCreatedAt())));
        if (t.getClosedAt() != null) {
            sender.sendMessage(MessageUtil.colorize("&7Chiuso: &f" + TimeUtil.formatAbsolute(t.getClosedAt())));
        }
        sender.sendMessage(MessageUtil.colorize("&7Oggetto: &f" + t.getSubject()));

        if (!t.getReplies().isEmpty()) {
            sender.sendMessage(MessageUtil.colorize("&7&m---&r &6Risposte &7&m---"));
            for (TicketReply r : t.getReplies()) {
                String who = r.isStaff() ? "&a[STAFF] " : "&b[PLAYER] ";
                sender.sendMessage(MessageUtil.colorize(who + "&f" + r.getAuthorName() +
                        " &8(" + TimeUtil.formatRelative(r.getCreatedAt()) + ")&7: &f" + r.getMessage()));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        boolean staff = sender.hasPermission("supportticket.staff");
        sender.sendMessage(MessageUtil.colorize("&8&m-----&r &cSupportTicket Help &8&m-----"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket create <priorità> <messaggio> &7- Crea un ticket"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket list &7- Lista dei ticket"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket info <id> &7- Dettagli ticket"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket reply <id> <messaggio> &7- Rispondi a un ticket"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket close <id> &7- Chiudi un ticket"));
        sender.sendMessage(MessageUtil.colorize("&e/ticket gui &7- Apri la GUI"));
        if (staff) {
            sender.sendMessage(MessageUtil.colorize("&c/ticket claim <id> &7- Prendi in carico"));
        }
        if (sender.hasPermission("supportticket.admin")) {
            sender.sendMessage(MessageUtil.colorize("&c/ticket reload &7- Ricarica config"));
            sender.sendMessage(MessageUtil.colorize("&c/ticket delete <id> &7- Elimina ticket"));
        }
    }

    // ==============================================================
    // Tab completion
    // ==============================================================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "create", "close", "list", "info", "reply", "gui", "help"));
            if (sender.hasPermission("supportticket.staff")) subs.add("claim");
            if (sender.hasPermission("supportticket.admin")) {
                subs.add("reload");
                subs.add("delete");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("create")) {
                return filter(Arrays.stream(Priority.values()).map(Enum::name).toList(), args[1]);
            }
            if (sub.equals("close") || sub.equals("info") || sub.equals("reply") ||
                    sub.equals("claim") || sub.equals("delete")) {
                // Suggerisce ID ticket aperti: qui non andiamo sul DB per performance,
                // lasciamo libero l'input.
                return List.of("<id>");
            }
        }
        return List.of();
    }

    // ==============================================================
    // Utility
    // ==============================================================

    private Component msg(String path) {
        return MessageUtil.colorize(
                plugin.getConfig().getString("messages.prefix", "") +
                        plugin.getConfig().getString(path, ""));
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private char priorityColorCode(Priority p) {
        return switch (p) {
            case LOW -> '9';
            case NORMAL -> 'a';
            case HIGH -> '6';
            case URGENT -> 'c';
        };
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
