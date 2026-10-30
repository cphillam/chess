package client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import server.Server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerFacadeTests {
    private static Server server;
    private static URI serverURL;

    @BeforeAll
    public static void init() {
        server = new Server();
        int port = server.run(0);
        serverURL = URI.create("http://localhost:" + port);
    }

    @BeforeEach
    public void clearDatabase() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            HttpRequest request = HttpRequest.newBuilder(serverURL.resolve("/db"))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Unable to clear the test database");
        }
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }
}
