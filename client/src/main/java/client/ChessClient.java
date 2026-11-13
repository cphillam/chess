package client;

import chess.ChessBoard;
import chess.ChessGame.TeamColor;
import model.AuthData;
import ui.BoardRenderer;
import ui.LobbyRenderer;
import ui.Repl;

import java.util.List;
import java.util.Locale;

public class ChessClient implements Repl.CommandHandler {
    public enum State {
        SIGNED_OUT,
        SIGNED_IN,
        BOARD_PREVIEW,
        QUIT
    }

    private final ServerFacade facade;
    private State state = State.SIGNED_OUT;
    private AuthData session;
    private List<GameSummary> listedGames;

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
            case SIGNED_IN -> List.of(Command.Type.HELP, Command.Type.QUIT, Command.Type.LOGOUT,
                    Command.Type.CREATE, Command.Type.LIST, Command.Type.PLAY, Command.Type.OBSERVE);
            case BOARD_PREVIEW -> List.of(Command.Type.HELP, Command.Type.BACK, Command.Type.QUIT);
            case QUIT -> List.of();
        };
    }

    @Override
    public String prompt() {
        return switch (state) {
            case SIGNED_OUT -> "signed out> ";
            case SIGNED_IN -> "signed in> ";
            case BOARD_PREVIEW -> "board preview> ";
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
        try {
            return executeCommand(command);
        } catch (ClientException exception) {
            if (exception.getKind() == ClientException.Kind.UNAUTHORIZED) {
                clearSession();
            }
            throw exception;
        }
    }

    private String executeCommand(Command command) throws ClientException {
        List<String> arguments = command.arguments();
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
            case CREATE -> createGame(arguments.getFirst());
            case LIST -> listGames();
            case PLAY -> play(arguments.get(0), arguments.get(1));
            case OBSERVE -> observe(arguments.getFirst());
            case BACK -> {
                state = State.SIGNED_IN;
                yield "Returned to the lobby.";
            }
            case QUIT -> {
                exit();
                yield "";
            }
            default -> "";
        };
    }

    private String logout() throws ClientException {
        facade.logout(session.authToken());
        clearSession();
        return "You are signed out.";
    }

    private String createGame(String gameName) throws ClientException {
        facade.createGame(gameName, session.authToken());
        return "Game created. Run list to refresh the game list.";
    }

    private String listGames() throws ClientException {
        List<GameSummary> snapshot = List.copyOf(facade.listGames(session.authToken()));
        String listing = LobbyRenderer.render(snapshot);
        listedGames = snapshot;
        return listing;
    }

    GameSummary gameForNumber(int number) throws ClientException {
        if (listedGames == null) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST, "Run list before choosing a game.");
        }
        if (number < 1 || number > listedGames.size()) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST,
                    "That game number is not in the latest list. Run list to refresh the game list.");
        }
        return listedGames.get(number - 1);
    }

    private String play(String gameNumber, String color) throws ClientException {
        int number = parseGameNumber(gameNumber);
        TeamColor teamColor;
        try {
            teamColor = TeamColor.valueOf(color.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST, "Choose white or black.");
        }
        GameSummary game = gameForNumber(number);
        facade.joinGame(game.gameID(), teamColor.name(), session.authToken());
        String colorName = teamColor == TeamColor.WHITE ? "White" : "Black";
        return showPreview("Playing game " + number + " as " + colorName + ".", teamColor);
    }

    private String observe(String gameNumber) throws ClientException {
        int number = parseGameNumber(gameNumber);
        GameSummary selected = gameForNumber(number);
        boolean available = facade.listGames(session.authToken()).stream()
                .anyMatch(game -> game.gameID() == selected.gameID());
        if (!available) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST,
                    "That game is no longer available. Run list to refresh the game list.");
        }
        return showPreview("Observing game " + number + ". White's perspective.", TeamColor.WHITE);
    }

    private int parseGameNumber(String gameNumber) throws ClientException {
        try {
            return Integer.parseInt(gameNumber.strip());
        } catch (NumberFormatException exception) {
            throw new ClientException(ClientException.Kind.BAD_REQUEST, "Enter a whole game number from the latest list.");
        }
    }

    private String showPreview(String heading, TeamColor perspective) {
        ChessBoard board = new ChessBoard();
        board.resetBoard();
        String preview = heading + System.lineSeparator() + BoardRenderer.render(board, perspective)
                + System.lineSeparator() + "Use back to return to the lobby or quit to exit.";
        state = State.BOARD_PREVIEW;
        return preview;
    }

    private void clearSession() {
        session = null;
        listedGames = null;
        state = State.SIGNED_OUT;
    }

    @Override
    public void exit() {
        clearSession();
        state = State.QUIT;
    }
}
