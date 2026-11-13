package client;

import model.AuthData;

import java.util.ArrayList;
import java.util.List;

class ClientFacadeStub extends ServerFacade {
    final List<Command> authRequests = new ArrayList<>();
    final List<String> logoutTokens = new ArrayList<>();
    final List<GameCreation> createdGames = new ArrayList<>();
    final List<String> listTokens = new ArrayList<>();
    final List<GameJoin> joinedGames = new ArrayList<>();
    ClientException nextFailure;
    List<GameSummary> games = List.of();
    int createdGameID = 9001;

    ClientFacadeStub() {
        super(8080);
    }

    @Override
    public AuthData register(String username, String password, String email) throws ClientException {
        return authenticate(new Command(Command.Type.REGISTER, List.of(username, password, email)));
    }

    @Override
    public AuthData login(String username, String password) throws ClientException {
        return authenticate(new Command(Command.Type.LOGIN, List.of(username, password)));
    }

    private AuthData authenticate(Command command) throws ClientException {
        authRequests.add(command);
        failIfRequested();
        return new AuthData("private-" + command.type() + "-token", command.arguments().getFirst());
    }

    @Override
    public void logout(String authToken) throws ClientException {
        logoutTokens.add(authToken);
        failIfRequested();
    }

    @Override
    public int createGame(String gameName, String authToken) throws ClientException {
        createdGames.add(new GameCreation(gameName, authToken));
        failIfRequested();
        return createdGameID;
    }

    @Override
    public List<GameSummary> listGames(String authToken) throws ClientException {
        listTokens.add(authToken);
        failIfRequested();
        return games;
    }

    @Override
    public void joinGame(int gameID, String teamColor, String authToken) throws ClientException {
        joinedGames.add(new GameJoin(gameID, teamColor, authToken));
        failIfRequested();
    }

    private void failIfRequested() throws ClientException {
        if (nextFailure != null) {
            ClientException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
    }

    record GameCreation(String gameName, String authToken) {
    }

    record GameJoin(int gameID, String teamColor, String authToken) {
    }
}
