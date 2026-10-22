package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTests {
    private UserDAO userDAO;
    private AuthDAO authDAO;
    private UserService userService;

    @BeforeEach
    public void setup() {
        userDAO = new MemoryUserDAO();
        authDAO = new MemoryAuthDAO();
        userService = new UserService(userDAO, authDAO);
    }

    @Test
    public void registerStoresUserAndAuthToken() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "password", "alice@example.com");

        RegisterResult result = userService.register(request);

        assertEquals("alice", result.username());
        assertNotNull(UUID.fromString(result.authToken()));
        assertEquals(new UserData("alice", "password", "alice@example.com"), userDAO.getUser("alice"));
        assertEquals(new AuthData(result.authToken(), "alice"), authDAO.getAuth(result.authToken()));
    }

    @Test
    public void registerCreatesDistinctTokensForDifferentUsers() throws Exception {
        RegisterResult first = userService.register(new RegisterRequest("alice", "password", "alice@example.com"));
        RegisterResult second = userService.register(new RegisterRequest("bob", "password", "bob@example.com"));

        assertNotEquals(first.authToken(), second.authToken());
        assertEquals("alice", authDAO.getAuth(first.authToken()).username());
        assertEquals("bob", authDAO.getAuth(second.authToken()).username());
    }

    @Test
    public void duplicateUsernamePreservesOriginalUserAndSession() throws Exception {
        RegisterResult original = userService.register(new RegisterRequest("alice", "password", "alice@example.com"));
        RegisterRequest duplicate = new RegisterRequest("alice", "replacement", "other@example.com");

        assertThrows(AlreadyTakenException.class, () -> userService.register(duplicate));

        assertEquals(new UserData("alice", "password", "alice@example.com"), userDAO.getUser("alice"));
        assertEquals(new AuthData(original.authToken(), "alice"), authDAO.getAuth(original.authToken()));
    }

    @Test
    public void registerRejectsNullRequest() {
        assertThrows(BadRequestException.class, () -> userService.register(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void registerRejectsMissingUsername(String username) throws DataAccessException {
        RegisterRequest request = new RegisterRequest(username, "password", "alice@example.com");

        assertThrows(BadRequestException.class, () -> userService.register(request));
        assertNull(userDAO.getUser(username));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void registerRejectsMissingPassword(String password) throws DataAccessException {
        RegisterRequest request = new RegisterRequest("alice", password, "alice@example.com");

        assertThrows(BadRequestException.class, () -> userService.register(request));
        assertNull(userDAO.getUser("alice"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void registerRejectsMissingEmail(String email) throws DataAccessException {
        RegisterRequest request = new RegisterRequest("alice", "password", email);

        assertThrows(BadRequestException.class, () -> userService.register(request));
        assertNull(userDAO.getUser("alice"));
    }

    @Test
    public void registrationStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to read users");
        UserDAO failingUserDAO = new UserDAO() {
            @Override
            public void createUser(UserData user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserData getUser(String username) throws DataAccessException {
                throw failure;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
        UserService failingService = new UserService(failingUserDAO, authDAO);
        RegisterRequest request = new RegisterRequest("alice", "password", "alice@example.com");

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.register(request)));
    }
}
