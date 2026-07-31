# Automatisierung

Die Automatisierung erweitert `hibiscus.ly.reports` um lokal ausgeführte
JavaScript-Automationen für Hibiscus-Daten. Automationen können manuell,
als Testlauf oder zeitgesteuert gestartet werden. Sie verwenden dieselben
fachlichen Datenobjekte wie die dynamischen Reports und können kontrolliert
SEPA-Zahlungsentwürfe vorbereiten.

Die Report-Template-Objekte sind in [README_REPORTS.md](README_REPORTS.md)
dokumentiert. Der optionale MCP-Zugriff auf ähnliche Daten und Funktionen ist
in [README_MCP.md](README_MCP.md) beschrieben.

## Funktionsumfang

Der aktuelle Implementierungsstand enthält:

- eigene Automation-Ansicht in Jameica
- Anzeige gespeicherter Automationen im Jameica-Navigationsbaum
- SQL-Datenhaltung für Automationen, Trigger, Läufe, Logs und wartende
  Entscheidungen
- manuelle Ausführung
- Testlauf ohne schreibende Nebenwirkungen
- zeitgesteuerte Trigger per Cron-Ausdruck oder Preset
- fester Laufmodus `single`
- Nashorn JavaScript-Engine mit eigener Engine pro Lauf
- gesperrten direkten Java-Klassenzugriff aus Scripts
- lesenden Zugriff auf Report-Datenobjekte
- `konten.mitIban(...)`
- Dialog-API für Bestätigung, Eingabe und Kontoauswahl
- Zahlungs-API für bestätigte SEPA-Zahlungsentwürfe
- Verlauf mit Läufen, Lauf-Logs und wartenden Entscheidungen für verpasste
  Zeittrigger
- Import und Export von Automationen als JSON
- `sync`-API für Hibiscus-Kontensynchronisierung aus Automationen

## Aufruf in Jameica

