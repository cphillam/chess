package dataaccess;

import model.UserData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryUserDAO implements UserDAO {
    private final Map<String, UserData> users = new ConcurrentHashMap<>();

    @Override
    public void createUser(UserData user) throws DataAccessException {
        if (user == null || user.username() == null) {
            throw new DataAccessException("User and username are required");
        }
        if (users.putIfAbsent(user.username(), user) != null) {
            throw new DataAccessException("User already exists");
        }
    }

    @Override
    public UserData getUser(String username) {
        return username == null ? null : users.get(username);
    }

    @Override
    public void clear() {
        users.clear();
    }
}
