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

## MCP-Zugriff

Der lokale MCP-Server des Report-Plugins erzeugt seinen Datenkontext ueber
dieselbe `ReportTemplateContext`-Extension wie der HTML-Renderer. Objekte aus
anderen Plugins sind dadurch automatisch auch fuer MCP-Clients sichtbar, ohne
dass das Report-Plugin diese Plugins direkt kennen muss.

Der MCP-Zugriff ist standardmaessig lesend. Fuer fremde Plugin-Objekte ist
`hibiscus_template_render` der generische Zugriffspfad: Der MCP-Client kann
einen Jinjava-Ausdruck gegen den aktuellen Template-Kontext rendern. Fuer die
Hibiscus-Standardobjekte gibt es zusaetzlich strukturierte Tools fuer Konten,
Kontogruppen und Umsaetze.

Ueberweisungen anlegen muss im MCP-Dialog separat aktiviert werden. Aktuell
nutzt nur `hibiscus_sepa_transfer_create` diese Freigabe. Das Tool legt lokale
SEPA-Ueberweisungsentwuerfe an und fuehrt keine Bankkommunikation aus.

Andere Plugins koennen optional eigene strukturierte MCP-Tools bereitstellen.
Der Extension-Punkt lautet:

```text
hibiscus.ly.reports.mcp.tools
```

Das Extendable-Objekt besitzt die Methode `register(Object provider)`. Damit
andere Plugins unabhaengig bleiben koennen, ist der Provider bewusst duck-typed:

| Methode | Bedeutung |
| --- | --- |
| `getNamespace()` | Liefert den Tool-Namespace, z. B. `depotviewer` |
| `getTools()` | Liefert Tool-Definitionen als `List<Map<String,Object>>` |
| `call(String localToolName, Map<String,Object> arguments)` | Fuehrt ein Tool aus |

Toolnamen werden vom MCP-Server als `<namespace>_<name>` veroeffentlicht. Ein
Depotviewer-Tool mit lokalem Namen `depots_list` erscheint also als
`depotviewer_depots_list`.

Provider sollten nur serialisierbare Werte zurueckgeben: `Map`, `List`,
`String`, `Number`, `Boolean`, `Date`, `LocalDate`, `LocalDateTime` oder
`null`. Interne Datenbankobjekte sollten nicht zurueckgegeben werden.

Proxy-Objekte sollten deshalb weiterhin eine endusernahe API anbieten und keine
internen Datenbankobjekte durchreichen. Methoden wie `aktive`, `alle`, `limit`,
`letzteTage` und `zeitraum` bleiben die bevorzugte Form fuer begrenzbare
Listen.

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
