# Entwicklungswerkzeuge

Skripte zum lokalen Testen ohne Apache Ant und ohne die eigene Jameica-Installation
anzufassen. Alle Testdaten sind erfunden.

## Voraussetzungen

- JDK 17 oder neuer (`JAVA_HOME` oder `java`/`javac`/`jar` im `PATH`), `curl`, `jq`.
- `JAMEICA_DIR`: Jameica 2.12. Ohne weitere Angaben wird das Layout des Nix-Pakets erwartet
  (`share/java/jameica.jar`, `share/jameica-*/`); fuer andere Installationen zusaetzlich
  `JAMEICA_CP` (Classpath) und `JAMEICA_CWD` (Startverzeichnis) setzen. Getestet ist nur das Nix-Layout.
- `HIBISCUS_DIR`: ein installiertes Hibiscus-Plugin (Ordner mit `hibiscus.jar` und `lib/`).
  Es wird nur gelesen; die Test-Instanz arbeitet mit einer Kopie.
- Pfade mit Leerzeichen werden nicht unterstuetzt.

```bash
export JAMEICA_DIR=/nix/store/...-jameica-2.12.0
export HIBISCUS_DIR=~/.jameica/plugins/hibiscus   # bzw. das eigene Jameica-Arbeitsverzeichnis
```

## Tests ohne Ant

```bash
dev/run-tests.sh                                                   # CoreTests
dev/run-tests.sh de.open4me.hibiscus.reports.mcp.McpJsonRpcHandlerTests   # einzelne Klassen
```

`run-tests.sh` uebersetzt `src/` und `test/` mit `javac --release 17` und ruft die statische
Methode `run()` jeder genannten Klasse auf. Der Classpath folgt `compile.path` aus `build/build.xml`:
die Jars aus `lib/` zuerst, von Hibiscus nur `hibiscus.jar`, `swtchart` und `obantoo`. `CoreTests` bricht derzeit in `DynamicReportTests` ab
(fehlende Datei `reports/Umsatzkategorien.html`); einzelne Klassen laufen davon unabhaengig.

## Isolierte Jameica-Instanz

Eine Wegwerf-Instanz mit eigenem Arbeitsverzeichnis, eigenem `user.home`, eigenem MCP-Port (37691)
und erfundenen Testkonten. Eine laufende echte Jameica bleibt unberuehrt. Laufzeitdaten liegen in
`dev/isolated-jameica/run/` (ignoriert von Git; anderer Ort ueber `ISO_RUN_DIR`).

```bash
dev/isolated-jameica/build.sh          # Plugin aus diesem Checkout + Testdaten-Plugin, frischer Stand
dev/isolated-jameica/start.sh server   # ohne Oberflaeche (Hintergrund: & anhaengen), oder: start.sh gui
dev/isolated-jameica/wait.sh           # wartet, bis die Testdaten angelegt sind
dev/isolated-jameica/tools.sh          # angebotene MCP-Tools
dev/isolated-jameica/ids.sh            # Testumsaetze (ID-Spalte erst mit dem Feld id aus hibiscus_transactions_list)
dev/isolated-jameica/mcp.sh '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Testdaten (beim ersten Start angelegt): "Testkonto EUR" und "Testkonto CHF", ein Umsatzpaar, das sich
nur im Gegenkontonamen unterscheidet ("Rechnung 4711"), ein Umsatz mit gespeicherter Kategorie,
Kommentar und "geprueft", einer mit nur per Regel zugeordneter Kategorie, einer in CHF und 55
"Sammelposten" fuer lange Listen.

Die Instanz vor einem neuen `build.sh` beenden; `build.sh` loescht den alten Stand.

### Pruefskripte fuer `hibiscus_transactions_delete`

Setzen das Werkzeug `hibiscus_transactions_delete` voraus. Sie warten zuerst auf die Instanz, geben PASS/FAIL je Pruefung
aus und beenden sich mit der Anzahl der Fehlschlaege (0 = alles bestanden).

| Skript | Instanz | Prueft |
| --- | --- | --- |
| `check-delete-headless.sh` | `server`, frisch gebaut | Tool-Liste, Beschreibung, IDs, `dryRun`-Status und Verknuepfungen, Ablehnung ohne Oberflaeche, Eingabepruefung |
| `check-delete-disabled.sh` | Option "Umsaetze loeschen" aus | Tool nicht angeboten, Aufruf abgelehnt |
| `check-delete-timeout.sh` | `gui`, Dialog nicht anklicken | zweiter Aufruf bei offenem Dialog, Timeout nach 50 s |

`delete.sh <id> ...` schickt einen echten Loeschaufruf an die Test-Instanz; in der GUI-Instanz
erscheint dann der Bestaetigungsdialog. Die Pruefskripte nie gegen die GUI-Instanz laufen lassen,
wenn sie dort nicht ausdruecklich vorgesehen sind: ein Klick auf "Loeschen" loescht Testumsaetze
(dann einfach neu bauen).
