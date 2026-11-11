package client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class ClientMainTests {
    private HttpServer server;

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void defaultLaunchShowsTheSignedOutMenuAndCanQuitWithoutAConnection() {
        String output = run(new String[0], "help\nquit\n");

        assertTrue(output.contains("Welcome to 240 Chess!"));
        assertTrue(output.contains("signed out> "));
        assertTrue(output.contains("register <username> <password> <email>"));
        assertTrue(output.contains("login <username> <password>"));
        assertFalse(output.contains("logout -"));
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()));
    }

    @Test
    public void aCustomServerAddressIsUsedForLoginAndLogout() throws IOException {
        List<String> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/session", exchange -> {
            String method = exchange.getRequestMethod();
            requests.add(method);
            if ("DELETE".equals(method)) {
                requests.add(exchange.getRequestHeaders().getFirst("authorization"));
            }
            byte[] response = ("POST".equals(method)
                    ? "{\"username\":\"Alice\",\"authToken\":\"private-session-token\"}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();

        String output = run(new String[]{serverURL()}, "login Alice private-password\nhelp\nlogout\nhelp\nquit\n");

        assertEquals(List.of("POST", "DELETE", "private-session-token"), requests);
        assertTrue(output.contains("You are signed in."));
        assertTrue(output.contains("signed in> "));
        assertTrue(output.contains("logout - Sign out"));
        assertTrue(output.contains("You are signed out."));
        assertTrue(output.lastIndexOf("register <") > output.indexOf("You are signed out."));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("authToken"));
    }

    @Test
    public void anInvalidServerAddressHasASafeErrorInsteadOfAStackTrace() {
        String output = run(new String[]{"http://user:private@localhost"}, "quit\n");

        assertTrue(output.contains("Provide an HTTP or HTTPS server URL"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("Exception"));
        assertFalse(output.contains("Welcome"));
    }

    @Test
    public void extraLaunchArgumentsShowUsageWithoutEchoingThem() {
        String output = run(new String[]{"private-server", "private-argument"}, "quit\n");

        assertEquals("Usage: ClientMain [server URL]" + System.lineSeparator(), output);
    }

    @ParameterizedTest
    @CsvSource({"401, Incorrect username or password.", "500, The server could not complete your request. Please try again."})
    public void failedLoginKeepsTheConsoleUsableWithoutPrintingServerDetails(int status, String message) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/session", exchange -> {
            byte[] response = "{\"message\":\"private database error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();

        String output = run(new String[]{serverURL()}, "login Alice private-password\nhelp\nquit\n");

        assertTrue(output.contains(message));
        assertTrue(output.contains("register <username>"));
        assertTrue(output.contains("Goodbye!"));
        assertFalse(output.contains("signed in> "));
        assertFalse(output.contains("private"));
        assertFalse(output.contains(Integer.toString(status)));
        assertFalse(output.contains("Exception"));
    }

    private String serverURL() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String run(String[] args, String input) {
        StringWriter transcript = new StringWriter();
        ClientMain.run(args, new StringReader(input), new PrintWriter(transcript));
        return transcript.toString();
    }
}
