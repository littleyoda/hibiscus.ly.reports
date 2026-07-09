package de.open4me.hibiscus.reports.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import de.open4me.hibiscus.reports.model.DynamicReport;
import de.willuhn.jameica.system.Application;

public final class DynamicReportRepository
{
    private static final String EXAMPLE_REPORT = """
        <!doctype html>
        <html lang="de">
        <head>
          <meta charset="utf-8">
          <title>Beispiel-Report</title>
          <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.5.0/chart.umd.min.js" integrity="sha512-Y51n9mtKTVBh3Jbx5pZSJNDDMyY+yGe77DGtBPzRlgsf/YLCh13kSZ3JmfHGzYFCmOndraf0sQgfM654b7dJ3w==" crossorigin="anonymous" referrerpolicy="no-referrer"></script>
          <style>
            body {
              font-family: system-ui, sans-serif;
              margin: 24px;
              color: #1f2933;
            }
            h1 {
              margin: 0 0 8px;
            }
            h2 {
              margin-top: 36px;
            }
            h3 {
              margin-top: 24px;
            }
            p {
              color: #5b6673;
            }
            table {
              border-collapse: collapse;
              margin: 12px 0 20px;
              width: 100%;
            }
            th, td {
              border-bottom: 1px solid #d7dde5;
              padding: 8px 10px;
              text-align: left;
            }
            th {
              background: #f3f6f9;
            }
            .number {
              text-align: right;
              white-space: nowrap;
            }
            .chart-wrap {
              height: 360px;
              max-width: 980px;
              position: relative;
              width: 100%;
            }
            .example {
              background: #f7f9fb;
              border: 1px solid #d7dde5;
              padding: 12px 14px;
              white-space: pre-wrap;
            }
            .notice {
              border: 1px solid #d7dde5;
              padding: 12px 14px;
            }
          </style>
        </head>
        <body>
          <h1>Beispiel-Report</h1>
          <p>Dieser Report zeigt die wichtigsten Template-Objekte fuer eigene HTML-Reports.</p>

          <h2>Verfuegbare Top-Level-Objekte</h2>
          <table>
            <thead>
              <tr>
                <th>Objekt</th>
                <th>Bedeutung</th>
              </tr>
            </thead>
            <tbody>
              <tr><td><code>konten</code></td><td>Aktive Konten</td></tr>
              <tr><td><code>konten.aktive</code></td><td>Aktive Konten</td></tr>
              <tr><td><code>konten.alle</code></td><td>Alle Konten</td></tr>
              <tr><td><code>kontogruppen</code></td><td>Gruppen der aktiven Konten</td></tr>
              <tr><td><code>kontogruppen.alle</code></td><td>Gruppen aller Konten</td></tr>
              <tr><td><code>umsaetze</code></td><td>Umsaetze der letzten 90 Tage</td></tr>
              <tr><td><code>umsaetze.alle</code></td><td>Alle Umsaetze</td></tr>
            </tbody>
          </table>

          <h2>Saldo aller Konten</h2>
          <p>Beispiel fuer ein Chart.js-Balkendiagramm. X-Achse: Konto, Y-Achse: Saldo.</p>
          <div class="chart-wrap">
            <canvas id="saldoChart"></canvas>
          </div>
          <div id="chartError" class="notice" style="display: none;">Chart.js konnte nicht geladen werden.</div>

          <div id="kontoData" style="display: none;">
            {% for konto in konten.alle %}
            <span data-name="{{ konto.name }}" data-saldo="{{ konto.saldo }}"></span>
            {% endfor %}
          </div>

          <h2>Aktive Konten</h2>

          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Gruppe</th>
                <th>BLZ</th>
                <th>IBAN</th>
                <th>Aktualisiert</th>
                <th>Offline</th>
                <th class="number">Saldo</th>
                <th class="number">Verfügbar</th>
              </tr>
            </thead>
            <tbody>
              {% for konto in konten %}
              <tr>
                <td>{{ konto.name }}</td>
                <td>{{ konto.gruppe }}</td>
                <td>{{ konto.blz }}</td>
                <td>{{ konto.iban }}</td>
                <td>{{ konto.aktualisiert }}</td>
                <td>{% if konto.offline %}ja{% else %}nein{% endif %}</td>
                <td class="number">{{ konto.saldo }} EUR</td>
                <td class="number">{{ konto.verfuegbar }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>

          <h2>Alle Konten</h2>

          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Gruppe</th>
                <th>Offline</th>
                <th class="number">Saldo</th>
                <th class="number">Verfuegbar</th>
              </tr>
            </thead>
            <tbody>
              {% for konto in konten.alle %}
              <tr>
                <td>{{ konto.name }}</td>
                <td>{{ konto.gruppe }}</td>
                <td>{% if konto.offline %}ja{% else %}nein{% endif %}</td>
                <td class="number">{{ konto.saldo }} EUR</td>
                <td class="number">{{ konto.verfuegbar }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>

          <h2>Kontogruppen</h2>

          <table>
            <thead>
              <tr>
                <th>Gruppe</th>
                <th>Konten</th>
                <th class="number">Saldo</th>
                <th class="number">Verfügbar</th>
              </tr>
            </thead>
            <tbody>
              {% for gruppe in kontogruppen %}
              <tr>
                <td>{{ gruppe.name }}</td>
                <td>
                  {% for konto in gruppe.konten %}
                    {{ konto.name }}{% if not loop.last %}, {% endif %}
                  {% endfor %}
                </td>
                <td class="number">{{ gruppe.saldo }} EUR</td>
                <td class="number">{{ gruppe.verfuegbar }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>

          <h2>Letzte Umsätze</h2>
          <p>Der Standardzugriff auf <code>umsaetze</code> ist auf die letzten 90 Tage begrenzt.</p>

          <table>
            <thead>
              <tr>
                <th>Datum</th>
                <th>Konto</th>
                <th>Zweck</th>
                <th>Kategorie</th>
                <th>Kategoriepfad</th>
                <th class="number">Betrag</th>
              </tr>
            </thead>
            <tbody>
              {% for umsatz in umsaetze.limit(20) %}
              <tr>
                <td>{{ umsatz.datum }}</td>
                <td>{{ umsatz.konto.name }}</td>
                <td>{{ umsatz.zweck }}</td>
                <td>{{ umsatz.kategorie }}</td>
                <td>
                  {% for kategorie in umsatz.kategoriePfad %}
                    {{ kategorie.name }}{% if not loop.last %} &gt; {% endif %}
                  {% endfor %}
                </td>
                <td class="number">{{ umsatz.betrag }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>

          <h2>Letzte Umsätze je Konto</h2>

          {% for konto in konten %}
          <h3>{{ konto.name }}</h3>
          <table>
            <thead>
              <tr>
                <th>Datum</th>
                <th>Zweck</th>
                <th class="number">Betrag</th>
              </tr>
            </thead>
            <tbody>
              {% for umsatz in konto.umsaetze.limit(5) %}
              <tr>
                <td>{{ umsatz.datum }}</td>
                <td>{{ umsatz.zweck }}</td>
                <td class="number">{{ umsatz.betrag }} EUR</td>
              </tr>
              {% endfor %}
            </tbody>
          </table>
          {% endfor %}

          <h2>Umsatz-Filter</h2>
          <div class="example">{% raw %}{% for umsatz in umsaetze.letzteTage(30).limit(50) %}
  {{ umsatz.datum }} {{ umsatz.betrag }} {{ umsatz.zweck }}
{% endfor %}

{% for umsatz in umsaetze.zeitraum("2026-01-01", "2026-01-31") %}
  {{ umsatz.datum }} {{ umsatz.betrag }} {{ umsatz.zweck }}
{% endfor %}{% endraw %}</div>

          <script>
            var entries = document.getElementById('kontoData').getElementsByTagName('span');
            var labels = [];
            var values = [];
            var colors = [];
            for (var i = 0; i < entries.length; i++) {
              var name = entries[i].getAttribute('data-name');
              var saldo = parseFloat(entries[i].getAttribute('data-saldo'));
              labels.push(name);
              values.push(saldo);
              colors.push(saldo < 0 ? 'rgba(190, 48, 48, 0.72)' : 'rgba(47, 112, 193, 0.72)');
            }

            if (typeof Chart === 'undefined') {
              document.getElementById('chartError').style.display = 'block';
            } else if (entries.length > 0) {
              new Chart(document.getElementById('saldoChart'), {
                type: 'bar',
                data: {
                  labels: labels,
                  datasets: [{
                    label: 'Saldo',
                    data: values,
                    backgroundColor: colors,
                    borderColor: 'rgb(35, 83, 145)',
                    borderWidth: 1
                  }]
                },
                options: {
                  animation: false,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: {
                      display: false
                    }
                  },
                  scales: {
                    x: {
                      ticks: {
                        autoSkip: false
                      }
                    },
                    y: {
                      beginAtZero: true,
                      ticks: {
                        callback: function(value) {
                          return Number(value).toLocaleString('de-DE') + ' EUR';
                        }
                      }
                    }
                  }
                }
              });
            }
          </script>
        </body>
        </html>
        """;

