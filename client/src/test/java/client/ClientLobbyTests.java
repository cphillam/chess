package client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.Repl;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientLobbyTests {
    private final ClientFacadeStub facade = new ClientFacadeStub();
    private final ChessClient client = new ChessClient(facade);
    private final GameSummary friday = new GameSummary(4021, null, null, "Friday Night Chess");
    private final GameSummary saturday = new GameSummary(88, "Alice", "Bob", "Saturday Chess");

    @BeforeEach
    public void signIn() throws ClientException {
        client.execute(new Command(Command.Type.LOGIN, List.of("Alice", "private-password")));
    }

    @Test
    public void creationUsesTheCurrentSessionAndRequestsAListRefresh() throws ClientException {
        String response = client.execute(new Command(Command.Type.CREATE, List.of("Friday Night Chess")));

        assertEquals(List.of(new ClientFacadeStub.GameCreation("Friday Night Chess", "private-LOGIN-token")), facade.createdGames);
        assertEquals("Game created. Run list to refresh the game list.", response);
        assertTrue(facade.listTokens.isEmpty());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertFalse(response.contains(Integer.toString(facade.createdGameID)));
        assertFalse(response.contains("private"));
        assertListingRequired();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    public void blankGameNamesCannotReachTheFacade(String name) {
        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.CREATE, List.of(name))));

        assertEquals("Usage: create <game name>", error.getMessage());
        assertTrue(facade.createdGames.isEmpty());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @Test
    public void numbersStartAtOneAndMapToTheDisplayedServerIDs() throws ClientException {
        facade.games = List.of(friday, saturday);

        String listing = client.execute(new Command(Command.Type.LIST, List.of()));

        assertEquals(List.of("private-LOGIN-token"), facade.listTokens);
        assertEquals(List.of("Games:",
                "1. Friday Night Chess | White: (open) | Black: (open)",
                "2. Saturday Chess | White: Alice | Black: Bob"), listing.lines().toList());
        assertEquals(friday, client.gameForNumber(1));
        assertEquals(saturday, client.gameForNumber(2));
        assertFalse(listing.contains("4021"));
        assertFalse(listing.contains("88"));
        assertFalse(listing.contains("private"));
    }

    @Test
    public void listingsShowEitherOccupiedSeatAndPreserveUnicodeNames() throws ClientException {
        facade.games = List.of(new GameSummary(3001, "Échecs♟", null, "Sunday ♟ Chess"),
                new GameSummary(5019, null, "O'Brien", "Final"));

        String listing = client.execute(new Command(Command.Type.LIST, List.of()));

        assertTrue(listing.contains("1. Sunday ♟ Chess | White: Échecs♟ | Black: (open)"));
        assertTrue(listing.contains("2. Final | White: (open) | Black: O'Brien"));
    }

    @Test
    public void mappingUsesASnapshotOfTheLastDisplayedList() throws ClientException {
        List<GameSummary> serverGames = new ArrayList<>(List.of(friday, saturday));
        facade.games = serverGames;
        client.execute(new Command(Command.Type.LIST, List.of()));

        serverGames.clear();
        serverGames.add(new GameSummary(7002, null, null, "Replacement"));

        assertEquals(friday, client.gameForNumber(1));
        assertEquals(saturday, client.gameForNumber(2));
        assertEquals(1, facade.listTokens.size());
    }

    @Test
    public void successfulRefreshReplacesTheNumberMapping() throws ClientException {
        facade.games = List.of(friday, saturday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        facade.games = List.of(saturday);

        String listing = client.execute(new Command(Command.Type.LIST, List.of()));

        assertEquals(saturday, client.gameForNumber(1));
        assertThrows(ClientException.class, () -> client.gameForNumber(2));
        assertTrue(listing.contains("1. Saturday Chess"));
        assertFalse(listing.contains("Friday Night Chess"));
    }

    @Test
    public void anEmptyListReplacesAnyPreviousSelections() throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        facade.games = List.of();

        String listing = client.execute(new Command(Command.Type.LIST, List.of()));

        assertEquals("No games available. Use create to start one.", listing);
        assertThrows(ClientException.class, () -> client.gameForNumber(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 3, Integer.MAX_VALUE})
    public void invalidNumbersDoNotResolveToServerIDs(int number) throws ClientException {
        facade.games = List.of(friday, saturday);
        client.execute(new Command(Command.Type.LIST, List.of()));

        ClientException error = assertThrows(ClientException.class, () -> client.gameForNumber(number));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertTrue(error.getMessage().contains("not in the latest list"));
        assertEquals(1, facade.listTokens.size());
    }

    @Test
    public void creationKeepsTheDisplayedMappingUntilTheNextList() throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));

        client.execute(new Command(Command.Type.CREATE, List.of("Saturday Chess")));

        assertEquals(friday, client.gameForNumber(1));
        assertThrows(ClientException.class, () -> client.gameForNumber(2));
        assertEquals(1, facade.listTokens.size());

        facade.games = List.of(friday, saturday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        assertEquals(saturday, client.gameForNumber(2));
    }

    @ParameterizedTest
    @EnumSource(value = ClientException.Kind.class, names = {"CONNECTION_ERROR", "SERVER_ERROR", "INVALID_RESPONSE"})
    public void aFailedRefreshKeepsTheDisplayedMappingAndAllowsRetrying(ClientException.Kind kind) throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        facade.games = List.of(saturday);
        facade.nextFailure = new ClientException(kind, "Unable to list games. Please try again.");

        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.LIST, List.of())));

        assertEquals(friday, client.gameForNumber(1));
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        client.execute(new Command(Command.Type.LIST, List.of()));
        assertEquals(saturday, client.gameForNumber(1));
    }

    @Test
    public void failedCreationDoesNotReportSuccessOrChangeTheMapping() throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        facade.nextFailure = new ClientException(ClientException.Kind.SERVER_ERROR, "Unable to create the game. Please try again.");

        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.CREATE, List.of("Saturday Chess"))));

        assertEquals("Unable to create the game. Please try again.", error.getMessage());
        assertEquals(friday, client.gameForNumber(1));
        assertEquals(1, facade.listTokens.size());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"CREATE", "LIST", "LOGOUT"})
    public void expiredSessionsClearAuthenticationAndSelections(Command.Type type) throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        facade.nextFailure = new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");
        List<String> arguments = type == Command.Type.CREATE ? List.of("Saturday Chess") : List.of();

        assertThrows(ClientException.class, () -> client.execute(new Command(type, arguments)));

        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGIN));
        assertFalse(client.availableCommands().contains(Command.Type.LIST));
        assertListingRequired();
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"LOGOUT", "QUIT"})
    public void leavingASessionClearsItsGameSelections(Command.Type type) throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));

        client.execute(new Command(type, List.of()));

        assertListingRequired();
    }

    @Test
    public void loggingInAgainRequiresANewListing() throws ClientException {
        facade.games = List.of(friday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        client.execute(new Command(Command.Type.LOGOUT, List.of()));

        client.execute(new Command(Command.Type.LOGIN, List.of("Bob", "private-password")));

        assertListingRequired();
        facade.games = List.of(saturday);
        client.execute(new Command(Command.Type.LIST, List.of()));
        assertEquals(saturday, client.gameForNumber(1));
    }

    @Test
    public void controlCharactersCannotCreateExtraRowsOrChangeTerminalFormatting() throws ClientException {
        GameSummary game = new GameSummary(7201, "Alice\r\nBob", null, "Friday\nChess\u001b[31m\u2028Final");
        facade.games = List.of(game);

        String listing = client.execute(new Command(Command.Type.LIST, List.of()));

        assertEquals(2, listing.lines().count());
        assertTrue(listing.contains("Friday Chess [31m Final"));
        assertTrue(listing.contains("White: Alice  Bob"));
        assertFalse(listing.contains("\u001b"));
        assertEquals(game, client.gameForNumber(1));
    }

    @Test
    public void theConsoleSupportsBothCreationStylesAndTheLobbyHelp() {
        facade.games = List.of(friday, saturday);

        String output = run("help\ncreate Friday Night Chess\ncreate \"Saturday  ♟ Chess\"\n"
                + "create\nSunday Chess\nlist\nlogout\nhelp\nquit\n");

        assertEquals(List.of(
                new ClientFacadeStub.GameCreation("Friday Night Chess", "private-LOGIN-token"),
                new ClientFacadeStub.GameCreation("Saturday  ♟ Chess", "private-LOGIN-token"),
                new ClientFacadeStub.GameCreation("Sunday Chess", "private-LOGIN-token")), facade.createdGames);
        assertTrue(output.contains("create <game name> - Create a game without joining it."));
        assertTrue(output.contains("list - Show games and their players."));
        assertTrue(output.contains("Game name: "));
        assertTrue(output.contains("1. Friday Night Chess"));
        assertFalse(output.contains("4021"));
        assertFalse(output.contains("private"));
        assertFalse(output.substring(output.indexOf("You are signed out.")).contains("create <game name>"));
    }

    @Test
    public void endOfInputDuringGameCreationDoesNotSendAPartialRequest() {
        String output = run("create\n");

        assertTrue(output.contains("Game name: "));
        assertTrue(output.contains("Goodbye!"));
        assertTrue(facade.createdGames.isEmpty());
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    private void assertListingRequired() {
        ClientException error = assertThrows(ClientException.class, () -> client.gameForNumber(1));
        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertEquals("Run list before choosing a game.", error.getMessage());
    }

    private String run(String input) {
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader(input), new PrintWriter(transcript), client).run();
        return transcript.toString();
    }
}
