package client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ui.Repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ClientRecoveryIntegrationTests extends ServerFacadeTestSupport {
    private static final Reply LOGIN = new Reply(200, "{\"username\":\"Alice\",\"authToken\":\"private-first-token\"}");
    private static final Reply ORIGINAL_LIST = new Reply(200, "{\"games\":[{\"gameID\":4021,\"gameName\":\"Original\"}]}");
    private static final Reply REFRESHED_LIST = new Reply(200, "{\"games\":[{\"gameID\":88,\"gameName\":\"Refreshed\"}]}");
    private static final Reply NO_CONTENT = new Reply(204, "");

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> tokens = new CopyOnWriteArrayList<>();
    private final List<String> joinBodies = new CopyOnWriteArrayList<>();

    @Test
    public void aMalformedRefreshLeavesTheDisplayedGameAvailableForPlay() throws IOException {
        String output = run("login Alice secret\nlist\nlist\nplay 1 white\nback\nlogout\nquit\n", LOGIN, ORIGINAL_LIST,
                new Reply(200, "{\"games\":[{\"gameID\":88,\"gameName\":false}]}"), NO_CONTENT, NO_CONTENT);

        assertTrue(output.contains("The server returned an invalid response. Please try again."));
        assertTrue(output.contains("Playing game 1 as White."));
        assertTrue(output.contains("You are signed out."));
        assertEquals(List.of("POST /session", "GET /game", "GET /game", "PUT /game", "DELETE /session"), requests);
        assertEquals(JsonParser.parseString("{\"gameID\":4021,\"playerColor\":\"WHITE\"}"), JsonParser.parseString(joinBodies.getFirst()));
        assertFalse(output.contains("1. false"));
    }

    @Test
    public void aStalePlaySelectionCanBeRefreshedAndRetriedWithoutLeavingTheLobby() throws IOException {
        String output = run("login Alice secret\nlist\nplay 1 white\nhelp\nlist\nplay 1 black\nback\nquit\n",
                LOGIN, ORIGINAL_LIST, new Reply(400, "{\"message\":\"private missing game\"}"), REFRESHED_LIST, NO_CONTENT);

        assertTrue(output.contains("Unable to join that game. Run list to refresh the game list and try again."));
        assertFalse(output.contains("Playing game 1 as White."));
        assertTrue(output.contains("1. Refreshed"));
        assertTrue(output.contains("Playing game 1 as Black."));
        assertEquals(2, output.replaceAll("\u001b\\[[0-9;]*m", "").lines()
                .filter(line -> "hgfedcba".equals(line.replace(" ", ""))).count());
        assertEquals(List.of("POST /session", "GET /game", "PUT /game", "GET /game", "PUT /game"), requests);
        assertEquals(List.of(4021, 88), joinBodies.stream()
                .map(body -> JsonParser.parseString(body).getAsJsonObject().get("gameID").getAsInt()).toList());
    }

    @ParameterizedTest
    @EnumSource(value = Command.Type.class, names = {"PLAY", "OBSERVE"})
    public void expiredSelectionsRequireAuthenticationAndANewListBeforePreviewing(Command.Type type) throws IOException {
        String selection = type == Command.Type.PLAY ? "play 1 white" : "observe 1";
        String output = run("login Alice secret\nlist\n" + selection
                        + "\nhelp\nplay 1 white\nlogin Alice secret\nplay 1 white\nlist\nobserve 1\nback\nquit\n",
                LOGIN, ORIGINAL_LIST, new Reply(401, "{\"message\":\"private expired session\"}"),
                new Reply(200, "{\"username\":\"Alice\",\"authToken\":\"private-second-token\"}"), REFRESHED_LIST, REFRESHED_LIST);

        assertTrue(output.contains("Please log in and try again."));
        assertTrue(output.contains("That command is unavailable here."));
        assertTrue(output.contains("Run list before choosing a game."));
        assertFalse(output.contains("Playing game"));
        assertTrue(output.contains("Observing game 1. White's perspective."));
        assertEquals(List.of("POST /session", "GET /game", (type == Command.Type.PLAY ? "PUT" : "GET") + " /game",
                "POST /session", "GET /game", "GET /game"), requests);
        assertEquals(List.of("", "private-first-token", "private-first-token", "", "private-second-token", "private-second-token"), tokens);
    }

    private String run(String input, Reply... replies) throws IOException {
        AtomicInteger index = new AtomicInteger();
        ServerFacade scripted = stubFacade(exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String token = exchange.getRequestHeaders().getFirst("authorization");
            tokens.add(token == null ? "" : token);
            if ("PUT".equals(exchange.getRequestMethod())) {
                joinBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            int current = index.getAndIncrement();
            Reply reply = current < replies.length ? replies[current] : new Reply(500, "{}");
            if (reply.status() == 204) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            } else {
                respond(exchange, reply.status(), reply.body());
            }
        });
        ChessClient client = new ChessClient(scripted);
        StringWriter transcript = new StringWriter();
        new Repl(new StringReader(input), new PrintWriter(transcript), client).run();
        String output = transcript.toString();
        assertEquals(replies.length, index.get(), "Every expected HTTP exchange should occur exactly once");
        assertEquals(ChessClient.State.QUIT, client.getState());
        for (String detail : List.of("private", "4021", "88", "authToken", "gameID", "Exception")) {
            assertFalse(output.contains(detail), "Console must not display " + detail);
        }
        assertTrue(output.contains("Goodbye!"));
        return output;
    }

    private record Reply(int status, String body) {
    }
}
