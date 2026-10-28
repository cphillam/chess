package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class SqlDatabase {
    private static final String[] CREATE_TABLE_STATEMENTS = {
            """
            CREATE TABLE IF NOT EXISTS users (
                username VARCHAR(255) NOT NULL,
                password VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL,
                PRIMARY KEY (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin
            """,
            """
            CREATE TABLE IF NOT EXISTS auth_tokens (
                auth_token VARCHAR(255) NOT NULL,
                username VARCHAR(255) NOT NULL,
                PRIMARY KEY (auth_token)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin
            """,
            """
            CREATE TABLE IF NOT EXISTS games (
                game_id INT NOT NULL AUTO_INCREMENT,
                white_username VARCHAR(255) DEFAULT NULL,
                black_username VARCHAR(255) DEFAULT NULL,
                game_name VARCHAR(255) NOT NULL,
                game_json TEXT NOT NULL,
                PRIMARY KEY (game_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin
            """
    };

    private SqlDatabase() {
    }

    public static void initialize() throws DataAccessException {
        DatabaseManager.createDatabase();
        try (Connection conn = DatabaseManager.getConnection()) {
            for (String statement : CREATE_TABLE_STATEMENTS) {
                try (var preparedStatement = conn.prepareStatement(statement)) {
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("failed to create database tables", ex);
        }
    }
}
