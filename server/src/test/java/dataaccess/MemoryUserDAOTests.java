package dataaccess;

import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryUserDAOTests {
    private UserDAO userDAO;

    @BeforeEach
    public void setup() {
        userDAO = new MemoryUserDAO();
    }

    @Test
    public void createAndGetUserPreservesAllFields() throws DataAccessException {
        UserData user = new UserData("alice", "password", "alice@example.com");

        userDAO.createUser(user);

        assertEquals(user, userDAO.getUser("alice"));
    }

    @Test
    public void missingUserReturnsNull() throws DataAccessException {
        assertNull(userDAO.getUser("unknown"));
        assertNull(userDAO.getUser(null));
    }

    @Test
    public void duplicateUsernamePreservesOriginalUser() throws DataAccessException {
        UserData original = new UserData("alice", "original-password", "alice@example.com");
        UserData duplicate = new UserData("alice", "replacement-password", "other@example.com");
        userDAO.createUser(original);

        assertThrows(DataAccessException.class, () -> userDAO.createUser(duplicate));
        assertEquals(original, userDAO.getUser("alice"));
    }

    @Test
    public void createRejectsMissingUserOrUsername() {
        assertThrows(DataAccessException.class, () -> userDAO.createUser(null));
        assertThrows(DataAccessException.class,
                () -> userDAO.createUser(new UserData(null, "password", "alice@example.com")));
    }

    @Test
    public void clearRemovesAllUsersAndStoreCanBeReused() throws DataAccessException {
        UserData alice = new UserData("alice", "password", "alice@example.com");
        UserData bob = new UserData("bob", "password", "bob@example.com");
        userDAO.createUser(alice);
        userDAO.createUser(bob);

        userDAO.clear();
        userDAO.clear();

        assertNull(userDAO.getUser("alice"));
        assertNull(userDAO.getUser("bob"));
        userDAO.createUser(alice);
        assertEquals(alice, userDAO.getUser("alice"));
    }

    @Test
    public void storeInstancesAreIndependent() throws DataAccessException {
        UserDAO otherDAO = new MemoryUserDAO();
        UserData first = new UserData("alice", "first-password", "first@example.com");
        UserData second = new UserData("alice", "second-password", "second@example.com");
        userDAO.createUser(first);
        assertNull(otherDAO.getUser("alice"));
        otherDAO.createUser(second);

        userDAO.clear();

        assertNull(userDAO.getUser("alice"));
        assertEquals(second, otherDAO.getUser("alice"));
    }
}
