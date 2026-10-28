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
import org.mindrot.jbcrypt.BCrypt;

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
        UserData stored = userDAO.getUser("alice");
        assertEquals("alice", stored.username());
        assertEquals("alice@example.com", stored.email());
        assertNotEquals(request.password(), stored.password());
        assertTrue(BCrypt.checkpw(request.password(), stored.password()));
        assertEquals(new AuthData(result.authToken(), "alice"), authDAO.getAuth(result.authToken()));
    }

    @Test
    public void registrationUsesDifferentSaltsForTheSamePassword() throws Exception {
        userService.register(new RegisterRequest("alice", "password", "alice@example.com"));
        userService.register(new RegisterRequest("bob", "password", "bob@example.com"));

        String aliceHash = userDAO.getUser("alice").password();
        String bobHash = userDAO.getUser("bob").password();

        assertNotEquals(aliceHash, bobHash);
        assertTrue(BCrypt.checkpw("password", aliceHash));
        assertTrue(BCrypt.checkpw("password", bobHash));
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
        UserData originalUser = userDAO.getUser("alice");
        RegisterRequest duplicate = new RegisterRequest("alice", "replacement", "other@example.com");

        assertThrows(AlreadyTakenException.class, () -> userService.register(duplicate));

        assertEquals(originalUser, userDAO.getUser("alice"));
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
        UserService failingService = serviceWithFailingUserLookup(failure);
        RegisterRequest request = new RegisterRequest("alice", "password", "alice@example.com");

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.register(request)));
    }

    @Test
    public void loginReturnsNewAuthForExistingUser() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        UserData originalUser = userDAO.getUser("alice");

        LoginResult result = userService.login(new LoginRequest("alice", "password"));

        assertEquals("alice", result.username());
        assertNotNull(UUID.fromString(result.authToken()));
        assertNotEquals(registration.authToken(), result.authToken());
        assertEquals(new AuthData(result.authToken(), "alice"), authDAO.getAuth(result.authToken()));
        assertEquals(originalUser, userDAO.getUser("alice"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" padded password ", "pāsswörd♟"})
    public void registrationAndLoginPreservePasswordContent(String password) throws Exception {
        userService.register(new RegisterRequest("alice", password, "alice@example.com"));

        LoginResult result = userService.login(new LoginRequest("alice", password));

        assertEquals("alice", result.username());
        assertTrue(BCrypt.checkpw(password, userDAO.getUser("alice").password()));
        assertEquals(new AuthData(result.authToken(), "alice"), authDAO.getAuth(result.authToken()));
    }

    @Test
    public void loginRejectsTheStoredHashAsAPassword() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        UserData stored = userDAO.getUser("alice");

        assertThrows(UnauthorizedException.class, () -> userService.login(new LoginRequest("alice", stored.password())));

        assertEquals(stored, userDAO.getUser("alice"));
        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"password", "$2a$10$short", "$2a$xx$invalid"})
    public void loginRejectsMissingOrMalformedStoredHashes(String hash) throws DataAccessException {
        UserData stored = new UserData("alice", hash, "alice@example.com");
        userDAO.createUser(stored);

        assertThrows(UnauthorizedException.class, () -> userService.login(new LoginRequest("alice", "password")));

        assertEquals(stored, userDAO.getUser("alice"));
    }

    @Test
    public void repeatedLoginsKeepAllSessionsValid() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        LoginRequest request = new LoginRequest("alice", "password");

        LoginResult first = userService.login(request);
        LoginResult second = userService.login(request);

        assertNotEquals(registration.authToken(), first.authToken());
        assertNotEquals(registration.authToken(), second.authToken());
        assertNotEquals(first.authToken(), second.authToken());
        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
        assertEquals(new AuthData(first.authToken(), "alice"), authDAO.getAuth(first.authToken()));
        assertEquals(new AuthData(second.authToken(), "alice"), authDAO.getAuth(second.authToken()));
    }

    @Test
    public void loginRejectsUnknownUser() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));

        assertThrows(UnauthorizedException.class, () -> userService.login(new LoginRequest("unknown", "password")));

        assertNull(userDAO.getUser("unknown"));
        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "PASSWORD", " password "})
    public void loginRejectsIncorrectPassword(String password) throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        UserData originalUser = userDAO.getUser("alice");

        assertThrows(UnauthorizedException.class, () -> userService.login(new LoginRequest("alice", password)));

        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
        assertEquals(originalUser, userDAO.getUser("alice"));
    }

    @Test
    public void loginRejectsNullRequest() {
        assertThrows(BadRequestException.class, () -> userService.login(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void loginRejectsMissingUsername(String username) {
        assertThrows(BadRequestException.class, () -> userService.login(new LoginRequest(username, "password")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void loginRejectsMissingPassword(String password) {
        assertThrows(BadRequestException.class, () -> userService.login(new LoginRequest("alice", password)));
    }

    @Test
    public void loginStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to read users");
        UserService failingService = serviceWithFailingUserLookup(failure);
        LoginRequest request = new LoginRequest("alice", "password");

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.login(request)));
    }

    @Test
    public void loginAuthStorageFailurePropagatesToCaller() throws DataAccessException {
        UserData user = new UserData("alice", BCrypt.hashpw("password", BCrypt.gensalt(4)), "alice@example.com");
        userDAO.createUser(user);
        DataAccessException failure = new DataAccessException("Unable to store auth token");
        AuthDAO failingAuthDAO = new AuthDAO() {
            @Override
            public void createAuth(AuthData auth) throws DataAccessException {
                throw failure;
            }

            @Override
            public AuthData getAuth(String authToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteAuth(String authToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
        UserService failingService = new UserService(userDAO, failingAuthDAO);
        LoginRequest request = new LoginRequest("alice", "password");

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.login(request)));
        assertEquals(user, userDAO.getUser("alice"));
    }

    @Test
    public void logoutRemovesTokenAndPreservesUser() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        UserData originalUser = userDAO.getUser("alice");

        userService.logout(registration.authToken());

        assertNull(authDAO.getAuth(registration.authToken()));
        assertEquals(originalUser, userDAO.getUser("alice"));
    }

    @Test
    public void logoutPreservesOtherSessionsAndOtherUsers() throws Exception {
        RegisterResult alice = userService.register(new RegisterRequest("alice", "password", "alice@example.com"));
        LoginResult otherSession = userService.login(new LoginRequest("alice", "password"));
        RegisterResult bob = userService.register(new RegisterRequest("bob", "password", "bob@example.com"));

        userService.logout(alice.authToken());

        assertNull(authDAO.getAuth(alice.authToken()));
        assertEquals(new AuthData(otherSession.authToken(), "alice"), authDAO.getAuth(otherSession.authToken()));
        assertEquals(new AuthData(bob.authToken(), "bob"), authDAO.getAuth(bob.authToken()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "unknown-token"})
    public void logoutRejectsMissingOrUnknownToken(String authToken) throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));

        assertThrows(UnauthorizedException.class, () -> userService.logout(authToken));

        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
    }

    @Test
    public void logoutRejectsAlreadyUsedToken() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        userService.logout(registration.authToken());

        assertThrows(UnauthorizedException.class, () -> userService.logout(registration.authToken()));

        assertNull(authDAO.getAuth(registration.authToken()));
        assertNotNull(userDAO.getUser("alice"));
    }

    @Test
    public void userCanLogInAgainAfterLogout() throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        userService.logout(registration.authToken());

        LoginResult login = userService.login(new LoginRequest("alice", "password"));

        assertNotEquals(registration.authToken(), login.authToken());
        assertNull(authDAO.getAuth(registration.authToken()));
        assertEquals(new AuthData(login.authToken(), "alice"), authDAO.getAuth(login.authToken()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void logoutStorageFailurePropagatesToCaller(boolean failLookup) throws Exception {
        RegisterResult registration = userService.register(
                new RegisterRequest("alice", "password", "alice@example.com"));
        DataAccessException failure = new DataAccessException("Unable to access auth storage");
        AuthDAO failingAuthDAO = new AuthDAO() {
            @Override
            public void createAuth(AuthData auth) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthData getAuth(String authToken) throws DataAccessException {
                if (failLookup) {
                    throw failure;
                }
                return authDAO.getAuth(authToken);
            }

            @Override
            public void deleteAuth(String authToken) throws DataAccessException {
                throw failure;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
        UserService failingService = new UserService(userDAO, failingAuthDAO);

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> failingService.logout(registration.authToken())));
        assertEquals(new AuthData(registration.authToken(), "alice"), authDAO.getAuth(registration.authToken()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void registrationWriteFailurePropagatesToCaller(boolean failUserWrite) {
        DataAccessException failure = new DataAccessException("Unable to store registration");
        UserDAO failingUserDAO = new MemoryUserDAO() {
            @Override
            public void createUser(UserData user) throws DataAccessException {
                if (failUserWrite) {
                    throw failure;
                }
                super.createUser(user);
            }
        };
        AuthDAO failingAuthDAO = new MemoryAuthDAO() {
            @Override
            public void createAuth(AuthData auth) throws DataAccessException {
                throw failure;
            }
        };
        UserService failingService = new UserService(failingUserDAO, failingAuthDAO);
        RegisterRequest request = new RegisterRequest("alice", "password", "alice@example.com");

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.register(request)));
    }

    private UserService serviceWithFailingUserLookup(DataAccessException failure) {
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
        return new UserService(failingUserDAO, authDAO);
    }
}
