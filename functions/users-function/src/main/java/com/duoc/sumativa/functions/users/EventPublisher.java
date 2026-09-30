package com.duoc.sumativa.functions.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Publishes a single Event Grid schema event after a successful database mutation. */
final class EventPublisher {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private EventPublisher() { }

    static void publish(String type, String subject, Map<String, Object> data, Logger logger) {
        String endpoint = System.getenv("EVENT_GRID_TOPIC_ENDPOINT");
        String key = System.getenv("EVENT_GRID_TOPIC_KEY");
        if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()) {
            logger.warning("Event Grid is not configured; no event published for " + type);
            return;
        }
        String id = UUID.randomUUID().toString();
        try {
            String body = JSON.writeValueAsString(List.of(Map.of(
                    "id", id, "eventType", type, "subject", subject,
                    "eventTime", Instant.now().toString(), "data", data, "dataVersion", "1.0")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.contains("?") ? endpoint : endpoint + "?api-version=2018-01-01"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("aeg-sas-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                logger.severe("Event Grid rejected event " + id + " with HTTP " + response.statusCode());
            } else {
                logger.info("Event Grid accepted " + type + " eventId=" + id);
            }
        } catch (Exception e) {
            logger.severe("Could not publish Event Grid event " + id + ": " + e.getMessage());
        }
    }
}
