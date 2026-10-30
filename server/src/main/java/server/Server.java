package server;

import com.google.gson.Gson;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.UserDAO;
import dataaccess.sql.SqlAuthDAO;
import dataaccess.sql.SqlGameDAO;
import dataaccess.sql.SqlUserDAO;
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
        try {
            javalin = createJavalin(new SqlUserDAO(), new SqlAuthDAO(), new SqlGameDAO());
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Unable to initialize database", ex);
        }
    }

    public Server(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        javalin = createJavalin(userDAO, authDAO, gameDAO);
    }

    private Javalin createJavalin(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        Javalin app = Javalin.create(config -> config.staticFiles.add("web"));
        ClearService clearService = new ClearService(userDAO, authDAO, gameDAO);
        ClearHandler clearHandler = new ClearHandler(clearService);
        app.delete("/db", clearHandler::handleClear);

        UserService userService = new UserService(userDAO, authDAO);
        UserHandler userHandler = new UserHandler(userService);
        app.post("/user", userHandler::handleRegister);
        app.post("/session", userHandler::handleLogin);
        app.delete("/session", userHandler::handleLogout);

        GameService gameService = new GameService(gameDAO, authDAO);
        GameHandler gameHandler = new GameHandler(gameService);
        app.post("/game", gameHandler::handleCreateGame);
        app.get("/game", gameHandler::handleListGames);
        app.put("/game", gameHandler::handleJoinGame);

        app.exception(BadRequestException.class, (exception, context) -> sendError(exception, context, 400));
        app.exception(UnauthorizedException.class, (exception, context) -> sendError(exception, context, 401));
        app.exception(AlreadyTakenException.class, (exception, context) -> sendError(exception, context, 403));
        app.exception(DataAccessException.class, (exception, context) -> sendError(exception, context, 500));
        app.exception(Exception.class, (exception, context) -> sendError(exception, context, 500));
        return app;
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
