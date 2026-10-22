package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.BadRequestException;
import service.CreateGameRequest;
import service.CreateGameResult;
import service.GameService;
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
        CreateGameRequest request;
        try {
            request = gson.fromJson(context.body(), CreateGameRequest.class);
        } catch (JsonParseException exception) {
            throw new BadRequestException();
        }
        CreateGameResult result = gameService.createGame(request, context.header("authorization"));
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }

    public void handleListGames(Context context) throws UnauthorizedException, DataAccessException {
        ListGamesResult result = gameService.listGames(context.header("authorization"));
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }
}
