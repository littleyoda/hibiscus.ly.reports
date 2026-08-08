package de.open4me.hibiscus.reports.automation.sql;

import java.util.ArrayList;
import java.util.List;

public final class AutomationSqlChange
{
    private final int version;
    private final List<String> statements;

    private AutomationSqlChange(int version, List<String> statements)
    {
        this.version = version;
        this.statements = List.copyOf(statements);
    }

    int version()
    {
        return version;
    }

    List<String> statements()
    {
        return statements;
    }

    static List<AutomationSqlChange> changesSince(int currentVersion, AutomationSqlDialect dialect)
    {
        List<AutomationSqlChange> changes = new ArrayList<>();
        if (currentVersion < 1)
        {
            String id = dialect.idColumn();
            String text = dialect.text();
            String bool = dialect.bool();
            String ts = dialect.timestamp();
            changes.add(new AutomationSqlChange(1, List.of(
                "create table automation_cfg (id " + id + ", cfg_key varchar(200) not null, cfg_value varchar(2000), primary key (id))",
                "insert into automation_cfg (cfg_key, cfg_value) values ('dbversion', '1')",
                "create table automation_automation (id " + id
                    + ", name varchar(255) not null, description " + text + ", active " + bool
                    + " not null, mode varchar(20) not null, missed_policy varchar(20) not null, script " + text
                    + ", history_limit int not null, created_at " + ts
                    + ", updated_at " + ts + ", primary key (id))",
                "create table automation_trigger (id " + id
                    + ", automation_id int not null, name varchar(255), active " + bool
                    + " not null, trigger_type varchar(40) not null, schedule_expr varchar(255), next_run "
                    + ts + ", last_run " + ts + ", primary key (id))",
                "create index idx_automation_trigger_due on automation_trigger(active, next_run)",
                "create table automation_run (id " + id
                    + ", automation_id int not null, trigger_id int, status varchar(40) not null, source varchar(40), test_run "
                    + bool + " not null, started_at " + ts + ", finished_at " + ts + ", warning " + text
                    + ", error " + text + ", primary key (id))",
                "create index idx_automation_run_automation on automation_run(automation_id, started_at)",
                "create table automation_run_log (id " + id
                    + ", run_id int not null, created_at " + ts + ", level varchar(20) not null, message "
                    + text + ", primary key (id))",
                "create table automation_decision (id " + id
                    + ", run_id int not null, decision_type varchar(40) not null, payload_json " + text
                    + ", result_json " + text + ", resolved " + bool + " not null, created_at " + ts
                    + ", resolved_at " + ts + ", primary key (id))")));
        }
        if (currentVersion < 2)
        {
            changes.add(new AutomationSqlChange(2, List.of(
                "alter table automation_run drop column if exists condition_override")));
        }
        if (currentVersion < 3)
        {
            String id = dialect.idColumn();
            String ts = dialect.timestamp();
            changes.add(new AutomationSqlChange(3, List.of(
                "create table automation_trigger_event (id " + id
                    + ", trigger_id int not null, event_type varchar(40) not null, event_key varchar(200) not null, created_at "
                    + ts + ", primary key (id))",
                "create unique index idx_automation_trigger_event_unique on automation_trigger_event(trigger_id, event_type, event_key)")));
        }
        return changes;
    }
}
