package dataaccess;

import model.AuthData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryAuthDAOTests {
    private AuthDAO authDAO;

    @BeforeEach
    public void setup() {
        authDAO = new MemoryAuthDAO();
    }

    @Test
    public void createAndGetAuthPreservesUsernameAndToken() throws DataAccessException {
        AuthData auth = new AuthData("token-1", "alice");

        authDAO.createAuth(auth);

        assertEquals(auth, authDAO.getAuth("token-1"));
    }

    @Test
    public void userCanHaveMultipleSessions() throws DataAccessException {
        AuthData first = new AuthData("token-1", "alice");
        AuthData second = new AuthData("token-2", "alice");

        authDAO.createAuth(first);
        authDAO.createAuth(second);

        assertEquals(first, authDAO.getAuth("token-1"));
        assertEquals(second, authDAO.getAuth("token-2"));
    }

    @Test
    public void missingAuthReturnsNull() throws DataAccessException {
        assertNull(authDAO.getAuth("unknown"));
        assertNull(authDAO.getAuth(null));
    }

    @Test
    public void deletingOneSessionPreservesOtherSessions() throws DataAccessException {
        AuthData first = new AuthData("token-1", "alice");
        AuthData second = new AuthData("token-2", "alice");
        authDAO.createAuth(first);
        authDAO.createAuth(second);

        authDAO.deleteAuth("token-1");

        assertNull(authDAO.getAuth("token-1"));
        assertEquals(second, authDAO.getAuth("token-2"));
    }

    @Test
    public void deletingMissingOrNullTokensPreservesExistingSessions() throws DataAccessException {
        AuthData auth = new AuthData("token-1", "alice");
        authDAO.createAuth(auth);

        authDAO.deleteAuth("unknown");
        authDAO.deleteAuth("unknown");
        authDAO.deleteAuth(null);

        assertEquals(auth, authDAO.getAuth("token-1"));
    }

    @Test
    public void duplicateTokenPreservesOriginalSession() throws DataAccessException {
        AuthData original = new AuthData("token-1", "alice");
        AuthData duplicate = new AuthData("token-1", "bob");
        authDAO.createAuth(original);

        assertThrows(DataAccessException.class, () -> authDAO.createAuth(duplicate));
        assertEquals(original, authDAO.getAuth("token-1"));
    }

    @Test
    public void createRejectsMissingAuthTokenOrUsername() {
        assertThrows(DataAccessException.class, () -> authDAO.createAuth(null));
        assertThrows(DataAccessException.class, () -> authDAO.createAuth(new AuthData(null, "alice")));
        assertThrows(DataAccessException.class, () -> authDAO.createAuth(new AuthData("token-1", null)));
    }

    @Test
    public void clearRemovesAllSessionsAndStoreCanBeReused() throws DataAccessException {
        AuthData auth = new AuthData("token-1", "alice");
        authDAO.createAuth(auth);
        authDAO.createAuth(new AuthData("token-2", "bob"));

        authDAO.clear();
        authDAO.clear();

        assertNull(authDAO.getAuth("token-1"));
        assertNull(authDAO.getAuth("token-2"));
        authDAO.createAuth(auth);
        assertEquals(auth, authDAO.getAuth("token-1"));
    }

    @Test
    public void storeInstancesAreIndependent() throws DataAccessException {
        AuthDAO otherDAO = new MemoryAuthDAO();
        AuthData first = new AuthData("token-1", "alice");
        AuthData second = new AuthData("token-1", "bob");
        authDAO.createAuth(first);
        assertNull(otherDAO.getAuth("token-1"));
        otherDAO.createAuth(second);

        authDAO.clear();

        assertNull(authDAO.getAuth("token-1"));
        assertEquals(second, otherDAO.getAuth("token-1"));
    }
}
