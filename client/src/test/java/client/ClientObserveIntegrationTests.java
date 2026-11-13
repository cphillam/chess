package client;

import model.AuthData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientObserveIntegrationTests extends ServerFacadeTestSupport {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    public void observingPreservesEmptyAndOccupiedSeatsThroughBackAndLogout(int occupiedSeats) throws ClientException {
        AuthData host = facade.register("host", "secret", "host@example.com");
        int gameID = facade.createGame("Observers welcome", host.authToken());
        if ((occupiedSeats & 1) != 0) {
            facade.joinGame(gameID, "WHITE", host.authToken());
        }
        if ((occupiedSeats & 2) != 0) {
            AuthData guest = facade.register("guest", "secret", "guest@example.com");
            facade.joinGame(gameID, "BLACK", guest.authToken());
        }
        List<GameSummary> before = facade.listGames(host.authToken());
        StringWriter transcript = new StringWriter();

        ClientMain.run(new String[]{serverURL.toString()}, new StringReader(
                "register observer secret observer@example.com\nlist\nobserve 1\nhelp\nback\nlogout\nquit\n"), new PrintWriter(transcript));

        String output = transcript.toString();
        assertTrue(output.contains("Observing game 1. White's perspective."));
        assertEquals(2, output.replaceAll("\u001b\\[[0-9;]*m", "").lines()
                .filter(line -> "abcdefgh".equals(line.replace(" ", ""))).count());
        assertTrue(output.contains("Returned to the lobby."));
        assertTrue(output.contains("You are signed out."));
        assertTrue(output.contains("Goodbye!"));
        assertFalse(output.contains("Playing game"));
        assertFalse(output.contains("gameID"));
        assertFalse(output.contains("authToken"));
        assertEquals(before, facade.listGames(host.authToken()), "Observing and leaving the preview must preserve both seats");
    }
}
