package client;

import ui.Repl;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ClientMain {
    public static void main(String[] args) {
        run(args, new InputStreamReader(System.in, StandardCharsets.UTF_8),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8));
    }

    static void run(String[] args, Reader input, PrintWriter output) {
        if (args.length > 1) {
            output.println("Usage: ClientMain [server URL]");
            output.flush();
            return;
        }
        ServerFacade facade;
        try {
            facade = new ServerFacade(args.length == 0 ? "http://localhost:8080" : args[0]);
        } catch (IllegalArgumentException exception) {
            output.println(exception.getMessage());
            output.flush();
            return;
        }
        new Repl(input, output, new ChessClient(facade)).run();
    }
}
