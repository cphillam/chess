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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        GameService failingService = serviceWithFailingAuthLookup(failure);
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

    @Test
    public void listGamesReturnsEmptyCollectionWhenNoGamesExist() throws Exception {
        ListGamesResult result = gameService.listGames("token-1");

        assertNotNull(result.games());
        assertTrue(result.games().isEmpty());
    }

    @Test
    public void listGamesReturnsAllGameMetadataAndOpenSeats() throws Exception {
        int openID = gameDAO.createGame(new GameData(0, null, null, "Open game", new ChessGame()));
        int whiteID = gameDAO.createGame(new GameData(0, "alice", null, "White seat taken", new ChessGame()));
        int blackID = gameDAO.createGame(new GameData(0, null, "bob", "Black seat taken", new ChessGame()));
        int fullID = gameDAO.createGame(new GameData(0, "alice", "bob", "Full game", new ChessGame()));

        ListGamesResult result = gameService.listGames("token-1");

        Set<ListGamesResult.GameSummary> expected = Set.of(
                new ListGamesResult.GameSummary(openID, null, null, "Open game"),
                new ListGamesResult.GameSummary(whiteID, "alice", null, "White seat taken"),
                new ListGamesResult.GameSummary(blackID, null, "bob", "Black seat taken"),
                new ListGamesResult.GameSummary(fullID, "alice", "bob", "Full game"));
        assertEquals(4, result.games().size());
        assertEquals(expected, new HashSet<>(result.games()));
    }

    @Test
    public void listGamesShowsGamesCreatedByOtherUsers() throws Exception {
        authDAO.createAuth(new AuthData("token-2", "bob"));
        gameService.createGame(new CreateGameRequest("Alice's game"), "token-1");
        gameService.createGame(new CreateGameRequest("Bob's game"), "token-2");

        ListGamesResult aliceGames = gameService.listGames("token-1");
        ListGamesResult bobGames = gameService.listGames("token-2");

        assertEquals(2, aliceGames.games().size());
        assertEquals(new HashSet<>(aliceGames.games()), new HashSet<>(bobGames.games()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "unknown-token"})
    public void listGamesRejectsMissingOrUnknownToken(String authToken) throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "Existing game", new ChessGame()));

        assertThrows(UnauthorizedException.class, () -> gameService.listGames(authToken));

        assertEquals("Existing game", gameDAO.getGame(gameID).gameName());
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void listGamesRejectsRevokedToken() throws DataAccessException {
        authDAO.deleteAuth("token-1");

        assertThrows(UnauthorizedException.class, () -> gameService.listGames("token-1"));
    }

    @Test
    public void listGamesAuthStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to read auth tokens");
        GameService failingService = serviceWithFailingAuthLookup(failure);

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.listGames("token-1")));
    }

    @Test
    public void listGamesStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to list games");
        GameDAO failingGameDAO = new GameDAO() {
            @Override
            public int createGame(GameData game) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GameData getGame(int gameID) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Collection<GameData> listGames() throws DataAccessException {
                throw failure;
            }

            @Override
            public void updateGame(GameData game) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
        GameService failingService = new GameService(failingGameDAO, authDAO);

        assertSame(failure, assertThrows(DataAccessException.class, () -> failingService.listGames("token-1")));
    }

    private GameService serviceWithFailingAuthLookup(DataAccessException failure) {
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
        return new GameService(gameDAO, failingAuthDAO);
    }
}
