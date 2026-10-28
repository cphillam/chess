package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

public class UserService {
    private final UserDAO userDAO;
    private final AuthDAO authDAO;

    public UserService(UserDAO userDAO, AuthDAO authDAO) {
        this.userDAO = userDAO;
        this.authDAO = authDAO;
    }

    public RegisterResult register(RegisterRequest request)
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        if (request == null || isMissing(request.username())
                || isMissing(request.password()) || isMissing(request.email())) {
            throw new BadRequestException();
        }
        if (userDAO.getUser(request.username()) != null) {
            throw new AlreadyTakenException();
        }

        String passwordHash = BCrypt.hashpw(request.password(), BCrypt.gensalt());
        userDAO.createUser(new UserData(request.username(), passwordHash, request.email()));
        return new RegisterResult(request.username(), createAuth(request.username()));
    }

    public LoginResult login(LoginRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        if (request == null || isMissing(request.username()) || isMissing(request.password())) {
            throw new BadRequestException();
        }
        UserData user = userDAO.getUser(request.username());
        if (user == null || !passwordMatches(request.password(), user.password())) {
            throw new UnauthorizedException();
        }

        return new LoginResult(user.username(), createAuth(user.username()));
    }

    public void logout(String authToken) throws UnauthorizedException, DataAccessException {
        if (isMissing(authToken) || authDAO.getAuth(authToken) == null) {
            throw new UnauthorizedException();
        }
        authDAO.deleteAuth(authToken);
    }

    private boolean passwordMatches(String password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return false;
        }
    }

    private String createAuth(String username) throws DataAccessException {
        String authToken = UUID.randomUUID().toString();
        authDAO.createAuth(new AuthData(authToken, username));
        return authToken;
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
