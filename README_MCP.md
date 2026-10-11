# MCP-Server

`hibiscus.ly.reports` kann Hibiscus-Daten über einen lokalen MCP-Server
bereitstellen. Der Server ist standardmäßig deaktiviert und muss bewusst
aktiviert werden.

## Lokale Freigabe und lokales Netzwerk

Der MCP-Server bindet standardmäßig nur an `127.0.0.1`. Damit ist er nur vom
lokalen Rechner erreichbar. Optional kann der Zugriff aus dem lokalen Netzwerk
aktiviert werden; dann bindet der Server an `0.0.0.0`. Die Einstellungen dafür
stehen im Tab **Freigabe lokal / lokales Netzwerk** unter **Tools -> MCP-Server...**.

Lokale Clients und Clients im LAN müssen bei jeder Anfrage den Header
`Authorization: Bearer <Passwort>` senden. Das gemeinsame Passwort wird
**unterhalb der Tabs** angezeigt und im Jameica-Wallet gespeichert. Für direkte
lokale und LAN-Verbindungen gibt es keine OAuth-Anmeldung. Bei LAN-Freigabe
werden Anfragen einschließlich des Bearer-Tokens über unverschlüsseltes HTTP
übertragen. Diese Freigabe sollte daher nur in vertrauenswürdigen Netzen
verwendet werden.

Aktivierung:

- Menü **Tools -> MCP-Server...** öffnen und den Tab
  **Freigabe lokal / lokales Netzwerk** wählen.
- **MCP-Server aktivieren** einschalten.
- Optional **Überweisungen anlegen** einschalten, wenn lokale
  SEPA-Überweisungsentwürfe per MCP angelegt werden sollen.
- Optional **Zugriff aus lokalem Netzwerk erlauben** einschalten.
- Port prüfen oder anpassen.
- Den angezeigten Endpoint und das gemeinsame Passwort als Bearer-Token in den
  MCP-Client eintragen.

Wenn der MCP-Server bereits läuft, wird ein Wechsel der LAN-Option erst nach
einem Neustart von Hibiscus aktiv. Dadurch wird vermieden, dass der laufende
Listener im Betrieb zwischen `127.0.0.1` und `0.0.0.0` neu gebunden werden
muss.

Im Dialog **Über Tools -> Log** erscheinen die letzten 1.000 Zugriffe auf
`/mcp` aus der aktuellen Hibiscus-Sitzung, auch abgewiesene Anfragen. Das Log
zeigt nur Zeitpunkt, Zugriffsweg, Methode oder Tool, Ergebnis und Dauer.
Argumente, Antworten, IP-Adressen und Zugangsdaten werden nicht protokolliert.
Nach einem Hibiscus-Neustart ist das Log leer.


## Client-Konfiguration (lokal)

Beispielwerte:

```text
Endpoint: http://127.0.0.1:37653/mcp
Header:   Authorization: Bearer <Passwort>
```

`<Passwort>` muss durch das unterhalb der Tabs angezeigte gemeinsame Passwort
ersetzt werden.

## Nutzung mit Codex

