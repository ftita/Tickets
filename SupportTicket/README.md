# SupportTicket

Plugin Minecraft per Paper 1.21+ che implementa un sistema completo di ticket di supporto.

## Funzionalità

- **Creazione ticket** con 4 priorità (LOW / NORMAL / HIGH / URGENT)
- **Sistema claim**: lo staff prende in carico un ticket
- **Risposte bidirezionali** tra giocatore e staff
- **GUI interattive** separate per staff e giocatori
- **Persistenza SQLite** via HikariCP (pool di connessioni)
- **Notifiche Discord** via webhook configurabile (create, claim, close, reply)
- **Cooldown anti-spam** e limite massimo di ticket aperti per giocatore
- **Tab-completion** contestuale ai permessi
- **Notifiche al login** per staff (ticket non assegnati) e giocatori (ticket aperti)

## Comandi

| Comando | Permesso | Descrizione |
|--------|----------|-------------|
| `/ticket create <priorità> <messaggio>` | `supportticket.use` | Crea un ticket |
| `/ticket list` | `supportticket.use` | Lista dei ticket (propri o tutti se staff) |
| `/ticket info <id>` | `supportticket.use` | Dettagli di un ticket |
| `/ticket reply <id> <messaggio>` | `supportticket.use` | Risponde a un ticket |
| `/ticket close <id>` | `supportticket.use` (owner) / `supportticket.staff` | Chiude un ticket |
| `/ticket claim <id>` | `supportticket.staff` | Prende in carico un ticket |
| `/ticket gui` | `supportticket.use` | Apre la GUI |
| `/ticket reload` | `supportticket.admin` | Ricarica config |
| `/ticket delete <id>` | `supportticket.admin` | Elimina un ticket |
| `/tickets` | `supportticket.staff` | Alias che apre direttamente la GUI |

## Permessi

- `supportticket.use` - default `true`
- `supportticket.staff` - default `op`
- `supportticket.admin` - default `op`
- `supportticket.*` - raggruppa tutti

## Build

Richiede **Java 21** e **Maven 3.8+**:

```bash
mvn clean package
```

Il JAR finale si trova in `target/SupportTicket-1.0.0.jar`. Copialo in `plugins/` del tuo server Paper 1.21+.

## Configurazione Discord

Nel file `config.yml`:

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/ID/TOKEN"
```

Imposta `enabled: true` e incolla l'URL del webhook. Puoi scegliere quali eventi notificare tramite la sezione `discord.notify`.

## Struttura progetto

```
dev.support.ticket
├── SupportTicketPlugin.java       # Main
├── commands/                      # /ticket, /tickets
├── database/                      # DatabaseManager + TicketService
├── discord/                       # DiscordNotifier (HTTP webhook)
├── gui/                           # GuiManager (staff/player/detail)
├── listeners/                     # GuiListener, PlayerJoinListener
├── models/                        # Ticket, TicketReply, Priority, Status
└── utils/                         # MessageUtil, TimeUtil
```

## Note tecniche

- Tutte le operazioni DB sono eseguite su un **thread pool dedicato** (non sul main thread né sullo scheduler async di Bukkit).
- HikariCP è **rilocato** sotto `dev.support.ticket.libs.hikari` per evitare conflitti con altri plugin.
- I click nelle GUI sono identificati tramite **PersistentDataContainer** (namespaced keys), non via display name.
- Il webhook Discord usa `HttpURLConnection` standard per evitare dipendenze extra.
