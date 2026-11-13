package client;

import model.AuthData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientPlayIntegrationTests extends ServerFacadeTestSupport {
    @ParameterizedTest
    @CsvSource({"white, White, abcdefgh", "black, Black, hgfedcba"})
    public void theLauncherJoinsTheSelectedColorAndBackKeepsTheSeat(String color, String label, String files) throws ClientException {
        AuthData owner = facade.register("owner", "secret", "owner@example.com");
        int gameID = facade.createGame("Final", owner.authToken());

        String output = run("register player secret player@example.com\nlist\nplay 1 " + color + "\nhelp\nback\nlogout\nquit\n");

        assertTrue(output.contains("Playing game 1 as " + label + "."));
        String plain = output.replaceAll("\u001b\\[[0-9;]*m", "");
        assertEquals(2, plain.lines().filter(line -> files.equals(line.replace(" ", ""))).count());
        assertTrue(output.contains("board preview> "));
        assertTrue(output.contains("Returned to the lobby."));
        assertTrue(output.contains("You are signed out."));
        assertFalse(output.contains("gameID"));
        assertFalse(output.contains("authToken"));
        assertEquals(List.of(new GameSummary(gameID, "white".equals(color) ? "player" : null,
                "black".equals(color) ? "player" : null, "Final")), facade.listGames(owner.authToken()));
    }

    @Test
    public void anOccupiedSeatFailureCanBeFollowedByJoiningTheOtherColor() throws ClientException {
        AuthData owner = facade.register("owner", "secret", "owner@example.com");
        int gameID = facade.createGame("Final", owner.authToken());
        facade.joinGame(gameID, "WHITE", owner.authToken());

        String output = run("register player secret player@example.com\nlist\nplay 1 white\nplay 1 black\nback\nquit\n");

        assertTrue(output.contains("That seat is already taken."));
        assertFalse(output.contains("Playing game 1 as White."));
        assertTrue(output.contains("Playing game 1 as Black."));
        assertFalse(output.contains("Exception"));
        assertEquals(List.of(new GameSummary(gameID, "owner", "player", "Final")), facade.listGames(owner.authToken()));
    }

    private String run(String input) {
        StringWriter transcript = new StringWriter();
        ClientMain.run(new String[]{serverURL.toString()}, new StringReader(input), new PrintWriter(transcript));
        return transcript.toString();
    }
}
