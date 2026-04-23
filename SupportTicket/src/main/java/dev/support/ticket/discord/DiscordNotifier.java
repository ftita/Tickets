package dev.support.ticket.discord;

import dev.support.ticket.SupportTicketPlugin;
import dev.support.ticket.models.Priority;
import dev.support.ticket.models.Ticket;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Invia notifiche via Discord webhook.
 * Le richieste HTTP vengono eseguite in async per non bloccare il main thread.
 */
public class DiscordNotifier {

    private final SupportTicketPlugin plugin;

    public DiscordNotifier(SupportTicketPlugin plugin) {
        this.plugin = plugin;
    }

    public void notifyTicketCreated(int id, String player, Priority priority, String subject) {
        if (!isEnabled()) return;

        String title = "\uD83C\uDD95 Nuovo Ticket #" + id;
        String description = "**Giocatore:** " + escape(player) + "\n" +
                "**Priorità:** " + priority.name() + "\n" +
                "**Oggetto:** " + escape(subject);

        sendEmbed(title, description, priority.getDiscordColor());
    }

    public void notifyTicketClaimed(int id, String staff, Ticket ticket) {
        if (!isEnabled()) return;

        String title = "\u2705 Ticket #" + id + " preso in carico";
        String description = "**Staff:** " + escape(staff) + "\n" +
                "**Creatore:** " + escape(ticket.getCreatorName()) + "\n" +
                "**Oggetto:** " + escape(ticket.getSubject());

        sendEmbed(title, description, ticket.getPriority().getDiscordColor());
    }

    public void notifyTicketClosed(int id, String closedBy, Ticket ticket) {
        if (!isEnabled()) return;

        String title = "\uD83D\uDD12 Ticket #" + id + " chiuso";
        String description = "**Chiuso da:** " + escape(closedBy) + "\n" +
                "**Creatore:** " + escape(ticket.getCreatorName()) + "\n" +
                "**Oggetto:** " + escape(ticket.getSubject());

        sendEmbed(title, description, 9807270); // grigio
    }

    public void notifyReply(int id, String author, boolean staff, String message) {
        if (!isEnabled()) return;

        String title = "\uD83D\uDCAC Risposta al Ticket #" + id;
        String description = "**" + (staff ? "Staff" : "Giocatore") + ":** " + escape(author) + "\n" +
                "**Messaggio:** " + escape(message);

        sendEmbed(title, description, staff ? 3066993 : 3447003);
    }

    // ==============================================================
    // Low-level
    // ==============================================================

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    private void sendEmbed(String title, String description, int color) {
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("XXXX")) {
            return; // Webhook non configurato correttamente
        }

        String username = plugin.getConfig().getString("discord.username", "Support Tickets");

        String payload = """
                {
                  "username": "%s",
                  "embeds": [{
                    "title": "%s",
                    "description": "%s",
                    "color": %d,
                    "timestamp": "%s"
                  }]
                }
                """.formatted(
                escape(username),
                escape(title),
                escape(description),
                color,
                java.time.Instant.now().toString()
        );

        CompletableFuture.runAsync(() -> doPost(webhookUrl, payload));
    }

    private void doPost(String webhookUrl, String payload) {
        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "SupportTicket-Plugin/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 400) {
                plugin.getLogger().warning("Discord webhook ha risposto con codice " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Errore invio Discord webhook", e);
        }
    }

    /**
     * Escape dei caratteri speciali per JSON.
     * Semplificato: per uso produzione si consiglia Gson/Jackson, ma per un webhook
     * con testi controllati dal server va bene.
     */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
