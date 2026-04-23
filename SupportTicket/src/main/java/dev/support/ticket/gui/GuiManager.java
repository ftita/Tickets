package dev.support.ticket.gui;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.models.Priority;
import dev.support.ticket.models.Ticket;
import dev.support.ticket.utils.MessageUtil;
import dev.support.ticket.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestore delle GUI del plugin.
 *
 * Strategia:
 *  - tutte le GUI del plugin vengono registrate in una Map (inventario -> tipo GUI)
 *    così il listener può riconoscere quali inventari gestire
 *  - gli oggetti cliccabili hanno l'ID del ticket salvato in un PersistentDataContainer
 *    (namespaced key "ticket_id")
 */
public class GuiManager {

    public enum GuiType { STAFF_LIST, PLAYER_LIST, TICKET_DETAIL }

    private final SupportTicketPlugin plugin;

    // inventario -> tipo di GUI. IdentityHashMap style: comparo per reference.
    private final Map<Inventory, GuiType> openGuis = new HashMap<>();
    // inventario -> id ticket (per la vista dettaglio)
    private final Map<Inventory, Integer> detailViews = new HashMap<>();

    public GuiManager(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    // ==============================================================
    // Apertura GUI
    // ==============================================================

    public void openStaffList(Player staff) {
        plugin.getTicketService().getOpenTickets().thenAccept(tickets ->
                Bukkit.getScheduler().runTask(plugin, () -> buildStaffList(staff, tickets)));
    }

    public void openPlayerList(Player player) {
        plugin.getTicketService().getTicketsByCreator(player.getUniqueId()).thenAccept(tickets ->
                Bukkit.getScheduler().runTask(plugin, () -> buildPlayerList(player, tickets)));
    }

    public void openDetail(Player viewer, int ticketId) {
        plugin.getTicketService().getTicket(ticketId).thenAccept(opt ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (opt.isEmpty()) {
                        viewer.sendMessage(MessageUtil.format(
                                plugin.getConfig().getString("messages.prefix", "") +
                                        plugin.getConfig().getString("messages.ticket-not-found", ""),
                                "id", String.valueOf(ticketId)));
                        return;
                    }
                    buildDetail(viewer, opt.get());
                }));
    }

    // ==============================================================
    // Costruzione GUI
    // ==============================================================

    private void buildStaffList(Player staff, List<Ticket> tickets) {
        int rows = plugin.getConfig().getInt("gui.rows", 6);
        String title = plugin.getConfig().getString("gui.staff-title", "&8» &cGestione Ticket");

        Inventory inv = Bukkit.createInventory(null, rows * 9, MessageUtil.colorize(title));

        int slot = 0;
        int maxSlots = rows * 9 - 9; // ultima riga riservata a decorazioni
        for (Ticket t : tickets) {
            if (slot >= maxSlots) break;
            inv.setItem(slot++, createTicketItem(t, true));
        }

        // Decorazione ultima riga
        fillBottomRow(inv, rows);

        openGuis.put(inv, GuiType.STAFF_LIST);
        staff.openInventory(inv);
    }

    private void buildPlayerList(Player player, List<Ticket> tickets) {
        int rows = plugin.getConfig().getInt("gui.rows", 6);
        String title = plugin.getConfig().getString("gui.player-title", "&8» &aI tuoi Ticket");

        Inventory inv = Bukkit.createInventory(null, rows * 9, MessageUtil.colorize(title));

        int slot = 0;
        int maxSlots = rows * 9 - 9;
        for (Ticket t : tickets) {
            if (slot >= maxSlots) break;
            inv.setItem(slot++, createTicketItem(t, false));
        }

        fillBottomRow(inv, rows);

        openGuis.put(inv, GuiType.PLAYER_LIST);
        player.openInventory(inv);
    }

    private void buildDetail(Player viewer, Ticket ticket) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.colorize("&8» &eTicket #" + ticket.getId()));

        // Info ticket (slot 4 in alto)
        inv.setItem(4, createTicketItem(ticket, viewer.hasPermission("supportticket.staff")));

        // Replies come libri
        int slot = 18;
        for (var reply : ticket.getReplies()) {
            if (slot >= 45) break;
            ItemStack paper = new ItemStack(reply.isStaff() ? Material.WRITTEN_BOOK : Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text(
                    (reply.isStaff() ? "[STAFF] " : "[PLAYER] ") + reply.getAuthorName(),
                    reply.isStaff() ? NamedTextColor.AQUA : NamedTextColor.YELLOW
            ).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            // Spezzo il messaggio su più linee ogni 40 char
            for (String line : wrap(reply.getMessage(), 40)) {
                lore.add(Component.text(line, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text(TimeUtil.formatRelative(reply.getCreatedAt()), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            paper.setItemMeta(meta);
            inv.setItem(slot++, paper);
        }

        // Bottoni azione in fondo
        boolean isStaff = viewer.hasPermission("supportticket.staff");

        if (isStaff && ticket.isOpen()) {
            if (!ticket.isClaimed()) {
                inv.setItem(48, actionButton(Material.IRON_INGOT, "&aClaim Ticket",
                        "&7Clicca per prendere in carico", "claim", ticket.getId()));
            }
            inv.setItem(49, actionButton(Material.REDSTONE_BLOCK, "&cChiudi Ticket",
                    "&7Clicca per chiudere il ticket", "close", ticket.getId()));
            inv.setItem(50, actionButton(Material.WRITABLE_BOOK, "&bRispondi",
                    "&7Usa &f/ticket reply " + ticket.getId() + " <messaggio>",
                    "reply-hint", ticket.getId()));
        }

        // Back button
        inv.setItem(45, actionButton(Material.ARROW, "&7« Indietro",
                "&7Torna alla lista", "back", -1));

        openGuis.put(inv, GuiType.TICKET_DETAIL);
        detailViews.put(inv, ticket.getId());
        viewer.openInventory(inv);
    }

    // ==============================================================
    // Helpers
    // ==============================================================

    private ItemStack createTicketItem(Ticket t, boolean staffView) {
        ItemStack item = new ItemStack(t.getPriority().getIcon());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Ticket #" + t.getId(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Creatore: ", NamedTextColor.GRAY, t.getCreatorName(), NamedTextColor.WHITE));
        lore.add(line("Priorità: ", NamedTextColor.GRAY, t.getPriority().name(), t.getPriority().getColor()));
        lore.add(line("Stato: ", NamedTextColor.GRAY, t.getStatus().getLabel(), t.getStatus().getColor()));
        if (t.isClaimed()) {
            lore.add(line("In carico a: ", NamedTextColor.GRAY, t.getClaimedByName(), NamedTextColor.AQUA));
        }
        lore.add(line("Creato: ", NamedTextColor.GRAY,
                TimeUtil.formatRelative(t.getCreatedAt()), NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("Oggetto:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        for (String wrapped : wrap(t.getSubject(), 40)) {
            lore.add(Component.text(wrapped, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("» Clicca per aprire", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // Salvo l'ID nel PersistentDataContainer così il listener sa qual è il ticket
        meta.getPersistentDataContainer().set(
                plugin.ticketIdKey(),
                PersistentDataType.INTEGER,
                t.getId()
        );

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionButton(Material material, String name, String lore, String action, int ticketId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.colorize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MessageUtil.colorize(lore).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(
                plugin.actionKey(), PersistentDataType.STRING, action);
        if (ticketId > 0) {
            meta.getPersistentDataContainer().set(
                    plugin.ticketIdKey(), PersistentDataType.INTEGER, ticketId);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void fillBottomRow(Inventory inv, int rows) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);

        int start = (rows - 1) * 9;
        for (int i = start; i < rows * 9; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    private Component line(String label, NamedTextColor labelColor, String value, NamedTextColor valueColor) {
        return Component.text(label, labelColor)
                .append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > width) {
                lines.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(word).append(" ");
        }
        if (!current.isEmpty()) lines.add(current.toString().trim());
        return lines;
    }

    // ==============================================================
    // API per il listener
    // ==============================================================

    public GuiType getGuiType(Inventory inv) {
        return openGuis.get(inv);
    }

    public Integer getDetailTicketId(Inventory inv) {
        return detailViews.get(inv);
    }

    public void unregister(Inventory inv) {
        openGuis.remove(inv);
        detailViews.remove(inv);
    }

    /**
     * Se un giocatore è nella GUI del ticket X, la ricarica automaticamente
     * (utile dopo claim / reply via comando).
     */
    public void refreshIfViewing(UUID viewer, int ticketId) {
        Player p = Bukkit.getPlayer(viewer);
        if (p == null) return;
        Inventory top = p.getOpenInventory().getTopInventory();
        Integer viewingId = detailViews.get(top);
        if (viewingId != null && viewingId == ticketId) {
            openDetail(p, ticketId);
        }
    }
}
