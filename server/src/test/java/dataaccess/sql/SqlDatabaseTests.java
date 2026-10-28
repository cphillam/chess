package dataaccess.sql;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import com.google.gson.Gson;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SqlDatabaseTests {
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

        testDatabaseName = "chess-schema-test-" + UUID.randomUUID();
        testProperties = new Properties();
        testProperties.putAll(originalProperties);
        testProperties.setProperty("db.name", testDatabaseName);
        loadProperties(testProperties);
    }

    @AfterEach
    public void removeTemporaryDatabase() throws Exception {
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

    @Test
    public void initializeCreatesConfiguredDatabaseAndAllTables() throws Exception {
        SqlDatabase.initialize();

        try (Connection conn = DatabaseManager.getConnection();
             var tables = conn.getMetaData().getTables(testDatabaseName, null, "%", new String[]{"TABLE"})) {
            Set<String> tableNames = new HashSet<>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
            assertEquals(testDatabaseName, conn.getCatalog());
            assertEquals(Set.of("users", "auth_tokens", "games"), tableNames);
        }
    }

    @Test
    public void reinitializingPreservesUsersAndAuthTokens() throws Exception {
        SqlDatabase.initialize();
        String passwordHash = BCrypt.hashpw("sample-password", BCrypt.gensalt(4));
        executeUpdate("INSERT INTO users (username, password, email) VALUES (?, ?, ?)",
                "alice", passwordHash, "alice@example.com");
        executeUpdate("INSERT INTO auth_tokens (auth_token, username) VALUES (?, ?)", "alice-token", "alice");

        SqlDatabase.initialize();

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("""
                     SELECT users.username, password, email, auth_token
                     FROM users JOIN auth_tokens ON users.username = auth_tokens.username
                     """);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("alice", rows.getString("username"));
            assertEquals(passwordHash, rows.getString("password"));
            assertEquals("alice@example.com", rows.getString("email"));
            assertEquals("alice-token", rows.getString("auth_token"));
            assertFalse(rows.next());
        }
    }

    @Test
    public void reinitializingPreservesGameIdAndState() throws Exception {
        SqlDatabase.initialize();
        ChessGame game = new ChessGame();
        game.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        Gson gson = new Gson();
        int gameId = insertGame("Existing game", gson.toJson(game));

        SqlDatabase.initialize();

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("SELECT game_id, game_name, game_json FROM games");
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals(gameId, rows.getInt("game_id"));
            assertEquals("Existing game", rows.getString("game_name"));
            assertEquals(game, gson.fromJson(rows.getString("game_json"), ChessGame.class));
            assertFalse(rows.next());
        }
    }

    @Test
    public void gamesHaveGeneratedIdsAndNullablePlayerSeats() throws Exception {
        SqlDatabase.initialize();
        String json = new Gson().toJson(new ChessGame());

        int firstId = insertGame("First game", json);
        int secondId = insertGame("Second game", json);

        assertTrue(firstId > 0);
        assertTrue(secondId > 0);
        assertNotEquals(firstId, secondId);
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("SELECT white_username, black_username FROM games");
             var rows = statement.executeQuery()) {
            int count = 0;
            while (rows.next()) {
                assertNull(rows.getString("white_username"));
                assertNull(rows.getString("black_username"));
                count++;
            }
            assertEquals(2, count);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users (username, password, email) VALUES (?, ?, 'alice@example.com')",
            "INSERT INTO auth_tokens (auth_token, username) VALUES (?, ?)"
    })
    public void duplicateUsernamesAndAuthTokensAreRejected(String statement) throws Exception {
        SqlDatabase.initialize();
        executeUpdate(statement, "alice", "stored-value");

        assertThrows(SQLException.class, () -> executeUpdate(statement, "alice", "stored-value"));
    }

    @Test
    public void initializeReportsConnectionFailures() throws Exception {
        testProperties.setProperty("db.port", "100000");
        loadProperties(testProperties);

        DataAccessException exception = assertThrows(DataAccessException.class, SqlDatabase::initialize);

        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    public void getConnectionReportsMissingDatabase() {
        DataAccessException exception = assertThrows(DataAccessException.class, DatabaseManager::getConnection);

        assertInstanceOf(SQLException.class, exception.getCause());
    }

    private void executeUpdate(String sql, String... parameters) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private int insertGame(String gameName, String json) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(
                     "INSERT INTO games (game_name, game_json) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, gameName);
            statement.setString(2, json);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Game ID was not generated");
                }
                return keys.getInt(1);
            }
        }
    }

    private void loadProperties(Properties properties) throws ReflectiveOperationException {
        var method = DatabaseManager.class.getDeclaredMethod("loadProperties", Properties.class);
        method.setAccessible(true);
        method.invoke(null, properties);
    }
}
