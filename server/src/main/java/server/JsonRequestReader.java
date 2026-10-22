package server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import service.BadRequestException;

import java.io.IOException;
import java.io.StringReader;

final class JsonRequestReader {
    private static final Gson GSON = new Gson();

    private JsonRequestReader() {
    }

    static <T> T read(String body, Class<T> requestType) throws BadRequestException {
        if (body == null || body.isBlank()) {
            throw new BadRequestException();
        }
        validateStringContents(body);
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.setLenient(false);
            T request = GSON.getAdapter(requestType).read(reader);
            if (request == null || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new BadRequestException();
            }
            return request;
        } catch (IOException | JsonParseException | IllegalStateException | NumberFormatException exception) {
            throw new BadRequestException();
        }
    }

    private static void validateStringContents(String body) throws BadRequestException {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (!quoted) {
                quoted = character == '"';
            } else if (character < 0x20) {
                throw new BadRequestException();
            } else if (escaped) {
                if ("\"\\/bfnrtu".indexOf(character) < 0) {
                    throw new BadRequestException();
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                quoted = false;
            }
        }
    }
}
