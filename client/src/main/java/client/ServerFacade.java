package client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import model.AuthData;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServerFacade {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final URI serverURI;

    public ServerFacade(int port) {
        this(localServerURL(port));
    }

    public ServerFacade(String serverURL) {
        serverURI = parseServerURI(serverURL);
    }

    public AuthData register(String username, String password, String email) throws ClientException {
        return authenticate("/user", new RegisterRequest(username, password, email));
    }

    public AuthData login(String username, String password) throws ClientException {
        return authenticate("/session", new LoginRequest(username, password));
    }

    public void logout(String authToken) throws ClientException {
        sendAuthorized("DELETE", "/session", null, authToken, Void.class);
    }

    public int createGame(String gameName, String authToken) throws ClientException {
        CreateGameResponse response = sendAuthorized("POST", "/game", new CreateGameRequest(gameName),
                authToken, CreateGameResponse.class);
        if (response.gameID() <= 0) {
            throw invalidResponse(null);
        }
        return response.gameID();
    }

    public List<GameSummary> listGames(String authToken) throws ClientException {
        ListGamesResponse response = sendAuthorized("GET", "/game", null, authToken, ListGamesResponse.class);
        if (response.games() == null) {
            throw invalidResponse(null);
        }
        Set<Integer> gameIDs = new HashSet<>();
        for (GameSummary game : response.games()) {
            if (invalidSummary(game) || !gameIDs.add(game.gameID())) {
                throw invalidResponse(null);
            }
        }
        return List.copyOf(response.games());
    }

    public void joinGame(int gameID, String teamColor, String authToken) throws ClientException {
        sendAuthorized("PUT", "/game", new JoinGameRequest(teamColor, gameID), authToken, Void.class);
    }

    private boolean invalidSummary(GameSummary game) {
        return game == null || game.gameID() <= 0 || game.gameName() == null || game.gameName().isBlank()
                || (game.whiteUsername() != null && game.whiteUsername().isBlank())
                || (game.blackUsername() != null && game.blackUsername().isBlank());
    }

    private <T> T sendAuthorized(String method, String path, Object body, String authToken, Class<T> responseType)
            throws ClientException {
        if (authToken == null || authToken.isBlank()) {
            throw responseError(401, method, path);
        }
        return send(method, path, body, authToken, responseType);
    }

    private AuthData authenticate(String path, Object credentials) throws ClientException {
        AuthData auth = send("POST", path, credentials, null, AuthData.class);
        if (auth.authToken() == null || auth.authToken().isBlank() || auth.username() == null || auth.username().isBlank()) {
            throw invalidResponse(null);
        }
        return auth;
    }

    private <T> T send(String method, String path, Object body, String authToken, Class<T> responseType) throws ClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverURI.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        if (authToken != null) {
            try {
                builder.header("authorization", authToken);
            } catch (IllegalArgumentException exception) {
                throw responseError(401, method, path);
            }
        }
        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClientException(ClientException.Kind.INTERRUPTED, "The request was interrupted.", exception);
        } catch (IOException exception) {
            throw new ClientException(ClientException.Kind.CONNECTION_ERROR,
                    "Unable to contact the server. Please try again.", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw responseError(response.statusCode(), method, path);
        }
        if (responseType == Void.class) {
            return null;
        }
        try {
            return ResponseJson.read(response.body(), responseType);
        } catch (JsonParseException exception) {
            throw invalidResponse(exception);
        }
    }

    private ClientException responseError(int statusCode, String method, String path) {
        if (statusCode == 400 && "PUT".equals(method) && "/game".equals(path)) {
            return new ClientException(ClientException.Kind.BAD_REQUEST,
                    "Unable to join that game. Run list to refresh the game list and try again.");
        }
        if (statusCode == 401 && "POST".equals(method) && "/session".equals(path)) {
            return new ClientException(ClientException.Kind.UNAUTHORIZED, "Incorrect username or password.");
        }
        if (statusCode == 403 && "PUT".equals(method) && "/game".equals(path)) {
            return new ClientException(ClientException.Kind.ALREADY_TAKEN, "That seat is already taken.");
        }
        return switch (statusCode) {
            case 400 -> new ClientException(ClientException.Kind.BAD_REQUEST, "Please check the details and try again.");
            case 401 -> new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");
            case 403 -> new ClientException(ClientException.Kind.ALREADY_TAKEN,
                    "/user".equals(path) ? "That username is already taken." : "That action is not allowed.");
            default -> new ClientException(ClientException.Kind.SERVER_ERROR,
                    "The server could not complete your request. Please try again.");
        };
    }

    private ClientException invalidResponse(Throwable cause) {
        return new ClientException(ClientException.Kind.INVALID_RESPONSE,
                "The server returned an invalid response. Please try again.", cause);
    }

    private static String localServerURL(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535.");
        }
        return "http://localhost:" + port;
    }

    private static URI parseServerURI(String serverURL) {
        if (serverURL == null || serverURL.isBlank()) {
            throw invalidServerURL();
        }
        URI uri;
        try {
            uri = URI.create(serverURL);
        } catch (IllegalArgumentException exception) {
            throw invalidServerURL();
        }
        if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (!uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw invalidServerURL();
        }
        return uri;
    }

    private static IllegalArgumentException invalidServerURL() {
        return new IllegalArgumentException("Provide an HTTP or HTTPS server URL with a host and an optional port.");
    }

    private record RegisterRequest(String username, String password, String email) {
    }

    private record LoginRequest(String username, String password) {
    }

    private record CreateGameRequest(String gameName) {
    }

    private record CreateGameResponse(int gameID) {
    }

    private record ListGamesResponse(List<GameSummary> games) {
    }

    private record JoinGameRequest(String playerColor, int gameID) {
    }
}
