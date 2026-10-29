package dataaccess.sql;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.GameDAO;
import model.GameData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;

public class SqlGameDAO implements GameDAO {
    private static final String SELECT_GAMES = """
            SELECT game_id, white_username, black_username, game_name, game_json
            FROM games
            """;

    private final Gson gson = new Gson();

    public SqlGameDAO() throws DataAccessException {
        SqlDatabase.initialize();
    }

    @Override
    public int createGame(GameData game) throws DataAccessException {
        validateGame(game);
        String sql = """
                INSERT INTO games (white_username, black_username, game_name, game_json)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindGame(statement, game);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DataAccessException("database did not return a game ID");
            }
        } catch (SQLException ex) {
            throw new DataAccessException("failed to create game", ex);
        }
    }

    @Override
    public GameData getGame(int gameID) throws DataAccessException {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(SELECT_GAMES + "WHERE game_id = ?")) {
            statement.setInt(1, gameID);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? readGame(rows) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("failed to read game", ex);
        }
    }

    @Override
    public Collection<GameData> listGames() throws DataAccessException {
        Collection<GameData> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(SELECT_GAMES + "ORDER BY game_id");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                games.add(readGame(rows));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("failed to list games", ex);
        }
        return games;
    }

    @Override
    public void updateGame(GameData game) throws DataAccessException {
        validateGame(game);
        String sql = """
                UPDATE games
                SET white_username = ?, black_username = ?, game_name = ?, game_json = ?
                WHERE game_id = ?
                """;
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement(sql)) {
            bindGame(statement, game);
            statement.setInt(5, game.gameID());
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Game not found");
            }
        } catch (SQLException ex) {
            throw new DataAccessException("failed to update game", ex);
        }
    }

    @Override
    public void clear() throws DataAccessException {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DELETE FROM games")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to clear games", ex);
        }
    }

    private void validateGame(GameData game) throws DataAccessException {
        if (game == null || game.game() == null) {
            throw new DataAccessException("Game and game state are required");
        }
    }

    private void bindGame(PreparedStatement statement, GameData game) throws SQLException {
        statement.setString(1, game.whiteUsername());
        statement.setString(2, game.blackUsername());
        statement.setString(3, game.gameName());
        statement.setString(4, gson.toJson(game.game()));
    }

    private GameData readGame(ResultSet rows) throws SQLException, DataAccessException {
        try {
            ChessGame game = gson.fromJson(rows.getString("game_json"), ChessGame.class);
            if (game == null) {
                throw new DataAccessException("stored game state is missing");
            }
            return new GameData(rows.getInt("game_id"), rows.getString("white_username"),
                    rows.getString("black_username"), rows.getString("game_name"), game);
        } catch (JsonParseException ex) {
            throw new DataAccessException("failed to read stored game state", ex);
        }
    }
}
