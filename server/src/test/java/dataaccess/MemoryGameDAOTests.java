package dataaccess;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import model.GameData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryGameDAOTests {
    private GameDAO gameDAO;

    @BeforeEach
    public void setup() {
        gameDAO = new MemoryGameDAO();
    }

    @Test
    public void createStoresGameWithPositiveAssignedID() throws DataAccessException {
        ChessGame chessGame = new ChessGame();
        GameData game = new GameData(0, "alice", "bob", "First game", chessGame);

        int gameID = gameDAO.createGame(game);

        assertTrue(gameID > 0);
        assertEquals(new GameData(gameID, "alice", "bob", "First game", chessGame), gameDAO.getGame(gameID));
    }

    @Test
    public void createAssignsUniqueIDsEvenWhenInputsHaveTheSameID() throws DataAccessException {
        int firstID = gameDAO.createGame(new GameData(99, null, null, "First game", new ChessGame()));
        int secondID = gameDAO.createGame(new GameData(99, null, null, "Second game", new ChessGame()));

        assertTrue(firstID > 0);
        assertTrue(secondID > 0);
        assertNotEquals(firstID, secondID);
        assertEquals("First game", gameDAO.getGame(firstID).gameName());
        assertEquals("Second game", gameDAO.getGame(secondID).gameName());
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
    public void listGamesContainsAllStoredGames() throws DataAccessException {
        GameDAOAssertions.assertListsEveryStoredGame(gameDAO);
    }

    @Test
    public void listGamesReturnsAnIndependentSnapshot() throws DataAccessException {
        int firstID = gameDAO.createGame(new GameData(0, null, null, "First game", new ChessGame()));
        Collection<GameData> snapshot = gameDAO.listGames();

        int secondID = gameDAO.createGame(new GameData(0, null, null, "Second game", new ChessGame()));

        assertEquals(1, snapshot.size());
        snapshot.clear();
        assertEquals(2, gameDAO.listGames().size());
        assertNotNull(gameDAO.getGame(firstID));
        assertNotNull(gameDAO.getGame(secondID));
    }

    @Test
    public void updateReplacesGameMetadataAndStateWithoutChangingOtherGames() throws Exception {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "First game", new ChessGame()));
        int otherID = gameDAO.createGame(new GameData(0, null, null, "Other game", new ChessGame()));
        GameData otherGame = gameDAO.getGame(otherID);
        ChessGame updatedState = new ChessGame();
        updatedState.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        GameData updatedGame = new GameData(gameID, "alice", "bob", "Updated game", updatedState);

        gameDAO.updateGame(updatedGame);

        assertEquals(updatedGame, gameDAO.getGame(gameID));
        assertEquals(ChessGame.TeamColor.BLACK, gameDAO.getGame(gameID).game().getTeamTurn());
        assertEquals(otherGame, gameDAO.getGame(otherID));
        assertEquals(2, gameDAO.listGames().size());
    }

    @Test
    public void updateMissingGameFailsWithoutAddingOrChangingGames() throws DataAccessException {
        int existingID = gameDAO.createGame(new GameData(0, null, null, "Existing game", new ChessGame()));
        GameData existingGame = gameDAO.getGame(existingID);
        GameData missingGame = new GameData(Integer.MAX_VALUE, "alice", null, "Missing game", new ChessGame());

        assertThrows(DataAccessException.class, () -> gameDAO.updateGame(missingGame));

        assertNull(gameDAO.getGame(Integer.MAX_VALUE));
        assertEquals(existingGame, gameDAO.getGame(existingID));
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void createRejectsNullGame() throws DataAccessException {
        assertThrows(DataAccessException.class, () -> gameDAO.createGame(null));
        assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void updateRejectsNullGameWithoutRemovingExistingGames() throws DataAccessException {
        int gameID = gameDAO.createGame(new GameData(0, null, null, "Existing game", new ChessGame()));
        GameData existingGame = gameDAO.getGame(gameID);

        assertThrows(DataAccessException.class, () -> gameDAO.updateGame(null));

        assertEquals(existingGame, gameDAO.getGame(gameID));
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void clearRemovesAllGamesAndStoreCanBeReused() throws DataAccessException {
        int firstID = gameDAO.createGame(new GameData(0, null, null, "First game", new ChessGame()));
        int secondID = gameDAO.createGame(new GameData(0, null, null, "Second game", new ChessGame()));

        gameDAO.clear();
        gameDAO.clear();

        assertNull(gameDAO.getGame(firstID));
        assertNull(gameDAO.getGame(secondID));
        assertTrue(gameDAO.listGames().isEmpty());
        int newID = gameDAO.createGame(new GameData(0, null, null, "New game", new ChessGame()));
        assertTrue(newID > 0);
        assertEquals("New game", gameDAO.getGame(newID).gameName());
        assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void storeInstancesAreIndependent() throws DataAccessException {
        GameDAO otherDAO = new MemoryGameDAO();
        int firstID = gameDAO.createGame(new GameData(0, null, null, "First store", new ChessGame()));
        assertNull(otherDAO.getGame(firstID));
        int secondID = otherDAO.createGame(new GameData(0, null, null, "Second store", new ChessGame()));

        gameDAO.clear();

        assertTrue(gameDAO.listGames().isEmpty());
        assertEquals("Second store", otherDAO.getGame(secondID).gameName());
        assertEquals(1, otherDAO.listGames().size());
    }

    @Test
    public void concurrentCreationAssignsUniqueIDsAndPreservesEveryGame() throws Exception {
        List<Callable<Integer>> creations = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String name = "Game " + i;
            creations.add(() -> gameDAO.createGame(new GameData(0, null, null, name, new ChessGame())));
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> results = executor.invokeAll(creations);
            Set<Integer> gameIDs = new HashSet<>();
            for (Future<Integer> result : results) {
                int gameID = result.get();
                assertTrue(gameID > 0);
                assertTrue(gameIDs.add(gameID));
                assertNotNull(gameDAO.getGame(gameID));
            }
            assertEquals(100, gameIDs.size());
            assertEquals(100, gameDAO.listGames().size());
        }
    }
}
