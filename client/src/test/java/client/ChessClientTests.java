package client;

import model.AuthData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.Repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ChessClientTests {
    private final AuthFacadeStub facade = new AuthFacadeStub();
    private final ChessClient client = new ChessClient(facade);

    @Test
    public void newClientsOfferOnlySignedOutCommands() {
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertEquals(List.of(Command.Type.HELP, Command.Type.QUIT, Command.Type.REGISTER, Command.Type.LOGIN), client.availableCommands());
        assertEquals("signed out> ", client.prompt());
        assertTrue(facade.authRequests.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"REGISTER", "LOGIN"})
    public void successfulAuthenticationStartsASession(Command.Type type) throws ClientException {
        Command command = authenticationCommand(type);

        String response = client.execute(command);

        assertEquals(List.of(command), facade.authRequests);
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGOUT));
        assertFalse(client.availableCommands().contains(Command.Type.LOGIN));
        assertFalse(client.availableCommands().contains(Command.Type.REGISTER));
        assertEquals("signed in> ", client.prompt());
        assertTrue(response.contains("You are signed in."));
        assertFalse(response.contains("private"));
    }

    @ParameterizedTest
    @MethodSource("authenticationFailures")
    public void failedAuthenticationLeavesTheClientSignedOutAndCanBeRetried(Command.Type type, ClientException.Kind kind)
            throws ClientException {
        ClientException failure = new ClientException(kind, "Unable to sign in. Please try again.");
        facade.nextFailure = failure;

        assertSame(failure, assertThrows(ClientException.class, () -> client.execute(authenticationCommand(type))));
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGIN));
        assertFalse(client.availableCommands().contains(Command.Type.LOGOUT));

        client.execute(authenticationCommand(Command.Type.LOGIN));
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    static Stream<Arguments> authenticationFailures() {
        return Stream.of(
                Arguments.of(Command.Type.REGISTER, ClientException.Kind.ALREADY_TAKEN),
                Arguments.of(Command.Type.REGISTER, ClientException.Kind.BAD_REQUEST),
                Arguments.of(Command.Type.LOGIN, ClientException.Kind.UNAUTHORIZED),
                Arguments.of(Command.Type.LOGIN, ClientException.Kind.CONNECTION_ERROR),
                Arguments.of(Command.Type.LOGIN, ClientException.Kind.INVALID_RESPONSE));
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"REGISTER", "LOGIN"})
    public void logoutUsesTheCurrentTokenAndReturnsToTheSignedOutMenu(Command.Type type) throws ClientException {
        client.execute(authenticationCommand(type));

        String response = client.execute(new Command(Command.Type.LOGOUT, List.of()));

        assertEquals(List.of("private-" + type + "-token"), facade.logoutTokens);
        assertEquals("You are signed out.", response);
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.REGISTER));
        assertFalse(client.availableCommands().contains(Command.Type.LOGOUT));
    }

    @Test
    public void anExpiredLogoutSessionClearsLocalAuthentication() throws ClientException {
        client.execute(authenticationCommand(Command.Type.LOGIN));
        facade.nextFailure = new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");

        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.LOGOUT, List.of())));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGIN));
        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.LOGOUT, List.of())));
        assertEquals(1, facade.logoutTokens.size());
    }

    @ParameterizedTest
    @EnumSource(value = ClientException.Kind.class, names = {"CONNECTION_ERROR", "SERVER_ERROR"})
    public void failedLogoutPreservesTheSessionForARetry(ClientException.Kind kind) throws ClientException {
        client.execute(authenticationCommand(Command.Type.LOGIN));
        facade.nextFailure = new ClientException(kind, "Unable to sign out. Please try again.");

        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.LOGOUT, List.of())));

        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGOUT));
        client.execute(new Command(Command.Type.LOGOUT, List.of()));
        assertEquals(List.of("private-LOGIN-token", "private-LOGIN-token"), facade.logoutTokens);
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"REGISTER", "LOGIN"})
    public void anExistingSessionCannotBeReplacedWithoutLoggingOut(Command.Type type) throws ClientException {
        client.execute(authenticationCommand(Command.Type.REGISTER));

        assertThrows(ClientException.class, () -> client.execute(authenticationCommand(type)));

        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertEquals(1, facade.authRequests.size());
        client.execute(new Command(Command.Type.LOGOUT, List.of()));
        assertEquals(List.of("private-REGISTER-token"), facade.logoutTokens);
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"LOGOUT", "CREATE", "LIST", "PLAY", "OBSERVE", "BACK"})
    public void unavailableCommandsCannotSendRequests(Command.Type type) {
        ClientException error = assertThrows(ClientException.class, () -> client.execute(new Command(type, List.of())));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(facade.authRequests.isEmpty());
        assertTrue(facade.logoutTokens.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidAuthenticationCommands")
    public void invalidFieldsAreRejectedBeforeAuthentication(Command command) {
        ClientException error = assertThrows(ClientException.class, () -> client.execute(command));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("Usage: " + command.type().usage(), error.getMessage());
        assertTrue(facade.authRequests.isEmpty());
    }

    static Stream<Command> invalidAuthenticationCommands() {
        return Stream.of(
                new Command(Command.Type.LOGIN, List.of("Alice")),
                new Command(Command.Type.LOGIN, List.of("Alice", "private-password", "extra")),
                new Command(Command.Type.LOGIN, List.of("Alice", " ")),
                new Command(Command.Type.REGISTER, List.of("Alice", "private-password")));
    }

    @Test
    public void helpLeavesTheSessionAloneAndQuitClearsIt() throws ClientException {
        client.execute(authenticationCommand(Command.Type.LOGIN));

        assertEquals("", client.execute(new Command(Command.Type.HELP, List.of())));
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertEquals("", client.execute(new Command(Command.Type.QUIT, List.of())));
        assertEquals(ChessClient.State.QUIT, client.getState());
        assertTrue(client.availableCommands().isEmpty());
        assertEquals("", client.prompt());
        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.LOGOUT, List.of())));
        assertTrue(facade.logoutTokens.isEmpty());
    }

    @Test
    public void theConsoleSupportsRegistrationLogoutAndQuotedLogin() {
        String output = run("register\nAlice Smith\nprivate-password\nalice@example.com\nlogout\n"
                + "login \"Alice Smith\" private-password\nlogout\nquit\n");

        assertEquals(List.of(
                new Command(Command.Type.REGISTER, List.of("Alice Smith", "private-password", "alice@example.com")),
                new Command(Command.Type.LOGIN, List.of("Alice Smith", "private-password"))), facade.authRequests);
        assertEquals(List.of("private-REGISTER-token", "private-LOGIN-token"), facade.logoutTokens);
        assertTrue(output.contains("Registration successful. You are signed in."));
        assertTrue(output.contains("You are signed out."));
        assertTrue(output.contains("signed out> "));
        assertTrue(output.contains("signed in> "));
        assertFalse(output.contains("private"));
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    @Test
    public void helpChangesWithTheSessionAndUnavailableCommandsDoNotPrompt() throws ClientException {
        String signedOut = run("help\nlogout\ncreate\nplay\nquit\n");

        assertTrue(signedOut.contains("register <username> <password> <email>"));
        assertFalse(signedOut.contains("logout -"));
        assertFalse(signedOut.contains("Game name: "));
        assertFalse(signedOut.contains("Game number: "));
        assertTrue(facade.authRequests.isEmpty());

        ChessClient signedInClient = new ChessClient(facade);
        signedInClient.execute(authenticationCommand(Command.Type.LOGIN));
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader("help\nregister\nlogin\nlogout\nquit\n"), new PrintWriter(transcript), signedInClient).run();
        String signedIn = transcript.toString();

        assertTrue(signedIn.contains("logout - Sign out"));
        assertFalse(signedIn.contains("register <"));
        assertFalse(signedIn.contains("login <"));
        assertFalse(signedIn.contains("Username: "));
        assertEquals(1, facade.authRequests.size());
        assertEquals(1, facade.logoutTokens.size());
    }

    @Test
    public void consoleAuthenticationErrorsAreSafeAndAllowRetrying() {
        facade.nextFailure = new ClientException(ClientException.Kind.UNAUTHORIZED, "Incorrect username or password.",
                new IOException("private database details"));

        String output = run("login Alice private-wrong-password\nhelp\nlogin Alice private-password\nlogout\nquit\n");

        assertTrue(output.contains("Incorrect username or password."));
        assertTrue(output.contains("register <username>"));
        assertTrue(output.contains("You are signed in."));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
        assertFalse(output.contains("authToken"));
        assertEquals(2, facade.authRequests.size());
        assertEquals(1, facade.logoutTokens.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit\n", ""})
    public void quitAndEndOfInputExitFromASignedInSession(String ending) throws ClientException {
        client.execute(authenticationCommand(Command.Type.LOGIN));

        String output = run(ending);

        assertTrue(output.contains("Goodbye!"));
        assertEquals(ChessClient.State.QUIT, client.getState());
        assertTrue(client.availableCommands().isEmpty());
        assertTrue(facade.logoutTokens.isEmpty());
    }

    private Command authenticationCommand(Command.Type type) {
        List<String> arguments = type == Command.Type.REGISTER
                ? List.of("Alice", "private-password", "alice@example.com") : List.of("Alice", "private-password");
        return new Command(type, arguments);
    }

    private String run(String input) {
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader(input), new PrintWriter(transcript), client).run();
        return transcript.toString();
    }

    private static class AuthFacadeStub extends ServerFacade {
        private final List<Command> authRequests = new ArrayList<>();
        private final List<String> logoutTokens = new ArrayList<>();
        private ClientException nextFailure;

        AuthFacadeStub() {
            super(8080);
        }

        @Override
        public AuthData register(String username, String password, String email) throws ClientException {
            return authenticate(new Command(Command.Type.REGISTER, List.of(username, password, email)));
        }

        @Override
        public AuthData login(String username, String password) throws ClientException {
            return authenticate(new Command(Command.Type.LOGIN, List.of(username, password)));
        }

        private AuthData authenticate(Command command) throws ClientException {
            authRequests.add(command);
            failIfRequested();
            return new AuthData("private-" + command.type() + "-token", command.arguments().getFirst());
        }

        @Override
        public void logout(String authToken) throws ClientException {
            logoutTokens.add(authToken);
            failIfRequested();
        }

        private void failIfRequested() throws ClientException {
            if (nextFailure != null) {
                ClientException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
        }
    }
}
