package com.careerintelligence.database;

import com.careerintelligence.util.EnvLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central place that knows how to open a JDBC connection to MySQL.
 * Reads configuration from the ".env" file (see util.EnvLoader) so no
 * credentials are hard-coded in source.
 *
 * DAO classes call {@link #getConnection()} to obtain a fresh connection
 * per operation (simple and safe for a teaching/console project; a pooled
 * DataSource such as HikariCP is a natural upgrade for future extensibility.)
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static volatile boolean driverLoaded = false;

    private DatabaseConnection() {
    }

    private static void loadDriverIfNeeded() {
        if (!driverLoaded) {
            synchronized (DatabaseConnection.class) {
                if (!driverLoaded) {
                    try {
                        Class.forName(DRIVER_CLASS);
                        driverLoaded = true;
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(
                                "MySQL JDBC driver not found on classpath. "
                                        + "Run 'mvn clean install' so dependencies are downloaded.", e);
                    }
                }
            }
        }
    }

    private static String buildJdbcUrl() {
        String explicitUrl = EnvLoader.get("DB_URL", null);
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        String host = EnvLoader.get("DB_HOST", "localhost");
        String port = EnvLoader.get("DB_PORT", "3306");
        String dbName = EnvLoader.get("DB_NAME", "career_intelligence_db");
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    }

    /**
     * Opens and returns a new JDBC connection. Callers are responsible for
     * closing it (try-with-resources is recommended, see any DAO method).
     */
    public static Connection getConnection() throws SQLException {
        loadDriverIfNeeded();
        String url = buildJdbcUrl();
        String user = EnvLoader.get("DB_USER", "root");
        String password = EnvLoader.get("DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Simple connectivity check used at application startup so the user
     * gets a clear error message instead of a stack trace on first query.
     * Catches both SQLException (bad host/credentials/DB not running) and
     * the driver-not-on-classpath IllegalStateException, so a
     * misconfigured environment always produces a readable message
     * instead of crashing Main.main with a raw stack trace.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return false;
        } catch (IllegalStateException e) {
            System.err.println("Database driver problem: " + e.getMessage());
            return false;
        }
    }
}
