package client;

import java.util.List;

public record Command(Type type, List<String> arguments) {
    public Command {
        arguments = List.copyOf(arguments);
    }

    public enum Type {
        HELP("help", "Show available commands."),
        QUIT("quit", "Exit the client."),
        REGISTER("register <username> <password> <email>", "Create an account.", "Username", "Password", "Email"),
        LOGIN("login <username> <password>", "Sign in to your account.", "Username", "Password"),
        LOGOUT("logout", "Sign out of your account."),
        CREATE("create <game name>", "Create a game without joining it.", "Game name"),
        LIST("list", "Show games and their players."),
        PLAY("play <game number> <white|black>", "Join a listed game as a player.", "Game number", "Color"),
        OBSERVE("observe <game number>", "Preview a listed game as an observer.", "Game number"),
        BACK("back", "Return from the board preview to the lobby.");

        private final String usage;
        private final String description;
        private final List<String> fields;

        Type(String usage, String description, String... fields) {
            this.usage = usage;
            this.description = description;
            this.fields = List.of(fields);
        }

        public String usage() {
            return usage;
        }

        public String description() {
            return description;
        }

        public List<String> fields() {
            return fields;
        }
    }
}
