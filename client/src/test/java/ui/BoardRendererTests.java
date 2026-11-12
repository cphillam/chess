package ui;

import chess.ChessBoard;
import chess.ChessGame.TeamColor;
import chess.ChessPiece;
import chess.ChessPiece.PieceType;
import chess.ChessPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class BoardRendererTests {
    private static final String RESET = "\u001b[0m";
    private static final Pattern ANSI = Pattern.compile("\u001b\\[([0-9;]*)m");

    @ParameterizedTest
    @CsvSource({"WHITE, abcdefgh, 8, 1", "BLACK, hgfedcba, 1, 8"})
    public void coordinatesMatchTheViewingColor(TeamColor perspective, String files, int firstRank, int lastRank) {
        List<String> lines = plainLines(BoardRenderer.render(new ChessBoard(), perspective));

        assertEquals(10, lines.size());
        assertEquals(files, lines.getFirst().replace(" ", ""));
        assertEquals(lines.getFirst(), lines.getLast());
        for (int index = 1; index <= 8; index++) {
            String line = lines.get(index);
            int rank = firstRank + (firstRank < lastRank ? index - 1 : 1 - index);
            assertEquals(" " + rank + " ", line.substring(0, 3));
            assertEquals(line.substring(0, 3), line.substring(27));
        }
        assertTrue(lines.stream().allMatch(line -> line.length() == 30));
    }

    @ParameterizedTest
    @CsvSource({
            "WHITE, 1, 1, 7, 0", "WHITE, 1, 8, 7, 7", "WHITE, 8, 1, 0, 0", "WHITE, 8, 8, 0, 7",
            "WHITE, 1, 4, 7, 3", "WHITE, 8, 5, 0, 4", "BLACK, 1, 1, 0, 7", "BLACK, 1, 8, 0, 0",
            "BLACK, 8, 1, 7, 7", "BLACK, 8, 8, 7, 0", "BLACK, 1, 4, 0, 4", "BLACK, 8, 5, 7, 3"
    })
    public void piecesAppearAtTheirOrientedCoordinates(TeamColor perspective, int row, int column, int screenRow, int screenColumn) {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(row, column), new ChessPiece(TeamColor.WHITE, PieceType.QUEEN));

        String output = BoardRenderer.render(board, perspective);

        assertEquals(" ♕ ", squareText(plainLines(output), screenRow, screenColumn));
        assertEquals(1, output.chars().filter(character -> character == '♕').count());
    }

    @ParameterizedTest
    @CsvSource({"WHITE, KING, ♔", "WHITE, QUEEN, ♕", "WHITE, BISHOP, ♗", "WHITE, KNIGHT, ♘", "WHITE, ROOK, ♖", "WHITE, PAWN, ♙",
            "BLACK, KING, ♚", "BLACK, QUEEN, ♛", "BLACK, BISHOP, ♝", "BLACK, KNIGHT, ♞", "BLACK, ROOK, ♜", "BLACK, PAWN, ♟"})
    public void everyPieceUsesItsUnicodeGlyphAndTeamColor(TeamColor color, PieceType type, String glyph) {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(1, 1), new ChessPiece(color, type));

        String output = BoardRenderer.render(board, TeamColor.WHITE);
        StyledCharacter piece = styledLines(output).get(8).get(4);

        assertEquals(" " + glyph + " ", squareText(plainLines(output), 7, 0));
        assertEquals(glyph.charAt(0), piece.character());
        assertEquals(color == TeamColor.WHITE ? 15 : 226, piece.foreground());
    }

    @Test
    public void theInitialBoardHasTheCorrectBackRanksFromEachPerspective() {
        ChessBoard board = new ChessBoard();
        board.resetBoard();

        List<String> white = plainLines(BoardRenderer.render(board, TeamColor.WHITE));
        List<String> black = plainLines(BoardRenderer.render(board, TeamColor.BLACK));

        assertEquals(" ♜  ♞  ♝  ♛  ♚  ♝  ♞  ♜ ", white.get(1).substring(3, 27));
        assertEquals(" ♖  ♘  ♗  ♕  ♔  ♗  ♘  ♖ ", white.get(8).substring(3, 27));
        assertEquals(" ♖  ♘  ♗  ♔  ♕  ♗  ♘  ♖ ", black.get(1).substring(3, 27));
        assertEquals(" ♜  ♞  ♝  ♚  ♛  ♝  ♞  ♜ ", black.get(8).substring(3, 27));
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void initialBoardsIncludeAllPawnsAndEmptyMiddleRanks(TeamColor perspective) {
        ChessBoard board = new ChessBoard();
        board.resetBoard();

        String output = BoardRenderer.render(board, perspective);
        List<String> lines = plainLines(output);

        assertEquals(8, output.chars().filter(character -> character == '♙').count());
        assertEquals(8, output.chars().filter(character -> character == '♟').count());
        for (int row = 3; row <= 6; row++) {
            assertEquals(" \u2003 ".repeat(8), lines.get(row).substring(3, 27));
        }
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void squaresAlternateInBothDirectionsWithLightCornersAtH1AndA8(TeamColor perspective) {
        List<List<StyledCharacter>> lines = styledLines(BoardRenderer.render(new ChessBoard(), perspective));
        int lightSquares = 0;
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                int color = squareColor(lines, row, column);
                assertTrue(color == 242 || color == 235);
                lightSquares += color == 242 ? 1 : 0;
                if (column > 0) {
                    assertNotEquals(color, squareColor(lines, row, column - 1));
                }
                if (row > 0) {
                    assertNotEquals(color, squareColor(lines, row - 1, column));
                }
            }
        }
        assertEquals(32, lightSquares);
        assertEquals(242, squareColor(lines, 0, 0));
        assertEquals(242, squareColor(lines, 7, 7));
        assertEquals(235, squareColor(lines, 0, 7));
        assertEquals(235, squareColor(lines, 7, 0));
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void piecesRemainReadableOnEitherSquareColor(TeamColor color) {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(1, 1), new ChessPiece(color, PieceType.KING));
        board.addPiece(new ChessPosition(1, 2), new ChessPiece(color, PieceType.QUEEN));

        List<StyledCharacter> row = styledLines(BoardRenderer.render(board, TeamColor.WHITE)).get(8);

        assertEquals(235, row.get(4).background());
        assertEquals(242, row.get(7).background());
        assertEquals(color == TeamColor.WHITE ? 15 : 226, row.get(4).foreground());
        assertEquals(row.get(4).foreground(), row.get(7).foreground());
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void bordersAndResetsKeepPieceFormattingOutOfLabelsAndFollowingOutput(TeamColor perspective) {
        ChessBoard board = new ChessBoard();
        board.resetBoard();

        String output = BoardRenderer.render(board, perspective);
        List<List<StyledCharacter>> lines = styledLines(output);

        assertTrue(output.startsWith(RESET));
        assertTrue(output.endsWith(RESET));
        assertTrue(output.lines().allMatch(line -> line.endsWith(RESET)));
        for (List<StyledCharacter> line : lines) {
            assertEquals(0, line.getFirst().background());
            assertEquals(15, line.getFirst().foreground());
            assertEquals(0, line.getLast().background());
            assertEquals(15, line.getLast().foreground());
        }
        StyledCharacter following = parseLine(output.lines().toList().getLast() + "x").getLast();
        assertEquals(-1, following.background());
        assertEquals(-1, following.foreground());
    }

    @Test
    public void renderingPreservesTheSuppliedBoard() {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(4, 5), new ChessPiece(TeamColor.BLACK, PieceType.KNIGHT));
        ChessBoard before = board.clone();

        BoardRenderer.render(board, TeamColor.WHITE);
        BoardRenderer.render(board, TeamColor.BLACK);

        assertEquals(before, board);
    }

    @Test
    public void missingBoardOrPerspectiveIsRejected() {
        assertThrows(NullPointerException.class, () -> BoardRenderer.render(null, TeamColor.WHITE));
        assertThrows(NullPointerException.class, () -> BoardRenderer.render(new ChessBoard(), null));
    }

    private List<String> plainLines(String output) {
        return output.lines().map(line -> ANSI.matcher(line).replaceAll("")).toList();
    }

    private String squareText(List<String> lines, int row, int column) {
        int offset = 3 + column * 3;
        return lines.get(row + 1).substring(offset, offset + 3);
    }

    private List<List<StyledCharacter>> styledLines(String output) {
        return output.lines().map(this::parseLine).toList();
    }

    private int squareColor(List<List<StyledCharacter>> lines, int row, int column) {
        return lines.get(row + 1).get(4 + column * 3).background();
    }

    private List<StyledCharacter> parseLine(String line) {
        List<StyledCharacter> characters = new ArrayList<>();
        int foreground = -1;
        int background = -1;
        int cursor = 0;
        Matcher escape = ANSI.matcher(line);
        while (escape.find()) {
            appendCharacters(characters, line.substring(cursor, escape.start()), foreground, background);
            String code = escape.group(1);
            if ("0".equals(code)) {
                foreground = -1;
                background = -1;
            } else if (code.startsWith("38;5;")) {
                foreground = Integer.parseInt(code.substring(5));
            } else if (code.startsWith("48;5;")) {
                background = Integer.parseInt(code.substring(5));
            } else {
                fail("Unexpected terminal style: " + code);
            }
            cursor = escape.end();
        }
        appendCharacters(characters, line.substring(cursor), foreground, background);
        return characters;
    }

    private void appendCharacters(List<StyledCharacter> characters, String text, int foreground, int background) {
        for (int index = 0; index < text.length(); index++) {
            characters.add(new StyledCharacter(text.charAt(index), foreground, background));
        }
    }

    private record StyledCharacter(char character, int foreground, int background) {
    }
}
