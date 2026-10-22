package server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.AlreadyTakenException;
import service.BadRequestException;
import service.RegisterRequest;
import service.RegisterResult;
import service.UserService;

public class UserHandler {
    private final UserService userService;
    private final Gson gson = new Gson();

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    public void handleRegister(Context context)
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        RegisterRequest request;
        try {
            request = gson.fromJson(context.body(), RegisterRequest.class);
        } catch (JsonParseException exception) {
            throw new BadRequestException();
        }
        RegisterResult result = userService.register(request);
        context.status(200).contentType("application/json").result(gson.toJson(result));
    }
}
