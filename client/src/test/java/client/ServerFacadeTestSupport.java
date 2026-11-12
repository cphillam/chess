package client;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class ServerFacadeTestSupport {
    protected static URI serverURL;
    protected static ServerFacade facade;
    private static Server server;
    private HttpServer stubServer;

    @BeforeAll
    public static void init() {
        server = new Server();
        int port = server.run(0);
        serverURL = URI.create("http://localhost:" + port);
        facade = new ServerFacade(port);
    }

    @BeforeEach
    public void clearDatabase() throws Exception {
        assertEquals(200, request("DELETE", "/db", null, null).statusCode(), "Unable to clear the test database");
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @AfterEach
    public void stopStubServer() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    protected void joinAsPlayer(int gameID, String color, String token) throws Exception {
        String body = new Gson().toJson(Map.of("gameID", gameID, "playerColor", color));
        assertEquals(200, request("PUT", "/game", body, token).statusCode(), "Unable to prepare an occupied player seat");
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(serverURL.resolve(path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (token != null) {
                builder.header("authorization", token);
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    protected ServerFacade responseFacade(int status, String body) throws IOException {
        return stubFacade(exchange -> respond(exchange, status, body));
    }

    protected void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    protected ServerFacade stubFacade(HttpHandler handler) throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.createContext("/", handler);
        stubServer.start();
        return new ServerFacade(stubServer.getAddress().getPort());
    }

    protected ServerFacade recordingNoContentFacade(AtomicReference<List<String>> received) throws IOException {
        return stubFacade(exchange -> {
            received.set(List.of(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
    }

    protected static Stream<Arguments> invalidJoinRequests() {
        Stream<Arguments> invalidIDs = IntStream.of(-1, 0, Integer.MAX_VALUE)
                .mapToObj(gameID -> Arguments.of(gameID, "WHITE"));
        Stream<Arguments> invalidColors = Stream.of(null, "", " ", "white", "RED", "OBSERVER")
                .map(color -> Arguments.of(null, color));
        return Stream.concat(invalidIDs, invalidColors);
    }

    protected static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of(null, "secret", "alice@example.com"),
                Arguments.of("", "secret", "alice@example.com"),
                Arguments.of(" ", "secret", "alice@example.com"),
                Arguments.of("alice", null, "alice@example.com"),
                Arguments.of("alice", "", "alice@example.com"),
                Arguments.of("alice", " ", "alice@example.com"),
                Arguments.of("alice", "secret", null),
                Arguments.of("alice", "secret", ""),
                Arguments.of("alice", "secret", " "));
    }

    protected static Stream<Arguments> invalidLoginCredentials() {
        return Stream.of(null, "", " ").flatMap(value -> Stream.of(
                Arguments.of(value, "secret"), Arguments.of("alice", value)));
    }

    protected static Stream<Arguments> invalidAuthenticationResponses() {
        return Stream.of(
                "not-json", "null", "", "[]", "{}", "{\"username\":\"alice\"}", "{\"authToken\":\"token\"}",
                "{\"username\":\"alice\",\"authToken\":\"\"}", "{\"username\":\" \",\"authToken\":\"token\"}",
                "{\"username\":\"alice\",\"authToken\":{}}")
                .flatMap(body -> Stream.of(Arguments.of(false, body), Arguments.of(true, body)));
    }

    protected static Stream<Arguments> invalidGameSessions() {
        return Stream.of(null, "", " ", "not-a-session", "private\r\ninjected-header: value", "♟")
                .flatMap(token -> Stream.of(Arguments.of(false, token), Arguments.of(true, token)));
    }

    protected static Stream<Arguments> invalidGameResponses() {
        Stream<Arguments> common = Stream.of("not-json", "null", "", "[]", "{}")
                .flatMap(body -> Stream.of(Arguments.of(false, body), Arguments.of(true, body)));
        Stream<Arguments> creation = Stream.of(
                "{\"gameID\":null}", "{\"gameID\":0}", "{\"gameID\":-1}", "{\"gameID\":1.5}",
                "{\"gameID\":\"invalid\"}", "{\"gameID\":{}}")
                .map(body -> Arguments.of(true, body));
        Stream<Arguments> listing = Stream.of(
                "{\"games\":null}", "{\"games\":{}}", "{\"games\":[null]}", "{\"games\":[{}]}",
                "{\"games\":[{\"gameID\":1}]}", "{\"games\":[{\"gameID\":1,\"gameName\":\" \"}]}",
                "{\"games\":[{\"gameID\":0,\"gameName\":\"Final\"}]}",
                "{\"games\":[{\"gameID\":1,\"gameName\":\"Final\",\"whiteUsername\":\"\"}]}",
                "{\"games\":[{\"gameID\":1,\"gameName\":\"Final\",\"blackUsername\":\" \"}]}",
                "{\"games\":[{\"gameID\":1,\"gameName\":\"First\"},{\"gameID\":1,\"gameName\":\"Second\"}]}")
                .map(body -> Arguments.of(false, body));
        return Stream.concat(common, Stream.concat(creation, listing));
    }
}
