package dataaccess;

import chess.ChessGame;
import model.GameData;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class GameDAOAssertions {
    private GameDAOAssertions() {
    }

    public static void assertListsEveryStoredGame(GameDAO gameDAO) throws DataAccessException {
        ChessGame firstState = new ChessGame();
        ChessGame secondState = new ChessGame();
        int firstID = gameDAO.createGame(new GameData(0, "alice", null, "First game", firstState));
        int secondID = gameDAO.createGame(new GameData(0, null, "bob", "Second game", secondState));

        Collection<GameData> games = gameDAO.listGames();

        Set<GameData> expected = Set.of(
                new GameData(firstID, "alice", null, "First game", firstState),
                new GameData(secondID, null, "bob", "Second game", secondState));
        assertEquals(expected.size(), games.size());
        assertEquals(expected, new HashSet<>(games));
    }
}
