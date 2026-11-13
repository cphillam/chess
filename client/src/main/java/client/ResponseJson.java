package client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;

final class ResponseJson {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(String.class, new TextAdapter().nullSafe())
            .registerTypeAdapter(int.class, new GameIDAdapter())
            .create();

    private ResponseJson() {
    }

    static <T> T read(String body, Class<T> responseType) throws JsonParseException {
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            T response = GSON.getAdapter(responseType).read(reader);
            if (response == null || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Expected one JSON response.");
            }
            return response;
        } catch (IOException | IllegalStateException exception) {
            throw new JsonParseException("Invalid JSON response.", exception);
        }
    }

    private static void requireToken(JsonReader reader, JsonToken token) throws IOException {
        if (reader.peek() != token) {
            throw new JsonParseException("Unexpected JSON value.");
        }
    }

    private static final class TextAdapter extends TypeAdapter<String> {
        @Override
        public String read(JsonReader reader) throws IOException {
            requireToken(reader, JsonToken.STRING);
            return reader.nextString();
        }

        @Override
        public void write(JsonWriter writer, String value) throws IOException {
            writer.value(value);
        }
    }

    private static final class GameIDAdapter extends TypeAdapter<Integer> {
        @Override
        public Integer read(JsonReader reader) throws IOException {
            requireToken(reader, JsonToken.NUMBER);
            try {
                return Integer.parseInt(reader.nextString());
            } catch (NumberFormatException exception) {
                throw new JsonParseException("Expected a whole game number.", exception);
            }
        }

        @Override
        public void write(JsonWriter writer, Integer value) throws IOException {
            writer.value(value);
        }
    }
}
