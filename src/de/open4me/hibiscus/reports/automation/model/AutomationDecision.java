package de.open4me.hibiscus.reports.automation.model;

import java.time.LocalDateTime;

public record AutomationDecision(String id, String runId, String type, String payloadJson, String resultJson,
                                 boolean resolved, LocalDateTime createdAt, LocalDateTime resolvedAt)
{
    public AutomationDecision
    {
        type = type == null ? "" : type;
        payloadJson = payloadJson == null ? "" : payloadJson;
        resultJson = resultJson == null ? "" : resultJson;
    }
}
