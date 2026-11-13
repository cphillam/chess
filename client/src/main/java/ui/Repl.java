package ui;

import client.ClientException;
import client.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class Repl {
    private final BufferedReader input;
    private final PrintWriter output;
    private final CommandHandler handler;
    private final CommandParser parser = new CommandParser();

    public Repl(Reader input, PrintWriter output, CommandHandler handler) {
        this.input = new BufferedReader(input);
        this.output = output;
        this.handler = handler;
    }

    public void run() {
        output.println("Welcome to 240 Chess! Type help to see available commands.");
        while (true) {
            String line = readLine(handler.prompt());
            if (line == null) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            Command command;
            try {
                command = parser.parse(line);
            } catch (IllegalArgumentException exception) {
                output.println(exception.getMessage());
                continue;
            }
            if (!handler.availableCommands().contains(command.type())) {
                output.println("That command is unavailable here. Type help to see available commands.");
                continue;
            }
            if (command.type() == Command.Type.QUIT) {
                break;
            }
            if (command.type() == Command.Type.HELP) {
                printHelp();
            } else if (!execute(command)) {
                break;
            }
        }
        handler.exit();
        output.println("Goodbye!");
        output.flush();
    }

    private boolean execute(Command command) {
        List<String> arguments = new ArrayList<>(command.arguments());
        if (arguments.isEmpty()) {
            for (String field : command.type().fields()) {
                String value = readField(field);
                if (value == null) {
                    return false;
                }
                arguments.add(value);
            }
        }
        try {
            String response = handler.execute(new Command(command.type(), arguments));
            if (!response.isBlank()) {
                output.println(response);
            }
        } catch (ClientException exception) {
            output.println(exception.getMessage());
        }
        return true;
    }

    private String readField(String field) {
        while (true) {
            String value = readLine(field + ": ");
            if (value == null || !value.isBlank()) {
                return value;
            }
            output.println("Please enter a value.");
        }
    }

    private String readLine(String prompt) {
        output.print(prompt);
        output.flush();
        try {
            String line = input.readLine();
            if (line == null) {
                output.println();
            }
            return line;
        } catch (IOException exception) {
            output.println("Unable to read input. Please restart the client.");
            return null;
        }
    }

    private void printHelp() {
        List<Command.Type> commands = handler.availableCommands();
        for (Command.Type type : commands) {
            output.println("  " + type.usage() + " - " + type.description());
        }
        if (commands.stream().anyMatch(type -> !type.fields().isEmpty())) {
            output.println("Enter a command without arguments to be prompted for its fields.");
            output.println("Use double quotes around fields containing spaces.");
        }
    }

    @FunctionalInterface
    public interface CommandHandler {
        String execute(Command command) throws ClientException;

        default List<Command.Type> availableCommands() {
            return List.of(Command.Type.values());
        }

        default String prompt() {
            return "chess> ";
        }

        default void exit() {
        }
    }
}
