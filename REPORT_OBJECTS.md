# Report-Objekte

Diese Datei beschreibt die Objekte, die in dynamischen HTML-Reports mit
[Jinjava](https://github.com/HubSpot/jinjava) verwendet werden koennen.

Jinjava ist eine Java-Implementierung, die einen Teil der Template-Befehle von
[Jinja](https://jinja.palletsprojects.com/en/stable/templates/) unterstuetzt. Die Reports
nutzen diese Template-Syntax fuer Schleifen, Bedingungen, Variablen und Filter.

## Verfuegbare Top-Level-Objekte

| Objekt | Bedeutung |
| --- | --- |
| `konten` | Aktive Konten |
| `konten.aktive` | Aktive Konten |
| `konten.alle` | Alle Konten |
| `kontogruppen` | Gruppen der aktiven Konten |
| `kontogruppen.aktive` | Gruppen der aktiven Konten |
| `kontogruppen.alle` | Gruppen aller Konten |
| `umsaetze` | Umsaetze der letzten 90 Tage |
| `umsaetze.alle` | Alle Umsaetze |

Direkte Iteration ueber `konten`, `kontogruppen` oder `umsaetze` verwendet die
Standardauswahl. Bei Konten und Kontogruppen sind das aktive Konten. Bei
Umsaetzen sind das die letzten 90 Tage.

```jinja
{% for konto in konten %}
  {{ konto.name }}: {{ konto.saldo }}
{% endfor %}
```

## Konten

Ein Konto besitzt folgende Felder:

| Feld | Bedeutung |
| --- | --- |
| `konto.id` | Interne Hibiscus-ID |
| `konto.name` | Anzeigename des Kontos |
| `konto.blz` | Bankleitzahl |
| `konto.iban` | IBAN |
| `konto.gruppe` | Kontogruppe |
| `konto.saldo` | Kontosaldo, auf zwei Nachkommastellen gerundet |
| `konto.verfuegbar` | Verfuegbarer Betrag, auf zwei Nachkommastellen gerundet |
| `konto.aktualisiert` | Datum und Uhrzeit der letzten Saldo-Aktualisierung |
| `konto.aktiv` | `true`, wenn das Konto in Hibiscus nicht deaktiviert ist |
| `konto.offline` | `true`, wenn es ein Hibiscus-Offline-Konto ist |
| `konto.umsaetze` | Umsaetze dieses Kontos |

Beispiel:

```jinja
<table>
  <tr>
    <th>Name</th>
    <th>Gruppe</th>
    <th>Saldo</th>
    <th>Verfuegbar</th>
    <th>Aktualisiert</th>
    <th>Offline</th>
  </tr>
  {% for konto in konten %}
  <tr>
    <td>{{ konto.name }}</td>
    <td>{{ konto.gruppe }}</td>
    <td>{{ konto.saldo }} EUR</td>
    <td>{{ konto.verfuegbar }} EUR</td>
    <td>{{ konto.aktualisiert }}</td>
    <td>{% if konto.offline %}ja{% else %}nein{% endif %}</td>
  </tr>
  {% endfor %}
</table>
```

Alle Konten, auch inaktive:

```jinja
{% for konto in konten.alle %}
  {{ konto.name }}
{% endfor %}
```

## Kontogruppen

Kontogruppen werden aus `konto.gruppe` gebildet. Konten ohne Gruppe werden der
Gruppe `Ohne Gruppe` zugeordnet.

Eine Kontogruppe besitzt folgende Felder:

| Feld | Bedeutung |
| --- | --- |
| `gruppe.name` | Name der Kontogruppe |
| `gruppe.konten` | Konten dieser Gruppe |
| `gruppe.anzahl` | Anzahl der Konten in dieser Gruppe |
| `gruppe.saldo` | Summe der Salden, auf zwei Nachkommastellen gerundet |
| `gruppe.verfuegbar` | Summe der verfuegbaren Betraege, auf zwei Nachkommastellen gerundet |

Beispiel:

```jinja
{% for gruppe in kontogruppen %}
  <h2>{{ gruppe.name }}</h2>
  <p>{{ gruppe.anzahl }} Konten, {{ gruppe.saldo }} EUR</p>

  <ul>
    {% for konto in gruppe.konten %}
      <li>{{ konto.name }}: {{ konto.saldo }} EUR</li>
    {% endfor %}
  </ul>
{% endfor %}
```

Gruppen aller Konten:

```jinja
{% for gruppe in kontogruppen.alle %}
  {{ gruppe.name }}
{% endfor %}
```

## Umsaetze

Der Standardzugriff auf `umsaetze` ist auf die letzten 90 Tage begrenzt. Fuer
grosse Datenbestaende sollte zusaetzlich `limit(...)` verwendet werden.

```jinja
{% for umsatz in umsaetze.limit(20) %}
  {{ umsatz.datum }} {{ umsatz.betrag }} {{ umsatz.zweck }}
{% endfor %}
```

Ein Umsatz besitzt folgende Felder:

| Feld | Bedeutung |
| --- | --- |
| `umsatz.datum` | Buchungsdatum |
| `umsatz.valuta` | Valutadatum |
| `umsatz.betrag` | Buchungsbetrag |
| `umsatz.saldo` | Kontosaldo nach der Buchung |
| `umsatz.zweck` | Verwendungszweck |
| `umsatz.zweck2` | Zweite Zweckzeile |
| `umsatz.verwendungszwecke` | Weitere Verwendungszwecke als Liste |
| `umsatz.gegenkontoName` | Name des Gegenkontos |
| `umsatz.gegenkontoNummer` | Nummer des Gegenkontos |
| `umsatz.gegenkontoBlz` | BLZ des Gegenkontos |
| `umsatz.art` | Buchungsart |
| `umsatz.kategorie` | Name der zugeordneten Kategorie |
| `umsatz.kategoriePfad` | Kategoriepfad als Liste |
| `umsatz.vorgemerkt` | `true`, wenn der Umsatz vorgemerkt ist |
| `umsatz.konto` | Konto dieses Umsatzes |

## Umsatz-Filter

Umsatzlisten koennen begrenzt werden:

| Ausdruck | Bedeutung |
| --- | --- |
| `umsaetze` | Umsaetze der letzten 90 Tage |
| `umsaetze.alle` | Alle Umsaetze |
| `umsaetze.limit(100)` | Maximal 100 Umsaetze |
| `umsaetze.letzteTage(30)` | Umsaetze der letzten 30 Tage |
| `umsaetze.zeitraum("2026-01-01", "2026-01-31")` | Umsaetze im angegebenen Zeitraum |

Die Filter koennen kombiniert werden:

```jinja
{% for umsatz in umsaetze.letzteTage(30).limit(50) %}
  {{ umsatz.datum }} {{ umsatz.betrag }} {{ umsatz.zweck }}
{% endfor %}
```

Kontospezifische Umsaetze:

```jinja
{% for konto in konten %}
  <h2>{{ konto.name }}</h2>

  {% for umsatz in konto.umsaetze.limit(10) %}
    {{ umsatz.datum }} {{ umsatz.betrag }} {{ umsatz.zweck }}
  {% endfor %}
{% endfor %}
```

## Kategorien

`umsatz.kategoriePfad` enthaelt die Kategorie-Hierarchie. Ein Eintrag besitzt:

| Feld | Bedeutung |
| --- | --- |
| `kategorie.id` | Interne Hibiscus-ID |
| `kategorie.name` | Name der Kategorie |
| `kategorie.skipReports` | `true`, wenn die Kategorie in Auswertungen ignoriert wird |
| `kategorie.color` | Farbe als RGB-Zahl, falls gesetzt |

Beispiel:

```jinja
{% for umsatz in umsaetze.limit(20) %}
  {% for kategorie in umsatz.kategoriePfad %}
    {{ kategorie.name }}{% if not loop.last %} > {% endif %}
  {% endfor %}
{% endfor %}
```

## Depotviewer

Wenn das Plugin `hibiscus.depotviewer` installiert ist, steht der Namespace
`depotviewer` zur Verfuegung.

Alle Depotviewer-Listen sind Proxy-Listen. Sie koennen direkt iteriert werden
und bieten einheitlich `size()`, `isEmpty()`, `asList()` und `limit(...)`.

`depotviewer.depots` iteriert ueber aktive Depots. Mit
`depotviewer.depots.alle` koennen alle Depots inklusive deaktivierter Depots
geladen werden.

```jinja
{% for depot in depotviewer.depots %}
  <h2>{{ depot.name }}</h2>
  <p>{{ depot.kontonummer }} {{ depot.iban }}</p>

  <h3>Bestand</h3>
  {% for bestand in depot.bestand.limit(20) %}
    {{ bestand.wertpapier.name }}:
    {{ bestand.anzahl }} Stueck,
    {{ bestand.wert }} {{ bestand.wertwaehrung }}
  {% endfor %}

  <h3>Bestand am Stichtag</h3>
  {% for bestand in depot.bestand.am("2026-06-30") %}
    {{ bestand.wertpapier.name }}:
    {{ bestand.wert }} {{ bestand.wertwaehrung }}
  {% endfor %}

  <h3>Orderbuch</h3>
  {% for order in depot.orderbuch.letzteTage(30).limit(10) %}
    {{ order.buchungsdatum }} {{ order.aktion }}
    {{ order.anzahl }} {{ order.wertpapier.name }}
  {% endfor %}
{% endfor %}
```

`depotviewer.wertpapiere` enthaelt Wertpapiere mit aktuellem Bestand.
`depotviewer.wertpapiere.alle` enthaelt alle Wertpapiere aus der Stammliste.

```jinja
{% for wertpapier in depotviewer.wertpapiere.limit(20) %}
  {% set kurs = wertpapier.kurs("2026-01-31") %}
  {{ wertpapier.name }}:
  {% if kurs %}
    {{ kurs.wert }} {{ kurs.waehrung }} am {{ kurs.datum }}
  {% endif %}
{% endfor %}
```

Zeitreihen unterstuetzen zusaetzlich `zeitraum("YYYY-MM-DD", "YYYY-MM-DD")`
und `letzteTage(...)`.

```jinja
{% for order in depotviewer.orderbuch.zeitraum("2026-01-01", "2026-01-31").limit(50) %}
  {{ order.buchungsdatum }} {{ order.wertpapier.name }} {{ order.aktion }}
{% endfor %}

{% for kurs in wertpapier.kurse.letzteTage(365).limit(10) %}
  {{ kurs.datum }} {{ kurs.wert }} {{ kurs.waehrung }}
{% endfor %}
```

## Hinweise

- Datumswerte werden im Format `YYYY-MM-DD` ausgegeben. `konto.aktualisiert`
  enthaelt zusaetzlich die Uhrzeit im Format `YYYY-MM-DDTHH:MM:SS`.
- Geldwerte sind Zahlen. Bei Konten und Kontogruppen sind Saldo und verfuegbarer
  Betrag bereits auf zwei Nachkommastellen gerundet.
- `umsaetze.alle` kann bei grossen Hibiscus-Datenbestaenden sehr viele Daten
  laden. Fuer Reports ist meist ein Zeitraum plus `limit(...)` sinnvoller.
