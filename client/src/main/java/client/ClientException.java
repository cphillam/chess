package client;

public class ClientException extends Exception {
    public enum Kind {
        BAD_REQUEST,
        UNAUTHORIZED,
        ALREADY_TAKEN,
        SERVER_ERROR,
        CONNECTION_ERROR,
        INVALID_RESPONSE,
        INTERRUPTED
    }

    private final Kind kind;

    public ClientException(Kind kind, String message) {
        this(kind, message, null);
    }

    public ClientException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
