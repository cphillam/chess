package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.UserDAO;
import model.UserData;

import java.sql.Connection;
import java.sql.SQLException;

public class SqlUserDAO implements UserDAO {
    public SqlUserDAO() throws DataAccessException {
        SqlDatabase.initialize();
    }

    @Override
    public void createUser(UserData user) throws DataAccessException {
        if (user == null || user.username() == null) {
            throw new DataAccessException("User and username are required");
        }

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("INSERT INTO users (username, password, email) VALUES (?, ?, ?)")) {
            statement.setString(1, user.username());
            statement.setString(2, user.password());
            statement.setString(3, user.email());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to create user", ex);
        }
    }

    @Override
    public UserData getUser(String username) throws DataAccessException {
        if (username == null) {
            return null;
        }

        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("SELECT username, password, email FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return new UserData(rows.getString("username"), rows.getString("password"), rows.getString("email"));
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DataAccessException("failed to read user", ex);
        }
    }

    @Override
    public void clear() throws DataAccessException {
        try (Connection conn = DatabaseManager.getConnection();
             var statement = conn.prepareStatement("DELETE FROM users")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("failed to clear users", ex);
        }
    }
}
