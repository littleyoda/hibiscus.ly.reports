# Entwicklung

Diese Datei beschreibt technische Details fuer Entwickler. Die fuer
Report-Autoren nutzbaren Template-Objekte sind in [REPORT_OBJECTS.md](REPORT_OBJECTS.md)
dokumentiert.

## Template-Objekte aus anderen Plugins

Andere Jameica-Plugins koennen eigene Template-Objekte bereitstellen, ohne dass
`hibiscus.ly.reports` diese Plugins kennen muss. Dafuer stellt das Report-Plugin
einen Jameica-Extension-Punkt bereit.

Die Extendable-ID lautet:

```text
hibiscus.ly.reports.template.context
```

Das Extendable-Objekt ist `de.open4me.hibiscus.reports.api.ReportTemplateContext`.
Es enthaelt die Standardobjekte `konten`, `kontogruppen` und `umsaetze` und kann
von Extensions um weitere Top-Level-Objekte ergaenzt werden.

Wichtige Methoden:

| Methode | Bedeutung |
| --- | --- |
| `put(String name, Object value)` | Fuegt ein neues Template-Objekt ein und wirft einen Fehler bei Namenskonflikt |
| `putIfAbsent(String name, Object value)` | Fuegt ein Template-Objekt nur ein, wenn der Name noch nicht existiert |
| `contains(String name)` | Prueft, ob ein Objektname bereits vergeben ist |
| `objects()` | Liefert die unveraenderliche Map fuer Jinjava |

Empfohlen ist ein eigener Top-Level-Namespace pro Plugin, damit keine
Namenskonflikte entstehen. Ein Depotviewer-Plugin sollte also zum Beispiel
`depotviewer` bereitstellen statt direkte Namen wie `depots` oder `wertpapiere`.

## Beispiel: plugin.xml

Wenn das andere Plugin auch ohne Reports-Plugin funktionieren soll, sollte die
Abhaengigkeit optional sein und die Extension mit `requires` abgesichert werden.

```xml
<requires jameica="2.10.0+">
  <import plugin="hibiscus.ly.reports" version="0.7.3+" required="false" />
</requires>

<extensions>
  <extension
      extends="hibiscus.ly.reports.template.context"
      class="example.reports.ExampleReportTemplateExtension"
      requires="hibiscus.ly.reports" />
</extensions>
```

## Beispiel: Extension

Das Plugin kann direkt gegen `ReportTemplateContext` kompilieren oder, wenn es
keine harte Compile-Abhaengigkeit auf das Reports-Plugin haben soll, per
Reflection arbeiten.

```java
public class ExampleReportTemplateExtension implements Extension
{
    @Override
    public void extend(Extendable extendable)
    {
        if (!"hibiscus.ly.reports.template.context".equals(extendable.getExtendableID()))
            return;

        ReportTemplateContext context = (ReportTemplateContext) extendable;
        context.putIfAbsent("example", new ExampleReportObjects());
    }
}
```

Fehler in einer Extension werden vom Renderer abgefangen und als Report-Fehler
angezeigt. Der Render-Vorgang laeuft mit den uebrigen Template-Objekten weiter.

## API-Gestaltung fuer Template-Objekte

Template-Objekte sollten nicht die interne Datenstruktur eines Plugins
offenlegen. Stattdessen sollten sie kleine Proxy-Objekte mit stabilen,
sprechenden Getter-Methoden bereitstellen.

Empfohlene Konventionen fuer Listen-Proxies:

| Methode | Bedeutung |
| --- | --- |
| `iterator()` | Direkte Iteration in Jinjava |
| `size()` | Anzahl der Eintraege |
| `isEmpty()` | Leere Liste pruefen |
| `asList()` | Materialisierte Liste |
| `limit(int)` | Begrenzte Liste |
| `letzteTage(int)` | Zeitfilter fuer Zeitreihen |
| `zeitraum(String from, String to)` | Zeitfilter fuer Zeitreihen im Format `YYYY-MM-DD` |

Snapshot-Listen sollten keinen Zeitraumfilter vortaeuschen. Fuer Stichtage ist
eine Methode wie `am("YYYY-MM-DD")` klarer.
