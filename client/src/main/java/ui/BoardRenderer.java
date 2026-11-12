package ui;

import chess.ChessBoard;
import chess.ChessGame.TeamColor;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.Objects;
import java.util.StringJoiner;

import static ui.EscapeSequences.*;

public class BoardRenderer {
    private static final int SIZE = 8;
    private static final String BORDER_STYLE = SET_BG_COLOR_BLACK + SET_TEXT_COLOR_WHITE;

    public static String render(ChessBoard board, TeamColor perspective) {
        Objects.requireNonNull(board, "A board is required.");
        Objects.requireNonNull(perspective, "A viewing color is required.");
        boolean whiteView = perspective == TeamColor.WHITE;
        String files = fileLabels(whiteView);
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        lines.add(RESET + files);
        for (int index = 0; index < SIZE; index++) {
            int row = whiteView ? SIZE - index : index + 1;
            lines.add(rank(board, row, whiteView));
        }
        lines.add(files);
        return lines.toString();
    }

    private static String fileLabels(boolean whiteView) {
        StringBuilder labels = new StringBuilder(BORDER_STYLE).append("   ");
        for (int index = 0; index < SIZE; index++) {
            char file = (char) ('a' + column(index, whiteView) - 1);
            labels.append(' ').append(file).append(' ');
        }
        return labels.append("   ").append(RESET).toString();
    }

    private static String rank(ChessBoard board, int row, boolean whiteView) {
        StringBuilder line = new StringBuilder(BORDER_STYLE).append(' ').append(row).append(' ');
        for (int index = 0; index < SIZE; index++) {
            int column = column(index, whiteView);
            line.append((row + column) % 2 == 1 ? SET_BG_COLOR_LIGHT_GREY : SET_BG_COLOR_DARK_GREY);
            line.append(piece(board.getPiece(new ChessPosition(row, column))));
        }
        return line.append(BORDER_STYLE).append(' ').append(row).append(' ').append(RESET).toString();
    }

    private static int column(int index, boolean whiteView) {
        return whiteView ? index + 1 : SIZE - index;
    }

    private static String piece(ChessPiece piece) {
        if (piece == null) {
            return EMPTY;
        }
        boolean white = piece.getTeamColor() == TeamColor.WHITE;
        String glyph = switch (piece.getPieceType()) {
            case KING -> white ? WHITE_KING : BLACK_KING;
            case QUEEN -> white ? WHITE_QUEEN : BLACK_QUEEN;
            case BISHOP -> white ? WHITE_BISHOP : BLACK_BISHOP;
            case KNIGHT -> white ? WHITE_KNIGHT : BLACK_KNIGHT;
            case ROOK -> white ? WHITE_ROOK : BLACK_ROOK;
            case PAWN -> white ? WHITE_PAWN : BLACK_PAWN;
        };
        return (white ? SET_TEXT_COLOR_WHITE : SET_TEXT_COLOR_YELLOW) + glyph;
    }
}
