package client;

import model.AuthData;
import ui.Repl;

import java.util.List;

public class ChessClient implements Repl.CommandHandler {
    public enum State {
        SIGNED_OUT,
        SIGNED_IN,
        QUIT
    }

    private final ServerFacade facade;
    private State state = State.SIGNED_OUT;
    private AuthData session;

    public ChessClient(ServerFacade facade) {
        this.facade = facade;
    }

    public State getState() {
        return state;
    }

    @Override
    public List<Command.Type> availableCommands() {
        return switch (state) {
            case SIGNED_OUT -> List.of(Command.Type.HELP, Command.Type.QUIT, Command.Type.REGISTER, Command.Type.LOGIN);
            case SIGNED_IN -> List.of(Command.Type.HELP, Command.Type.QUIT, Command.Type.LOGOUT);
            case QUIT -> List.of();
        };
    }

    @Override
    public String prompt() {
        return switch (state) {
            case SIGNED_OUT -> "signed out> ";
            case SIGNED_IN -> "signed in> ";
            case QUIT -> "";
        };
    }

    @Override
    public String execute(Command command) throws ClientException {
        if (!availableCommands().contains(command.type())) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST,
                    "That command is unavailable here. Type help to see available commands.");
        }
        List<String> arguments = command.arguments();
        if (arguments.size() != command.type().fields().size() || arguments.stream().anyMatch(String::isBlank)) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST, "Usage: " + command.type().usage());
        }
        return switch (command.type()) {
            case REGISTER -> {
                session = facade.register(arguments.get(0), arguments.get(1), arguments.get(2));
                state = State.SIGNED_IN;
                yield "Registration successful. You are signed in.";
            }
            case LOGIN -> {
                session = facade.login(arguments.get(0), arguments.get(1));
                state = State.SIGNED_IN;
                yield "You are signed in.";
            }
            case LOGOUT -> logout();
            case QUIT -> {
                exit();
                yield "";
            }
            default -> "";
        };
    }

    private String logout() throws ClientException {
        try {
            facade.logout(session.authToken());
        } catch (ClientException exception) {
            if (exception.getKind() == ClientException.Kind.UNAUTHORIZED) {
                clearSession();
            }
            throw exception;
        }
        clearSession();
        return "You are signed out.";
    }

    private void clearSession() {
        session = null;
        state = State.SIGNED_OUT;
    }

    @Override
    public void exit() {
        clearSession();
        state = State.QUIT;
    }
}