Die Ansicht ist über den Menüpunkt `Tools -> Automatisierung...` und über
den Navigationseintrag `Automatisierung` erreichbar. Gespeicherte
Automationen werden unter diesem Navigationseintrag als klickbare Kindknoten
angezeigt. Namen mit `/` oder `\` werden als Ordnerstruktur dargestellt.

Die Ansicht bietet aktuell:

- Auswahl vorhandener Automationen
- Neue Automation
- Kopieren
- Löschen
- Importieren
- Exportieren
- Tab `Konfiguration` mit Speichern, manueller Ausführung und Testlauf
- Tab `Verlauf` mit Aktualisieren, Laufstatus und Lauf-Logs

Pro Automation werden Name, Beschreibung, Verhalten bei verpassten Triggern,
aktive Zeitsteuerung, Zeitplan und Script bearbeitet. Die Historie ist aktuell
fest auf 100 Einträge begrenzt.

## Automationsmodell

Eine Automation besteht aus:

- Name
- Beschreibung
- Verhalten bei verpassten Triggern
- aktive Zeitsteuerung
- History-Limit
- Hauptscript
- optionalem Zeittrigger

Die technische Speicherung erfolgt in eigenen SQL-Tabellen mit Prefix
`automation_`.

## Import und Export

Automationen können als JSON-Bundle exportiert und importiert werden. Das
Bundle enthält:

- Metadaten der Automation
- Verhalten bei verpassten Triggern
- Script
- Trigger mit Name, Aktivstatus, Typ und Zeitplan

Nicht exportiert werden technische IDs, Laufhistorie, Logs und wartende
Entscheidungen.

Beim Import wird immer eine neue Automation angelegt. Existiert der Name
bereits, wird automatisch ein Import-Suffix ergänzt. Bestehende Automationen
werden nicht überschrieben. Der nächste Lauf importierter Trigger wird aus
dem importierten Zeitplan neu berechnet.

## Zeittrigger

Zeittrigger werden nur ausgewertet, wenn Jameica läuft. Das Plugin startet
keinen externen Hintergrunddienst.

Das Feld `Cron/Preset` akzeptiert:

- `taeglich`
- `täglich`
- `woechentlich`
- `wöchentlich`
- `monatlich`
- [Quartz-Cron-Ausdrücke](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/crontrigger.html)
- [Unix-Cron-Ausdrücke](https://manpages.debian.org/trixie/cron/crontab.5.en.html)
  mit 5 Feldern; intern wird dann `0` als Sekundenfeld ergänzt

Beispiele:

```text
taeglich
0 0 8 * * ?
0 30 7 ? * MON-FRI
30 7 * * *
```

Die Cron-Auswertung nutzt
[`cron-utils`](https://cron-parser.com/), eine Java-Bibliothek zum Parsen,
Validieren und Beschreiben von Cron-Ausdrücken.

## Laufmodus

Automationen werden fest im Modus `single` ausgeführt. Wenn eine Automation
erneut gestartet wird, während bereits ein Lauf derselben Automation aktiv
ist, wird kein neuer Lauf gestartet. Stattdessen wird ein übersprungener Lauf
mit Warnung protokolliert.

Testläufe werden weiterhin direkt gestartet.

## Offene Läufe und Neustart

Beim Beenden von Jameica/Hibiscus werden evtl. noch offene Läufe als `fehlgeschlagen` geschlossen. 

## Verpasste Trigger

Das Datenmodell kennt die Optionen:

- `ignorieren`
- `nachholen`
- `nachfragen`

Die aktuelle Scheduler-Implementierung führt fällige aktive Trigger aus,
während Jameica läuft. Beim Jameica-Start prüft das Plugin verpasste aktive
Zeittrigger vor dem Start des periodischen Schedulers:

- `ignorieren`: kein Lauf wird gestartet, der nächste zukünftige Lauf wird
  berechnet.
- `nachholen`: maximal ein verpasster Lauf wird gestartet, danach wird der
  nächste zukünftige Lauf berechnet.
- `nachfragen`: es wird eine wartende Entscheidung im Verlauf angelegt, danach
  wird der nächste zukünftige Lauf berechnet. Im Verlauf kann der Nutzer
  diese Entscheidung mit `Jetzt nachholen` oder `Ignorieren` abschließen.

Script-Dialoge werden innerhalb des laufenden JavaScript-Stacks angezeigt.
Wenn Hibiscus gerade synchronisiert, wartet der laufende Automation-Thread,
bis die Synchronisierung beendet ist, und zeigt den Dialog danach automatisch.
Der Lauf wird währenddessen als `wartet` angezeigt. Die Automation wird dafür
nicht neu gestartet.

## Script-Laufzeit

Jeder Lauf erhält eine eigene Nashorn-Engine. Dadurch teilen sich Läufe keine
Script-Variablen.

Direkter Java-Klassenzugriff wird per Nashorn `ClassFilter` blockiert. Scripts
sollen nur die freigegebenen API-Objekte verwenden.

Nicht Bestandteil der freigegebenen API sind:

- direkter Zugriff auf Java-Klassen
- direkter Zugriff auf Jameica- oder Hibiscus-Services
- HTTP-Zugriffe
- allgemeiner Dateisystemzugriff

## Freigegebene Script-Objekte

### `konten`

Konten entsprechen den Report-Objekten.

Verfügbar sind:

- `konten`
- `konten.aktive`
- `konten.alle`
- `konten.mitIban(iban)`
- `konten.mitKontonummer(kontonummer)`
- `konten.mitKundenkennung(kundenkennung)`
- `konten.mitKundennummer(kundennummer)`
- `konten.mitBezeichnung(bezeichnung)`

Direkte Iteration über `konten` verwendet aktive Konten.

Beispiel:

```javascript
konten.aktive.asList().forEach(function(konto) {
  log.info(konto.name + ": " + konto.saldo);
});
```

Konto über IBAN finden:

```javascript
var konto = konten.mitIban("DE02120300000000202051");
if (konto == null) {
  log.error("Konto mit dieser IBAN wurde nicht gefunden.");
  return;
}

