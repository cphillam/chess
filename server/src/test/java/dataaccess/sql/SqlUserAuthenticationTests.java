package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import service.LoginRequest;
import service.LoginResult;
import service.RegisterRequest;
import service.RegisterResult;
import service.UnauthorizedException;
import service.UserService;

import static org.junit.jupiter.api.Assertions.*;

public class SqlUserAuthenticationTests extends SqlTestSupport {
    private static final RegisterRequest REGISTRATION =
            new RegisterRequest("alice", "sample-password", "alice@example.com");

    private UserDAO userDAO;
    private MemoryAuthDAO authDAO;
    private UserService userService;

    @BeforeEach
    public void setupService() throws DataAccessException {
        userDAO = new SqlUserDAO();
        authDAO = new MemoryAuthDAO();
        userService = new UserService(userDAO, authDAO);
    }

    @Test
    public void registrationStoresBcryptPasswordInSql() throws Exception {
        RegisterResult registration = userService.register(REGISTRATION);

        UserData stored = userDAO.getUser(REGISTRATION.username());

        assertEquals(REGISTRATION.username(), stored.username());
        assertEquals(REGISTRATION.email(), stored.email());
        assertNotEquals(REGISTRATION.password(), stored.password());
        assertTrue(BCrypt.checkpw(REGISTRATION.password(), stored.password()));
        assertEquals(new AuthData(registration.authToken(), REGISTRATION.username()),
                authDAO.getAuth(registration.authToken()));
    }

    @Test
    public void newServiceInstanceAuthenticatesStoredHash() throws Exception {
        userService.register(REGISTRATION);
        UserData originalUser = userDAO.getUser(REGISTRATION.username());
        MemoryAuthDAO newAuthDAO = new MemoryAuthDAO();
        UserService reopenedService = new UserService(new SqlUserDAO(), newAuthDAO);

        LoginResult login = reopenedService.login(new LoginRequest(REGISTRATION.username(), REGISTRATION.password()));

        assertEquals(REGISTRATION.username(), login.username());
        assertEquals(new AuthData(login.authToken(), REGISTRATION.username()), newAuthDAO.getAuth(login.authToken()));
        assertEquals(originalUser, userDAO.getUser(REGISTRATION.username()));
    }

    @Test
    public void incorrectPasswordPreservesSqlHashAndSession() throws Exception {
        RegisterResult registration = userService.register(REGISTRATION);
        UserData originalUser = userDAO.getUser(REGISTRATION.username());

        assertThrows(UnauthorizedException.class,
                () -> userService.login(new LoginRequest(REGISTRATION.username(), "wrong-password")));

        assertEquals(originalUser, userDAO.getUser(REGISTRATION.username()));
        assertEquals(new AuthData(registration.authToken(), REGISTRATION.username()),
                authDAO.getAuth(registration.authToken()));
    }
}
