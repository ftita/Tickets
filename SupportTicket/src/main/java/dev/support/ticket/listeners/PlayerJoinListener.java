package dev.support.ticket.listeners;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Al login:
 *  - se lo staff ha ticket aperti non claimati, glielo ricorda
 *  - se il giocatore ha ticket aperti, glielo ricorda
 */
public class PlayerJoinListener implements Listener {

    private final SupportTicketPlugin plugin;

    public PlayerJoinListener(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Notifica staff: ticket aperti ma non claimati
        if (player.hasPermission("supportticket.staff")) {
            plugin.getTicketService().getOpenTickets().thenAccept(tickets -> {
                long unclaimed = tickets.stream().filter(t -> !t.isClaimed()).count();
                if (unclaimed > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            player.sendMessage(MessageUtil.colorize(
                                    plugin.getConfig().getString("messages.prefix", "") +
                                            "&eCi sono &c" + unclaimed + " &eticket non assegnati. &7Usa &f/tickets")),
                            40L); // 2 secondi di delay dopo il join
                }
            });
        }

        // Notifica player: ticket aperti propri
        if (player.hasPermission("supportticket.use")) {
            plugin.getTicketService().getTicketsByCreator(player.getUniqueId()).thenAccept(tickets -> {
                long open = tickets.stream().filter(t -> t.isOpen()).count();
                if (open > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            player.sendMessage(MessageUtil.colorize(
                                    plugin.getConfig().getString("messages.prefix", "") +
                                            "&aHai &e" + open + " &aticket ancora aperti. &7Usa &f/ticket list")),
                            40L);
                }
            });
        }
    }
}
