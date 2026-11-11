package ui;

import client.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CommandParserTests {
    private final CommandParser parser = new CommandParser();

    @ParameterizedTest
    @MethodSource("validCommands")
    public void inlineCommandsKeepTheirFieldValues(String line, Command.Type type, List<String> arguments) {
        assertEquals(new Command(type, arguments), parser.parse(line));
    }

    static Stream<Arguments> validCommands() {
        return Stream.of(
                Arguments.of("HeLp", Command.Type.HELP, List.of()),
                Arguments.of(" QUIT ", Command.Type.QUIT, List.of()),
                Arguments.of("REGISTER Alice Secret Alice@Example.com", Command.Type.REGISTER,
                        List.of("Alice", "Secret", "Alice@Example.com")),
                Arguments.of("\tLoGiN\tAlice\tSecret  ", Command.Type.LOGIN, List.of("Alice", "Secret")),
                Arguments.of("logout", Command.Type.LOGOUT, List.of()),
                Arguments.of("create Friday Night Chess", Command.Type.CREATE, List.of("Friday Night Chess")),
                Arguments.of("list", Command.Type.LIST, List.of()),
                Arguments.of("play 2 BLACK", Command.Type.PLAY, List.of("2", "BLACK")),
                Arguments.of("observe 3", Command.Type.OBSERVE, List.of("3")),
                Arguments.of("back", Command.Type.BACK, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login", "create", "play", "observe"})
    public void commandsWithoutFieldsCanBePrompted(String line) {
        Command command = parser.parse(line);

        assertFalse(command.type().fields().isEmpty());
        assertTrue(command.arguments().isEmpty());
    }

    @Test
    public void quotedFieldsPreserveSpacesAndUnicode() {
        Command command = parser.parse("register \"Échecs O'Brien ♟\" \"My  Password\" alice@example.com");

        assertEquals(List.of("Échecs O'Brien ♟", "My  Password", "alice@example.com"), command.arguments());
    }

    @Test
    public void quotedGameNamesPreserveTheirSpacing() {
        Command command = parser.parse("create \"Friday  Night Chess\"");

        assertEquals(List.of("Friday  Night Chess"), command.arguments());
    }

    @Test
    public void escapedQuotesAndBackslashesWorkInsideQuotes() {
        Command command = parser.parse("login Alice \"a \\\"quoted\\\" password with \\\\ spaces\"");

        assertEquals(List.of("Alice", "a \"quoted\" password with \\ spaces"), command.arguments());
    }

    @Test
    public void ordinaryBackslashesAndApostrophesStayLiteral() {
        Command command = parser.parse("login O'Brien C:\\secret\\password");

        assertEquals(List.of("O'Brien", "C:\\secret\\password"), command.arguments());
    }

    @Test
    public void commandNamesAreIndependentOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals(Command.Type.LOGIN, parser.parse("login Alice Secret").type());
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    public void incompleteExtraOrEmptyFieldsShowUsage(String line, Command.Type type) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(line));

        assertEquals("Usage: " + type.usage(), error.getMessage());
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of("login Alice", Command.Type.LOGIN),
                Arguments.of("register Alice Secret", Command.Type.REGISTER),
                Arguments.of("play 1", Command.Type.PLAY),
                Arguments.of("login Alice Secret extra", Command.Type.LOGIN),
                Arguments.of("observe 1 extra", Command.Type.OBSERVE),
                Arguments.of("help extra", Command.Type.HELP),
                Arguments.of("quit extra", Command.Type.QUIT),
                Arguments.of("logout extra", Command.Type.LOGOUT),
                Arguments.of("list extra", Command.Type.LIST),
                Arguments.of("back extra", Command.Type.BACK),
                Arguments.of("login \"\" Secret", Command.Type.LOGIN),
                Arguments.of("register Alice \" \" alice@example.com", Command.Type.REGISTER),
                Arguments.of("create \"\"", Command.Type.CREATE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"login Alice \"private password", "create \"Friday Chess", "login Alice \"private\\\""})
    public void unterminatedQuotesHaveAFriendlyError(String line) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(line));

        assertEquals("Close any double-quoted fields and try again.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown private-value", "exit", "moves"})
    public void unknownCommandsDoNotEchoTheirInput(String line) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(line));

        assertEquals("Unknown command. Type help to see available commands.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    public void emptyCommandsHaveAFriendlyError(String line) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(line));

        assertEquals("Enter a command. Type help to see available commands.", error.getMessage());
    }
}
