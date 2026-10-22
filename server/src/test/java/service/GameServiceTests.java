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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @ParameterizedTest
    @ValueSource(strings = {"WHITE", "BLACK"})
    public void joinGameClaimsRequestedColorAndPreservesOpponentAndState(String color) throws Exception {
        boolean joiningWhite = "WHITE".equals(color);
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        int gameID = gameDAO.createGame(new GameData(
                0, joiningWhite ? null : "bob", joiningWhite ? "bob" : null, "Existing game", chessGame));

        gameService.joinGame(new JoinGameRequest(color, gameID), "token-1");

        GameData game = gameDAO.getGame(gameID);
        assertEquals(gameID, game.gameID());
        assertEquals(joiningWhite ? "alice" : "bob", game.whiteUsername());
        assertEquals(joiningWhite ? "bob" : "alice", game.blackUsername());
        assertEquals("Existing game", game.gameName());
        assertSame(chessGame, game.game());
        assertEquals(ChessGame.TeamColor.BLACK, game.game().getTeamTurn());
    }

    @Test
    public void joinGameAllowsDifferentUsersToClaimBothColors() throws Exception {
        authDAO.createAuth(new AuthData("token-2", "bob"));
        int gameID = gameService.createGame(new CreateGameRequest("First game"), "token-1").gameID();

        gameService.joinGame(new JoinGameRequest("WHITE", gameID), "token-1");
        gameService.joinGame(new JoinGameRequest("BLACK", gameID), "token-2");

        assertEquals("alice", gameDAO.getGame(gameID).whiteUsername());
        assertEquals("bob", gameDAO.getGame(gameID).blackUsername());
    }

    @Test
    public void joinGameAllowsOneUserToPlayBothColors() throws Exception {
        int gameID = gameService.createGame(new CreateGameRequest("First game"), "token-1").gameID();

        gameService.joinGame(new JoinGameRequest("WHITE", gameID), "token-1");
        gameService.joinGame(new JoinGameRequest("BLACK", gameID), "token-1");

        assertEquals("alice", gameDAO.getGame(gameID).whiteUsername());
        assertEquals("alice", gameDAO.getGame(gameID).blackUsername());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WHITE", "BLACK"})
    public void joinGameRejectsOccupiedSeatWithoutChangingGame(String color) throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, "bob", "bob", "Full game", new ChessGame()));
        GameData original = gameDAO.getGame(gameID);

        assertThrows(AlreadyTakenException.class,
                () -> gameService.joinGame(new JoinGameRequest(color, gameID), "token-1"));
        assertEquals(original, gameDAO.getGame(gameID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WHITE", "BLACK"})
    public void joinGameRejectsRepeatedClaimOfOwnSeat(String color) throws Exception {
        int gameID = gameService.createGame(new CreateGameRequest("First game"), "token-1").gameID();
        JoinGameRequest request = new JoinGameRequest(color, gameID);
        gameService.joinGame(request, "token-1");
        GameData original = gameDAO.getGame(gameID);

        assertThrows(AlreadyTakenException.class, () -> gameService.joinGame(request, "token-1"));
        assertEquals(original, gameDAO.getGame(gameID));
    }

    @Test
    public void joinGameRejectsNullRequest() {
        assertThrows(BadRequestException.class, () -> gameService.joinGame(null, "token-1"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "GREEN", "white", "black", " WHITE "})
    public void joinGameRejectsInvalidColor(String color) throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "Open game", new ChessGame()));
        GameData original = gameDAO.getGame(gameID);

        assertThrows(BadRequestException.class,
                () -> gameService.joinGame(new JoinGameRequest(color, gameID), "token-1"));
        assertEquals(original, gameDAO.getGame(gameID));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    public void joinGameRejectsMissingOrNonexistentGame(Integer gameID) throws DataAccessException {
        assertThrows(BadRequestException.class,
                () -> gameService.joinGame(new JoinGameRequest("WHITE", gameID), "token-1"));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "unknown-token"})
    public void joinGameRejectsMissingOrUnknownToken(String authToken) throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "Open game", new ChessGame()));
        GameData original = gameDAO.getGame(gameID);

        assertThrows(UnauthorizedException.class,
                () -> gameService.joinGame(new JoinGameRequest("WHITE", gameID), authToken));
        assertEquals(original, gameDAO.getGame(gameID));
    }

    @Test
    public void joinGameRejectsRevokedToken() throws DataAccessException {
        authDAO.deleteAuth("token-1");

        assertThrows(UnauthorizedException.class,
                () -> gameService.joinGame(new JoinGameRequest("WHITE", 1), "token-1"));
    }

    @Test
    public void joinGameAuthStorageFailurePropagatesToCaller() {
        DataAccessException failure = new DataAccessException("Unable to read auth tokens");
        GameService failingService = serviceWithFailingAuthLookup(failure);

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> failingService.joinGame(new JoinGameRequest("WHITE", 1), "token-1")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void joinGameStorageFailurePropagatesToCaller(boolean failLookup) throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "Open game", new ChessGame()));
        GameData original = gameDAO.getGame(gameID);
        DataAccessException failure = new DataAccessException("Unable to access game storage");
        GameDAO failingGameDAO = new GameDAO() {
            @Override
            public int createGame(GameData game) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GameData getGame(int id) throws DataAccessException {
                if (failLookup) {
                    throw failure;
                }
                return gameDAO.getGame(id);
            }

            @Override
            public Collection<GameData> listGames() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updateGame(GameData game) throws DataAccessException {
                throw failure;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
        GameService failingService = new GameService(failingGameDAO, authDAO);

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> failingService.joinGame(new JoinGameRequest("WHITE", gameID), "token-1")));
        assertEquals(original, gameDAO.getGame(gameID));
    }

    @Test
    public void simultaneousJoinsCannotClaimTheSameSeat() throws Exception {
        authDAO.createAuth(new AuthData("token-2", "bob"));
        int gameID = gameService.createGame(new CreateGameRequest("Open game"), "token-1").gameID();
        GameService otherService = new GameService(gameDAO, authDAO);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> tryJoinAfterStart(gameService, new JoinGameRequest("WHITE", gameID), "token-1", start));
            Future<Boolean> second = executor.submit(
                    () -> tryJoinAfterStart(otherService, new JoinGameRequest("WHITE", gameID), "token-2", start));
            start.countDown();

            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertTrue(Set.of("alice", "bob").contains(gameDAO.getGame(gameID).whiteUsername()));
            assertNull(gameDAO.getGame(gameID).blackUsername());
        }
    }

    @Test
    public void simultaneousJoinsToDifferentColorsPreserveBothPlayers() throws Exception {
        authDAO.createAuth(new AuthData("token-2", "bob"));
        int gameID = gameService.createGame(new CreateGameRequest("Open game"), "token-1").gameID();
        GameService otherService = new GameService(gameDAO, authDAO);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> tryJoinAfterStart(gameService, new JoinGameRequest("WHITE", gameID), "token-1", start));
            Future<Boolean> second = executor.submit(
                    () -> tryJoinAfterStart(otherService, new JoinGameRequest("BLACK", gameID), "token-2", start));
            start.countDown();

            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals("alice", gameDAO.getGame(gameID).whiteUsername());
            assertEquals("bob", gameDAO.getGame(gameID).blackUsername());
        }
    }

    private boolean tryJoinAfterStart(GameService target, JoinGameRequest request, String token, CountDownLatch start)
            throws Exception {
        start.await();
        try {
            target.joinGame(request, token);
            return true;
        } catch (AlreadyTakenException exception) {
            return false;
        }
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
