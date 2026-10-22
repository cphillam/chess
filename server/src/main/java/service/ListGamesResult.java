package service;

import java.util.Collection;

public record ListGamesResult(Collection<GameSummary> games) {
    public record GameSummary(int gameID, String whiteUsername, String blackUsername, String gameName) {
    }
}
