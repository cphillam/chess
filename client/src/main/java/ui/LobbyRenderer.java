package ui;

import client.GameSummary;

import java.util.List;
import java.util.StringJoiner;

public class LobbyRenderer {
    public static String render(List<GameSummary> games) {
        if (games.isEmpty()) {
            return "No games available. Use create to start one.";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        lines.add("Games:");
        for (int index = 0; index < games.size(); index++) {
            GameSummary game = games.get(index);
            lines.add((index + 1) + ". " + displayText(game.gameName())
                    + " | White: " + playerName(game.whiteUsername()) + " | Black: " + playerName(game.blackUsername()));
        }
        return lines.toString();
    }

    private static String playerName(String username) {
        return username == null ? "(open)" : displayText(username);
    }

    private static String displayText(String value) {
        return value.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ");
    }
}
