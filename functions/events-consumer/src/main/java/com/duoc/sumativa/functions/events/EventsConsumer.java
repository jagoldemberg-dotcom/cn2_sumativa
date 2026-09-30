package com.duoc.sumativa.functions.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Event Grid destination plus read-only audit endpoint for the demonstration. */
public class EventsConsumer {
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionName("consumeUserRoleEvent")
    public void consume(@EventGridTrigger(name = "event") String payload,
                        final ExecutionContext context) throws Exception {
        JsonNode event = JSON.readTree(payload);
        String id = required(event, "id");
        String type = required(event, "eventType");
        String subject = required(event, "subject");
        if (!type.startsWith("Sumativa.") || event.path("data").isMissingNode()) {
            throw new IllegalArgumentException("Unexpected event schema");
        }
        String sql = "INSERT INTO EVENT_AUDIT "
                + "(EVENT_ID, EVENT_TYPE, SUBJECT, EVENT_TIME, EVENT_DATA) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, subject);
            ps.setString(4, required(event, "eventTime"));
            ps.setString(5, event.get("data").toString());
            ps.executeUpdate();
            context.getLogger().info("Event consumed eventId=" + id + " type=" + type);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) { // ORA-00001: Event Grid redelivery
                context.getLogger().info("Duplicate event ignored eventId=" + id);
                return;
            }
            throw e; // A failure causes Event Grid to retry delivery.
        }
    }

    private static String required(JsonNode event, String name) {
        JsonNode field = event.get(name);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException("Missing event field " + name);
        }
        return field.asText();
    }

    @FunctionName("listEventAudit")
    public HttpResponseMessage list(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "events",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        String sql = "SELECT EVENT_ID, EVENT_TYPE, SUBJECT, EVENT_TIME, EVENT_DATA, RECEIVED_AT "
                + "FROM (SELECT EVENT_ID, EVENT_TYPE, SUBJECT, EVENT_TIME, EVENT_DATA, RECEIVED_AT "
                + "FROM EVENT_AUDIT ORDER BY RECEIVED_AT DESC) WHERE ROWNUM <= 30";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> items = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("EVENT_ID"));
                row.put("eventType", rs.getString("EVENT_TYPE"));
                row.put("subject", rs.getString("SUBJECT"));
                row.put("eventTime", rs.getString("EVENT_TIME"));
                row.put("data", JSON.readTree(rs.getString("EVENT_DATA")));
                row.put("receivedAt", rs.getTimestamp("RECEIVED_AT").toString());
                items.add(row);
            }
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(JSON.writeValueAsString(items)).build();
        } catch (Exception e) {
            context.getLogger().severe("Audit read failed: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Could not read audit\"}").build();
        }
    }
}