    private final Path baseDirectory;
    private final Path reportDirectory;

    public DynamicReportRepository(Path baseDirectory)
    {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        this.reportDirectory = this.baseDirectory.resolve("reports");
    }

    public static DynamicReportRepository jameica()
    {
        return new DynamicReportRepository(Path.of(Application.getConfig().getWorkDir(), "hibiscus.ly.reports"));
    }

    public Path baseDirectory()
    {
        return baseDirectory;
    }

    public void initialize() throws IOException
    {
        Files.createDirectories(reportDirectory);
        if (listReports().isEmpty())
            Files.writeString(reportDirectory.resolve("Beispiel.html"), EXAMPLE_REPORT, StandardCharsets.UTF_8);
    }

    public List<DynamicReport> listReports() throws IOException
    {
        if (!Files.isDirectory(reportDirectory))
            return List.of();
        try (Stream<Path> paths = Files.walk(reportDirectory))
        {
            return paths.filter(Files::isRegularFile)
                .filter(DynamicReportRepository::isHtml)
                .map(this::toReport)
                .sorted(Comparator.comparing(DynamicReport::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    public String read(DynamicReport report) throws IOException
    {
        return Files.readString(report.path(), StandardCharsets.UTF_8);
    }

    public void write(DynamicReport report, String content) throws IOException
    {
        Path file = checkedReportPath(report.path());
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public DynamicReport createReport(String name) throws IOException
    {
        Path file = checkedReportPath(reportPath(name));
        if (Files.exists(file))
            throw new IOException("Report existiert bereits: " + reportDirectory.relativize(file));
        Files.createDirectories(file.getParent());
        Files.writeString(file, EXAMPLE_REPORT, StandardCharsets.UTF_8);
        return toReport(file);
    }

    private Path reportPath(String name) throws IOException
    {
        if (name == null || name.isBlank())
            throw new IOException("Bitte einen Reportnamen eingeben.");
        String normalized = name.trim().replace('\\', '/');
        if (!normalized.endsWith(".html") && !normalized.endsWith(".htm"))
            normalized += ".html";
        Path relative = Path.of(normalized);
        if (relative.isAbsolute())
            throw new IOException("Absolute Pfade sind nicht erlaubt.");
        for (Path part : relative)
        {
            if ("..".equals(part.toString()))
                throw new IOException("Übergeordnete Pfade sind nicht erlaubt.");
        }
        return reportDirectory.resolve(relative);
    }

    private Path checkedReportPath(Path path) throws IOException
    {
        Path directory = reportDirectory.toAbsolutePath().normalize();
        Path file = path.toAbsolutePath().normalize();
        if (!file.startsWith(directory))
            throw new IOException("Reports müssen unter reports/ liegen.");
        if (!isHtml(file))
            throw new IOException("Reports müssen HTML-Dateien sein.");
        return file;
    }

    private DynamicReport toReport(Path path)
    {
        String relative = reportDirectory.relativize(path).toString().replace('\\', '/');
        int dot = relative.lastIndexOf('.');
        String display = dot > 0 ? relative.substring(0, dot) : relative;
        return new DynamicReport(path, display);
    }

    private static boolean isHtml(Path path)
    {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }
}
