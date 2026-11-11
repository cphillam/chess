package client;

import model.AuthData;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ClientLobbyIntegrationTests extends ServerFacadeTestSupport {
    @Test
    public void theLauncherCreatesAndListsGamesThroughTheRealServer() throws ClientException {
        StringWriter transcript = new StringWriter();
        String input = "register lobby-player secret lobby@example.com\nlist\ncreate Friday Night Chess\n"
                + "create\nSaturday ♟ Chess\nlist\nlogout\nlist\nhelp\nquit\n";

        ClientMain.run(new String[]{serverURL.toString()}, new StringReader(input), new PrintWriter(transcript));

        String output = transcript.toString();
        assertTrue(output.contains("No games available. Use create to start one."));
        assertTrue(output.contains("Game created. Run list to refresh the game list."));
        assertTrue(output.contains("Game name: "));
        assertTrue(output.contains("1. Friday Night Chess | White: (open) | Black: (open)"));
        assertTrue(output.contains("2. Saturday ♟ Chess | White: (open) | Black: (open)"));
        assertTrue(output.contains("You are signed out."));
        assertTrue(output.contains("That command is unavailable here."));
        assertTrue(output.contains("Goodbye!"));
        assertFalse(output.contains("gameID"));
        assertFalse(output.contains("authToken"));
        assertFalse(output.contains("Exception"));

        AuthData auditor = facade.register("auditor", "secret", "auditor@example.com");
        List<GameSummary> games = facade.listGames(auditor.authToken());
        assertEquals(2, games.size());
        assertEquals(Set.of("Friday Night Chess", "Saturday ♟ Chess"), Set.copyOf(games.stream().map(GameSummary::gameName).toList()));
        assertTrue(games.stream().allMatch(game -> game.whiteUsername() == null && game.blackUsername() == null));
    }
}
