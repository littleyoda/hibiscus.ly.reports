package de.open4me.hibiscus.reports.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import de.open4me.hibiscus.reports.model.DynamicReport;
import de.open4me.hibiscus.reports.model.ReportAccount;
import de.open4me.hibiscus.reports.model.ReportTransaction;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;

public final class DynamicReportTests
{
    private DynamicReportTests()
    {
    }

    public static void run()
    {
        try
        {
            createsExampleReportWhenNoReportsExist();
            listsOnlyReportsBelowReportsDirectory();
            createsNewReportsBelowReportsDirectory();
            rejectsInvalidNewReportPaths();
            writesReportContent();
            restrictsResourceLoadingToBaseDirectory();
            rendersReportWithExtendsAndAccountProxy();
            rendersExplicitActiveAndAllAccountProxyLists();
            rendersAccountGroups();
            rendersGlobalAndAccountTransactions();
            rendersExampleReportWithTransactions();
            rendersAccountsWithUnavailableAmounts();
        }
        catch (Exception e)
        {
            throw new AssertionError("dynamic report tests failed", e);
        }
    }

    private static void createsExampleReportWhenNoReportsExist() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-example");
        DynamicReportRepository repository = new DynamicReportRepository(base);
        repository.initialize();

        List<DynamicReport> reports = repository.listReports();
        check(reports.size() == 1, "example report count");
        checkEquals("Beispiel", reports.get(0).displayName(), "example report name");
        check(Files.exists(base.resolve("reports/Beispiel.html")), "example report file");
    }

    private static void listsOnlyReportsBelowReportsDirectory() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-list");
        write(base.resolve("reports/foo.html"), "foo");
        write(base.resolve("reports/sub/bar.htm"), "bar");
        write(base.resolve("layouts/base.html"), "layout");
        write(base.resolve("reports/readme.txt"), "text");

        List<String> names = new DynamicReportRepository(base).listReports().stream()
            .map(DynamicReport::displayName).toList();
        checkEquals(List.of("foo", "sub/bar"), names, "report names");
    }

    private static void createsNewReportsBelowReportsDirectory() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-create");
        DynamicReportRepository repository = new DynamicReportRepository(base);

        DynamicReport first = repository.createReport("foo");
        DynamicReport second = repository.createReport("sub/bar.html");

        checkEquals("foo", first.displayName(), "created report name");
        checkEquals("sub/bar", second.displayName(), "created sub report name");
        check(Files.exists(base.resolve("reports/foo.html")), "created html report");
        check(Files.exists(base.resolve("reports/sub/bar.html")), "created nested html report");
    }

    private static void rejectsInvalidNewReportPaths() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-invalid");
        DynamicReportRepository repository = new DynamicReportRepository(base);
        repository.createReport("foo");

        expectIOException(() -> repository.createReport("foo"), "duplicate report");
        expectIOException(() -> repository.createReport("../outside"), "parent traversal");
        expectIOException(() -> repository.createReport("sub/../outside"), "normalized traversal");
        expectIOException(() -> repository.createReport(base.resolve("absolute").toString()), "absolute path");
    }

    private static void writesReportContent() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-write");
        DynamicReportRepository repository = new DynamicReportRepository(base);
        DynamicReport report = repository.createReport("foo");

        repository.write(report, "<h1>Gespeichert</h1>");

        checkEquals("<h1>Gespeichert</h1>", repository.read(report), "saved report content");
    }

    private static void restrictsResourceLoadingToBaseDirectory() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-loader");
        write(base.resolve("layouts/base.html"), "base");
        Path outside = Files.createTempFile("hibiscus-reports-outside", ".html");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);

        JameicaReportResourceLoader loader = new JameicaReportResourceLoader(base);
        checkEquals("base",
            loader.getString("layouts/base.html", StandardCharsets.UTF_8, null), "resource in base");
        expectIOException(() -> loader.getString("../" + outside.getFileName(), StandardCharsets.UTF_8, null),
            "parent traversal");
        expectIOException(() -> loader.getString(outside.toString(), StandardCharsets.UTF_8, null),
            "absolute path");
    }

    private static void rendersReportWithExtendsAndAccountProxy() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-render");
        write(base.resolve("layouts/base.html"), "<html><body>{% block body %}{% endblock %}</body></html>");
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, new FakeAccountProvider());

        String template = """
            {% extends "layouts/base.html" %}
            {% block body %}
            {% for konto in konten %}{{ konto.name }} {{ konto.blz }} {{ konto.iban }} {{ konto.gruppe }} {{ konto.saldo }} {{ konto.verfuegbar }} {{ konto.aktualisiert }}{% endfor %}
            {% endblock %}
            """;
        DynamicReportRenderer.RenderedReport rendered = renderer.render(template);

        check(rendered.errors().isEmpty(), "render errors");
        check(rendered.html().contains("Aktives Girokonto"), "active account name rendered");
        check(rendered.html().contains("12345678"), "account blz rendered");
        check(rendered.html().contains("DE001234"), "account iban rendered");
        check(rendered.html().contains("Privat"), "account group rendered");
        check(rendered.html().contains("123.46"), "rounded account balance rendered");
        check(rendered.html().contains("120.12"), "rounded available balance rendered");
        check(rendered.html().contains("2026-07-08"), "account update date rendered");
        check(!rendered.html().contains("Archivkonto"), "inactive account is not rendered by default");
    }

    private static void rendersExplicitActiveAndAllAccountProxyLists() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-render-lists");
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, new FakeAccountProvider());

        DynamicReportRenderer.RenderedReport active = renderer.render(
            "{% for konto in konten.aktive %}{{ konto.name }};{% endfor %}");
        DynamicReportRenderer.RenderedReport all = renderer.render(
            "{% for konto in konten.alle %}{{ konto.name }};{% endfor %}");

        check(active.errors().isEmpty(), "active render errors");
        check(all.errors().isEmpty(), "all render errors");
        checkEquals("Aktives Girokonto;Aktives Tagesgeld;", active.html(), "active accounts");
        checkEquals("Aktives Girokonto;Aktives Tagesgeld;Archivkonto;", all.html(), "all accounts");
    }

    private static void rendersAccountGroups() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-groups");
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, new FakeAccountProvider());

        DynamicReportRenderer.RenderedReport active = renderer.render(
            "{% for gruppe in kontogruppen %}{{ gruppe.name }}={{ gruppe.anzahl }}={{ gruppe.saldo }}:{% for konto in gruppe.konten %}{{ konto.name }},{% endfor %};{% endfor %}");
        DynamicReportRenderer.RenderedReport all = renderer.render(
            "{% for gruppe in kontogruppen.alle %}{{ gruppe.name }}={{ gruppe.anzahl }};{% endfor %}");

        check(active.errors().isEmpty(), "active group render errors");
        check(all.errors().isEmpty(), "all group render errors");
        check(active.html().contains("Privat=2=173.46:Aktives Girokonto,Aktives Tagesgeld,;"),
            "active group accounts");
        check(!active.html().contains("Archiv=1"), "inactive group is not rendered by default");
        check(all.html().contains("Privat=2;"), "all groups active group");
        check(all.html().contains("Archiv=1;"), "all groups archived group");
    }

    private static void rendersGlobalAndAccountTransactions() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-transactions");
        FakeTransactionProvider transactionProvider = new FakeTransactionProvider();
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, new FakeAccountProvider(transactionProvider),
            transactionProvider);

        DynamicReportRenderer.RenderedReport rendered = renderer.render("""
            {% for umsatz in umsaetze.limit(1) %}G:{{ umsatz.konto.name }} {{ umsatz.zweck }};{% endfor %}
            {% for konto in konten %}
            {% for umsatz in konto.umsaetze.limit(1) %}K:{{ konto.name }} {{ umsatz.zweck }};{% endfor %}
            {% endfor %}
            """);

        check(rendered.errors().isEmpty(), "transaction render errors");
        check(rendered.html().contains("G:Aktives Girokonto Globaler Umsatz"), "global transaction rendered");
        check(rendered.html().contains("K:Aktives Girokonto Kontoumsatz"), "account transaction rendered");
        check(transactionProvider.queries.stream().anyMatch(query -> query.accountId() == null
            && query.from() != null && query.limit() != null && query.limit() == 1), "global default query");
        check(transactionProvider.queries.stream().anyMatch(query -> "active".equals(query.accountId())
            && query.limit() != null && query.limit() == 1), "account query");
    }

    private static void rendersExampleReportWithTransactions() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-example-render");
        DynamicReportRepository repository = new DynamicReportRepository(base);
        repository.initialize();
        String template = repository.read(repository.listReports().get(0));

        FakeTransactionProvider transactionProvider = new FakeTransactionProvider();
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, new FakeAccountProvider(transactionProvider),
            transactionProvider);
        DynamicReportRenderer.RenderedReport rendered = renderer.render(template);

        check(rendered.errors().isEmpty(), "example report render errors");
        check(rendered.html().contains("Beispiel-Report"), "example title");
        check(rendered.html().contains("Saldo aller Konten"), "chart section");
        check(rendered.html().contains("Chart.js"), "chart js section");
        check(rendered.html().contains("Letzte Umsätze"), "global transaction section");
        check(rendered.html().contains("Letzte Umsätze je Konto"), "account transaction section");
        check(rendered.html().contains("Umsatz-Filter"), "transaction filter section");
        check(rendered.html().contains("Kategoriepfad"), "category path column");
        check(rendered.html().contains("Kontogruppen"), "account groups section");
        check(rendered.html().contains("Globaler Umsatz"), "example global transaction");
        check(rendered.html().contains("Kontoumsatz"), "example account transaction");
        check(rendered.html().contains("Archivkonto"), "all accounts section");
    }

    private static void rendersAccountsWithUnavailableAmounts() throws Exception
    {
        Path base = Files.createTempDirectory("hibiscus-reports-unavailable-amounts");
        DynamicReportRenderer renderer = new DynamicReportRenderer(base, filter -> List.of(
            new ReportAccount("nan", Double.NaN, Double.NaN, null, "Konto ohne Betrag", "", "", "",
                null)));

        DynamicReportRenderer.RenderedReport rendered = renderer.render(
            "{% for konto in konten %}{{ konto.name }}={{ konto.saldo }}={{ konto.verfuegbar }}={{ konto.aktualisiert }}{% endfor %}");

        check(rendered.errors().isEmpty(), "unavailable amount render errors");
        checkEquals("Konto ohne Betrag=0.0=0.0=", rendered.html(), "unavailable amount fallback");
    }

    private static void write(Path path, String text) throws Exception
    {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void expectIOException(ThrowingRunnable runnable, String message) throws Exception
    {
        try
        {
            runnable.run();
        }
        catch (java.io.IOException e)
        {
            return;
        }
        throw new AssertionError("expected IOException: " + message);
    }

    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message)
    {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
    }

    private static final class FakeAccountProvider implements ReportAccountProvider
    {
        private final ReportTransactionProvider transactionProvider;

        private FakeAccountProvider()
        {
            this(query -> List.of());
        }

        private FakeAccountProvider(ReportTransactionProvider transactionProvider)
        {
            this.transactionProvider = transactionProvider;
        }

        @Override
        public List<ReportAccount> loadAccounts(KontoFilter filter)
        {
            if (filter == KontoFilter.ACTIVE)
                return List.of(new ReportAccount("active", 123.456d, 120.123d, LocalDate.of(2026, 7, 8),
                        "Aktives Girokonto", "12345678", "DE001234", "Privat",
                        ReportTransactionsProxy.forAccount(transactionProvider, "active")),
                    new ReportAccount("daily", 50d, 50d, LocalDate.of(2026, 7, 8), "Aktives Tagesgeld",
                        "12345678", "DE005678", "Privat",
                        ReportTransactionsProxy.forAccount(transactionProvider, "daily")));
            if (filter == KontoFilter.ALL)
                return List.of(new ReportAccount("active", 123.456d, 120.123d, LocalDate.of(2026, 7, 8),
                        "Aktives Girokonto", "12345678", "DE001234", "Privat",
                        ReportTransactionsProxy.forAccount(transactionProvider, "active")),
                    new ReportAccount("daily", 50d, 50d, LocalDate.of(2026, 7, 8), "Aktives Tagesgeld",
                        "12345678", "DE005678", "Privat",
                        ReportTransactionsProxy.forAccount(transactionProvider, "daily")),
                    new ReportAccount("archive", 0d, 0d, null, "Archivkonto", "87654321", "DE009876",
                        "Archiv", ReportTransactionsProxy.forAccount(transactionProvider, "archive")));
            throw new AssertionError("unexpected account filter: " + filter);
        }
    }

    private static final class FakeTransactionProvider implements ReportTransactionProvider
    {
        private final List<ReportTransactionQuery> queries = new ArrayList<>();

        @Override
        public List<ReportTransaction> loadTransactions(ReportTransactionQuery query)
        {
            queries.add(query);
            ReportAccount account = new ReportAccount(query.accountId() == null ? "active" : query.accountId(),
                123.456d, 120.123d, LocalDate.of(2026, 7, 8), "Aktives Girokonto", "12345678",
                "DE001234", "Privat", null);
            String purpose = query.accountId() == null ? "Globaler Umsatz" : "Kontoumsatz";
            List<ReportTransaction> result = List.of(new ReportTransaction(LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 8), 12.34d, 123.45d, purpose, "", List.of(), "Gegenkonto",
                "111", "222", "Überweisung", "Kategorie", List.of(), false, account));
            if (query.limit() != null)
                return result.stream().limit(query.limit()).toList();
            return result;
        }
    }
}