log.info("Gefundenes Konto: " + konto.name);
```

Konto über andere Hibiscus-Kennungen finden:

```javascript
var konto1 = konten.mitKontonummer("0000202051");
var konto2 = konten.mitKundenkennung("KUNDE-01");
var konto3 = konten.mitBezeichnung("DKB Hauptkonto");
```

Wichtige Kontofelder:

- `konto.id`
- `konto.name`
- `konto.bezeichnung`
- `konto.iban`
- `konto.blz`
- `konto.kontonummer`
- `konto.kundenkennung`
- `konto.kundennummer`
- `konto.gruppe`
- `konto.saldo`
- `konto.verfuegbar`
- `konto.aktiv`
- `konto.offline`
- `konto.umsaetze`

### `kontogruppen`

Kontogruppen entsprechen den Report-Objekten.

Verfügbar sind:

- `kontogruppen`
- `kontogruppen.aktive`
- `kontogruppen.alle`

### `umsaetze`

Umsätze entsprechen den Report-Objekten.

Verfügbar sind:

- `umsaetze`
- `umsaetze.alle`
- `umsaetze.limit(n)`
- `umsaetze.letzteTage(n)`
- `umsaetze.zeitraum(von, bis)`

### `log`

Logs werden dem aktuellen Lauf zugeordnet.

```javascript
log.info("Information");
log.warn("Warnung");
log.error("Fehler");
```

### `kontext` und `automation`

Beide Namen zeigen auf den Laufkontext.

Wichtige Felder:

- `kontext.automationId`
- `kontext.automationName`
- `kontext.runId`
- `kontext.triggerId`
- `kontext.source`
- `kontext.testlauf`
- `kontext.writeAllowed`

### `dialoge`

Dialoge können in manuellen und zeitgesteuerten Läufen angezeigt werden. Der
laufende JavaScript-Stack blockiert bis zur Nutzerentscheidung. Wenn Hibiscus
gerade synchronisiert, wird die Dialoganzeige verzögert, bis die
Synchronisierung beendet ist. Der Laufstatus wechselt für diese Zeit auf
`wartet` und danach wieder auf `laeuft`.

Info-Dialog:

```javascript
dialoge.info("Prüfung abgeschlossen", "Alle Konten wurden geprüft.");
```

Alternativ kann derselbe Dialog deutsch benannt aufgerufen werden:

```javascript
dialoge.hinweis("Prüfung abgeschlossen", "Alle Konten wurden geprüft.");
```

Bestätigung:

```javascript
if (!dialoge.bestaetigen("Prüfung", "Soll die Automation fortfahren?")) {
  log.info("Abgebrochen.");
  return;
}
```

Eingabe:

```javascript
var text = dialoge.eingabe("Notiz erfassen", "Welche Notiz soll protokolliert werden?");
if (text.abgebrochen) {
  log.info("Eingabe wurde abgebrochen.");
  return;
}

log.info("Notiz: " + text.wert);
```

Kontoauswahl:

```javascript
var auswahl = dialoge.kontoAuswaehlen("Auftraggeberkonto auswählen");
if (auswahl.abgebrochen) {
  log.info("Kontoauswahl wurde abgebrochen.");
  return;
}

var konto = auswahl.wert;
```

### `sync`

`sync` startet die normale Hibiscus-Kontensynchronisierung aus einer
Automatisierung heraus. Die API ist nicht auf HBCI beschränkt, sondern nutzt
alle in Hibiscus registrierten Synchronisierungs-Backends.

Der Aufruf wartet, bis Hibiscus die Synchronisierung beendet hat. Danach werden
die bekannten Konto-, Kontogruppen- und Umsatz-Proxies neu geladen, wenn sie
erneut abgefragt werden. Bereits in Script-Variablen gespeicherte Listen oder
Kontoobjekte bleiben unverändert; Synchronisierung sollte deshalb möglichst
am Anfang des Scripts erfolgen.

`sync` erzwingt den Abruf von Saldo und Umsätzen für die angegebenen Konten,
auch wenn diese Abrufe in den normalen Hibiscus-Synchronisierungsoptionen nicht
aktiviert sind. Nicht-wiederkehrende Jobs wie auszuführende Zahlungsaufträge
werden nicht über `sync` gestartet.

Alle prinzipiell synchronisierbaren, nicht deaktivierten Konten
synchronisieren:

```javascript
sync.alle();
```

Ein einzelnes Konto über IBAN finden und synchronisieren:

```javascript
var konto = konten.mitIban("DE02120300000000202051");
if (konto == null) {
  log.error("Konto wurde nicht gefunden.");
  return;
}

