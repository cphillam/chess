package dataaccess.sql;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import model.AuthData;

import java.sql.Connection;
import java.sql.SQLException;

public class SqlAuthDAO implements AuthDAO {
    public SqlAuthDAO() throws DataAccessException {
        SqlDatabase.initialize();
    }

    @Override
    public void createAuth(AuthData auth) throws DataAccessException {
        if (auth == null || auth.authToken() == null || auth.username() == null) {
            throw new DataAccessException("Auth token and username are required");
        }

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("INSERT INTO auth_tokens (auth_token, username) VALUES (?, ?)")) {
            statement.setString(1, auth.authToken());
            statement.setString(2, auth.username());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to create auth token", ex);
        }
    }

    @Override
    public AuthData getAuth(String authToken) throws DataAccessException {
        if (authToken == null) {
            return null;
        }

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("SELECT auth_token, username FROM auth_tokens WHERE auth_token = ?")) {
            statement.setString(1, authToken);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return new AuthData(rows.getString("auth_token"), rows.getString("username"));
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DataAccessException("failed to read auth token", ex);
        }
    }

    @Override
    public void deleteAuth(String authToken) throws DataAccessException {
        if (authToken == null) {
            return;
        }

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DELETE FROM auth_tokens WHERE auth_token = ?")) {
            statement.setString(1, authToken);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to delete auth token", ex);
        }
    }

    @Override
    public void clear() throws DataAccessException {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DELETE FROM auth_tokens")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to clear auth tokens", ex);
        }
    }
}
