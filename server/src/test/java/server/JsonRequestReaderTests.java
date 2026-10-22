package server;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import service.BadRequestException;
import service.CreateGameRequest;
import service.JoinGameRequest;
import service.LoginRequest;
import service.RegisterRequest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JsonRequestReaderTests {
    @ParameterizedTest
    @MethodSource("validRequests")
    public void readAcceptsValidRequests(String body, Class<?> requestType, Object expected)
            throws BadRequestException {
        assertEquals(expected, JsonRequestReader.read(body, requestType));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "null", "true", "1", "\"alice\"", "[]", "{", "{} {}",
            "/* comment */{}", "{'username':'alice'}", "{username:\"alice\"}",
            "{\"username\":\"alice\",}", "{\"username\":\"alice\"} // comment",
            "{\"username\":\"alice\"} garbage", "{\"username\":\"alice\",\"password\":}",
            "{\"username\":\"al\nice\"}", "{\"username\":\"al\tice\"}",
            "{\"username\":\"al\u0000ice\"}", "{\"username\":\"al\\'ice\"}",
            "{\"username\":\"al\\qice\"}", "{\"username\":\"\\uZZZZ\"}"
    })
    public void readRejectsMalformedOrNonObjectRequests(String body) {
        assertThrows(BadRequestException.class, () -> JsonRequestReader.read(body, RegisterRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "2147483648", "-2147483649", "\"invalid\"", "true", "[]", "{}"})
    public void readRejectsGameIDsThatCannotBeIntegers(String gameID) {
        String body = "{\"playerColor\":\"WHITE\",\"gameID\":" + gameID + "}";

        assertThrows(BadRequestException.class, () -> JsonRequestReader.read(body, JoinGameRequest.class));
    }

    private static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of("{\"username\":\"al\\\"ice\",\"password\":\"password\",\"email\":\"alice@example.com\"}",
                        RegisterRequest.class, new RegisterRequest("al\"ice", "password", "alice@example.com")),
                Arguments.of("{\"username\":\"alice\",\"password\":\"password\"}",
                        LoginRequest.class, new LoginRequest("alice", "password")),
                Arguments.of("{\"gameName\":\"First game\"}",
                        CreateGameRequest.class, new CreateGameRequest("First game")),
                Arguments.of("{\"gameName\":\"Line\\nTab\\tSlash\\\\Quote\\\"Unicode\\u0041\"}",
                        CreateGameRequest.class, new CreateGameRequest("Line\nTab\tSlash\\Quote\"UnicodeA")),
                Arguments.of("{\"gameName\":\"Escaped slash\\/ and \\b\\f\\r\"}",
                        CreateGameRequest.class, new CreateGameRequest("Escaped slash/ and \b\f\r")),
                Arguments.of("{\"playerColor\":\"WHITE\",\"gameID\":1}",
                        JoinGameRequest.class, new JoinGameRequest("WHITE", 1)));
    }
}
