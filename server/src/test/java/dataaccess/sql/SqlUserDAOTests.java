package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.UserDAO;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SqlUserDAOTests extends SqlTestSupport {
    private static final String PASSWORD = "sample-password";
    private static final String PASSWORD_HASH = BCrypt.hashpw(PASSWORD, BCrypt.gensalt(4));
    private static final UserData ALICE = new UserData("alice", PASSWORD_HASH, "alice@example.com");

    private UserDAO userDAO;

    @BeforeEach
    public void setupDAO() throws DataAccessException {
        userDAO = new SqlUserDAO();
    }

    @Test
    public void createAndGetPreservesEveryFieldAndPasswordHash() throws DataAccessException {
        userDAO.createUser(ALICE);

        UserData stored = userDAO.getUser(ALICE.username());

        assertEquals(ALICE, stored);
        assertNotEquals(PASSWORD, stored.password());
        assertTrue(BCrypt.checkpw(PASSWORD, stored.password()));
    }

    @Test
    public void createdUserPersistsAcrossDAOInstances() throws DataAccessException {
        userDAO.createUser(ALICE);

        UserDAO reopenedDAO = new SqlUserDAO();

        assertEquals(ALICE, reopenedDAO.getUser(ALICE.username()));
    }

    @Test
    public void missingUserReturnsNull() throws DataAccessException {
        assertNull(userDAO.getUser("unknown"));
        assertNull(userDAO.getUser(null));
    }

    @Test
    public void duplicateUsernamePreservesOriginalUser() throws DataAccessException {
        userDAO.createUser(ALICE);
        UserData duplicate = new UserData(ALICE.username(),
                BCrypt.hashpw("other-password", BCrypt.gensalt(4)), "other@example.com");

        DataAccessException exception = assertThrows(DataAccessException.class, () -> userDAO.createUser(duplicate));

        assertInstanceOf(SQLException.class, exception.getCause());
        assertEquals(ALICE, userDAO.getUser(ALICE.username()));
    }

    @ParameterizedTest
    @MethodSource("invalidUsers")
    public void createRejectsMissingFieldsWithoutChangingExistingUsers(UserData invalid) throws DataAccessException {
        userDAO.createUser(ALICE);

        assertThrows(DataAccessException.class, () -> userDAO.createUser(invalid));

        assertEquals(ALICE, userDAO.getUser(ALICE.username()));
        assertNull(userDAO.getUser("bob"));
    }

    static Stream<UserData> invalidUsers() {
        return Stream.of(
                null,
                new UserData(null, PASSWORD_HASH, "bob@example.com"),
                new UserData("bob", null, "bob@example.com"),
                new UserData("bob", PASSWORD_HASH, null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"o'connor", "someone' OR '1'='1", "échecs♟", "alice "})
    public void specialCharactersAreStoredAndReadAsData(String username) throws DataAccessException {
        userDAO.createUser(ALICE);
        UserData user = new UserData(username, PASSWORD_HASH, "special@example.com");

        userDAO.createUser(user);

        assertEquals(user, userDAO.getUser(username));
        assertEquals(ALICE, userDAO.getUser(ALICE.username()));
        assertNull(userDAO.getUser("missing' OR '1'='1"));
    }

    @Test
    public void usernamesRemainCaseSensitive() throws DataAccessException {
        UserData capitalized = new UserData("Alice", PASSWORD_HASH, "capitalized@example.com");

        userDAO.createUser(ALICE);
        userDAO.createUser(capitalized);

        assertEquals(ALICE, userDAO.getUser("alice"));
        assertEquals(capitalized, userDAO.getUser("Alice"));
        assertNull(userDAO.getUser("ALICE"));
    }

    @Test
    public void clearRemovesAllUsersAndStoreCanBeReused() throws DataAccessException {
        UserData bob = new UserData("bob", PASSWORD_HASH, "bob@example.com");
        userDAO.createUser(ALICE);
        userDAO.createUser(bob);
        UserDAO reopenedDAO = new SqlUserDAO();

        userDAO.clear();
        userDAO.clear();

        assertNull(userDAO.getUser(ALICE.username()));
        assertNull(userDAO.getUser(bob.username()));
        assertNull(reopenedDAO.getUser(ALICE.username()));
        userDAO.createUser(ALICE);
        assertEquals(ALICE, reopenedDAO.getUser(ALICE.username()));
    }

    @Test
    public void clearPreservesDataOwnedByOtherDAOs() throws Exception {
        userDAO.createUser(ALICE);
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("INSERT INTO auth_tokens (auth_token, username) VALUES (?, ?)")) {
            statement.setString(1, "alice-token");
            statement.setString(2, ALICE.username());
            statement.executeUpdate();
        }

        userDAO.clear();

        assertNull(userDAO.getUser(ALICE.username()));
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("SELECT auth_token, username FROM auth_tokens");
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("alice-token", rows.getString("auth_token"));
            assertEquals(ALICE.username(), rows.getString("username"));
            assertFalse(rows.next());
        }
    }

    @Test
    public void databaseConnectionFailuresArePropagated() throws Exception {
        makeDatabaseUnavailable();

        DataAccessException createError = assertThrows(DataAccessException.class, () -> userDAO.createUser(ALICE));
        DataAccessException readError = assertThrows(DataAccessException.class, () -> userDAO.getUser(ALICE.username()));
        DataAccessException clearError = assertThrows(DataAccessException.class, userDAO::clear);

        assertInstanceOf(SQLException.class, createError.getCause());
        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }

    @Test
    public void constructorReportsInitializationFailures() throws Exception {
        makeDatabaseUnavailable();

        DataAccessException exception = assertThrows(DataAccessException.class, SqlUserDAO::new);

        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    public void readAndClearTranslateSqlErrors() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DROP TABLE users")) {
            statement.executeUpdate();
        }

        DataAccessException readError = assertThrows(DataAccessException.class, () -> userDAO.getUser(ALICE.username()));
        DataAccessException clearError = assertThrows(DataAccessException.class, userDAO::clear);

        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }
}
