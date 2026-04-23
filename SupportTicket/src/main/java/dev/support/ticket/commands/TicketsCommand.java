package dev.support.ticket.commands;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /tickets - shortcut che apre la GUI dello staff (o quella del giocatore se non è staff).
 */
public class TicketsCommand implements CommandExecutor {

    private final SupportTicketPlugin plugin;

    public TicketsCommand(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString("messages.player-only", "")));
            return true;
        }

        if (player.hasPermission("supportticket.staff")) {
            plugin.getGuiManager().openStaffList(player);
        } else {
            plugin.getGuiManager().openPlayerList(player);
        }
        return true;
    }
}
