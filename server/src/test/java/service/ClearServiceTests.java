package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClearServiceTests {
    private UserDAO userDAO;
    private AuthDAO authDAO;
    private GameDAO gameDAO;
    private ClearService clearService;

    @BeforeEach
    public void setup() {
        userDAO = new MemoryUserDAO();
        authDAO = new MemoryAuthDAO();
        gameDAO = new MemoryGameDAO();
        clearService = new ClearService(userDAO, authDAO, gameDAO);
    }

    @Test
    public void clearRemovesAllUsersAuthTokensAndGames() throws DataAccessException {
        userDAO.createUser(new UserData("alice", "password", "alice@example.com"));
        userDAO.createUser(new UserData("bob", "password", "bob@example.com"));
        authDAO.createAuth(new AuthData("token-1", "alice"));
        authDAO.createAuth(new AuthData("token-2", "bob"));
        int firstID = gameDAO.createGame(new GameData(0, "alice", "bob", "First game", new ChessGame()));
        int secondID = gameDAO.createGame(new GameData(0, null, null, "Second game", new ChessGame()));

        clearService.clear();

        assertNull(userDAO.getUser("alice"));
        assertNull(userDAO.getUser("bob"));
        assertNull(authDAO.getAuth("token-1"));
        assertNull(authDAO.getAuth("token-2"));
        assertNull(gameDAO.getGame(firstID));
        assertNull(gameDAO.getGame(secondID));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void clearCanBeRepeatedOnEmptyStores() throws DataAccessException {
        clearService.clear();
        clearService.clear();

        assertNull(userDAO.getUser("alice"));
        assertNull(authDAO.getAuth("token-1"));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void storesCanBeReusedAfterClear() throws DataAccessException {
        UserData user = new UserData("alice", "password", "alice@example.com");
        AuthData auth = new AuthData("token-1", "alice");
        userDAO.createUser(user);
        authDAO.createAuth(auth);
        gameDAO.createGame(new GameData(0, null, null, "Old game", new ChessGame()));

        clearService.clear();

        userDAO.createUser(user);
        authDAO.createAuth(auth);
        int gameID = gameDAO.createGame(new GameData(0, null, null, "New game", new ChessGame()));
        assertEquals(user, userDAO.getUser("alice"));
        assertEquals(auth, authDAO.getAuth("token-1"));
        assertEquals("New game", gameDAO.getGame(gameID).gameName());
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void storageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to clear users");
        UserDAO failingUserDAO = new UserDAO() {
            @Override
            public void createUser(UserData user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserData getUser(String username) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() throws DataAccessException {
                throw failure;
            }
        };
        ClearService failingService = new ClearService(failingUserDAO, authDAO, gameDAO);

        assertSame(failure, assertThrows(DataAccessException.class, failingService::clear));
    }
}
