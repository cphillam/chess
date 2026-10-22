package server;

import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.ClearService;

public class ClearHandler {
    private final ClearService clearService;

    public ClearHandler(ClearService clearService) {
        this.clearService = clearService;
    }

    public void handleClear(Context context) throws DataAccessException {
        clearService.clear();
        context.status(200).contentType("application/json").result("{}");
    }
}
