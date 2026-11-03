package client;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.AuthData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ServerFacadeTests {
    private static Server server;
    private static URI serverURL;
    private static ServerFacade facade;
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
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            HttpRequest request = HttpRequest.newBuilder(serverURL.resolve("/db"))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Unable to clear the test database");
        }
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

    @Test
    public void registerReturnsUsernameAndAuthenticationToken() throws ClientException {
        AuthData auth = facade.register("alice", "secret", "alice@example.com");

        assertEquals("alice", auth.username());
        assertNotNull(auth.authToken());
        assertFalse(auth.authToken().isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/"})
    public void serverURLConstructorCanRegister(String suffix) throws ClientException {
        ServerFacade configured = new ServerFacade(serverURL + suffix);

        AuthData auth = configured.register("alice", "secret", "alice@example.com");

        assertEquals("alice", auth.username());
        assertFalse(auth.authToken().isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"O'Brien", "Échecs♟", "alice smith"})
    public void registrationPreservesSpecialCharacters(String username) throws ClientException {
        AuthData auth = facade.register(username, "quoted \"password\" with \\ spaces", "alice+tag@example.com");

        assertEquals(username, auth.username());
        assertFalse(auth.authToken().isBlank());
    }

    @Test
    public void duplicateUsernameHasAFriendlyError() throws ClientException {
        facade.register("alice", "secret", "alice@example.com");

        ClientException error = assertThrows(ClientException.class,
                () -> facade.register("alice", "different-secret", "other@example.com"));

        assertEquals(ClientException.Kind.ALREADY_TAKEN, error.getKind());
        assertEquals("That username is already taken.", error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    public void missingRegistrationFieldsAreReported(String username, String password, String email) {
        ClientException error = assertThrows(ClientException.class, () -> facade.register(username, password, email));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("Please check the details and try again.", error.getMessage());
    }

    static Stream<Arguments> invalidRegistrations() {
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

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65536})
    public void invalidPortsAreRejected(int port) {
        assertThrows(IllegalArgumentException.class, () -> new ServerFacade(port));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "not a URL", "localhost:8080", "ftp://localhost", "http:///missing-host",
            "http://localhost:0", "http://localhost:65536", "http://user:private@localhost",
            "http://localhost?token=private", "http://localhost/#private", "http://localhost/path"
    })
    public void invalidServerURLsAreRejectedWithoutEchoingTheirContents(String url) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new ServerFacade(url));

        assertEquals("Provide an HTTP or HTTPS server URL with a host and an optional port.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "https://localhost", "HTTP://LOCALHOST:8080/", "http://[::1]:8080/"})
    public void supportedServerAddressesAreAccepted(String url) {
        assertDoesNotThrow(() -> new ServerFacade(url));
    }

    @ParameterizedTest
    @CsvSource({"400, BAD_REQUEST", "401, UNAUTHORIZED", "403, ALREADY_TAKEN", "404, SERVER_ERROR",
            "500, SERVER_ERROR", "503, SERVER_ERROR", "302, SERVER_ERROR"})
    public void serverErrorsDoNotExposeResponseBodies(int status, ClientException.Kind expected) throws Exception {
        ServerFacade failing = responseFacade(status, "{\"message\":\"private database error\"}");

        ClientException error = assertThrows(ClientException.class,
                () -> failing.register("alice", "secret", "alice@example.com"));

        assertEquals(expected, error.getKind());
        assertFalse(error.getMessage().contains("private"));
        assertFalse(error.getMessage().contains(Integer.toString(status)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "null", "", "[]", "{}", "{\"username\":\"alice\"}", "{\"authToken\":\"token\"}",
            "{\"username\":\"alice\",\"authToken\":\"\"}", "{\"username\":\" \",\"authToken\":\"token\"}",
            "{\"username\":\"alice\",\"authToken\":{}}"
    })
    public void malformedSuccessResponsesAreReportedSafely(String body) throws Exception {
        ServerFacade malformed = responseFacade(200, body);

        ClientException error = assertThrows(ClientException.class,
                () -> malformed.register("alice", "secret", "alice@example.com"));

        assertEquals(ClientException.Kind.INVALID_RESPONSE, error.getKind());
        assertEquals("The server returned an invalid response. Please try again.", error.getMessage());
    }

    @Test
    public void closingTheConnectionWithoutAResponseIsReportedSafely() throws Exception {
        ServerFacade disconnected = stubFacade(exchange -> exchange.close());

        ClientException error = assertThrows(ClientException.class,
                () -> disconnected.register("alice", "secret", "alice@example.com"));

        assertEquals(ClientException.Kind.CONNECTION_ERROR, error.getKind());
        assertInstanceOf(IOException.class, error.getCause());
        assertEquals("Unable to contact the server. Please try again.", error.getMessage());
    }

    @Test
    public void anUnresponsiveServerTimesOut() throws Exception {
        try (ServerSocket unresponsive = new ServerSocket(0)) {
            ServerFacade waiting = new ServerFacade(unresponsive.getLocalPort());

            ClientException error = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
                    assertThrows(ClientException.class, () -> waiting.register("alice", "secret", "alice@example.com")));

            assertEquals(ClientException.Kind.CONNECTION_ERROR, error.getKind());
            assertInstanceOf(HttpTimeoutException.class, error.getCause());
        }
    }

    @Test
    public void interruptedRequestsPreserveTheInterruptFlag() {
        try {
            Thread.currentThread().interrupt();

            ClientException error = assertThrows(ClientException.class,
                    () -> facade.register("alice", "secret", "alice@example.com"));

            assertEquals(ClientException.Kind.INTERRUPTED, error.getKind());
            assertTrue(Thread.currentThread().isInterrupted());
            assertInstanceOf(InterruptedException.class, error.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    private ServerFacade responseFacade(int status, String body) throws IOException {
        return stubFacade(exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            } finally {
                exchange.close();
            }
        });
    }

    private ServerFacade stubFacade(HttpHandler handler) throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.createContext("/user", handler);
        stubServer.start();
        return new ServerFacade(stubServer.getAddress().getPort());
    }
}
