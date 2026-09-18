package chess;

import java.util.Arrays;

/**
 * A chessboard that can hold and rearrange chess pieces.
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessBoard {
    private static final int BOARD_SIZE = 8;
    private final ChessPiece[][] pieces;

    public ChessBoard() {
        pieces = new ChessPiece[BOARD_SIZE][BOARD_SIZE];
    }

    /**
     * Adds a chess piece to the chessboard
     *
     * @param position where to add the piece to
     * @param piece    the piece to add
     */
    public void addPiece(ChessPosition position, ChessPiece piece) {
        pieces[toIndex(position.getRow())][toIndex(position.getColumn())] = piece;
    }

    /**
     * Gets a chess piece on the chessboard
     *
     * @param position The position to get the piece from
     * @return Either the piece at the position, or null if no piece is at that
     * position
     */
    public ChessPiece getPiece(ChessPosition position) {
        return pieces[toIndex(position.getRow())][toIndex(position.getColumn())];
    }

    /**
     * Sets the board to the default starting board
     * (How the game of chess normally starts)
     */
    public void resetBoard() {
        clearBoard();

        setBackRank(1, ChessGame.TeamColor.WHITE);
        setPawnRank(2, ChessGame.TeamColor.WHITE);
        setPawnRank(7, ChessGame.TeamColor.BLACK);
        setBackRank(8, ChessGame.TeamColor.BLACK);
    }

    private void clearBoard() {
        for (ChessPiece[] row : pieces) {
            Arrays.fill(row, null);
        }
    }

    private void setPawnRank(int row, ChessGame.TeamColor color) {
        for (int column = 1; column <= BOARD_SIZE; column++) {
            addPiece(new ChessPosition(row, column), new ChessPiece(color, ChessPiece.PieceType.PAWN));
        }
    }

    private void setBackRank(int row, ChessGame.TeamColor color) {
        ChessPiece.PieceType[] backRank = {
                ChessPiece.PieceType.ROOK,
                ChessPiece.PieceType.KNIGHT,
                ChessPiece.PieceType.BISHOP,
                ChessPiece.PieceType.QUEEN,
                ChessPiece.PieceType.KING,
                ChessPiece.PieceType.BISHOP,
                ChessPiece.PieceType.KNIGHT,
                ChessPiece.PieceType.ROOK
        };

        for (int column = 1; column <= BOARD_SIZE; column++) {
            addPiece(new ChessPosition(row, column), new ChessPiece(color, backRank[column - 1]));
        }
    }

    private int toIndex(int coordinate) {
        return coordinate - 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ChessBoard other)) {
            return false;
        }
        return Arrays.deepEquals(pieces, other.pieces);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(pieces);
    }

    @Override
    public String toString() {
        return "ChessBoard{pieces=%s}".formatted(Arrays.deepToString(pieces));
    }
}
