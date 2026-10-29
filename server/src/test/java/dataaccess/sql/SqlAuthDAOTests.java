package dataaccess.sql;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.UserDAO;
import model.AuthData;
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

public class SqlAuthDAOTests extends SqlTestSupport {
    private static final AuthData ALICE = new AuthData("token-1", "alice");

    private AuthDAO authDAO;

    @BeforeEach
    public void setupDAO() throws DataAccessException {
        authDAO = new SqlAuthDAO();
    }

    @Test
    public void createAndGetPreservesAuthData() throws DataAccessException {
        AuthData auth = new AuthData("token-1", "al'ice♟");

        authDAO.createAuth(auth);

        assertEquals(auth, authDAO.getAuth(auth.authToken()));
    }

    @Test
    public void createdAuthPersistsAcrossDAOInstances() throws DataAccessException {
        authDAO.createAuth(ALICE);

        AuthDAO reopenedDAO = new SqlAuthDAO();

        assertEquals(ALICE, reopenedDAO.getAuth(ALICE.authToken()));
    }

    @Test
    public void userCanHaveMultipleSessions() throws DataAccessException {
        AuthData secondSession = new AuthData("token-2", ALICE.username());

        authDAO.createAuth(ALICE);
        authDAO.createAuth(secondSession);

        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
        assertEquals(secondSession, authDAO.getAuth(secondSession.authToken()));
    }

    @Test
    public void missingAuthReturnsNull() throws DataAccessException {
        assertNull(authDAO.getAuth("unknown"));
        assertNull(authDAO.getAuth(null));
    }

    @Test
    public void deletingOneSessionPreservesOtherSessions() throws DataAccessException {
        AuthData secondSession = new AuthData("token-2", ALICE.username());
        AuthData bobSession = new AuthData("bob-token", "bob");
        authDAO.createAuth(ALICE);
        authDAO.createAuth(secondSession);
        authDAO.createAuth(bobSession);

        authDAO.deleteAuth(ALICE.authToken());

        AuthDAO reopenedDAO = new SqlAuthDAO();
        assertNull(reopenedDAO.getAuth(ALICE.authToken()));
        assertEquals(secondSession, reopenedDAO.getAuth(secondSession.authToken()));
        assertEquals(bobSession, reopenedDAO.getAuth(bobSession.authToken()));
    }

    @Test
    public void deletingMissingOrNullTokensPreservesExistingSessions() throws DataAccessException {
        authDAO.createAuth(ALICE);

        authDAO.deleteAuth("unknown");
        authDAO.deleteAuth("unknown");
        authDAO.deleteAuth(null);

        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
    }

    @Test
    public void duplicateTokenPreservesOriginalSession() throws DataAccessException {
        authDAO.createAuth(ALICE);
        AuthData duplicate = new AuthData(ALICE.authToken(), "bob");

        DataAccessException exception = assertThrows(DataAccessException.class, () -> authDAO.createAuth(duplicate));

        assertInstanceOf(SQLException.class, exception.getCause());
        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
    }

    @ParameterizedTest
    @MethodSource("invalidAuthData")
    public void createRejectsMissingFieldsWithoutChangingExistingSessions(AuthData invalid) throws DataAccessException {
        authDAO.createAuth(ALICE);

        assertThrows(DataAccessException.class, () -> authDAO.createAuth(invalid));

        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
        assertNull(authDAO.getAuth("invalid-token"));
    }

    static Stream<AuthData> invalidAuthData() {
        return Stream.of(null, new AuthData(null, "bob"), new AuthData("invalid-token", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"token' OR '1'='1", "séance♟", "token-1 "})
    public void specialCharactersAreStoredAndQueriedAsData(String token) throws DataAccessException {
        authDAO.createAuth(ALICE);
        AuthData auth = new AuthData(token, "bob");

        authDAO.createAuth(auth);

        assertEquals(auth, authDAO.getAuth(token));
        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
        assertNull(authDAO.getAuth("missing' OR '1'='1"));
        authDAO.deleteAuth(token);
        assertNull(authDAO.getAuth(token));
        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
    }

    @Test
    public void tokenLookupRemainsCaseSensitive() throws DataAccessException {
        AuthData capitalized = new AuthData("TOKEN-1", "bob");

        authDAO.createAuth(ALICE);
        authDAO.createAuth(capitalized);

        assertEquals(ALICE, authDAO.getAuth(ALICE.authToken()));
        assertEquals(capitalized, authDAO.getAuth(capitalized.authToken()));
        assertNull(authDAO.getAuth("Token-1"));
    }

    @Test
    public void clearRemovesAllSessionsAndStoreCanBeReused() throws DataAccessException {
        AuthData bobSession = new AuthData("bob-token", "bob");
        authDAO.createAuth(ALICE);
        authDAO.createAuth(bobSession);
        AuthDAO reopenedDAO = new SqlAuthDAO();

        authDAO.clear();
        authDAO.clear();

        assertNull(reopenedDAO.getAuth(ALICE.authToken()));
        assertNull(reopenedDAO.getAuth(bobSession.authToken()));
        authDAO.createAuth(ALICE);
        assertEquals(ALICE, reopenedDAO.getAuth(ALICE.authToken()));
    }

    @Test
    public void clearPreservesUserRecords() throws DataAccessException {
        UserDAO userDAO = new SqlUserDAO();
        UserData user = new UserData(ALICE.username(),
                BCrypt.hashpw("sample-password", BCrypt.gensalt(4)), "alice@example.com");
        userDAO.createUser(user);
        authDAO.createAuth(ALICE);

        authDAO.clear();

        assertNull(authDAO.getAuth(ALICE.authToken()));
        assertEquals(user, userDAO.getUser(user.username()));
    }

    @Test
    public void databaseConnectionFailuresArePropagated() throws Exception {
        makeDatabaseUnavailable();

        DataAccessException createError = assertThrows(DataAccessException.class, () -> authDAO.createAuth(ALICE));
        DataAccessException readError = assertThrows(DataAccessException.class, () -> authDAO.getAuth(ALICE.authToken()));
        DataAccessException deleteError = assertThrows(DataAccessException.class, () -> authDAO.deleteAuth(ALICE.authToken()));
        DataAccessException clearError = assertThrows(DataAccessException.class, authDAO::clear);

        assertInstanceOf(SQLException.class, createError.getCause());
        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, deleteError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }

    @Test
    public void constructorReportsInitializationFailures() throws Exception {
        makeDatabaseUnavailable();

        DataAccessException exception = assertThrows(DataAccessException.class, SqlAuthDAO::new);

        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    public void readDeleteAndClearTranslateSqlErrors() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DROP TABLE auth_tokens")) {
            statement.executeUpdate();
        }

        DataAccessException readError = assertThrows(DataAccessException.class, () -> authDAO.getAuth(ALICE.authToken()));
        DataAccessException deleteError = assertThrows(DataAccessException.class, () -> authDAO.deleteAuth(ALICE.authToken()));
        DataAccessException clearError = assertThrows(DataAccessException.class, authDAO::clear);

        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, deleteError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }
}
