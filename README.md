# hibiscus.ly.reports

Interaktive Auswertungen für Hibiscus

`hibiscus.ly.reports` erweitert Hibiscus unter **Hibiscus -> Auswertungen** um
zusätzliche Ansichten für Sankey-Diagramme, Einnahmen, Ausgaben, Kontosalden
und eigene HTML-Reports.

![Geldfluss-Auswertung](img/geldfluss.png)

# Auswertungen

* **Geldfluss**: zeigt Einnahmequellen, Ausgabenkategorien und Überschuss oder Defizit als Sankey-Diagramm.
* **Monatsübersicht**: stellt Einnahmen, Ausgaben und Bilanz als Zeitreihe dar;
  wahlweise monatlich, quartalsweise oder jährlich gruppiert.
* **Saldo nach Gruppen**: zeigt die taggenauen Salden von Kontogruppen als
  Linienchart inklusive Gesamtsumme.
* **Reports**: rendert eigene HTML-Templates mit Konten, Kontogruppen und
  Umsätzen aus Hibiscus.

# Features
* Zeitraumsauswahl mit den bekannten Hibiscus-Vorgaben und taggenauen Von-/Bis-
  Feldern.
* Auswahl der auszuwertenden Konten je Ansicht.
* Berücksichtigung der Hibiscus-Kategorien inklusive der Option
  **In Auswertungen ignorieren**.
* Export der Diagramme als PNG oder SVG.
* Eigene HTML-Reports mit Jinjava-Templates, Tabellen, CSS und JavaScript.


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

# Hinweise zu den Auswertungen

## Geldfluss

Der Geldfluss zeigt, aus welchen Quellen Einnahmen stammen und in welche
Kategorien Ausgaben fließen. Ausgabenkategorien lassen sich per Mausklick
auf- und zuklappen. Kleine Flüsse können in den Einstellungen als
**Sonstige** gebündelt werden.

## Monatsübersicht

Die Monatsübersicht zeigt Einnahmen als positive Balken, Ausgaben als negative
Balken und die Bilanz als Linie. Angefangene Randperioden enthalten nur
Buchungen innerhalb des gewählten Datumsbereichs; Perioden ohne Umsätze
bleiben sichtbar.

## Saldo nach Gruppen

Diese Ansicht fasst Kontosalden nach Hibiscus-Kontogruppen zusammen. Konten
ohne Gruppenzuordnung erscheinen unter **Ohne Gruppe**. Bei **Alle Gruppen**
wird zusätzlich die Gesamtsumme angezeigt.

## Reports

Die Reports-Ansicht rendert eigene HTML-Dateien als dynamische Auswertungen.
Die Dateien liegen im Jameica-Profil unter:

```text
~/.jameica/hibiscus.ly.reports/reports
```

Beim ersten Start wird ein Beispielreport angelegt. Weitere Reports können in
der Ansicht über **Neu** erstellt und anschließend direkt bearbeitet werden.
Die Vorschau wird im integrierten Browser angezeigt; **Speichern** schreibt das
Template zurück in den Report-Ordner.

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


# Lizenz

Dieses Plugin steht unter der GNU General Public License Version 3. Details
stehen in [LICENSE](LICENSE).
