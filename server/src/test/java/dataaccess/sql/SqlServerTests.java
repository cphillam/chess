package dataaccess.sql;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dataaccess.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SqlServerTests extends SqlTestSupport {
    private final Gson gson = new Gson();
    private Server server;
    private HttpClient client;
    private String serverURL;

    @BeforeEach
    public void setupClient() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    public void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void startupCreatesDatabaseAndTablesBeforeAnyRequest() throws Exception {
        startServer();

        assertConfiguredDatabaseHasAllTables();
    }

    @Test
    public void clearingDataPersistsAfterServerRestart() throws Exception {
        startServer();
        String originalToken = register();
        createGame(originalToken);

        HttpResponse<String> cleared = request("DELETE", "/db", null, null);
        assertEquals(200, cleared.statusCode());
        restartServer();

        assertEquals(401, request("GET", "/game", null, originalToken).statusCode());
        assertEquals(401, request("POST", "/session", credentials(), null).statusCode());
        String newToken = register();
        HttpResponse<String> games = request("GET", "/game", null, newToken);
        assertEquals(200, games.statusCode());
        assertTrue(json(games).getAsJsonArray("games").isEmpty());
    }

    @Test
    public void logoutPersistsAfterRestartAndPreservesOtherSessions() throws Exception {
        startServer();
        String originalToken = register();
        int gameID = createGame(originalToken);
        HttpResponse<String> login = request("POST", "/session", credentials(), null);
        assertEquals(200, login.statusCode());
        String otherToken = json(login).get("authToken").getAsString();

        assertEquals(200, request("DELETE", "/session", null, originalToken).statusCode());
        restartServer();

        assertEquals(401, request("GET", "/game", null, originalToken).statusCode());
        HttpResponse<String> games = request("GET", "/game", null, otherToken);
        assertEquals(200, games.statusCode());
        var entries = json(games).getAsJsonArray("games");
        assertEquals(1, entries.size());
        assertEquals(gameID, entries.get(0).getAsJsonObject().get("gameID").getAsInt());
    }

    @Test
    public void startupReportsDatabaseInitializationFailure() throws Exception {
        makeDatabaseUnavailable();

        IllegalStateException exception = assertThrows(IllegalStateException.class, Server::new);

        assertInstanceOf(DataAccessException.class, exception.getCause());
    }

    private void startServer() {
        server = new Server();
        serverURL = "http://127.0.0.1:" + server.run(0);
    }

    private void restartServer() {
        server.stop();
        startServer();
    }

    private String register() throws Exception {
        var registration = Map.of("username", "alice", "password", "password", "email", "alice@example.com");
        HttpResponse<String> response = request("POST", "/user", registration, null);
        assertEquals(200, response.statusCode());
        return json(response).get("authToken").getAsString();
    }

    private int createGame(String authToken) throws Exception {
        HttpResponse<String> response = request("POST", "/game", Map.of("gameName", "Saved game"), authToken);
        assertEquals(200, response.statusCode());
        return json(response).get("gameID").getAsInt();
    }

    private Map<String, String> credentials() {
        return Map.of("username", "alice", "password", "password");
    }

    private JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private HttpResponse<String> request(String method, String path, Map<String, String> body, String authToken)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(serverURL + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json");
        if (authToken != null) {
            builder.header("Authorization", authToken);
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(gson.toJson(body));
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
