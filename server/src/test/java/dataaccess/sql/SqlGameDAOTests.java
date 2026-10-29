package dataaccess.sql;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.GameDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SqlGameDAOTests extends SqlTestSupport {
    private GameDAO gameDAO;

    @BeforeEach
    public void setupDAO() throws DataAccessException {
        gameDAO = new SqlGameDAO();
    }

    @Test
    public void createAndGetPreservesInitialBoardAndMetadata() throws DataAccessException {
        ChessGame initial = new ChessGame();
        GameData game = new GameData(0, "alice", "bob", "First game", initial);

        int gameID = gameDAO.createGame(game);
        GameData stored = gameDAO.getGame(gameID);

        assertTrue(gameID > 0);
        assertEquals(new GameData(gameID, "alice", "bob", "First game", initial), stored);
        assertNotSame(initial, stored.game());
        assertEquals(initial.getBoard(), stored.game().getBoard());
        assertEquals(ChessGame.TeamColor.WHITE, stored.game().getTeamTurn());
        ChessPosition pawn = new ChessPosition(2, 5);
        assertEquals(new HashSet<>(initial.validMoves(pawn)), new HashSet<>(stored.game().validMoves(pawn)));
    }

    @Test
    public void createAssignsUniqueIdsAndPersistsAcrossDAOInstances() throws DataAccessException {
        GameData input = new GameData(99, null, null, "First game", new ChessGame());
        int firstID = gameDAO.createGame(input);
        int secondID = gameDAO.createGame(input);

        GameDAO reopenedDAO = new SqlGameDAO();

        assertTrue(firstID > 0);
        assertTrue(secondID > 0);
        assertNotEquals(firstID, secondID);
        assertEquals(new GameData(firstID, null, null, input.gameName(), input.game()), reopenedDAO.getGame(firstID));
        assertEquals(new GameData(secondID, null, null, input.gameName(), input.game()), reopenedDAO.getGame(secondID));
    }

    @Test
    public void missingGameReturnsNull() throws DataAccessException {
        assertNull(gameDAO.getGame(1));
        assertNull(gameDAO.getGame(0));
        assertNull(gameDAO.getGame(-1));
    }

    @Test
    public void listGamesStartsEmpty() throws DataAccessException {
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void listGamesContainsEveryGameAndPlayerAssignment() throws DataAccessException {
        int firstID = gameDAO.createGame(new GameData(0, "alice", null, "First game", new ChessGame()));
        int secondID = gameDAO.createGame(new GameData(0, null, "bob", "Second game", new ChessGame()));

        Collection<GameData> games = gameDAO.listGames();

        assertEquals(2, games.size());
        assertEquals(Set.of(gameDAO.getGame(firstID), gameDAO.getGame(secondID)), new HashSet<>(games));
    }

    @Test
    public void listGamesReturnsAnIndependentSnapshot() throws DataAccessException {
        gameDAO.createGame(newGame("First game"));
        Collection<GameData> snapshot = gameDAO.listGames();

        gameDAO.createGame(newGame("Second game"));

        assertEquals(1, snapshot.size());
        snapshot.clear();
        assertEquals(2, gameDAO.listGames().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void changingLocalGameObjectsDoesNotChangeStoredState(boolean readFromList) throws Exception {
        GameData original = newGame("Original game");
        int gameID = gameDAO.createGame(original);
        original.game().makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        GameData snapshot = readFromList ? gameDAO.listGames().iterator().next() : gameDAO.getGame(gameID);

        snapshot.game().makeMove(new ChessMove(new ChessPosition(2, 4), new ChessPosition(4, 4), null));

        GameData stored = gameDAO.getGame(gameID);
        assertEquals(new ChessGame().getBoard(), stored.game().getBoard());
        assertEquals(ChessGame.TeamColor.WHITE, stored.game().getTeamTurn());
    }

    @Test
    public void updatePersistsMovesCapturesAndMetadataWithoutChangingOtherGames() throws Exception {
        int gameID = gameDAO.createGame(newGame("First game"));
        int otherID = gameDAO.createGame(newGame("Other game"));
        GameData otherGame = gameDAO.getGame(otherID);
        ChessGame updatedState = new ChessGame();
        updatedState.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        updatedState.makeMove(new ChessMove(new ChessPosition(7, 4), new ChessPosition(5, 4), null));
        updatedState.makeMove(new ChessMove(new ChessPosition(4, 5), new ChessPosition(5, 4), null));
        GameData update = new GameData(gameID, "alice", "bob", "Updated game", updatedState);

        gameDAO.updateGame(update);

        GameData stored = new SqlGameDAO().getGame(gameID);
        assertEquals(update, stored);
        assertEquals(ChessGame.TeamColor.BLACK, stored.game().getTeamTurn());
        assertEquals(new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN),
                stored.game().getBoard().getPiece(new ChessPosition(5, 4)));
        assertNull(stored.game().getBoard().getPiece(new ChessPosition(4, 5)));
        assertEquals(otherGame, gameDAO.getGame(otherID));
        assertEquals(2, gameDAO.listGames().size());
    }

    @Test
    public void updatingAnUnchangedGameSucceeds() throws DataAccessException {
        int gameID = gameDAO.createGame(newGame("Existing game"));
        GameData original = gameDAO.getGame(gameID);

        gameDAO.updateGame(original);

        assertEquals(original, gameDAO.getGame(gameID));
        assertEquals(1, gameDAO.listGames().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, Integer.MAX_VALUE})
    public void updatingMissingGamesDoesNotInsertOrChangeGames(int missingID) throws DataAccessException {
        int existingID = gameDAO.createGame(newGame("Existing game"));
        GameData existing = gameDAO.getGame(existingID);
        GameData missing = new GameData(missingID, "alice", null, "Missing game", new ChessGame());

        assertThrows(DataAccessException.class, () -> gameDAO.updateGame(missing));

        assertNull(gameDAO.getGame(missingID));
        assertEquals(existing, gameDAO.getGame(existingID));
        assertEquals(1, gameDAO.listGames().size());
    }

    @ParameterizedTest
    @MethodSource("invalidGames")
    public void createRejectsMissingGameData(GameData invalid) throws DataAccessException {
        assertThrows(DataAccessException.class, () -> gameDAO.createGame(invalid));

        assertTrue(gameDAO.listGames().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidGames")
    public void updateRejectsMissingGameDataWithoutChangingStoredGames(GameData invalid) throws DataAccessException {
        int gameID = gameDAO.createGame(newGame("Existing game"));
        GameData original = gameDAO.getGame(gameID);
        GameData update = invalid == null ? null : new GameData(
                gameID, invalid.whiteUsername(), invalid.blackUsername(), invalid.gameName(), invalid.game());

        assertThrows(DataAccessException.class, () -> gameDAO.updateGame(update));

        assertEquals(original, gameDAO.getGame(gameID));
        assertEquals(1, gameDAO.listGames().size());
    }

    static Stream<GameData> invalidGames() {
        return Stream.of(null,
                new GameData(0, null, null, "Missing state", null),
                new GameData(0, null, null, null, new ChessGame()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"O'Brien's game", "game'); DELETE FROM games; --", "Échecs♟"})
    public void namesAndPlayersWithSpecialCharactersArePreserved(String name) throws DataAccessException {
        GameData game = new GameData(0, "al'ice", "bób♟", name, new ChessGame());

        int gameID = gameDAO.createGame(game);
        GameData updated = new GameData(gameID, game.blackUsername(), game.whiteUsername(), name + " updated", game.game());
        assertEquals(new GameData(gameID, game.whiteUsername(), game.blackUsername(), name, game.game()), gameDAO.getGame(gameID));
        gameDAO.updateGame(updated);

        assertEquals(updated, gameDAO.getGame(gameID));
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void clearRemovesAllGamesAndStoreCanBeReused() throws DataAccessException {
        int firstID = gameDAO.createGame(newGame("First game"));
        int secondID = gameDAO.createGame(newGame("Second game"));
        GameDAO reopenedDAO = new SqlGameDAO();

        gameDAO.clear();
        gameDAO.clear();

        assertTrue(reopenedDAO.listGames().isEmpty());
        assertNull(reopenedDAO.getGame(firstID));
        assertNull(reopenedDAO.getGame(secondID));
        int newID = gameDAO.createGame(newGame("New game"));
        assertTrue(newID > 0);
        assertEquals("New game", reopenedDAO.getGame(newID).gameName());
        assertEquals(1, reopenedDAO.listGames().size());
    }

    @Test
    public void clearPreservesUsersAndSessions() throws DataAccessException {
        UserDAO userDAO = new SqlUserDAO();
        UserData user = new UserData("alice", BCrypt.hashpw("sample-password", BCrypt.gensalt(4)), "alice@example.com");
        userDAO.createUser(user);
        AuthDAO authDAO = new SqlAuthDAO();
        AuthData auth = new AuthData("alice-token", user.username());
        authDAO.createAuth(auth);
        gameDAO.createGame(newGame("Existing game"));

        gameDAO.clear();

        assertTrue(gameDAO.listGames().isEmpty());
        assertEquals(user, userDAO.getUser(user.username()));
        assertEquals(auth, authDAO.getAuth(auth.authToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null"})
    public void corruptJsonIsReportedAsADataAccessFailure(String json) throws Exception {
        int gameID = gameDAO.createGame(newGame("Existing game"));
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("UPDATE games SET game_json = ? WHERE game_id = ?")) {
            statement.setString(1, json);
            statement.setInt(2, gameID);
            statement.executeUpdate();
        }

        assertThrows(DataAccessException.class, () -> gameDAO.getGame(gameID));
        assertThrows(DataAccessException.class, gameDAO::listGames);
    }

    @Test
    public void databaseConnectionFailuresArePropagated() throws Exception {
        makeDatabaseUnavailable();
        GameData game = newGame("Unavailable game");

        DataAccessException createError = assertThrows(DataAccessException.class, () -> gameDAO.createGame(game));
        DataAccessException readError = assertThrows(DataAccessException.class, () -> gameDAO.getGame(1));
        DataAccessException listError = assertThrows(DataAccessException.class, gameDAO::listGames);
        DataAccessException updateError = assertThrows(DataAccessException.class, () -> gameDAO.updateGame(game));
        DataAccessException clearError = assertThrows(DataAccessException.class, gameDAO::clear);

        assertInstanceOf(SQLException.class, createError.getCause());
        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, listError.getCause());
        assertInstanceOf(SQLException.class, updateError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }

    @Test
    public void constructorReportsInitializationFailures() throws Exception {
        makeDatabaseUnavailable();

        DataAccessException exception = assertThrows(DataAccessException.class, SqlGameDAO::new);

        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    public void operationsTranslateSqlErrors() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DROP TABLE games")) {
            statement.executeUpdate();
        }
        GameData game = newGame("Missing table");

        DataAccessException createError = assertThrows(DataAccessException.class, () -> gameDAO.createGame(game));
        DataAccessException readError = assertThrows(DataAccessException.class, () -> gameDAO.getGame(1));
        DataAccessException listError = assertThrows(DataAccessException.class, gameDAO::listGames);
        DataAccessException updateError = assertThrows(DataAccessException.class, () -> gameDAO.updateGame(game));
        DataAccessException clearError = assertThrows(DataAccessException.class, gameDAO::clear);

        assertInstanceOf(SQLException.class, createError.getCause());
        assertInstanceOf(SQLException.class, readError.getCause());
        assertInstanceOf(SQLException.class, listError.getCause());
        assertInstanceOf(SQLException.class, updateError.getCause());
        assertInstanceOf(SQLException.class, clearError.getCause());
    }

    @Test
    public void concurrentCreationAssignsUniqueIdsAndPreservesAllGames() throws Exception {
        List<Callable<Integer>> creations = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String name = "Game " + index;
            creations.add(() -> gameDAO.createGame(newGame(name)));
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> results = executor.invokeAll(creations);
            Set<Integer> gameIDs = new HashSet<>();
            for (int index = 0; index < results.size(); index++) {
                int gameID = results.get(index).get();
                assertTrue(gameID > 0);
                assertTrue(gameIDs.add(gameID));
                assertEquals("Game " + index, gameDAO.getGame(gameID).gameName());
            }
            assertEquals(20, gameIDs.size());
            assertEquals(20, gameDAO.listGames().size());
        }
    }

    private GameData newGame(String name) {
        return new GameData(0, null, null, name, new ChessGame());
    }
}
