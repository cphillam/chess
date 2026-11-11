package client;

import model.AuthData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ServerFacadeTests extends ServerFacadeTestSupport {
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

    @Test
    public void loginReturnsANewUsableSession() throws ClientException {
        AuthData registered = facade.register("alice", "secret", "alice@example.com");

        AuthData loggedIn = facade.login("alice", "secret");

        assertEquals("alice", loggedIn.username());
        assertNotNull(loggedIn.authToken());
        assertFalse(loggedIn.authToken().isBlank());
        assertNotEquals(registered.authToken(), loggedIn.authToken());
        assertDoesNotThrow(() -> facade.logout(loggedIn.authToken()));
        assertDoesNotThrow(() -> facade.logout(registered.authToken()));
    }

    @Test
    public void loginPreservesSpecialCharactersInCredentials() throws ClientException {
        String username = "Échecs O'Brien ♟";
        String password = "quoted \"password\" with \\ spaces";
        facade.register(username, password, "alice@example.com");

        AuthData auth = facade.login(username, password);

        assertEquals(username, auth.username());
        assertDoesNotThrow(() -> facade.logout(auth.authToken()));
    }

    @ParameterizedTest
    @CsvSource({"alice, wrong-password", "missing-user, secret"})
    public void invalidCredentialsHaveTheSameFriendlyError(String username, String password) throws ClientException {
        AuthData existingSession = facade.register("alice", "secret", "alice@example.com");

        ClientException error = assertThrows(ClientException.class, () -> facade.login(username, password));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals("Incorrect username or password.", error.getMessage());
        assertDoesNotThrow(() -> facade.logout(existingSession.authToken()));
    }

    @ParameterizedTest
    @MethodSource("invalidLoginCredentials")
    public void missingLoginFieldsAreReported(String username, String password) {
        ClientException error = assertThrows(ClientException.class, () -> facade.login(username, password));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("Please check the details and try again.", error.getMessage());
    }

    @Test
    public void logoutRevokesOnlyTheSuppliedSessionAndAllowsLoggingInAgain() throws ClientException {
        AuthData registered = facade.register("alice", "secret", "alice@example.com");
        AuthData anotherSession = facade.login("alice", "secret");
        AuthData otherUser = facade.register("bob", "secret", "bob@example.com");

        facade.logout(registered.authToken());

        ClientException error = assertThrows(ClientException.class, () -> facade.logout(registered.authToken()));
        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals("Please log in and try again.", error.getMessage());
        assertDoesNotThrow(() -> facade.logout(anotherSession.authToken()));
        assertDoesNotThrow(() -> facade.logout(otherUser.authToken()));
        AuthData newSession = facade.login("alice", "secret");
        assertNotEquals(registered.authToken(), newSession.authToken());
        assertDoesNotThrow(() -> facade.logout(newSession.authToken()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-session", "private\r\ninjected-header: value", "♟"})
    public void logoutRejectsMissingInvalidOrUnsafeTokens(String token) {
        ClientException error = assertThrows(ClientException.class, () -> facade.logout(token));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals("Please log in and try again.", error.getMessage());
    }

    @Test
    public void logoutSendsTheTokenInTheHeaderWithNoRequestBody() throws Exception {
        AtomicReference<List<String>> received = new AtomicReference<>();
        ServerFacade loggingOut = stubFacade(exchange -> {
            received.set(List.of(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        assertDoesNotThrow(() -> loggingOut.logout("session-token"));
        assertEquals(List.of("DELETE", "/session", "session-token", ""), received.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", ""})
    public void logoutAcceptsSuccessfulResponsesWithoutAResult(String body) throws Exception {
        ServerFacade loggingOut = responseFacade(200, body);

        assertDoesNotThrow(() -> loggingOut.logout("session-token"));
    }

    @Test
    public void logoutDoesNotTreatServerFailuresAsSuccess() throws Exception {
        ServerFacade failing = responseFacade(500, "{\"message\":\"private session-token\"}");

        ClientException error = assertThrows(ClientException.class, () -> failing.logout("session-token"));

        assertEquals(ClientException.Kind.SERVER_ERROR, error.getKind());
        assertEquals("The server could not complete your request. Please try again.", error.getMessage());
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
    @MethodSource("invalidAuthenticationResponses")
    public void malformedSuccessResponsesAreReportedSafely(boolean login, String body) throws Exception {
        ServerFacade malformed = responseFacade(200, body);

        ClientException error = assertThrows(ClientException.class, () -> {
            if (login) {
                malformed.login("alice", "secret");
            } else {
                malformed.register("alice", "secret", "alice@example.com");
            }
        });

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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    public void createdGamesAppearInTheListingWithOpenSeats(int count) throws ClientException {
        AuthData auth = facade.register("alice", "secret", "alice@example.com");
        List<GameSummary> expected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String name = "Friday ♟ \"Finals\" \\ Round " + index;
            int gameID = facade.createGame(name, auth.authToken());
            assertTrue(gameID > 0);
            expected.add(new GameSummary(gameID, null, null, name));
        }

        List<GameSummary> games = facade.listGames(auth.authToken());

        assertEquals(count, games.size());
        assertEquals(Set.copyOf(expected), Set.copyOf(games));
    }

    @Test
    public void listingsIncludePlayersAndGamesCreatedByOtherUsers() throws Exception {
        AuthData alice = facade.register("Alice Smith", "secret", "alice@example.com");
        AuthData bob = facade.register("Échecs♟", "secret", "bob@example.com");
        int gameID = facade.createGame("Final", alice.authToken());
        joinAsPlayer(gameID, "WHITE", alice.authToken());
        joinAsPlayer(gameID, "BLACK", bob.authToken());

        List<GameSummary> games = facade.listGames(bob.authToken());

        assertEquals(List.of(new GameSummary(gameID, "Alice Smith", "Échecs♟", "Final")), games);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    public void invalidGameNamesAreRejectedWithoutCreatingGames(String name) throws ClientException {
        AuthData auth = facade.register("alice", "secret", "alice@example.com");

        ClientException error = assertThrows(ClientException.class, () -> facade.createGame(name, auth.authToken()));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("Please check the details and try again.", error.getMessage());
        assertTrue(facade.listGames(auth.authToken()).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidGameSessions")
    public void gameOperationsRejectMissingInvalidOrUnsafeSessions(boolean create, String token) {
        ClientException error = assertThrows(ClientException.class, () -> requestGameOperation(facade, create, token));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals("Please log in and try again.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void loggedOutSessionsCannotCreateOrListGames(boolean create) throws ClientException {
        AuthData auth = facade.register("alice", "secret", "alice@example.com");
        facade.logout(auth.authToken());

        ClientException error = assertThrows(ClientException.class, () -> requestGameOperation(facade, create, auth.authToken()));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
    }

    @Test
    public void gameOperationsUseAuthorizationHeadersAndTheCorrectRequestBodies() throws Exception {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        ServerFacade configured = stubFacade(exchange -> {
            String method = exchange.getRequestMethod();
            received.add(List.of(method, exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, 200, "POST".equals(method) ? "{\"gameID\":73}"
                    : "{\"games\":[{\"gameID\":73,\"whiteUsername\":\"Alice\",\"gameName\":\"Friday Night Chess\"}]}");
        });

        assertEquals(73, configured.createGame("Friday Night Chess", "private-token"));
        assertEquals(List.of(new GameSummary(73, "Alice", null, "Friday Night Chess")), configured.listGames("private-token"));
        assertEquals(List.of(
                List.of("POST", "/game", "private-token", "{\"gameName\":\"Friday Night Chess\"}"),
                List.of("GET", "/game", "private-token", "")), received);
    }

    @ParameterizedTest
    @MethodSource("invalidGameResponses")
    public void malformedGameResponsesAreReportedSafely(boolean create, String body) throws Exception {
        ServerFacade malformed = responseFacade(200, body);

        ClientException error = assertThrows(ClientException.class, () -> requestGameOperation(malformed, create, "private-token"));

        assertEquals(ClientException.Kind.INVALID_RESPONSE, error.getKind());
        assertEquals("The server returned an invalid response. Please try again.", error.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"true, 400, BAD_REQUEST", "false, 400, BAD_REQUEST", "true, 401, UNAUTHORIZED", "false, 401, UNAUTHORIZED",
            "true, 403, ALREADY_TAKEN", "false, 403, ALREADY_TAKEN", "true, 500, SERVER_ERROR", "false, 500, SERVER_ERROR"})
    public void gameServerErrorsDoNotExposePrivateOrUnrelatedDetails(boolean create, int status, ClientException.Kind kind)
            throws Exception {
        ServerFacade failing = responseFacade(status, "{\"message\":\"private database error\"}");

        ClientException error = assertThrows(ClientException.class, () -> requestGameOperation(failing, create, "private-token"));

        assertEquals(kind, error.getKind());
        assertFalse(error.getMessage().contains("private"));
        assertFalse(error.getMessage().contains("username"));
        assertFalse(error.getMessage().contains(Integer.toString(status)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void disconnectedGameRequestsHaveAFriendlyError(boolean create) throws Exception {
        ServerFacade disconnected = stubFacade(exchange -> exchange.close());

        ClientException error = assertThrows(ClientException.class, () -> requestGameOperation(disconnected, create, "private-token"));

        assertEquals(ClientException.Kind.CONNECTION_ERROR, error.getKind());
        assertEquals("Unable to contact the server. Please try again.", error.getMessage());
    }

    private void requestGameOperation(ServerFacade target, boolean create, String token) throws ClientException {
        if (create) {
            target.createGame("Friday Night Chess", token);
        } else {
            target.listGames(token);
        }
    }
}