Für lokal gestartetes [Codex von ChatGPT](https://developers.openai.com/learn/docs-mcp)
auf demselben Rechner genügt die **lokale Freigabe**. Die Konfigurationsdatei
für das jeweilige Betriebssystem bearbeiten und folgenden Eintrag ergänzen:

| Betriebssystem | Konfigurationsdatei |
| --- | --- |
| Windows | `%USERPROFILE%\.codex\config.toml` |
| macOS | `~/.codex/config.toml` |
| Linux | `~/.codex/config.toml` |

```toml
[mcp_servers.hibiscus]
enabled = true
url = "http://127.0.0.1:37653/mcp"
http_headers = { Authorization = "Bearer TOKEN_AUS_DER_SOFTWARE" }
```

`TOKEN_AUS_DER_SOFTWARE` durch das im Hibiscus-MCP-Dialog angezeigte Passwort
ersetzen. `bearer_token_env_var` erwartet dagegen den **Namen einer
Umgebungsvariable**, nicht den Token selbst. Mit `http_headers` steht der Wert
direkt in der Konfiguration; die Datei sollte daher nur für den eigenen Benutzer
lesbar sein. Bei einem anderen MCP-Port die URL anpassen und Codex anschließend
neu starten. Die [OpenAI Docs zur Codex-Konfiguration](https://developers.openai.com/codex/config-reference)
beschreiben `http_headers` für statische HTTP-Header.


### LM Studio:

```json
{
  "mcpServers": {
    "hibiscus": {
      "url": "http://127.0.0.1:37653/mcp",
      "headers": {
        "Authorization": "Bearer <Passwort>"
      }
    }
  }
}
```

### LibreChat:

```yaml
mcpServers:
  hibiscus:
    type: streamable-http
    url: http://127.0.0.1:37653/mcp
    headers:
      Authorization: "Bearer <Passwort>"
```

### Claude Desktop: lokale Freigabe

[Claude Desktop](https://claude.com/de/download) kann den MCP-Server auf
demselben Rechner über `127.0.0.1` erreichen. Dafür genügt die **lokale
Freigabe**; weder die LAN-Freigabe noch ngrok sind erforderlich. Den MCP-Server
in Hibiscus aktivieren und die Claude-Desktop-Konfiguration von Hand bearbeiten:

| Betriebssystem | Konfigurationsdatei |
| --- | --- |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

Node.js mit `npx` muss installiert sein. Folgenden Eintrag in die JSON-Datei
übernehmen; `TOKEN_AUS_DER_SOFTWARE` durch das im Hibiscus-MCP-Dialog angezeigte
Passwort ersetzen. Wenn die Datei bereits einen `mcpServers`-Block enthält,
den Server darin ergänzen:

```json
{
  "mcpServers": {
    "Hibiscus Banking Server": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://127.0.0.1:37653/mcp",
        "--header",
        "Authorization:${HIBISCUS_MCP_HEADER}"
      ],
      "env": {
        "HIBISCUS_MCP_HEADER": "Bearer TOKEN_AUS_DER_SOFTWARE"
      }
    }
  }
}
```

Die Header-Variable vermeidet ein [bekanntes Problem mit Leerzeichen in
`mcp-remote`-Argumenten](https://github.com/punkpeye/mcp-remote#custom-headers)
unter Claude Desktop für Windows. Bei einem anderen
MCP-Port die URL entsprechend ändern. Anschließend Claude Desktop neu starten.
Die Konfigurationsdatei enthält das Passwort im Klartext und sollte nur für
den eigenen Benutzer lesbar sein.

## Konfiguration für globale Nutzung

Für eine globale Nutzung ist es nötig, dass der MCP-Server über den Dienst ngrok global freigegeben wird. Hierzu muss auf https://ngrok.com/ ein kostenfreier Account angelegt werden und unter "Your Authtoken" der Token kopiert werden.


Die mitgelieferten ngrok-Bibliotheken unterstützen Linux x86-64 und ARM64,
macOS x86-64 und ARM64 sowie Windows x86-64.


### Globale Freigabe mit ngrok

Im Tab **Globale Freigabe** kann optional ein ngrok Authtoken eingetragen werden.
Sobald der MCP-Server läuft, stellt das Plugin einen HTTPS-Tunnel zum lokalen
`127.0.0.1`-Listener her. Die LAN-Option ist dafür nicht erforderlich. Der
Dialog zeigt die von ngrok vergebene URL mit `/mcp` an. ngrok weist kostenlosen
Konten eine feste Dev-Domain zu. Die tatsächlich verwendete URL wird nach dem
Verbindungsaufbau im Dialog angezeigt.

**Warnung:** Bei aktivem Tunnel ist der MCP-Endpoint weltweit über das Internet
erreichbar. Die öffentliche URL ist kein Zugriffsschutz. Wer das gemeinsame
Passwort oder einen gültigen OAuth-Zugriffstoken besitzt, kann Hibiscus-Daten
abrufen. Wenn **Überweisungen anlegen** aktiviert ist, können berechtigte
Clients auch lokale Überweisungsentwürfe erstellen. 

Zum Beenden der globalen Freigabe das ngrok Authtoken
im Dialog entfernen und **Übernehmen** wählen. Der lokale MCP-Server funktioniert
auch dann weiter, wenn ngrok keine Verbindung herstellen kann.

### OAuth-Anmeldung

Öffentliche MCP-Clients können sich per OAuth anmelden. Im Client nur den
angezeigten öffentlichen Endpoint eintragen und dessen OAuth-Verbindung
starten. Der Client öffnet eine Anmeldeseite im Browser. Dort mit dem festen
Benutzernamen `hibiscus-mcp` und dem unterhalb der Tabs angezeigten gemeinsamen
Passwort anmelden und den angezeigten Client bestätigen. 


### Claude im Browser

Der MCP-Server kann wie im Artikel '[MCP-Connector in Claude im Browser](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)' 
beschrieben verbunden werden.

### ChatGPT
Der MCP-Server kann folgendermaßen hinzugefügt werden:
 * [Plugins](https://chatgpt.com/plugins)
 * Hinzufügen / Benutzerdefinierten MCP-Server hinzufügen 
 * Name: hibiscus
 * Beschreibung: MCP-Server für den Zugriff auf Bankdaten über Hibiscus
 * Verbindung: Die im ngrok-Tab angezeigte URL
 * Authentifizierung: OAuth

## Verfügbare Tools

| Tool | Zweck |
| --- | --- |
| `hibiscus_template_objects_list` | Top-Level-Objekte des Template-Kontexts auflisten |
| `hibiscus_template_render` | Jinjava-Template-String gegen aktuelle Daten rendern |
| `hibiscus_accounts_list` | Aktive oder alle Konten auflisten |
| `hibiscus_accounts_sync` | Konten über Hibiscus synchronisieren |
| `hibiscus_account_groups_list` | Aktive oder alle Kontogruppen auflisten |
| `hibiscus_categories_list` | Umsatzkategorien auflisten |
| `hibiscus_transactions_list` | Umsätze mit Zeitraum, Konto und Limit laden |
| `hibiscus_sepa_transfer_create` | Lokalen SEPA-Überweisungsentwurf anlegen |

Objekte, die andere Plugins per `hibiscus.ly.reports.template.context`
bereitstellen, sind im MCP-Kontext ebenfalls verfügbar.

## Konten auflisten

`hibiscus_accounts_list` nutzt standardmäßig aktive Konten. Mit
`scope: "all"` werden alle Konten geladen.

```json
{
  "scope": "all"
}
```

Rückgabefelder je Konto sind unter anderem:

- `id`
- `name`
- `bezeichnung`
- `iban`
- `kontonummer`
- `kundennummer`
- `kundenkennung`
- `gruppe`
- `backendClass`
- `saldo`
- `verfügbar`
- `aktiv`
- `offline`

## Konten synchronisieren

`hibiscus_accounts_sync` synchronisiert Konten über die registrierten
Hibiscus-Synchronisierungs-Backends. Die Funktion ist nicht auf HBCI
beschränkt.

Alle synchronisierbaren Konten:

```json
{
  "all": true
}
```

Gezielte Auswahl ist über einzelne Werte oder Listen möglich:

```json
{
  "ibans": ["DE02120300000000202051"],
  "backendClasses": [
    "de.gnampf.syncusgnampfus.scalablecapital.ScalablecapitalSynchronizeBackend"
  ]
}
```

Unterstützte Auswahlfelder:

- `accountId` / `accountIds`
- `iban` / `ibans`
- `kundennummer` / `kundennummern`
- `kundenkennung` / `kundenkennungen`
- `kontonummer` / `kontonummern`
- `bezeichnung` / `bezeichnungen`
- `backendClass` / `backendClasses`

Die Synchronisierung wartet, bis Hibiscus den Lauf beendet hat. Deaktivierte
oder nicht synchronisierbare Konten können von Hibiscus abgelehnt oder ohne
Jobs übersprungen werden.

## Umsätze laden

`hibiscus_transactions_list` kann global oder konto-spezifisch verwendet
werden.

```json
{
  "accountId": "17",
  "lastDays": 30,
  "limit": 50
}
```

Unterstützte Filter:

- `accountId`
- `all`
- `lastDays`
- `from`
- `to`
- `limit`

`from` und `to` verwenden das Format `YYYY-MM-DD`.

## Kategorien laden

`hibiscus_categories_list` liefert alle Umsatzkategorien als flache Liste mit
Hierarchiepfad, Notiz, Suchbegriff, Regex-Kennzeichen, Kategorieart,
Konto-/Gruppeneinschränkung, Ignorier-Markierungen und Farbe. Mit
`includeSkipped: false` werden auch Kategorien ausgeschlossen, die nur wegen
einer ignorierten Oberkategorie effektiv ignoriert sind.

Notizen und Suchmuster können private Informationen enthalten und werden nur
an Clients ausgegeben, die bereits Zugriff auf den authentifizierten MCP-Server
haben.

## SEPA-Zahlungsentwurf

`hibiscus_sepa_transfer_create` sendet keine Zahlung an die Bank. Es speichert
nur einen lokalen Entwurf in Hibiscus. Der Auftrag muss anschließend in
Hibiscus geprüft und manuell ausgeführt werden.

Das Tool funktioniert nur, wenn im MCP-Dialog **Überweisungen anlegen**
aktiviert wurde.

Pflichtfelder:

- `accountId`
- `recipientName`
- `recipientIban`
- `amount`

Optionale Felder:

- `recipientBic`
- `purpose`
- `purpose2`
- `additionalPurposes`
- `executionDate`
- `endToEndId`
- `pmtInfId`
- `purposeCode`
- `type`

Unterstützte Zahlungsarten:

- `Überweisung`
- `Terminüberweisung`
- `Interne Umbuchung`
- `Echtzeitüberweisung`

## Resources

Der Server stellt zusätzlich MCP-Resources bereit:

| URI | Inhalt |
| --- | --- |
| `hibiscus-reports://template-context` | Top-Level-Objekte im aktuellen Template-Kontext |
| `hibiscus-reports://report-objects-doc` | HTML-Hilfe zu Report-Objekten |
| `hibiscus-reports://mcp/providers` | Registrierte MCP-Tool-Provider anderer Plugins |

## Erweiterungen durch andere Plugins

Andere Plugins können eigene Template-Objekte und eigene strukturierte
MCP-Tools registrieren. Wenn zum Beispiel der Depotviewer installiert ist,
können dadurch Tools wie `depotviewer_depots_list`,
`depotviewer_portfolio_list` oder `depotviewer_orders_list` erscheinen.

Entwicklerdetails stehen in [DEVELOPMENT.md](DEVELOPMENT.md).
