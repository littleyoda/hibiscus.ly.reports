# hibiscus.ly.reports

Interaktive Auswertungen für Hibiscus

`hibiscus.ly.reports` erweitert Hibiscus unter **Hibiscus -> Auswertungen** um
zusätzliche Ansichten für Sankey-Diagramme, Einnahmen, Ausgaben und Kontosalden.

![Geldfluss-Auswertung](img/geldfluss.png)

# Auswertungen

* **Geldfluss**: zeigt Einnahmequellen, Ausgabenkategorien und Überschuss oder Defizit als Sankey-Diagramm.
* **Monatsübersicht**: stellt Einnahmen, Ausgaben und Bilanz als Zeitreihe dar;
  wahlweise monatlich, quartalsweise oder jährlich gruppiert.
* **Saldo nach Gruppen**: zeigt die taggenauen Salden von Kontogruppen als
  Linienchart inklusive Gesamtsumme.

# Features
* Zeitraumsauswahl mit den bekannten Hibiscus-Vorgaben und taggenauen Von-/Bis-
  Feldern.
* Auswahl der auszuwertenden Konten je Ansicht.
* Berücksichtigung der Hibiscus-Kategorien inklusive der Option
  **In Auswertungen ignorieren**.
* Export der Diagramme als PNG oder SVG.


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


# Lizenz

Dieses Plugin steht unter der GNU General Public License Version 3. Details
stehen in [LICENSE](LICENSE).
