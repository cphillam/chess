package dataaccess;

import model.AuthData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryAuthDAO implements AuthDAO {
    private final Map<String, AuthData> authTokens = new ConcurrentHashMap<>();

    @Override
    public void createAuth(AuthData auth) throws DataAccessException {
        if (auth == null || auth.authToken() == null || auth.username() == null) {
            throw new DataAccessException("Auth token and username are required");
        }
        if (authTokens.putIfAbsent(auth.authToken(), auth) != null) {
            throw new DataAccessException("Auth token already exists");
        }
    }

    @Override
    public AuthData getAuth(String authToken) {
        return authToken == null ? null : authTokens.get(authToken);
    }

    @Override
    public void deleteAuth(String authToken) {
        if (authToken != null) {
            authTokens.remove(authToken);
        }
    }

    @Override
    public void clear() {
        authTokens.clear();
    }
}
