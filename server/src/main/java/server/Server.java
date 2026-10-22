package server;

import com.google.gson.Gson;
import dataaccess.AuthDAO;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import service.AlreadyTakenException;
import service.BadRequestException;
import service.ClearService;
import service.GameService;
import service.UnauthorizedException;
import service.UserService;

import java.util.Map;

public class Server {

    private final Javalin javalin;
    private final Gson gson = new Gson();

    public Server() {
        this(new MemoryUserDAO(), new MemoryAuthDAO(), new MemoryGameDAO());
    }

    public Server(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        javalin = Javalin.create(config -> config.staticFiles.add("web"));
        ClearService clearService = new ClearService(userDAO, authDAO, gameDAO);
        ClearHandler clearHandler = new ClearHandler(clearService);
        javalin.delete("/db", clearHandler::handleClear);

        UserService userService = new UserService(userDAO, authDAO);
        UserHandler userHandler = new UserHandler(userService);
        javalin.post("/user", userHandler::handleRegister);
        javalin.post("/session", userHandler::handleLogin);
        javalin.delete("/session", userHandler::handleLogout);

        GameService gameService = new GameService(gameDAO, authDAO);
        GameHandler gameHandler = new GameHandler(gameService);
        javalin.post("/game", gameHandler::handleCreateGame);

        javalin.exception(BadRequestException.class, (exception, context) -> sendError(exception, context, 400));
        javalin.exception(UnauthorizedException.class, (exception, context) -> sendError(exception, context, 401));
        javalin.exception(AlreadyTakenException.class, (exception, context) -> sendError(exception, context, 403));
        javalin.exception(Exception.class, (exception, context) -> sendError(exception, context, 500));
    }

    private void sendError(Exception exception, Context context, int status) {
        context.status(status).contentType("application/json");
        context.result(gson.toJson(Map.of("message", "Error: " + exception.getMessage())));
    }

    public int run(int desiredPort) {
        javalin.start(desiredPort);
        return javalin.port();
    }

    public void stop() {
        javalin.stop();
    }
}
