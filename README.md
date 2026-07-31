# hibiscus.ly.reports

`hibiscus.ly.reports` erweitert Hibiscus/Jameica um interaktive Auswertungen,
eigene HTML-Reports, lokale JavaScript-Automatisierungen und einen optionalen
lokalen MCP-Server.

## Funktionen und Dokumentation

- **Auswertungen und Reports**: Geldfluss, Monatsübersicht, Saldo nach
  Gruppen sowie eigene HTML-/Jinjava-Reports mit Hibiscus-Daten.
  Details: [README_REPORTS.md](README_REPORTS.md)
- **Automatisierung**: lokale JavaScript-Automationen, manuelle Ausführung,
  Testläufe, Zeitsteuerung, Dialoge, `sync` und Zahlungsentwürfe.
  Details: [README_AUTOMATION.md](README_AUTOMATION.md)
- **MCP-Server**: optionaler lokaler Zugriff für MCP-Clients mit Tools für
  Konten, Umsätze, Template-Rendering, Sync und SEPA-Entwürfe.
  Details: [README_MCP.md](README_MCP.md)
- **Entwicklung und Erweiterungen**: Extension-Points für Template-Objekte
  und MCP-Tools.
  Details: [DEVELOPMENT.md](DEVELOPMENT.md)

## Installation

### Über den Update-Manager

- In Jameica **Datei/Einstellungen** öffnen.
- Reiter **Updates** auswählen.
- Falls `https://www.open4me.de/hibiscus/` noch nicht aufgeführt ist:
  **Neues Repository hinzufügen** wählen und die URL eintragen.
- Repository öffnen, `hibiscus.ly.reports` auswählen und installieren.
- Jameica neu starten.

## Aufruf

Die wichtigsten Ansichten befinden sich in Hibiscus unter **Auswertungen** und
im Plugin-Menü **Tools**. Dynamische Reports und Automatisierungen werden
zusätzlich im Jameica-Navigationsbaum angezeigt.

Die Ansichten verwenden die in Hibiscus gespeicherten Daten. Vorgemerkte
Umsätze werden in Geldfluss und Monatsübersicht nicht berücksichtigt.
Kategorien, die in Hibiscus für Auswertungen ignoriert werden, werden
ebenfalls ausgelassen.