sync.starten(konto);
```

Mehrere Konten synchronisieren:

```javascript
sync.starten(konto1, konto2);
```

Im Testlauf wird keine echte Synchronisierung gestartet. Der geplante
Synchronisierungslauf wird nur protokolliert.

### `zahlungen`

`zahlungen.entwurf(...)` bereitet einen lokalen Hibiscus-Zahlungsentwurf vor.
Es wird kein Bankauftrag gesendet.

Das Auftraggeberkonto wird immer als Kontoobjekt übergeben:

```javascript
zahlungen.entwurf({
  konto: konto,
  empfaengerName: "Max Mustermann",
  iban: "DE02120300000000202051",
  bic: "BYLADEM1001",
  betrag: 50.00,
  verwendungszweck: "Beispiel"
});
```

Unterstützte Felder:

- `art`
- `konto`
- `empfaengerName`
- `iban`
- `bic`
- `betrag`
- `termin`
- `verwendungszweck`
- `verwendungszweck2`
- `weitereVerwendungszwecke`

Unterstützte Zahlungsarten:

- `Überweisung`
- `Ueberweisung`
- `Terminüberweisung`
- `Terminueberweisung`
- `Interne Umbuchung`
- `Echtzeitüberweisung`
- `Echtzeitueberweisung`

Vor dem echten Anlegen zeigt das Plugin eine Bestätigung mit Konto,
Empfänger, IBAN/BIC, Betrag, Termin, Zweck und Zahlungsart. Bei Abbruch wird
kein Entwurf angelegt.

Im Testlauf wird kein Entwurf angelegt. Der geplante Zahlungsentwurf wird nur
protokolliert.

## Beispiele

### Warnung bei niedrigem Kontostand

Script:

```javascript
var warnen = konten.aktive.asList().some(function(konto) {
  return konto.saldo < 100;
});

if (!warnen) {
  log.info("Alle aktiven Konten haben mindestens 100 EUR Saldo.");
  return;
}

konten.aktive.asList().forEach(function(konto) {
  if (konto.saldo < 100) {
    log.warn(konto.name + " hat nur noch " + konto.saldo + " EUR.");
  }
});
```

### Zahlungsentwurf mit Kontoauswahl

```javascript
var auswahl = dialoge.kontoAuswaehlen("Auftraggeberkonto auswählen");
if (auswahl.abgebrochen) {
  log.info("Kontoauswahl wurde abgebrochen.");
  return;
}

var konto = auswahl.wert;

zahlungen.entwurf({
  art: "Terminüberweisung",
  konto: konto,
  empfaengerName: "Max Mustermann",
  iban: "DE02120300000000202051",
  bic: "BYLADEM1001",
  betrag: 50.00,
  termin: "2026-08-01",
  verwendungszweck: "Monatliche Umbuchung"
});
```

### Zahlungsentwurf mit Konto per IBAN

```javascript
var konto = konten.mitIban("DE02120300000000202051");
if (konto == null) {
  log.error("Auftraggeberkonto nicht gefunden.");
  return;
}

zahlungen.entwurf({
  art: "Überweisung",
  konto: konto,
  empfaengerName: "Max Mustermann",
  iban: "DE44500105175407324931",
  bic: "INGDDEFFXXX",
  betrag: 25.00,
  verwendungszweck: "Beispielzahlung"
});
```

## Datenhaltung

Beim Pluginstart wird die Automation-Datenbankstruktur geprüft und bei Bedarf
angelegt.

Tabellen:

- `automation_cfg`
- `automation_automation`
- `automation_trigger`
- `automation_run`
- `automation_run_log`
- `automation_decision`

Die Tabellen liegen in der Hibiscus-Datenbank. Die Verbindung wird über den
Hibiscus-DB-Service ermittelt.

## Build und Abhängigkeiten

Die Automatisierung nutzt Nashorn aus Jameica:

- `../jameica/lib/javascript/nashorn-core-15.7.jar`

Für Cron-Auswertung wird mitgeliefert:

- `lib/cron-utils-9.2.1.jar`

Der Ant-Build nimmt `lib/**/*.jar` und `jameica/lib/javascript/*.jar` in den
Classpath auf.

Build:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ant -f build/build.xml clean zip
```

Tests:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ant -f build/build.xml test
```

## Grenzen der aktuellen Implementierung

- Wartende Entscheidungen gibt es aktuell nur für verpasste Zeittrigger mit
  `nachfragen`.
- Die UI zeigt einen Laufverlauf, aber noch keine Filter, Suche oder
  Detailansicht für größere Historien.
- Script-Dialoge werden nur innerhalb der laufenden Jameica/Hibiscus-Sitzung
  blockierend behandelt und überleben keinen Neustart.
- Es gibt keine Laufzeit-, Queue- oder Parallelitätslimits.
- Es gibt keine automatischen Retries.
- Es gibt keine HTTP- oder Dateisystem-API für Scripts.
