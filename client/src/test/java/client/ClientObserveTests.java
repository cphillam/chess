package client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.Repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientObserveTests {
    private final ClientFacadeStub facade = new ClientFacadeStub();
    private final ChessClient client = new ChessClient(facade);

    @BeforeEach
    public void prepareDisplayedGames() throws ClientException {
        facade.games = List.of(new GameSummary(7314, null, null, "Open game"),
                new GameSummary(96, "Alice", "Bob", "Full game"));
        client.execute(new Command(Command.Type.LOGIN, List.of("Observer", "secret")));
        client.execute(new Command(Command.Type.LIST, List.of()));
    }

    @AfterEach
    public void observingNeverClaimsASeat() {
        assertTrue(facade.joinedGames.isEmpty(), "Observing must never send a join request");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", " 2 "})
    public void openAndFullGamesCanBeObservedFromWhite(String number) throws ClientException {
        String preview = observe(number);

        assertTrue(preview.startsWith("Observing game " + number.strip() + ". White's perspective."));
        assertEquals(2, preview.replaceAll("\u001b\\[[0-9;]*m", "").lines()
                .filter(line -> "abcdefgh".equals(line.replace(" ", ""))).count());
        assertEquals(List.of("private-LOGIN-token", "private-LOGIN-token"), facade.listTokens);
        assertEquals(ChessClient.State.BOARD_PREVIEW, client.getState());
        assertEquals(List.of(Command.Type.HELP, Command.Type.BACK, Command.Type.QUIT), client.availableCommands());
        assertFalse(preview.contains("7314"));
        assertFalse(preview.contains("96"));
        assertFalse(preview.contains("private"));
    }

    @Test
    public void silentValidationKeepsTheLastDisplayedNumbersDespiteServerReordering() throws ClientException {
        GameSummary first = facade.games.getFirst();
        GameSummary second = facade.games.getLast();
        facade.games = List.of(new GameSummary(8000, null, null, "New game"), second, first);

        assertTrue(observe("1").contains("Observing game 1."));
        client.execute(new Command(Command.Type.BACK, List.of()));

        assertEquals(first, client.gameForNumber(1));
        assertEquals(second, client.gameForNumber(2));
        assertThrows(ClientException.class, () -> client.gameForNumber(3));
        assertTrue(client.availableCommands().contains(Command.Type.OBSERVE));
        assertTrue(facade.logoutTokens.isEmpty());
        assertEquals(2, facade.listTokens.size());
    }

    @Test
    public void aRemovedGameCannotBeReplacedByAnotherGameAtTheSamePosition() throws ClientException {
        facade.games = List.of(facade.games.getLast());

        ClientException error = assertThrows(ClientException.class, () -> observe("1"));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("That game is no longer available. Run list to refresh the game list.", error.getMessage());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertEquals(7314, client.gameForNumber(1).gameID());
        client.execute(new Command(Command.Type.LIST, List.of()));
        assertTrue(observe("1").contains("Observing game 1."));
        assertEquals(96, client.gameForNumber(1).gameID());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zero", "1.5", "2147483648", "-1", "0", "3", "7314"})
    public void invalidSelectionsAreRejectedBeforeContactingTheServer(String number) {
        ClientException error = assertThrows(ClientException.class, () -> observe(number));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals(1, facade.listTokens.size());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @Test
    public void aNewSessionMustDisplayAListBeforeObserving() throws ClientException {
        client.execute(new Command(Command.Type.LOGOUT, List.of()));
        client.execute(new Command(Command.Type.LOGIN, List.of("Other observer", "secret")));

        ClientException error = assertThrows(ClientException.class, () -> observe("1"));

        assertEquals("Run list before choosing a game.", error.getMessage());
        assertEquals(1, facade.listTokens.size());
    }

    @Test
    public void anEmptyDisplayedListHasNoObservableNumbers() throws ClientException {
        facade.games = List.of();
        client.execute(new Command(Command.Type.LIST, List.of()));

        assertThrows(ClientException.class, () -> observe("1"));
        assertEquals(2, facade.listTokens.size());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @ParameterizedTest
    @EnumSource(value = ClientException.Kind.class, names = {"CONNECTION_ERROR", "SERVER_ERROR", "INVALID_RESPONSE"})
    public void aFailedValidationCanBeRetriedUsingTheSameSelection(ClientException.Kind kind) throws ClientException {
        ClientException failure = new ClientException(kind, "Unable to check the game. Please try again.");
        facade.nextFailure = failure;

        assertSame(failure, assertThrows(ClientException.class, () -> observe("2")));
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertEquals(96, client.gameForNumber(2).gameID());
        assertTrue(observe("2").contains("Observing game 2."));
        assertEquals(3, facade.listTokens.size());
    }

    @Test
    public void expiredAuthenticationDuringValidationRequiresANewLoginAndList() {
        facade.nextFailure = new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");

        ClientException error = assertThrows(ClientException.class, () -> observe("1"));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertThrows(ClientException.class, () -> client.gameForNumber(1));
        assertFalse(client.availableCommands().contains(Command.Type.OBSERVE));
    }

    @Test
    public void promptedAndInlineObservationUsePreviewHelpAndReturnToTheLobby() {
        String output = run("help\nobserve\n2\nhelp\nback\nObSeRvE 1\nback\nquit\n");

        assertTrue(output.contains("observe <game number>"));
        assertTrue(output.contains("Game number: "));
        assertTrue(output.contains("Observing game 2. White's perspective."));
        assertTrue(output.contains("Observing game 1. White's perspective."));
        String help = output.substring(output.indexOf("board preview> "), output.indexOf("Returned to the lobby."));
        assertTrue(help.contains("help -"));
        assertTrue(help.contains("back -"));
        assertTrue(help.contains("quit -"));
        assertFalse(help.contains("observe <"));
        assertFalse(help.contains("play <"));
        assertEquals(3, facade.listTokens.size());
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    @Test
    public void invalidObservationInputRecoversBeforeRequestingAPreview() {
        String output = run("observe 1 white\nobserve wrong\nobserve 0\nobserve 2\nback\nquit\n");

        assertTrue(output.contains("Usage: observe <game number>"));
        assertTrue(output.contains("Enter a whole game number from the latest list."));
        assertTrue(output.contains("That game number is not in the latest list."));
        assertTrue(output.contains("Observing game 2."));
        assertEquals(2, facade.listTokens.size());
    }

    @Test
    public void aValidationErrorPrintsOnlyItsSafeMessageAndShowsNoBoard() {
        facade.nextFailure = new ClientException(ClientException.Kind.CONNECTION_ERROR, "Unable to connect to the server.",
                new IOException("private connection details"));

        String output = run("observe 1\nhelp\nquit\n");

        assertTrue(output.contains("Unable to connect to the server."));
        assertTrue(output.contains("observe <game number>"));
        assertFalse(output.contains("Observing game"));
        assertFalse(output.contains("board preview> "));
        assertFalse(output.contains("♔"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit\n", ""})
    public void observerPreviewExitsOnQuitOrEndOfInput(String ending) throws ClientException {
        observe("1");

        assertTrue(run(ending).contains("Goodbye!"));
        assertEquals(ChessClient.State.QUIT, client.getState());
        assertThrows(ClientException.class, () -> client.gameForNumber(1));
        assertTrue(facade.logoutTokens.isEmpty());
    }

    @Test
    public void endOfInputAtTheObservationPromptDoesNotFetchGames() {
        String output = run("observe\n");

        assertTrue(output.contains("Game number: "));
        assertTrue(output.contains("Goodbye!"));
        assertEquals(1, facade.listTokens.size());
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    private String observe(String number) throws ClientException {
        return client.execute(new Command(Command.Type.OBSERVE, List.of(number)));
    }

    private String run(String input) {
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader(input), new PrintWriter(transcript), client).run();
        return transcript.toString();
    }
}