# Reports
Einzelne Fragmente für die HTML-Reports

        <h2>Aktive Konten</h2>

          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Gruppe</th>
                <th>Aktualisiert</th>
                <th class="number">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {% for konto in konten %}
              <tr>
                <td>{{ konto.name }}</td>
                <td>{{ konto.gruppe }}</td>
                <td>{{ konto.aktualisiert }}</td>
                <td class="number">{{ konto.saldo }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>

![reportallekonten](img/reportallekonten.png)


          <h2>Letzte Umsätze</h2>

          <table>
            <thead>
              <tr>
                <th>Datum</th>
                <th>Konto</th>
                <th>Zweck</th>
                <th>Kategorie</th>
                <th class="number">Betrag</th>
              </tr>
            </thead>
            <tbody>
              {% for umsatz in umsaetze.limit(10) %}
              <tr>
                <td>{{ umsatz.datum }}</td>
                <td>{{ umsatz.konto.name }}</td>
                <td>{{ umsatz.zweck }}</td>
                <td> {{ umsatz.kategorie }} </td>
                <td class="number">{{ umsatz.betrag }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>
![reportletzteumsätze](img/reportletzteumsätze.png)
Die Reports-Ansicht rendert eigene HTML-Dateien als dynamische Auswertungen.
Die Dateien liegen im Jameica-Profil unter:

```text
<Benutzerverzeichnis>/.jameica/hibiscus.ly.reports/reports
```

Beim ersten Start wird ein Beispielreport angelegt. Weitere Reports können in
der Ansicht über **Neu** erstellt und anschließend direkt bearbeitet werden.
Die Vorschau wird im integrierten Browser angezeigt; **Speichern** schreibt das
Template zurück in den Report-Ordner.

Jeder Report steht zusätzlich als Element für die Jameica-Startseite zur
Verfügung. Über **Startseite anpassen** können einzelne Reports im Dialog
**Auswahl der anzuzeigenden Elemente** aktiviert, sortiert und in der Höhe
angepasst werden. Die Einträge erscheinen dort als **Reports: <Reportname>**.
Neue Reports werden nach dem Anlegen automatisch in dieser Auswahl angeboten.
Reports in Unterverzeichnissen behalten ihren Pfad im Anzeigenamen.

Templates werden mit Jinjava gerendert. Verfügbar sind unter anderem:

* `konten`, `konten.aktive`, `konten.alle`
* `kontogruppen`, `kontogruppen.aktive`, `kontogruppen.alle`
* `umsaetze`, `umsaetze.alle`, `umsaetze.limit(...)`,
  `umsaetze.letzteTage(...)`, `umsaetze.zeitraum(...)`

Die vollständige Beschreibung der Template-Objekte ist in der Reports-Ansicht
über das Hilfe-Symbol oben rechts verfügbar. Zusätzlich bleibt die
Repository-Dokumentation in [REPORT_OBJECTS.md](REPORT_OBJECTS.md) erhalten.

Da Reports normales HTML ausgeben, können sie CSS, Tabellen und JavaScript
verwenden. Externe Bibliotheken wie Chart.js können per CDN eingebunden werden.

## MCP-Server

Das Plugin kann die Report-Template-Objekte auch ueber einen lokalen
MCP-Server bereitstellen. Der Server ist standardmaessig deaktiviert und
muss bewusst aktiviert werden. Ohne weitere Freigabe ist der Zugriff lesend.

Aktivierung:

* Menue **Reports -> MCP-Server...** oeffnen.
* **MCP-Server aktivieren** einschalten.
* Optional **Ueberweisungen anlegen** einschalten, wenn lokale
  SEPA-Ueberweisungsentwuerfe per MCP angelegt werden sollen.
* Optional **Zugriff aus lokalem Netzwerk erlauben** einschalten, wenn der
  MCP-Server von anderen Geraeten im lokalen Netz erreichbar sein soll.
* Port pruefen oder anpassen.
* Den angezeigten Endpoint und Bearer-Token in den MCP-Client eintragen.

Der Server bindet standardmaessig ausschliesslich an `127.0.0.1`. Mit der
LAN-Option bindet er an `0.0.0.0`; Clients im lokalen Netz muessen dann die
IP-Adresse oder den Hostnamen des Hibiscus-Rechners verwenden. Jeder Request
muss den Header `Authorization: Bearer <token>` enthalten. Der Token kann im
Dialog neu erzeugt werden. Endpoint und Token koennen im Dialog per Button in
die Zwischenablage kopiert werden.

Wenn der MCP-Server bereits laeuft, wird ein Wechsel der LAN-Option erst nach
einem Neustart von Hibiscus aktiv. Dadurch wird vermieden, dass der laufende
Listener im Betrieb zwischen `127.0.0.1` und `0.0.0.0` neu gebunden werden
muss.

Beispiel:
`<token>` muss durch das im Programm angezeigte Token ersetzt werden.

```text
Endpoint: http://127.0.0.1:37653/mcp
Header:   Authorization: Bearer <token>
```

LM-Studio:

```text
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

Librechat:
```text
mcpServers:
  hibiscus:
    type: streamable-http
    url: http://127.0.0.1:37653/mcp
    requiresOAuth: false
    headers:
      Authorization: "Bearer <token>"
```



Verfuegbare Tools:

* `hibiscus_template_objects_list`: Top-Level-Objekte des Template-Kontexts
* `hibiscus_template_render`: Jinjava-Template-String gegen aktuelle Daten rendern
* `hibiscus_accounts_list`: aktive oder alle Konten auflisten
* `hibiscus_accounts_sync`: Konten ueber Hibiscus synchronisieren
* `hibiscus_account_groups_list`: aktive oder alle Kontogruppen auflisten
* `hibiscus_transactions_list`: Umsaetze mit Zeitraum, Konto und Limit laden
* `hibiscus_sepa_transfer_create`: lokalen SEPA-Ueberweisungsentwurf anlegen

Objekte, die andere Plugins per `hibiscus.ly.reports.template.context`
bereitstellen, sind im MCP-Kontext ebenfalls verfuegbar. Das Report-Plugin
muss diese Plugins dafuer nicht direkt kennen.

`hibiscus_sepa_transfer_create` sendet keine Zahlung an die Bank. Es speichert
nur einen lokalen Entwurf in Hibiscus. Der Auftrag muss anschliessend in
Hibiscus geprueft und manuell ausgefuehrt werden. Das Tool funktioniert nur,
wenn im MCP-Dialog **Ueberweisungen anlegen** aktiviert wurde.

`hibiscus_accounts_sync` synchronisiert Konten ueber die registrierten
Hibiscus-Synchronisierungs-Backends. Konten koennen ueber `all`, `accountIds`,
`ibans`, `kundennummern`, `kundenkennungen`, `kontonummern`, `bezeichnungen`
oder `backendClasses` ausgewaehlt werden.

Plugins koennen zusaetzlich eigene strukturierte MCP-Tools registrieren. Wenn
zum Beispiel der Depotviewer installiert ist, koennen dadurch Tools wie
`depotviewer_depots_list`, `depotviewer_portfolio_list` oder
`depotviewer_orders_list` in Codex erscheinen.



## Plugin über den Update-Manager installieren

* Menü **Datei/Einstellungen** öffnen.
* Reiter **Updates** auswählen.
* Falls `https://www.open4me.de/hibiscus/` noch nicht aufgeführt ist:
  * **Neues Repository hinzufügen** wählen.
  * `https://www.open4me.de/hibiscus/` eintragen.
* Doppelklick auf `https://www.open4me.de/hibiscus/`.
* `hibiscus.ly.reports` auswählen und die Installation starten.
* Jameica neu starten.

## Plugin aus einer ZIP-Datei installieren

Alternativ kann das Plugin als ZIP-Datei über den Jameica-Plugin-Manager
installiert werden. Danach Jameica neu starten.

# Nach der Installation

Die Auswertungen befinden sich in Hibiscus unter **Auswertungen**:

* **Geldfluss**
* **Monatsübersicht**
* **Saldo nach Gruppen**
* **Reports**

Die Ansichten verwenden ausschließlich die in Hibiscus gespeicherten Daten.
Vorgemerkte Umsätze werden in Geldfluss und Monatsübersicht nicht
berücksichtigt. Kategorien, die in Hibiscus für Auswertungen ignoriert werden,
werden ebenfalls ausgelassen.


# Lizenz

Dieses Plugin steht unter der GNU General Public License Version 3. Details
stehen in [LICENSE](LICENSE).
