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

    public ListGamesResult listGames(String authToken) throws UnauthorizedException, DataAccessException {
        validateAuth(authToken);
        var games = gameDAO.listGames().stream()
                .map(game -> new ListGamesResult.GameSummary(
                        game.gameID(), game.whiteUsername(), game.blackUsername(), game.gameName()))
                .toList();
        return new ListGamesResult(games);
    }

    public void joinGame(JoinGameRequest request, String authToken)
            throws UnauthorizedException, BadRequestException, AlreadyTakenException, DataAccessException {
        AuthData auth = validateAuth(authToken);
        if (request == null || request.gameID() == null || request.gameID() <= 0
                || (!"WHITE".equals(request.playerColor()) && !"BLACK".equals(request.playerColor()))) {
            throw new BadRequestException();
        }

        synchronized (gameDAO) {
            GameData game = gameDAO.getGame(request.gameID());
            if (game == null) {
                throw new BadRequestException();
            }
            boolean joiningWhite = "WHITE".equals(request.playerColor());
            String occupant = joiningWhite ? game.whiteUsername() : game.blackUsername();
            if (occupant != null) {
                throw new AlreadyTakenException();
            }

            gameDAO.updateGame(new GameData(
                    game.gameID(),
                    joiningWhite ? auth.username() : game.whiteUsername(),
                    joiningWhite ? game.blackUsername() : auth.username(),
                    game.gameName(),
                    game.game()));
        }
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
