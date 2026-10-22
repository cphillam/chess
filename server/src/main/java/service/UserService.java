package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;

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

        userDAO.createUser(new UserData(request.username(), request.password(), request.email()));
        String authToken = UUID.randomUUID().toString();
        authDAO.createAuth(new AuthData(authToken, request.username()));
        return new RegisterResult(request.username(), authToken);
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
