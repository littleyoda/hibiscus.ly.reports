package de.open4me.hibiscus.reports.automation.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import de.open4me.hibiscus.reports.automation.model.Automation;
import de.open4me.hibiscus.reports.automation.model.AutomationDecision;
import de.open4me.hibiscus.reports.automation.model.AutomationLogEntry;
import de.open4me.hibiscus.reports.automation.model.AutomationRun;
import de.open4me.hibiscus.reports.automation.model.AutomationTrigger;
import de.open4me.hibiscus.reports.automation.model.MissedTriggerPolicy;
import de.open4me.hibiscus.reports.automation.model.RunMode;
import de.open4me.hibiscus.reports.automation.model.RunStatus;

public final class AutomationRepository
{
    private final AutomationConnectionProvider connectionProvider;

    public AutomationRepository(AutomationConnectionProvider connectionProvider)
    {
        this.connectionProvider = connectionProvider;
    }

    public static AutomationRepository hibiscus()
    {
        return new AutomationRepository(new HibiscusAutomationConnectionProvider());
    }

    public List<Automation> listAutomations() throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_automation order by name"))
        {
            List<Automation> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(automation(rs));
            }
            return result;
        }
    }

    public List<Automation> listActiveAutomations() throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_automation where active = ? order by name"))
        {
            statement.setBoolean(1, true);
            List<Automation> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(automation(rs));
            }
            return result;
        }
    }

    public Automation getAutomation(String id) throws Exception
    {
        if (id == null || id.isBlank())
            return null;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_automation where id = ?"))
        {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery())
            {
                return rs.next() ? automation(rs) : null;
            }
        }
    }

    public Automation saveAutomation(Automation automation) throws Exception
    {
        if (automation.id() == null || automation.id().isBlank())
            return insertAutomation(automation);
        updateAutomation(automation);
        return automation;
    }

    private Automation insertAutomation(Automation automation) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into automation_automation (name, description, active, mode, missed_policy, script, history_limit, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS))
        {
            fillAutomation(statement, automation);
            int nowIndex = 8;
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setTimestamp(nowIndex, now);
            statement.setTimestamp(nowIndex + 1, now);
            statement.executeUpdate();
            return automation.withId(generatedId(statement));
        }
    }

    private void updateAutomation(Automation automation) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "update automation_automation set name = ?, description = ?, active = ?, mode = ?, missed_policy = ?, script = ?, history_limit = ?, updated_at = ? where id = ?"))
        {
            fillAutomation(statement, automation);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(9, automation.id());
            statement.executeUpdate();
        }
    }

    public void deleteAutomation(String id) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection())
        {
            connection.setAutoCommit(false);
            try
            {
                deleteByAutomation(connection, "automation_decision", id);
                deleteByAutomation(connection, "automation_run_log", id);
                deleteByAutomation(connection, "automation_run", id);
                deleteByAutomation(connection, "automation_trigger", id);
                try (PreparedStatement statement = connection.prepareStatement(
                    "delete from automation_automation where id = ?"))
                {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
                connection.commit();
            }
            catch (Exception e)
            {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void deleteByAutomation(Connection connection, String table, String automationId) throws Exception
    {
        String sql;
        if ("automation_run_log".equals(table) || "automation_decision".equals(table))
            sql = "delete from " + table + " where run_id in (select id from automation_run where automation_id = ?)";
        else
            sql = "delete from " + table + " where automation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, automationId);
            statement.executeUpdate();
        }
    }

    public List<AutomationTrigger> listTriggers(String automationId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_trigger where automation_id = ? order by name, id"))
        {
            statement.setString(1, automationId);
            List<AutomationTrigger> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(trigger(rs));
            }
            return result;
        }
    }

    public List<AutomationTrigger> listDueTriggers(LocalDateTime now) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_trigger where active = ? and next_run is not null and next_run <= ? order by next_run, id"))
        {
            statement.setBoolean(1, true);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            List<AutomationTrigger> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(trigger(rs));
            }
            return result;
        }
    }

    public AutomationTrigger saveTrigger(AutomationTrigger trigger) throws Exception
    {
        if (trigger.id() == null || trigger.id().isBlank())
            return insertTrigger(trigger);
        updateTrigger(trigger);
        return trigger;
    }

    private AutomationTrigger insertTrigger(AutomationTrigger trigger) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into automation_trigger (automation_id, name, active, trigger_type, schedule_expr, next_run, last_run) values (?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS))
        {
            fillTrigger(statement, trigger);
            statement.executeUpdate();
            return trigger.withId(generatedId(statement));
        }
    }

    private void updateTrigger(AutomationTrigger trigger) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "update automation_trigger set automation_id = ?, name = ?, active = ?, trigger_type = ?, schedule_expr = ?, next_run = ?, last_run = ? where id = ?"))
        {
            fillTrigger(statement, trigger);
            statement.setString(8, trigger.id());
            statement.executeUpdate();
        }
    }

    public AutomationRun createRun(String automationId, String triggerId, String source, boolean testRun)
        throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into automation_run (automation_id, trigger_id, status, source, test_run, started_at, finished_at, warning, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS))
        {
            statement.setString(1, automationId);
            statement.setString(2, triggerId);
            statement.setString(3, RunStatus.GEPLANT.value());
            statement.setString(4, source);
            statement.setBoolean(5, testRun);
            statement.setTimestamp(6, null);
            statement.setTimestamp(7, null);
            statement.setString(8, "");
            statement.setString(9, "");
            statement.executeUpdate();
            return new AutomationRun(generatedId(statement), automationId, triggerId, RunStatus.GEPLANT, source,
                testRun, null, null, "", "");
        }
    }

    public void updateRunStatus(String runId, RunStatus status, String warning, String error, boolean finished)
        throws Exception
    {
        String sql = finished
            ? "update automation_run set status = ?, warning = ?, error = ?, finished_at = ? where id = ?"
            : "update automation_run set status = ?, warning = ?, error = ? where id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, status.value());
            statement.setString(2, warning == null ? "" : warning);
            statement.setString(3, error == null ? "" : error);
            if (finished)
            {
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                statement.setString(5, runId);
            }
            else
            {
                statement.setString(4, runId);
            }
            statement.executeUpdate();
        }
    }

    public void markRunStarted(String runId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "update automation_run set status = ?, started_at = ? where id = ?"))
        {
            statement.setString(1, RunStatus.LAEUFT.value());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, runId);
            statement.executeUpdate();
        }
    }

    public List<AutomationRun> listRuns(String automationId, int limit) throws Exception
    {
        String suffix = limit > 0 ? " fetch first " + limit + " rows only" : "";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_run where automation_id = ? order by id desc" + suffix))
        {
            statement.setString(1, automationId);
            List<AutomationRun> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(run(rs));
            }
            return result;
        }
        catch (Exception e)
        {
            if (limit <= 0)
                throw e;
            return listRunsPortable(automationId, limit);
        }
    }

    private List<AutomationRun> listRunsPortable(String automationId, int limit) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_run where automation_id = ? order by id desc"))
        {
            statement.setString(1, automationId);
            List<AutomationRun> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next() && result.size() < limit)
                    result.add(run(rs));
            }
            return result;
        }
    }

    public boolean hasActiveRun(String automationId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select id from automation_run where automation_id = ? and status in (?, ?, ?)"))
        {
            statement.setString(1, automationId);
            statement.setString(2, RunStatus.GEPLANT.value());
            statement.setString(3, RunStatus.LAEUFT.value());
            statement.setString(4, RunStatus.WARTET.value());
            try (ResultSet rs = statement.executeQuery())
            {
                return rs.next();
            }
        }
    }

    public int failOpenRuntimeRuns(String reason) throws Exception
    {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String message = reason == null || reason.isBlank()
            ? "Jameica/Hibiscus wurde beendet; offene Automation wurde beendet."
            : reason;
        String resultJson = "{\"status\":\"failed\",\"reason\":\"" + json(message) + "\"}";
        try (Connection connection = connectionProvider.getConnection())
        {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try
            {
                List<String> runIds = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                    "select id from automation_run where status in (?, ?, ?)"))
                {
                    statement.setString(1, RunStatus.GEPLANT.value());
                    statement.setString(2, RunStatus.LAEUFT.value());
                    statement.setString(3, RunStatus.WARTET.value());
                    try (ResultSet rs = statement.executeQuery())
                    {
                        while (rs.next())
                            runIds.add(rs.getString(1));
                    }
                }

                if (runIds.isEmpty())
                {
                    connection.commit();
                    return 0;
                }

                try (PreparedStatement statement = connection.prepareStatement(
                    "update automation_decision set result_json = ?, resolved = ?, resolved_at = ? "
                        + "where resolved = ? and run_id in "
                        + "(select id from automation_run where status in (?, ?, ?))"))
                {
                    statement.setString(1, resultJson);
                    statement.setBoolean(2, true);
                    statement.setTimestamp(3, now);
                    statement.setBoolean(4, false);
                    statement.setString(5, RunStatus.GEPLANT.value());
                    statement.setString(6, RunStatus.LAEUFT.value());
                    statement.setString(7, RunStatus.WARTET.value());
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                    "insert into automation_run_log (run_id, created_at, level, message) values (?, ?, ?, ?)"))
                {
                    for (String runId : runIds)
                    {
                        statement.setString(1, runId);
                        statement.setTimestamp(2, now);
                        statement.setString(3, "error");
                        statement.setString(4, message);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                    "update automation_run set status = ?, warning = ?, error = ?, finished_at = ? "
                        + "where status in (?, ?, ?)"))
                {
                    statement.setString(1, RunStatus.FEHLGESCHLAGEN.value());
                    statement.setString(2, "");
                    statement.setString(3, message);
                    statement.setTimestamp(4, now);
                    statement.setString(5, RunStatus.GEPLANT.value());
                    statement.setString(6, RunStatus.LAEUFT.value());
                    statement.setString(7, RunStatus.WARTET.value());
                    statement.executeUpdate();
                }

                connection.commit();
                return runIds.size();
            }
            catch (Exception e)
            {
                connection.rollback();
                throw e;
            }
            finally
            {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    public void addLog(String runId, String level, String message) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into automation_run_log (run_id, created_at, level, message) values (?, ?, ?, ?)"))
        {
            statement.setString(1, runId);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, level);
            statement.setString(4, message);
            statement.executeUpdate();
        }
    }

    public List<AutomationLogEntry> listLogs(String runId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_run_log where run_id = ? order by id"))
        {
            statement.setString(1, runId);
            List<AutomationLogEntry> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(log(rs));
            }
            return result;
        }
    }

    public AutomationDecision createDecision(String runId, String type, String payloadJson) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "insert into automation_decision (run_id, decision_type, payload_json, result_json, resolved, created_at, resolved_at) values (?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS))
        {
            statement.setString(1, runId);
            statement.setString(2, type);
            statement.setString(3, payloadJson);
            statement.setString(4, "");
            statement.setBoolean(5, false);
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(7, null);
            statement.executeUpdate();
            return new AutomationDecision(generatedId(statement), runId, type, payloadJson, "", false,
                LocalDateTime.now(), null);
        }
    }

    public List<AutomationDecision> listOpenDecisions() throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_decision where resolved = ? order by id"))
        {
            statement.setBoolean(1, false);
            List<AutomationDecision> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(decision(rs));
            }
            return result;
        }
    }

    public List<AutomationDecision> listOpenDecisions(String runId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select * from automation_decision where run_id = ? and resolved = ? order by id"))
        {
            statement.setString(1, runId);
            statement.setBoolean(2, false);
            List<AutomationDecision> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery())
            {
                while (rs.next())
                    result.add(decision(rs));
            }
            return result;
        }
    }

    public void resolveDecision(String decisionId, String resultJson) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "update automation_decision set result_json = ?, resolved = ?, resolved_at = ? where id = ?"))
        {
            statement.setString(1, resultJson == null ? "" : resultJson);
            statement.setBoolean(2, true);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(4, decisionId);
            statement.executeUpdate();
        }
    }

    public void pruneHistory(String automationId, int keep) throws Exception
    {
        if (keep <= 0)
            return;
        List<AutomationRun> runs = listRunsPortable(automationId, Integer.MAX_VALUE);
        for (int i = keep; i < runs.size(); i++)
        {
            AutomationRun run = runs.get(i);
            if (run.status() == RunStatus.WARTET)
                continue;
            deleteRun(run.id());
        }
    }

    private void deleteRun(String runId) throws Exception
    {
        try (Connection connection = connectionProvider.getConnection())
        {
            connection.setAutoCommit(false);
            try
            {
                try (PreparedStatement statement = connection.prepareStatement(
                    "delete from automation_run_log where run_id = ?"))
                {
                    statement.setString(1, runId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "delete from automation_decision where run_id = ? and resolved = ?"))
                {
                    statement.setString(1, runId);
                    statement.setBoolean(2, true);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "delete from automation_run where id = ?"))
                {
                    statement.setString(1, runId);
                    statement.executeUpdate();
                }
                connection.commit();
            }
            catch (Exception e)
            {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void fillAutomation(PreparedStatement statement, Automation automation) throws Exception
    {
        statement.setString(1, automation.name());
        statement.setString(2, automation.description());
        statement.setBoolean(3, automation.active());
        statement.setString(4, automation.mode().value());
        statement.setString(5, automation.missedTriggerPolicy().value());
        statement.setString(6, automation.script());
        statement.setInt(7, automation.historyLimit());
    }

    private static void fillTrigger(PreparedStatement statement, AutomationTrigger trigger) throws Exception
    {
        statement.setString(1, trigger.automationId());
        statement.setString(2, trigger.name());
        statement.setBoolean(3, trigger.active());
        statement.setString(4, trigger.type());
        statement.setString(5, trigger.schedule());
        statement.setTimestamp(6, timestamp(trigger.nextRun()));
        statement.setTimestamp(7, timestamp(trigger.lastRun()));
    }

    private static Automation automation(ResultSet rs) throws Exception
    {
        return new Automation(rs.getString("id"), rs.getString("name"), rs.getString("description"),
            rs.getBoolean("active"), RunMode.parse(rs.getString("mode")),
            MissedTriggerPolicy.parse(rs.getString("missed_policy")), rs.getString("script"),
            rs.getInt("history_limit"));
    }

    private static AutomationTrigger trigger(ResultSet rs) throws Exception
    {
        return new AutomationTrigger(rs.getString("id"), rs.getString("automation_id"), rs.getString("name"),
            rs.getBoolean("active"), rs.getString("trigger_type"), rs.getString("schedule_expr"),
            localDateTime(rs.getTimestamp("next_run")), localDateTime(rs.getTimestamp("last_run")));
    }

    private static AutomationRun run(ResultSet rs) throws Exception
    {
        return new AutomationRun(rs.getString("id"), rs.getString("automation_id"), rs.getString("trigger_id"),
            RunStatus.parse(rs.getString("status")), rs.getString("source"), rs.getBoolean("test_run"),
            localDateTime(rs.getTimestamp("started_at")), localDateTime(rs.getTimestamp("finished_at")),
            rs.getString("warning"), rs.getString("error"));
    }

    private static AutomationLogEntry log(ResultSet rs) throws Exception
    {
        return new AutomationLogEntry(rs.getString("id"), rs.getString("run_id"),
            localDateTime(rs.getTimestamp("created_at")), rs.getString("level"), rs.getString("message"));
    }

    private static AutomationDecision decision(ResultSet rs) throws Exception
    {
        return new AutomationDecision(rs.getString("id"), rs.getString("run_id"), rs.getString("decision_type"),
            rs.getString("payload_json"), rs.getString("result_json"), rs.getBoolean("resolved"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("resolved_at")));
    }

    private static String generatedId(PreparedStatement statement) throws Exception
    {
        try (ResultSet keys = statement.getGeneratedKeys())
        {
            if (keys.next())
                return keys.getString(1);
        }
        throw new IllegalStateException("Keine generierte ID erhalten.");
    }

    private static Timestamp timestamp(LocalDateTime value)
    {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value)
    {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String json(String value)
    {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
