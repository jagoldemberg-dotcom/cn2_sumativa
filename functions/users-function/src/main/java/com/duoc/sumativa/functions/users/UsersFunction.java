package com.duoc.sumativa.functions.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Azure Function (Java) exposing CRUD endpoints for USERS and their role
 * assignments. Talks directly to Oracle via hand-written JDBC (no ORM).
 */
public class UsersFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -----------------------------------------------------------------
    // GET /api/users -> list all users
    // -----------------------------------------------------------------
    @FunctionName("listUsers")
    public HttpResponseMessage listUsers(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "users", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        List<Map<String, Object>> users = new ArrayList<>();

        String sql = "SELECT ID, USERNAME, EMAIL, CREATED_AT FROM USERS ORDER BY ID";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(toUserMap(rs));
            }
            return jsonResponse(request, HttpStatus.OK, users);
        } catch (SQLException e) {
            context.getLogger().severe("listUsers failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // GET /api/users/{id} -> get one user with its roles
    // -----------------------------------------------------------------
    @FunctionName("getUser")
    public HttpResponseMessage getUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "users/{id}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") long id,
            final ExecutionContext context) {

        String sql = "SELECT ID, USERNAME, EMAIL, CREATED_AT FROM USERS WHERE ID = ?";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            Map<String, Object> user;
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return errorResponse(request, HttpStatus.NOT_FOUND, "User " + id + " not found");
                }
                user = toUserMap(rs);
            }
            user.put("roles", findRolesForUser(conn, id));
            return jsonResponse(request, HttpStatus.OK, user);
        } catch (SQLException e) {
            context.getLogger().severe("getUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // POST /api/users -> create user
    // -----------------------------------------------------------------
    @FunctionName("createUser")
    public HttpResponseMessage createUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "users", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        JsonNode body;
        try {
            body = parseBody(request);
        } catch (Exception e) {
            return errorResponse(request, HttpStatus.BAD_REQUEST, "Invalid JSON body");
        }

        String username = textOrNull(body, "username");
        String email = textOrNull(body, "email");
        String password = textOrNull(body, "password");

        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            return errorResponse(request, HttpStatus.BAD_REQUEST, "username, email and password are required");
        }

        String passwordHash = hashPassword(password);

        String sql = "INSERT INTO USERS (USERNAME, EMAIL, PASSWORD_HASH) VALUES (?, ?, ?)";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"ID"})) {

            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            long generatedId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                generatedId = keys.getLong(1);
            }

            Map<String, Object> created = new LinkedHashMap<>();
            created.put("id", generatedId);
            created.put("username", username);
            created.put("email", email);
            return jsonResponse(request, HttpStatus.CREATED, created);
        } catch (SQLException e) {
            context.getLogger().severe("createUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.BAD_REQUEST, "Could not create user: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // PUT /api/users/{id} -> update user
    // -----------------------------------------------------------------
    @FunctionName("updateUser")
    public HttpResponseMessage updateUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, route = "users/{id}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") long id,
            final ExecutionContext context) {

        JsonNode body;
        try {
            body = parseBody(request);
        } catch (Exception e) {
            return errorResponse(request, HttpStatus.BAD_REQUEST, "Invalid JSON body");
        }

        String username = textOrNull(body, "username");
        String email = textOrNull(body, "email");
        String password = textOrNull(body, "password");

        if (isBlank(username) || isBlank(email)) {
            return errorResponse(request, HttpStatus.BAD_REQUEST, "username and email are required");
        }

        String sql = isBlank(password)
                ? "UPDATE USERS SET USERNAME = ?, EMAIL = ? WHERE ID = ?"
                : "UPDATE USERS SET USERNAME = ?, EMAIL = ?, PASSWORD_HASH = ? WHERE ID = ?";

        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, email);
            if (isBlank(password)) {
                ps.setLong(3, id);
            } else {
                ps.setString(3, hashPassword(password));
                ps.setLong(4, id);
            }

            int updated = ps.executeUpdate();
            if (updated == 0) {
                return errorResponse(request, HttpStatus.NOT_FOUND, "User " + id + " not found");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("username", username);
            result.put("email", email);
            return jsonResponse(request, HttpStatus.OK, result);
        } catch (SQLException e) {
            context.getLogger().severe("updateUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.BAD_REQUEST, "Could not update user: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // DELETE /api/users/{id} -> delete user
    // -----------------------------------------------------------------
    @FunctionName("deleteUser")
    public HttpResponseMessage deleteUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, route = "users/{id}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") long id,
            final ExecutionContext context) {

        String sql = "DELETE FROM USERS WHERE ID = ?";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                return errorResponse(request, HttpStatus.NOT_FOUND, "User " + id + " not found");
            }
            return request.createResponseBuilder(HttpStatus.NO_CONTENT).build();
        } catch (SQLException e) {
            context.getLogger().severe("deleteUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // POST /api/users/{id}/roles/{roleId} -> assign role to user
    // -----------------------------------------------------------------
    @FunctionName("assignRoleToUser")
    public HttpResponseMessage assignRoleToUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "users/{id}/roles/{roleId}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") long id,
            @BindingName("roleId") long roleId,
            final ExecutionContext context) {

        String sql = "INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (?, ?)";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.setLong(2, roleId);
            ps.executeUpdate();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", id);
            result.put("roleId", roleId);
            result.put("status", "ASSIGNED");
            return jsonResponse(request, HttpStatus.CREATED, result);
        } catch (SQLException e) {
            context.getLogger().severe("assignRoleToUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.BAD_REQUEST, "Could not assign role: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // DELETE /api/users/{id}/roles/{roleId} -> remove role from user
    // -----------------------------------------------------------------
    @FunctionName("removeRoleFromUser")
    public HttpResponseMessage removeRoleFromUser(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, route = "users/{id}/roles/{roleId}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") long id,
            @BindingName("roleId") long roleId,
            final ExecutionContext context) {

        String sql = "DELETE FROM USER_ROLES WHERE USER_ID = ? AND ROLE_ID = ?";
        try (Connection conn = JdbcHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.setLong(2, roleId);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                return errorResponse(request, HttpStatus.NOT_FOUND, "Role assignment not found for user " + id + " and role " + roleId);
            }
            return request.createResponseBuilder(HttpStatus.NO_CONTENT).build();
        } catch (SQLException e) {
            context.getLogger().severe("removeRoleFromUser failed: " + e.getMessage());
            return errorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private List<Map<String, Object>> findRolesForUser(Connection conn, long userId) throws SQLException {
        String sql = "SELECT r.ID, r.NAME, r.DESCRIPTION " +
                "FROM ROLES r JOIN USER_ROLES ur ON ur.ROLE_ID = r.ID " +
                "WHERE ur.USER_ID = ? ORDER BY r.ID";
        List<Map<String, Object>> roles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> role = new LinkedHashMap<>();
                    role.put("id", rs.getLong("ID"));
                    role.put("name", rs.getString("NAME"));
                    role.put("description", rs.getString("DESCRIPTION"));
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private Map<String, Object> toUserMap(ResultSet rs) throws SQLException {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", rs.getLong("ID"));
        user.put("username", rs.getString("USERNAME"));
        user.put("email", rs.getString("EMAIL"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        user.put("createdAt", createdAt != null ? createdAt.toString() : null);
        return user;
    }

    private JsonNode parseBody(HttpRequestMessage<Optional<String>> request) throws Exception {
        String raw = request.getBody().orElse("{}");
        return MAPPER.readTree(raw);
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Very small placeholder hash so we never store the plain password.
     * NOTE: for a real production system this must be replaced by a proper
     * password hashing algorithm (BCrypt/Argon2). Kept simple here since
     * authentication is out of scope for this assignment.
     */
    private String hashPassword(String rawPassword) {
        return Integer.toHexString(rawPassword.hashCode()) + ":" + rawPassword.length();
    }

    private HttpResponseMessage jsonResponse(HttpRequestMessage<Optional<String>> request, HttpStatus status, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Serialization error\"}")
                    .build();
        }
    }

    private HttpResponseMessage errorResponse(HttpRequestMessage<Optional<String>> request, HttpStatus status, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        return jsonResponse(request, status, error);
    }
}
