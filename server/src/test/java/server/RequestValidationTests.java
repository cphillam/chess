package server;

import chess.ChessGame;
import com.google.gson.JsonParser;
import dataaccess.AuthDAO;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import service.ClearService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RequestValidationTests {
    private static UserDAO userDAO;
    private static AuthDAO authDAO;
    private static GameDAO gameDAO;
    private static Server server;
    private static HttpClient client;
    private static String serverURL;
    private int gameID;

    @BeforeAll
    public static void startServer() {
        userDAO = new MemoryUserDAO();
        authDAO = new MemoryAuthDAO();
        gameDAO = new MemoryGameDAO();
        server = new Server(userDAO, authDAO, gameDAO);
        serverURL = "http://127.0.0.1:" + server.run(0);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    public static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        new ClearService(userDAO, authDAO, gameDAO).clear();
        userDAO.createUser(new UserData("alice", "password", "alice@example.com"));
        authDAO.createAuth(new AuthData("alice-token", "alice"));
        gameID = gameDAO.createGame(new GameData(0, null, null, "Existing game", new ChessGame()));
    }

    @ParameterizedTest
    @MethodSource("malformedRequests")
    public void malformedJSONReturnsBadRequest(RequestCase requestCase, String style) throws Exception {
        String body = requestCase.body().replace("GAME_ID", Integer.toString(gameID));
        body = switch (style) {
            case "single quotes" -> body.replace('"', '\'');
            case "unquoted names" -> body.replaceAll("\"([A-Za-z]+)\":", "$1:");
            case "leading comment" -> "/* comment */" + body;
            case "trailing comment" -> body + " // comment";
            default -> throw new IllegalArgumentException(style);
        };
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverURL + requestCase.path()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "alice-token")
                .method(requestCase.method(), HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        var error = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(Set.of("message"), error.keySet());
        assertEquals("Error: bad request", error.get("message").getAsString());
        assertNull(userDAO.getUser("new-user"));
        assertEquals(1, gameDAO.listGames().size());
        assertNull(gameDAO.getGame(gameID).whiteUsername());
        assertNull(gameDAO.getGame(gameID).blackUsername());
        assertEquals(new AuthData("alice-token", "alice"), authDAO.getAuth("alice-token"));
    }

    private static Stream<Arguments> malformedRequests() {
        return Stream.of(
                new RequestCase("POST", "/user",
                        "{\"username\":\"new-user\",\"password\":\"password\",\"email\":\"new@example.com\"}"),
                new RequestCase("POST", "/session", "{\"username\":\"alice\",\"password\":\"password\"}"),
                new RequestCase("POST", "/game", "{\"gameName\":\"New game\"}"),
                new RequestCase("PUT", "/game", "{\"playerColor\":\"WHITE\",\"gameID\":GAME_ID}"))
                .flatMap(request -> Stream.of("single quotes", "unquoted names", "leading comment", "trailing comment")
                        .map(style -> Arguments.of(request, style)));
    }

    private record RequestCase(String method, String path, String body) {
    }
}
