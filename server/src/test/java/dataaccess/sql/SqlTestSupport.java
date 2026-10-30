package dataaccess.sql;

import dataaccess.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

abstract class SqlTestSupport {
    private Properties originalProperties;
    private String testDatabaseName;

    @BeforeEach
    public void configureTestDatabase() throws Exception {
        originalProperties = new Properties();
        try (var stream = DatabaseManager.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Unable to load db.properties");
            }
            originalProperties.load(stream);
        }

        testDatabaseName = originalProperties.getProperty("db.name");
        loadProperties(originalProperties);
        dropTestDatabase();
    }

    @AfterEach
    public void removeTestDatabase() throws Exception {
        if (testDatabaseName == null) {
            return;
        }
        try {
            dropTestDatabase();
        } finally {
            loadProperties(originalProperties);
        }
    }

    protected String getTestDatabaseName() {
        return testDatabaseName;
    }

    protected void makeDatabaseUnavailable() throws ReflectiveOperationException {
        Properties unavailableProperties = new Properties();
        unavailableProperties.putAll(originalProperties);
        unavailableProperties.setProperty("db.port", "100000");
        loadProperties(unavailableProperties);
    }

    private void dropTestDatabase() throws SQLException {
        String url = "jdbc:mysql://%s:%s".formatted(
                originalProperties.getProperty("db.host"), originalProperties.getProperty("db.port"));
        String quotedName = "`" + testDatabaseName.replace("`", "``") + "`";
        try (Connection conn = DriverManager.getConnection(url,
                originalProperties.getProperty("db.user"), originalProperties.getProperty("db.password"));
             var statement = conn.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + quotedName);
        }
    }

    private void loadProperties(Properties properties) throws ReflectiveOperationException {
        var method = DatabaseManager.class.getDeclaredMethod("loadProperties", Properties.class);
        method.setAccessible(true);
        method.invoke(null, properties);
    }
}
