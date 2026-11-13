package client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.Repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientPlayTests {
    private final ClientFacadeStub facade = new ClientFacadeStub();
    private final ChessClient client = new ChessClient(facade);

    @BeforeEach
    public void signInAndListGames() throws ClientException {
        facade.games = List.of(new GameSummary(4021, null, null, "Friday Chess"), new GameSummary(88, null, null, "Saturday Chess"));
        client.execute(new Command(Command.Type.LOGIN, List.of("Alice", "private-password")));
        client.execute(new Command(Command.Type.LIST, List.of()));
    }

    @ParameterizedTest
    @CsvSource({"white, WHITE, White, abcdefgh", "bLaCk, BLACK, Black, hgfedcba",
            "' white ', WHITE, White, abcdefgh", "' BLACK ', BLACK, Black, hgfedcba"})
    public void playMapsTheDisplayedNumberAndShowsTheChosenPerspective(String color, String canonical, String label, String files)
            throws ClientException {
        String output = client.execute(new Command(Command.Type.PLAY, List.of("2", color)));

        assertEquals(List.of(new ClientFacadeStub.GameJoin(88, canonical, "private-LOGIN-token")), facade.joinedGames);
        assertEquals(1, facade.listTokens.size());
        assertEquals(ChessClient.State.BOARD_PREVIEW, client.getState());
        assertEquals(List.of(Command.Type.HELP, Command.Type.BACK, Command.Type.QUIT), client.availableCommands());
        assertEquals("board preview> ", client.prompt());
        assertTrue(output.contains("Playing game 2 as " + label + "."));
        assertEquals(2, plain(output).lines().filter(line -> files.equals(line.replace(" ", ""))).count());
        assertFalse(output.contains("private"));
        assertFalse(output.contains("4021"));
        assertFalse(output.contains("88"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "1.5", "2147483648", "-1", "0", "3"})
    public void invalidGameNumbersCannotSendJoinRequests(String number) {
        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.PLAY, List.of(number, "white"))));

        assertEquals(ClientException.Kind.BAD_REQUEST, error.getKind());
        assertTrue(facade.joinedGames.isEmpty());
        assertEquals(1, facade.listTokens.size());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @ParameterizedTest
    @ValueSource(strings = {"red", "observe", "whte", "WHITE BLACK"})
    public void invalidColorsCannotSendJoinRequests(String color) {
        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.PLAY, List.of("1", color))));

        assertEquals("Choose white or black.", error.getMessage());
        assertTrue(facade.joinedGames.isEmpty());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @Test
    public void aNewSessionRequiresListingBeforePlay() throws ClientException {
        client.execute(new Command(Command.Type.LOGOUT, List.of()));
        client.execute(new Command(Command.Type.LOGIN, List.of("Bob", "private-password")));

        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.PLAY, List.of("1", "white"))));

        assertEquals("Run list before choosing a game.", error.getMessage());
        assertTrue(facade.joinedGames.isEmpty());
    }

    @Test
    public void anEmptyListingCannotBePlayed() throws ClientException {
        facade.games = List.of();
        client.execute(new Command(Command.Type.LIST, List.of()));

        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.PLAY, List.of("1", "white"))));

        assertTrue(facade.joinedGames.isEmpty());
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
    }

    @ParameterizedTest
    @EnumSource(value = ClientException.Kind.class, names = {"ALREADY_TAKEN", "BAD_REQUEST", "CONNECTION_ERROR", "SERVER_ERROR"})
    public void failedJoinsLeaveTheLobbyAndSelectionAvailableForRetrying(ClientException.Kind kind) throws ClientException {
        ClientException failure = new ClientException(kind, "Unable to join the game. Please try again.");
        facade.nextFailure = failure;

        assertSame(failure, assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.PLAY, List.of("1", "white")))));

        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertEquals(4021, client.gameForNumber(1).gameID());
        assertTrue(client.availableCommands().contains(Command.Type.PLAY));
        client.execute(new Command(Command.Type.PLAY, List.of("1", "black")));
        assertEquals(ChessClient.State.BOARD_PREVIEW, client.getState());
        assertEquals(List.of(new ClientFacadeStub.GameJoin(4021, "WHITE", "private-LOGIN-token"),
                new ClientFacadeStub.GameJoin(4021, "BLACK", "private-LOGIN-token")), facade.joinedGames);
    }

    @Test
    public void anExpiredJoinSessionClearsAuthenticationAndSelections() {
        facade.nextFailure = new ClientException(ClientException.Kind.UNAUTHORIZED, "Please log in and try again.");

        ClientException error = assertThrows(ClientException.class,
                () -> client.execute(new Command(Command.Type.PLAY, List.of("1", "white"))));

        assertEquals(ClientException.Kind.UNAUTHORIZED, error.getKind());
        assertEquals(ChessClient.State.SIGNED_OUT, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LOGIN));
        assertThrows(ClientException.class, () -> client.gameForNumber(1));
    }

    @Test
    public void backReturnsToTheLobbyWithoutMakingAnotherServerRequest() throws ClientException {
        client.execute(new Command(Command.Type.PLAY, List.of("1", "white")));

        String response = client.execute(new Command(Command.Type.BACK, List.of()));

        assertEquals("Returned to the lobby.", response);
        assertEquals(ChessClient.State.SIGNED_IN, client.getState());
        assertTrue(client.availableCommands().contains(Command.Type.LIST));
        assertTrue(client.availableCommands().contains(Command.Type.PLAY));
        assertEquals(4021, client.gameForNumber(1).gameID());
        assertEquals(1, facade.joinedGames.size());
        assertEquals(1, facade.listTokens.size());
        assertTrue(facade.logoutTokens.isEmpty());
        assertThrows(ClientException.class, () -> client.execute(new Command(Command.Type.BACK, List.of())));
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"REGISTER", "LOGIN", "LOGOUT", "CREATE", "LIST", "PLAY", "OBSERVE"})
    public void previewModeRejectsLobbyCommandsBeforeTheyCanSendRequests(Command.Type type) throws ClientException {
        client.execute(new Command(Command.Type.PLAY, List.of("1", "white")));

        assertThrows(ClientException.class, () -> client.execute(new Command(type, List.of())));

        assertEquals(ChessClient.State.BOARD_PREVIEW, client.getState());
        assertEquals(1, facade.authRequests.size());
        assertEquals(1, facade.joinedGames.size());
        assertEquals(1, facade.listTokens.size());
        assertTrue(facade.createdGames.isEmpty());
        assertTrue(facade.logoutTokens.isEmpty());
    }

    @Test
    public void promptedAndInlinePlayUsePreviewHelpAndReturnToTheLobby() {
        String output = run("play\n1\nbLaCk\nhelp\ncreate\nback\nhelp\nplay 2 white\nhelp\nback\nquit\n");

        assertEquals(List.of(new ClientFacadeStub.GameJoin(4021, "BLACK", "private-LOGIN-token"),
                new ClientFacadeStub.GameJoin(88, "WHITE", "private-LOGIN-token")), facade.joinedGames);
        assertTrue(output.contains("Game number: "));
        assertTrue(output.contains("Color: "));
        assertTrue(output.contains("Playing game 1 as Black."));
        assertTrue(output.contains("Playing game 2 as White."));
        String previewHelp = output.substring(output.indexOf("board preview> "), output.indexOf("Returned to the lobby."));
        assertTrue(previewHelp.contains("back - Return from the board preview to the lobby."));
        assertTrue(previewHelp.contains("quit - Exit the client."));
        assertFalse(previewHelp.contains("create <"));
        assertFalse(previewHelp.contains("Game name: "));
        assertFalse(previewHelp.contains("double quotes"));
        assertTrue(output.contains("play <game number> <white|black>"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("4021"));
        assertFalse(output.contains("88"));
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    @Test
    public void invalidPlayInputRecoversWithoutSendingPartialRequests() {
        String output = run("play 1\nplay hello white\nplay 1 red\nplay 2 black\nback\nquit\n");

        assertTrue(output.contains("Usage: play <game number> <white|black>"));
        assertTrue(output.contains("Enter a whole game number from the latest list."));
        assertTrue(output.contains("Choose white or black."));
        assertEquals(List.of(new ClientFacadeStub.GameJoin(88, "BLACK", "private-LOGIN-token")), facade.joinedGames);
    }

    @Test
    public void aRejectedJoinPrintsAFriendlyErrorWithoutShowingABoard() {
        facade.nextFailure = new ClientException(ClientException.Kind.ALREADY_TAKEN, "That seat is already taken.",
                new IOException("private database details"));

        String output = run("play 1 white\nhelp\nquit\n");

        assertTrue(output.contains("That seat is already taken."));
        assertTrue(output.contains("play <game number> <white|black>"));
        assertFalse(output.contains("Playing game"));
        assertFalse(output.contains("board preview> "));
        assertFalse(output.contains("♔"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit\n", ""})
    public void quitAndEndOfInputExitFromPreviewMode(String ending) throws ClientException {
        client.execute(new Command(Command.Type.PLAY, List.of("1", "white")));

        String output = run(ending);

        assertTrue(output.contains("Goodbye!"));
        assertEquals(ChessClient.State.QUIT, client.getState());
        assertTrue(client.availableCommands().isEmpty());
        assertThrows(ClientException.class, () -> client.gameForNumber(1));
        assertTrue(facade.logoutTokens.isEmpty());
        assertEquals(1, facade.joinedGames.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"play\n", "play\n1\n"})
    public void endOfInputWhilePromptingDoesNotJoinAGame(String input) {
        String output = run(input);

        assertTrue(output.contains("Goodbye!"));
        assertTrue(facade.joinedGames.isEmpty());
        assertEquals(ChessClient.State.QUIT, client.getState());
    }

    private String run(String input) {
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader(input), new PrintWriter(transcript), client).run();
        return transcript.toString();
    }

    private String plain(String output) {
        return output.replaceAll("\u001b\\[[0-9;]*m", "");
    }
}
