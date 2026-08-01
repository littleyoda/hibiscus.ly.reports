# Duplikats-Erkennung

Die Duplikats-Erkennung findet mögliche doppelte Umsätze in Hibiscus. Sie ist
unter **Tools/Duplikate suchen...** erreichbar.

## Suche

- **Zeitraum**: Die Suche ist auf einen frei wählbaren Zeitraum begrenzt.
  Standardmäßig wird das aktuelle Kalenderjahr bis heute verwendet.
- **Konten**: Die Kontenauswahl enthält `<Alle Konten>`, Kontogruppen und
  einzelne aktive Konten. `<Alle Konten>` prüft alle aktiven Konten.
- **Vorgemerkte Umsätze**: Vorgemerkte Buchungen werden ignoriert.

## Erkennung

Die Prüfung vergleicht Umsätze innerhalb desselben Kontos. Als mögliche
Duplikate gelten Buchungen mit gleichem Datum, nahezu gleichem Betrag und
gleichem Gegenkonto. Zusätzlich wird der Verwendungszweck bewertet:

- gleicher Verwendungszweck
- gleicher Verwendungszweck nach Entfernen von Leerzeichen und Zeilenumbrüchen
- gekürzter oder verlängerter Verwendungszweck

Unterschiedliche Salden senken die Bewertung. Die Treffer werden als
`niedrig`, `mittel`, `hoch` oder `sehr hoch` eingestuft.

## Ergebnisliste

Jedes mögliche Duplikat-Paar erscheint mit zwei Zeilen. Die Spalte **Paar**
zeigt, welche beiden Umsätze zusammengehören. Per Doppelklick oder
Rechtsklick **Anzeigen** wird der Umsatz in Hibiscus geöffnet.

Die Zeile mit der niedrigeren Umsatz-ID wird fett dargestellt. Das ist der ältere
Eintrag und in der Regel der Kandidat, der gelöscht werden sollte.

Mit **Alle Duplikate auswählen** werden alle fett dargestellten Zeilen markiert.
Danach können diese Treffer über das Kontextmenü gemeinsam gelöscht werden.

## Löschen

Über das Kontextmenü **Löschen** können ausgewählte Umsätze entfernt werden.
Vor dem Löschen fragt Jameica nach einer Bestätigung. Die Funktion entscheidet
nicht automatisch, welcher Umsatz der richtige ist; die Auswahl bleibt beim
Benutzer.
