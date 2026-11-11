package ui;

import client.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommandParser {
    public Command parse(String line) {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Enter a command. Type help to see available commands.");
        }
        Command.Type type;
        try {
            type = Command.Type.valueOf(tokens.getFirst().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown command. Type help to see available commands.");
        }
        List<String> arguments = tokens.subList(1, tokens.size());
        if (type == Command.Type.CREATE && !arguments.isEmpty()) {
            arguments = List.of(String.join(" ", arguments));
        }
        if ((!arguments.isEmpty() && arguments.size() != type.fields().size())
                || arguments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Usage: " + type.usage());
        }
        return new Command(type, arguments);
    }

    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted && character == '\\' && index + 1 < line.length()
                    && (line.charAt(index + 1) == '"' || line.charAt(index + 1) == '\\')) {
                token.append(line.charAt(++index));
            } else if (character == '"') {
                quoted = !quoted;
                started = true;
            } else if (!quoted && Character.isWhitespace(character)) {
                if (started) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(character);
                started = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Close any double-quoted fields and try again.");
        }
        if (started) {
            tokens.add(token.toString());
        }
        return tokens;
    }
}
