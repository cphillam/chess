package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.AuthData;
import model.GameData;

public class GameService {
    private final GameDAO gameDAO;
    private final AuthDAO authDAO;

    public GameService(GameDAO gameDAO, AuthDAO authDAO) {
        this.gameDAO = gameDAO;
        this.authDAO = authDAO;
    }

    public CreateGameResult createGame(CreateGameRequest request, String authToken)
            throws UnauthorizedException, BadRequestException, DataAccessException {
        validateAuth(authToken);
        if (request == null || request.gameName() == null || request.gameName().isBlank()) {
            throw new BadRequestException();
        }

        int gameID = gameDAO.createGame(new GameData(0, null, null, request.gameName(), new ChessGame()));
        return new CreateGameResult(gameID);
    }

    private AuthData validateAuth(String authToken) throws UnauthorizedException, DataAccessException {
        if (authToken == null || authToken.isBlank()) {
            throw new UnauthorizedException();
        }
        AuthData auth = authDAO.getAuth(authToken);
        if (auth == null) {
            throw new UnauthorizedException();
        }
        return auth;
    }
}
