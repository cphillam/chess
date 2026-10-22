package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.AlreadyTakenException;
import service.BadRequestException;
import service.CreateGameRequest;
import service.CreateGameResult;
import service.GameService;
import service.JoinGameRequest;
import service.ListGamesResult;
import service.UnauthorizedException;

public class GameHandler {
    private final GameService gameService;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public GameHandler(GameService gameService) {
        this.gameService = gameService;
    }

    public void handleCreateGame(Context context)
            throws UnauthorizedException, BadRequestException, DataAccessException {
        CreateGameRequest request = JsonRequestReader.read(context.body(), CreateGameRequest.class);
        CreateGameResult result = gameService.createGame(request, context.header("authorization"));
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }

    public void handleListGames(Context context) throws UnauthorizedException, DataAccessException {
        ListGamesResult result = gameService.listGames(context.header("authorization"));
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }

    public void handleJoinGame(Context context)
            throws UnauthorizedException, BadRequestException, AlreadyTakenException, DataAccessException {
        JoinGameRequest request = JsonRequestReader.read(context.body(), JoinGameRequest.class);
        gameService.joinGame(request, context.header("authorization"));
        context.status(200).contentType("application/json").result("{}");
    }
}
