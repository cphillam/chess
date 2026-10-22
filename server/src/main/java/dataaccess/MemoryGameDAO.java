package dataaccess;

import model.GameData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryGameDAO implements GameDAO {
    private final Map<Integer, GameData> games = new ConcurrentHashMap<>();
    private final AtomicInteger nextGameID = new AtomicInteger(1);

    @Override
    public int createGame(GameData game) throws DataAccessException {
        if (game == null) {
            throw new DataAccessException("Game is required");
        }
        int gameID = nextGameID.getAndIncrement();
        GameData storedGame = new GameData(
                gameID, game.whiteUsername(), game.blackUsername(), game.gameName(), game.game());
        games.put(gameID, storedGame);
        return gameID;
    }

    @Override
    public GameData getGame(int gameID) {
        return games.get(gameID);
    }

    @Override
    public Collection<GameData> listGames() {
        return new ArrayList<>(games.values());
    }

    @Override
    public void updateGame(GameData game) throws DataAccessException {
        if (game == null) {
            throw new DataAccessException("Game is required");
        }
        if (games.replace(game.gameID(), game) == null) {
            throw new DataAccessException("Game not found");
        }
    }

    @Override
    public void clear() {
        games.clear();
    }
}
