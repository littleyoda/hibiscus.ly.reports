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

## Lizenz

Dieses Plugin steht unter der GNU General Public License Version 3. Details
stehen in [LICENSE](LICENSE).
