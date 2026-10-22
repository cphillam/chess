package server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.AlreadyTakenException;
import service.BadRequestException;
import service.LoginRequest;
import service.LoginResult;
import service.RegisterRequest;
import service.RegisterResult;
import service.UnauthorizedException;
import service.UserService;

public class UserHandler {
    private final UserService userService;
    private final Gson gson = new Gson();

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    public void handleRegister(Context context)
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        RegisterRequest request = readRequest(context, RegisterRequest.class);
        RegisterResult result = userService.register(request);
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }

    public void handleLogin(Context context)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        LoginRequest request = readRequest(context, LoginRequest.class);
        LoginResult result = userService.login(request);
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }

    public void handleLogout(Context context) throws UnauthorizedException, DataAccessException {
        userService.logout(context.header("authorization"));
        context.status(200).contentType("application/json").result("{}");
    }

    private <T> T readRequest(Context context, Class<T> requestType) throws BadRequestException {
        try {
            return gson.fromJson(context.body(), requestType);
        } catch (JsonParseException exception) {
            throw new BadRequestException();
        }
    }
}
