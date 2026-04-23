package dev.support.ticket;

import dev.support.ticket.commands.TicketCommand;
import dev.support.ticket.commands.TicketsCommand;
import dev.support.ticket.database.DatabaseManager;
import dev.support.ticket.database.TicketService;
import dev.support.ticket.discord.DiscordNotifier;
import dev.support.ticket.gui.GuiManager;
import dev.support.ticket.listeners.GuiListener;
import dev.support.ticket.listeners.PlayerJoinListener;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Classe main del plugin SupportTicket.
 * Inizializza in ordine: config -> database -> service -> gui -> listeners -> commands.
 */
public final class SupportTicketPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private TicketService ticketService;
    private DiscordNotifier discordNotifier;
    private GuiManager guiManager;

    // Chiavi per il PersistentDataContainer degli ItemStack delle GUI
    private NamespacedKey ticketIdKey;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        // 1) Config
        saveDefaultConfig();

        // 2) Namespaced keys (vanno create dopo che il plugin è registrato)
        this.ticketIdKey = new NamespacedKey(this, "ticket_id");
        this.actionKey = new NamespacedKey(this, "action");

        // 3) Database
        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Impossibile inizializzare il database. Il plugin verrà disabilitato.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4) Discord & service
        this.discordNotifier = new DiscordNotifier(this);
        this.ticketService = new TicketService(this, databaseManager, discordNotifier);

        // 5) GUI
        this.guiManager = new GuiManager(this);

        // 6) Listeners
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // 7) Commands
        TicketCommand ticketCommand = new TicketCommand(this);
        PluginCommand tk = Objects.requireNonNull(getCommand("ticket"),
                "Comando 'ticket' non dichiarato in plugin.yml");
        tk.setExecutor(ticketCommand);
        tk.setTabCompleter(ticketCommand);

        PluginCommand tks = Objects.requireNonNull(getCommand("tickets"),
                "Comando 'tickets' non dichiarato in plugin.yml");
        tks.setExecutor(new TicketsCommand(this));

        getLogger().info("SupportTicket abilitato con successo.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("SupportTicket disabilitato.");
    }

    // ==============================================================
    // Accessor globali
    // ==============================================================

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TicketService getTicketService() {
        return ticketService;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public NamespacedKey ticketIdKey() {
        return ticketIdKey;
    }

    public NamespacedKey actionKey() {
        return actionKey;
    }
}
