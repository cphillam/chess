package service;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class GameServiceTests {
    private GameDAO gameDAO;
    private AuthDAO authDAO;
    private GameService gameService;

    @BeforeEach
    public void setup() throws DataAccessException {
        gameDAO = new MemoryGameDAO();
        authDAO = new MemoryAuthDAO();
        authDAO.createAuth(new AuthData("token-1", "alice"));
        gameService = new GameService(gameDAO, authDAO);
    }

    @Test
    public void createGameStoresInitialBoardWithBothSeatsOpen() throws Exception {
        CreateGameResult result = gameService.createGame(new CreateGameRequest("First game"), "token-1");

        GameData game = gameDAO.getGame(result.gameID());
        assertTrue(result.gameID() > 0);
        assertEquals(result.gameID(), game.gameID());
        assertEquals("First game", game.gameName());
        assertNull(game.whiteUsername());
        assertNull(game.blackUsername());
        assertEquals(new ChessGame().getBoard(), game.game().getBoard());
        assertEquals(ChessGame.TeamColor.WHITE, game.game().getTeamTurn());
    }

    @Test
    public void createGamesHaveUniqueIDsAndIndependentBoards() throws Exception {
        CreateGameRequest request = new CreateGameRequest("Same game name");

        CreateGameResult first = gameService.createGame(request, "token-1");
        CreateGameResult second = gameService.createGame(request, "token-1");

        assertNotEquals(first.gameID(), second.gameID());
        assertEquals(2, gameDAO.listGames().size());
        ChessGame firstGame = gameDAO.getGame(first.gameID()).game();
        ChessGame secondGame = gameDAO.getGame(second.gameID()).game();
        firstGame.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        assertEquals(new ChessGame().getBoard(), secondGame.getBoard());
        assertEquals(ChessGame.TeamColor.WHITE, secondGame.getTeamTurn());
    }

    @Test
    public void createGameRejectsNullRequest() throws DataAccessException {
        assertThrows(BadRequestException.class, () -> gameService.createGame(null, "token-1"));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    public void createGameRejectsMissingName(String gameName) throws DataAccessException {
        CreateGameRequest request = new CreateGameRequest(gameName);

        assertThrows(BadRequestException.class, () -> gameService.createGame(request, "token-1"));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "unknown-token"})
    public void createGameRejectsMissingOrUnknownToken(String authToken) throws DataAccessException {
        CreateGameRequest request = new CreateGameRequest("First game");

        assertThrows(UnauthorizedException.class, () -> gameService.createGame(request, authToken));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void createGameRejectsRevokedToken() throws DataAccessException {
        authDAO.deleteAuth("token-1");
        CreateGameRequest request = new CreateGameRequest("First game");

        assertThrows(UnauthorizedException.class, () -> gameService.createGame(request, "token-1"));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void createGameAuthStorageFailurePropagatesToCaller() throws DataAccessException {
        DataAccessException failure = new DataAccessException("Unable to read auth tokens");
        AuthDAO failingAuthDAO = new AuthDAO() {
            @Override
            public void createAuth(AuthData auth) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthData getAuth(String authToken) throws DataAccessException {
                throw failure;
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
        GameService failingService = new GameService(gameDAO, failingAuthDAO);
        CreateGameRequest request = new CreateGameRequest("First game");

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> failingService.createGame(request, "token-1")));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void createGameStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to store game");
        GameDAO failingGameDAO = new MemoryGameDAO() {
            @Override
            public int createGame(GameData game) throws DataAccessException {
                throw failure;
            }

        };
        GameService failingService = new GameService(failingGameDAO, authDAO);
        CreateGameRequest request = new CreateGameRequest("First game");

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> failingService.createGame(request, "token-1")));
    }
}
