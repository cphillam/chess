package ui;

import client.ClientException;
import client.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ReplTests {
    private final StringWriter transcript = new StringWriter();
    private final List<Command> commands = new ArrayList<>();

    @Test
    public void helpExplainsCommandsAndBothInputStyles() {
        String output = run("HeLp\nquit\n");

        for (Command.Type type : Command.Type.values()) {
            assertTrue(output.contains(type.usage()));
            assertTrue(output.contains(type.description()));
        }
        assertTrue(output.contains("without arguments to be prompted"));
        assertTrue(output.contains("double quotes"));
        assertTrue(commands.isEmpty());
    }

    @Test
    public void quitIsCaseInsensitiveAndStopsReadingCommands() {
        String output = run("QuIt\nlogin Alice Secret\n");

        assertTrue(output.contains("Welcome to 240 Chess!"));
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()));
        assertTrue(commands.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "login\n", "login\nAlice\n", "register\nAlice\nSecret\n", "play\n1\n"})
    public void endOfInputExitsWithoutSendingPartialCommands(String input) {
        String output = run(input);

        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()));
        assertTrue(commands.isEmpty());
    }

    @Test
    public void aCompleteCommandAtEndOfInputIsStillExecuted() {
        String output = run("LoGiN Alice Secret");

        assertEquals(List.of(new Command(Command.Type.LOGIN, List.of("Alice", "Secret"))), commands);
        assertTrue(output.contains("Done."));
        assertTrue(output.contains("Goodbye!"));
    }

    @Test
    public void registrationPromptsForEachFieldWithoutEchoingCredentials() {
        String output = run("register\nAlice Smith\nmy private password\nalice@example.com\nquit\n");

        assertEquals(List.of(new Command(Command.Type.REGISTER,
                List.of("Alice Smith", "my private password", "alice@example.com"))), commands);
        assertTrue(output.indexOf("Username: ") < output.indexOf("Password: "));
        assertTrue(output.indexOf("Password: ") < output.indexOf("Email: "));
        assertFalse(output.contains("my private password"));
    }

    @Test
    public void blankPromptedValuesAreRequestedAgain() {
        String output = run("login\n  \nAlice\n\nSecret\nquit\n");

        assertEquals(List.of(new Command(Command.Type.LOGIN, List.of("Alice", "Secret"))), commands);
        assertTrue(output.contains("Username: Please enter a value."));
        assertTrue(output.contains("Password: Please enter a value."));
    }

    @Test
    public void promptedValuesKeepTheirSpacesAndQuotesLiterally() {
        run("login\nÉchecs O'Brien\n  a \"quoted\" password  \nquit\n");

        assertEquals(List.of(new Command(Command.Type.LOGIN,
                List.of("Échecs O'Brien", "  a \"quoted\" password  "))), commands);
    }

    @Test
    public void promptedAndInlineCommandsCanShareOneSession() {
        String output = run("login\nAlice\nSecret\ncreate Friday Night Chess\nplay\n2\nblack\nobserve 3\nback\nquit\n");

        assertEquals(List.of(
                new Command(Command.Type.LOGIN, List.of("Alice", "Secret")),
                new Command(Command.Type.CREATE, List.of("Friday Night Chess")),
                new Command(Command.Type.PLAY, List.of("2", "black")),
                new Command(Command.Type.OBSERVE, List.of("3")),
                new Command(Command.Type.BACK, List.of())), commands);
        assertTrue(output.contains("Game number: "));
        assertTrue(output.contains("Color: "));
    }

    @Test
    public void inlineQuotedFieldsAreForwardedWithoutPrompting() {
        String output = run("login \"Alice Smith\" \"my private password\"\nquit\n");

        assertEquals(List.of(new Command(Command.Type.LOGIN, List.of("Alice Smith", "my private password"))), commands);
        assertFalse(output.contains("Username: "));
        assertFalse(output.contains("Password: "));
        assertFalse(output.contains("my private password"));
    }

    @ParameterizedTest
    @MethodSource("incompleteCommands")
    public void incompleteInlineArgumentsShowUsageAndLeaveTheNextCommandAlone(String input, Command.Type type) {
        String output = run(input + "\nhelp\nquit\n");

        assertTrue(output.contains("Usage: " + type.usage()));
        assertTrue(output.contains("Show available commands."));
        assertFalse(output.contains("Username: "));
        assertFalse(output.contains("Game number: "));
        assertTrue(commands.isEmpty());
    }

    static Stream<Arguments> incompleteCommands() {
        return Stream.of(Arguments.of("login Alice", Command.Type.LOGIN),
                Arguments.of("register Alice Secret", Command.Type.REGISTER), Arguments.of("play 1", Command.Type.PLAY));
    }

    @Test
    public void blankLinesDoNotProduceErrorsOrCommands() {
        String output = run("\n \n\t\nquit\n");

        assertFalse(output.contains("Unknown command"));
        assertFalse(output.contains("Usage:"));
        assertTrue(commands.isEmpty());
    }

    @Test
    public void invalidInputRecoversWithoutExposingEnteredValues() {
        String output = run("unknown private-value\nlogin Alice \"private password\nhelp\nquit\n");

        assertTrue(output.contains("Unknown command. Type help"));
        assertTrue(output.contains("Close any double-quoted fields"));
        assertTrue(output.contains("Show available commands."));
        assertFalse(output.contains("private"));
        assertTrue(commands.isEmpty());
    }

    @Test
    public void extraQuitArgumentsDoNotExitTheLoop() {
        String output = run("quit extra\nlist\nquit\n");

        assertTrue(output.contains("Usage: quit"));
        assertEquals(List.of(new Command(Command.Type.LIST, List.of())), commands);
    }

    @Test
    public void commandFailuresPrintOnlyTheFriendlyMessageAndKeepTheLoopRunning() {
        String output = run(new StringReader("login Alice Secret\nhelp\nquit\n"), command -> {
            throw new ClientException(ClientException.Kind.CONNECTION_ERROR, "Unable to contact the server. Please try again.",
                    new IOException("private transport details"));
        });

        assertTrue(output.contains("Unable to contact the server. Please try again."));
        assertTrue(output.contains("Show available commands."));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
    }

    @Test
    public void inputFailuresExitWithAFriendlyMessage() {
        Reader brokenInput = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("private input details");
            }

            @Override
            public void close() {
            }
        };

        String output = run(brokenInput, command -> "Done.");

        assertTrue(output.contains("Unable to read input. Please restart the client."));
        assertTrue(output.contains("Goodbye!"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
    }

    private String run(String input) {
        return run(new StringReader(input), command -> {
            commands.add(command);
            return "Done.";
        });
    }

    private String run(Reader input, Repl.CommandHandler handler) {
        new Repl(input, new PrintWriter(transcript), handler).run();
        return transcript.toString();
    }
}
