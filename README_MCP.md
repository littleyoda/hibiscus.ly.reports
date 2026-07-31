# MCP-Server

`hibiscus.ly.reports` kann Hibiscus-Daten über einen lokalen MCP-Server
bereitstellen. Der Server ist standardmäßig deaktiviert und muss bewusst
aktiviert werden.

## Sicherheit und Aktivierung

Der MCP-Server bindet standardmäßig nur an `127.0.0.1`. Damit ist er nur vom
lokalen Rechner erreichbar. Optional kann der Zugriff aus dem lokalen Netzwerk
aktiviert werden; dann bindet der Server an `0.0.0.0`.

Jeder Request muss den Header `Authorization: Bearer <token>` enthalten. Das
Token wird im Jameica-Wallet gespeichert und kann im MCP-Dialog neu erzeugt
werden.

Aktivierung:

- Menü **Tools -> MCP-Server...** öffnen.
- **MCP-Server aktivieren** einschalten.
- Optional **Überweisungen anlegen** einschalten, wenn lokale
  SEPA-Überweisungsentwürfe per MCP angelegt werden sollen.
- Optional **Zugriff aus lokalem Netzwerk erlauben** einschalten.
- Port prüfen oder anpassen.
- Endpoint und Bearer-Token in den MCP-Client eintragen.

Wenn der MCP-Server bereits läuft, wird ein Wechsel der LAN-Option erst nach
einem Neustart von Hibiscus aktiv. Dadurch wird vermieden, dass der laufende
Listener im Betrieb zwischen `127.0.0.1` und `0.0.0.0` neu gebunden werden
muss.

## Client-Konfiguration

Beispielwerte:

```text
Endpoint: http://127.0.0.1:37653/mcp
Header:   Authorization: Bearer <token>
```

`<token>` muss durch das im Dialog angezeigte Token ersetzt werden.

LM Studio:

```json
{
  "mcpServers": {
    "hibiscus": {
      "url": "http://127.0.0.1:37653/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

LibreChat:

```yaml
mcpServers:
  hibiscus:
    type: streamable-http
    url: http://127.0.0.1:37653/mcp
    headers:
      Authorization: "Bearer <token>"
```

## Verfügbare Tools

| Tool | Zweck |
| --- | --- |
| `hibiscus_template_objects_list` | Top-Level-Objekte des Template-Kontexts auflisten |
| `hibiscus_template_render` | Jinjava-Template-String gegen aktuelle Daten rendern |
| `hibiscus_accounts_list` | Aktive oder alle Konten auflisten |
| `hibiscus_accounts_sync` | Konten über Hibiscus synchronisieren |
| `hibiscus_account_groups_list` | Aktive oder alle Kontogruppen auflisten |
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
- `verfuegbar`
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
