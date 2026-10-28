package dataaccess.sql;

import dataaccess.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

abstract class SqlTestSupport {
    private Properties originalProperties;
    private Properties testProperties;
    private String testDatabaseName;

    @BeforeEach
    public void configureTemporaryDatabase() throws Exception {
        originalProperties = new Properties();
        try (var stream = DatabaseManager.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Unable to load db.properties");
            }
            originalProperties.load(stream);
        }

        testDatabaseName = "chess-sql-test-" + UUID.randomUUID();
        testProperties = new Properties();
        testProperties.putAll(originalProperties);
        testProperties.setProperty("db.name", testDatabaseName);
        loadProperties(testProperties);
    }

    @AfterEach
    public void removeTemporaryDatabase() throws Exception {
        if (testDatabaseName == null) {
            return;
        }
        String url = "jdbc:mysql://%s:%s".formatted(
                originalProperties.getProperty("db.host"), originalProperties.getProperty("db.port"));
        try (Connection conn = DriverManager.getConnection(url,
                originalProperties.getProperty("db.user"), originalProperties.getProperty("db.password"));
             var statement = conn.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + testDatabaseName + "`");
        } finally {
            loadProperties(originalProperties);
        }
    }

    protected String getTestDatabaseName() {
        return testDatabaseName;
    }

    protected void makeDatabaseUnavailable() throws ReflectiveOperationException {
        Properties unavailableProperties = new Properties();
        unavailableProperties.putAll(testProperties);
        unavailableProperties.setProperty("db.port", "100000");
        loadProperties(unavailableProperties);
    }

    private void loadProperties(Properties properties) throws ReflectiveOperationException {
        var method = DatabaseManager.class.getDeclaredMethod("loadProperties", Properties.class);
        method.setAccessible(true);
        method.invoke(null, properties);
    }
}
