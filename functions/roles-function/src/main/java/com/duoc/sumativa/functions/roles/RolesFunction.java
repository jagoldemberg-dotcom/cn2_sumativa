package com.duoc.sumativa.functions.roles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Azure Function that exposes the ROLES domain through GraphQL.
 *
 * Endpoint:
 *   POST /api/graphql
 *
 * Example body:
 *   {"query":"query { roles { id name description } }"}
 *
 * The function talks directly to Oracle through JDBC. The BFF consumes this
 * GraphQL endpoint while keeping a REST-friendly contract for external clients.
 */
public class RolesFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SCHEMA = """
            type Role {
              id: ID!
              name: String!
              description: String
            }

            type Query {
              roles: [Role!]!
              role(id: ID!): Role
            }

            type Mutation {
              createRole(name: String!, description: String): Role!
              updateRole(id: ID!, name: String!, description: String): Role
              deleteRole(id: ID!): Boolean!
            }
            """;

    private static final GraphQL GRAPHQL = buildGraphQL();

    @FunctionName("rolesGraphql")
    public HttpResponseMessage graphql(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "graphql",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            JsonNode body = MAPPER.readTree(request.getBody().orElse("{}"));
            String query = body.hasNonNull("query") ? body.get("query").asText() : null;

            if (query == null || query.isBlank()) {
                return jsonResponse(request, HttpStatus.BAD_REQUEST,
                        Map.of("error", "GraphQL field 'query' is required"));
            }

            Map<String, Object> variables = Collections.emptyMap();
            if (body.has("variables") && !body.get("variables").isNull()) {
                variables = MAPPER.convertValue(
                        body.get("variables"),
                        new TypeReference<Map<String, Object>>() { }
                );
            }

            ExecutionInput.Builder input = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables);

            if (body.hasNonNull("operationName") && !body.get("operationName").asText().isBlank()) {
                input.operationName(body.get("operationName").asText());
            }

            ExecutionResult result = GRAPHQL.execute(input.build());
            return jsonResponse(request, HttpStatus.OK, result.toSpecification());
        } catch (Exception e) {
            context.getLogger().severe("rolesGraphql failed: " + e.getMessage());
            return jsonResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("error", "Could not execute GraphQL request"));
        }
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(SCHEMA);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("roles", env -> findAllRoles())
                        .dataFetcher("role", env -> findRole(asLong(env.getArgument("id")))))
                .type("Mutation", builder -> builder
                        .dataFetcher("createRole", env -> createRole(
                                env.getArgument("name"),
                                env.getArgument("description")))
                        .dataFetcher("updateRole", env -> updateRole(
                                asLong(env.getArgument("id")),
                                env.getArgument("name"),
                                env.getArgument("description")))
                        .dataFetcher("deleteRole", env -> deleteRole(asLong(env.getArgument("id")))))
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private static long asLong(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private static List<Map<String, Object>> findAllRoles() throws SQLException {
        String sql = "SELECT ID, NAME, DESCRIPTION FROM ROLES ORDER BY ID";
        List<Map<String, Object>> roles = new ArrayList<>();

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                roles.add(toRoleMap(rs));
            }
        }
        return roles;
    }

    private static Map<String, Object> findRole(long id) throws SQLException {
        String sql = "SELECT ID, NAME, DESCRIPTION FROM ROLES WHERE ID = ?";

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toRoleMap(rs) : null;
            }
        }
    }

    private static Map<String, Object> createRole(String name, String description) throws SQLException {
        String sql = "INSERT INTO ROLES (NAME, DESCRIPTION) VALUES (?, ?)";

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"ID"})) {

            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Oracle did not return the generated role ID");
                }

                Map<String, Object> role = new LinkedHashMap<>();
                role.put("id", keys.getLong(1));
                role.put("name", name);
                role.put("description", description);
                return role;
            }
        }
    }

    private static Map<String, Object> updateRole(long id, String name, String description) throws SQLException {
        String sql = "UPDATE ROLES SET NAME = ?, DESCRIPTION = ? WHERE ID = ?";

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                return null;
            }

            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", id);
            role.put("name", name);
            role.put("description", description);
            return role;
        }
    }

    private static boolean deleteRole(long id) throws SQLException {
        String sql = "DELETE FROM ROLES WHERE ID = ?";

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Map<String, Object> toRoleMap(ResultSet rs) throws SQLException {
        Map<String, Object> role = new LinkedHashMap<>();
        role.put("id", rs.getLong("ID"));
        role.put("name", rs.getString("NAME"));
        role.put("description", rs.getString("DESCRIPTION"));
        return role;
    }

    private HttpResponseMessage jsonResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus status,
            Object body) {
        try {
            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Serialization error\"}")
                    .build();
        }
    }
}
