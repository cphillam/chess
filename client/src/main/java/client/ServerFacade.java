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
        AuthData auth = send("POST", "/user", new RegisterRequest(username, password, email), AuthData.class);
        if (auth.authToken() == null || auth.authToken().isBlank() || auth.username() == null || auth.username().isBlank()) {
            throw invalidResponse(null);
        }
        return auth;
    }

    private <T> T send(String method, String path, Object body, Class<T> responseType) throws ClientException {
        HttpRequest request = HttpRequest.newBuilder(serverURI.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClientException(ClientException.Kind.INTERRUPTED, "The request was interrupted.", exception);
        } catch (IOException exception) {
            throw new ClientException(ClientException.Kind.CONNECTION_ERROR,
                    "Unable to contact the server. Please try again.", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw responseError(response.statusCode());
        }
        try {
            T result = GSON.fromJson(response.body(), responseType);
            if (result == null) {
                throw invalidResponse(null);
            }
            return result;
        } catch (JsonParseException exception) {
            throw invalidResponse(exception);
        }
    }

    private ClientException responseError(int statusCode) {
        return switch (statusCode) {
            case 400 -> new ClientException(ClientException.Kind.BAD_REQUEST, "Please check the details and try again.");
            case 401 -> new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");
            case 403 -> new ClientException(ClientException.Kind.ALREADY_TAKEN, "That username is already taken.");
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
}
