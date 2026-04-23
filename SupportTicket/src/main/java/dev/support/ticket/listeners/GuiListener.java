package dev.support.ticket.listeners;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.gui.GuiManager;
import dev.support.ticket.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Intercetta i click nelle GUI del plugin e smista verso il GuiManager / TicketService.
 */
public class GuiListener implements Listener {

    private final SupportTicketPlugin plugin;

    public GuiListener(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        GuiManager.GuiType type = plugin.getGuiManager().getGuiType(top);
        if (type == null) return; // Non è una nostra GUI

        // Tutte le nostre GUI sono read-only
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(top)) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Bottoni azione (claim/close/back/reply-hint)
        String action = pdc.get(plugin.actionKey(), PersistentDataType.STRING);
        if (action != null) {
            handleAction(player, top, action, pdc);
            return;
        }

        // Click su un ticket: apri dettaglio
        Integer ticketId = pdc.get(plugin.ticketIdKey(), PersistentDataType.INTEGER);
        if (ticketId != null) {
            plugin.getGuiManager().openDetail(player, ticketId);
        }
    }

    private void handleAction(Player player, Inventory top, String action, PersistentDataContainer pdc) {
        Integer ticketId = pdc.get(plugin.ticketIdKey(), PersistentDataType.INTEGER);

        switch (action) {
            case "claim" -> {
                if (!player.hasPermission("supportticket.staff") || ticketId == null) return;
                plugin.getTicketService().claimTicket(ticketId, player).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.sendMessage(MessageUtil.format(
                                plugin.getConfig().getString("messages.prefix", "") +
                                        plugin.getConfig().getString("messages.ticket-claimed", ""),
                                "id", String.valueOf(ticketId)));
                        plugin.getGuiManager().refreshIfViewing(player.getUniqueId(), ticketId);
                    }
                });
            }
            case "close" -> {
                if (!player.hasPermission("supportticket.staff") || ticketId == null) return;
                plugin.getTicketService().closeTicket(ticketId, player.getName()).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.sendMessage(MessageUtil.format(
                                plugin.getConfig().getString("messages.prefix", "") +
                                        plugin.getConfig().getString("messages.ticket-closed", ""),
                                "id", String.valueOf(ticketId)));
                        player.closeInventory();
                    }
                });
            }
            case "reply-hint" -> {
                if (ticketId == null) return;
                player.closeInventory();
                player.sendMessage(MessageUtil.colorize(
                        plugin.getConfig().getString("messages.prefix", "") +
                                "&7Usa &f/ticket reply " + ticketId + " <messaggio>"));
            }
            case "back" -> {
                if (player.hasPermission("supportticket.staff")) {
                    plugin.getGuiManager().openStaffList(player);
                } else {
                    plugin.getGuiManager().openPlayerList(player);
                }
            }
            default -> {
                // azione non gestita, ignoriamo
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        plugin.getGuiManager().unregister(event.getView().getTopInventory());
    }
}
