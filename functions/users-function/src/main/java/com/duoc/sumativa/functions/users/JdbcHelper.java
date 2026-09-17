package com.duoc.sumativa.functions.users;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Small helper responsible for opening JDBC connections to Oracle.
 * Connection info is read from environment variables so it can be configured
 * per-environment (local.settings.json for local dev, Application Settings
 * once deployed to Azure) without any code change.
 */
public final class JdbcHelper {

    private JdbcHelper() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("ORACLE_URL");
        String user = System.getenv("ORACLE_USER");
        String password = System.getenv("ORACLE_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new SQLException("Missing ORACLE_URL / ORACLE_USER / ORACLE_PASSWORD environment variables");
        }

        return DriverManager.getConnection(url, user, password);
    }
}
