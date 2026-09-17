package com.duoc.sumativa.bff.service;

import com.duoc.sumativa.bff.config.FunctionsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter between the public REST contract of the BFF and the GraphQL
 * roles-function deployed in Azure.
 */
@Service
public class RolesGraphqlService {

    private final RestTemplate restTemplate;
    private final FunctionsProperties functionsProperties;
    private final ObjectMapper objectMapper;

    public RolesGraphqlService(
            RestTemplate restTemplate,
            FunctionsProperties functionsProperties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.functionsProperties = functionsProperties;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> listRoles() {
        String query = "query { roles { id name description } }";
        return executeAndUnwrap(query, Map.of(), "roles", false);
    }

    public ResponseEntity<String> getRole(long id) {
        String query = "query($id: ID!) { role(id: $id) { id name description } }";
        return executeAndUnwrap(query, Map.of("id", String.valueOf(id)), "role", true);
    }

    public ResponseEntity<String> createRole(String name, String description) {
        String query = "mutation($name: String!, $description: String) { " +
                "createRole(name: $name, description: $description) { id name description } }";

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("name", name);
        variables.put("description", description);
        return executeAndUnwrap(query, variables, "createRole", false, HttpStatus.CREATED);
    }

    public ResponseEntity<String> updateRole(long id, String name, String description) {
        String query = "mutation($id: ID!, $name: String!, $description: String) { " +
                "updateRole(id: $id, name: $name, description: $description) { id name description } }";

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", String.valueOf(id));
        variables.put("name", name);
        variables.put("description", description);
        return executeAndUnwrap(query, variables, "updateRole", true);
    }

    public ResponseEntity<String> deleteRole(long id) {
        String query = "mutation($id: ID!) { deleteRole(id: $id) }";
        ResponseEntity<String> raw = executeRaw(query, Map.of("id", String.valueOf(id)));

        if (!raw.getStatusCode().is2xxSuccessful() || raw.getBody() == null) {
            return raw;
        }

        try {
            JsonNode root = objectMapper.readTree(raw.getBody());
            if (root.has("errors")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(root.toString());
            }

            JsonNode deleted = root.path("data").path("deleteRole");
            if (deleted.isBoolean() && deleted.asBoolean()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"Role " + id + " not found\"}");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Invalid GraphQL response from roles-function\"}");
        }
    }

    private ResponseEntity<String> executeAndUnwrap(
            String query,
            Map<String, Object> variables,
            String field,
            boolean notFoundWhenNull) {
        return executeAndUnwrap(query, variables, field, notFoundWhenNull, HttpStatus.OK);
    }

    private ResponseEntity<String> executeAndUnwrap(
            String query,
            Map<String, Object> variables,
            String field,
            boolean notFoundWhenNull,
            HttpStatus successStatus) {

        ResponseEntity<String> raw = executeRaw(query, variables);
        if (!raw.getStatusCode().is2xxSuccessful() || raw.getBody() == null) {
            return raw;
        }

        try {
            JsonNode root = objectMapper.readTree(raw.getBody());
            if (root.has("errors")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(root.toString());
            }

            JsonNode value = root.path("data").get(field);
            if (value == null || value.isNull()) {
                if (notFoundWhenNull) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("{\"error\":\"Role not found\"}");
                }
                return ResponseEntity.status(successStatus).body("null");
            }

            return ResponseEntity.status(successStatus).body(value.toString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Invalid GraphQL response from roles-function\"}");
        }
    }

    private ResponseEntity<String> executeRaw(String query, Map<String, Object> variables) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("query", query);
            payload.set("variables", objectMapper.valueToTree(variables));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);

            return restTemplate.exchange(
                    functionsProperties.getRolesGraphqlUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach roles GraphQL function: " + safe(ex.getMessage()) + "\"}");
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "unknown error";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
