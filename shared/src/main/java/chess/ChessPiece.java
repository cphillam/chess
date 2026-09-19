package chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Objects;

/**
 * Represents a single chess piece
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessPiece {
    private static final int[][] ROOK_DIRECTIONS = {
            {1, 0},
            {0, 1},
            {-1, 0},
            {0, -1}
    };
    private static final int[][] BISHOP_DIRECTIONS = {
            {1, 1},
            {-1, 1},
            {-1, -1},
            {1, -1}
    };
    private static final int[][] QUEEN_DIRECTIONS = {
            {1, 0},
            {0, 1},
            {-1, 0},
            {0, -1},
            {1, 1},
            {-1, 1},
            {-1, -1},
            {1, -1}
    };
    private static final int[][] KING_OFFSETS = {
            {1, 0},
            {1, 1},
            {0, 1},
            {-1, 1},
            {-1, 0},
            {-1, -1},
            {0, -1},
            {1, -1}
    };
    private static final PieceType[] PROMOTION_TYPES = {
            PieceType.QUEEN,
            PieceType.BISHOP,
            PieceType.ROOK,
            PieceType.KNIGHT
    };

    private final ChessGame.TeamColor pieceColor;
    private final PieceType type;

    public ChessPiece(ChessGame.TeamColor pieceColor, ChessPiece.PieceType type) {
        this.pieceColor = pieceColor;
        this.type = type;
    }

    /**
     * The various different chess piece options
     */
    public enum PieceType {
        KING,
        QUEEN,
        BISHOP,
        KNIGHT,
        ROOK,
        PAWN
    }

    /**
     * @return Which team this chess piece belongs to
     */
    public ChessGame.TeamColor getTeamColor() {
        return pieceColor;
    }

    /**
     * @return which type of chess piece this piece is
     */
    public PieceType getPieceType() {
        return type;
    }

    /**
     * Calculates all the positions a chess piece can move to
     * Does not take into account moves that are illegal due to leaving the king in
     * danger
     *
     * @return Collection of valid moves
     */
    public Collection<ChessMove> pieceMoves(ChessBoard board, ChessPosition myPosition) {
        if (type == PieceType.KNIGHT) {
            return knightMoves(board, myPosition);
        }
        if (type == PieceType.ROOK) {
            return slidingMoves(board, myPosition, ROOK_DIRECTIONS);
        }
        if (type == PieceType.BISHOP) {
            return slidingMoves(board, myPosition, BISHOP_DIRECTIONS);
        }
        if (type == PieceType.QUEEN) {
            return slidingMoves(board, myPosition, QUEEN_DIRECTIONS);
        }
        if (type == PieceType.KING) {
            return kingMoves(board, myPosition);
        }
        if (type == PieceType.PAWN) {
            return pawnMoves(board, myPosition);
        }
        return Collections.emptyList();
    }

    private Collection<ChessMove> knightMoves(ChessBoard board, ChessPosition myPosition) {
        int[][] offsets = {
                {2, 1},
                {1, 2},
                {-1, 2},
                {-2, 1},
                {-2, -1},
                {-1, -2},
                {1, -2},
                {2, -1}
        };
        Collection<ChessMove> moves = new ArrayList<>();

        for (int[] offset : offsets) {
            addMoveIfAvailable(board, myPosition, moves,
                    myPosition.getRow() + offset[0],
                    myPosition.getColumn() + offset[1]);
        }

        return moves;
    }

    private Collection<ChessMove> slidingMoves(ChessBoard board, ChessPosition myPosition, int[][] directions) {
        Collection<ChessMove> moves = new ArrayList<>();

        for (int[] direction : directions) {
            int row = myPosition.getRow() + direction[0];
            int column = myPosition.getColumn() + direction[1];

            while (isOnBoard(row, column)) {
                ChessPosition endPosition = new ChessPosition(row, column);
                ChessPiece destinationPiece = board.getPiece(endPosition);
                if (destinationPiece == null) {
                    moves.add(new ChessMove(myPosition, endPosition, null));
                } else {
                    if (destinationPiece.getTeamColor() != pieceColor) {
                        moves.add(new ChessMove(myPosition, endPosition, null));
                    }
                    break;
                }

                row += direction[0];
                column += direction[1];
            }
        }

        return moves;
    }

    private Collection<ChessMove> kingMoves(ChessBoard board, ChessPosition myPosition) {
        Collection<ChessMove> moves = new ArrayList<>();

        for (int[] offset : KING_OFFSETS) {
            addMoveIfAvailable(board, myPosition, moves,
                    myPosition.getRow() + offset[0],
                    myPosition.getColumn() + offset[1]);
        }

        return moves;
    }

    private Collection<ChessMove> pawnMoves(ChessBoard board, ChessPosition myPosition) {
        Collection<ChessMove> moves = new ArrayList<>();
        int direction = pieceColor == ChessGame.TeamColor.WHITE ? 1 : -1;
        int startRow = pieceColor == ChessGame.TeamColor.WHITE ? 2 : 7;
        int promotionRow = pieceColor == ChessGame.TeamColor.WHITE ? 8 : 1;

        int oneStepRow = myPosition.getRow() + direction;
        int col = myPosition.getColumn();

        // Forward moves
        if (isOnBoard(oneStepRow, col)) {
            ChessPosition oneStepPos = new ChessPosition(oneStepRow, col);
            if (board.getPiece(oneStepPos) == null) {
                addPawnMove(myPosition, oneStepPos, promotionRow, moves);

                if (myPosition.getRow() == startRow) {
                    int twoStepRow = myPosition.getRow() + (2 * direction);
                    if (isOnBoard(twoStepRow, col)) {
                        ChessPosition twoStepPos = new ChessPosition(twoStepRow, col);
                        if (board.getPiece(twoStepPos) == null) {
                            moves.add(new ChessMove(myPosition, twoStepPos, null));
                        }
                    }
                }
            }
        }

        // Diagonal capture moves
        int[] captureCols = {col - 1, col + 1};
        for (int captureCol : captureCols) {
            if (isOnBoard(oneStepRow, captureCol)) {
                ChessPosition capturePos = new ChessPosition(oneStepRow, captureCol);
                ChessPiece targetPiece = board.getPiece(capturePos);
                if (targetPiece != null && targetPiece.getTeamColor() != pieceColor) {
                    addPawnMove(myPosition, capturePos, promotionRow, moves);
                }
            }
        }

        return moves;
    }

    private void addPawnMove(ChessPosition startPosition, ChessPosition endPosition,
                             int promotionRow, Collection<ChessMove> moves) {
        if (endPosition.getRow() == promotionRow) {
            for (PieceType promotionType : PROMOTION_TYPES) {
                moves.add(new ChessMove(startPosition, endPosition, promotionType));
            }
        } else {
            moves.add(new ChessMove(startPosition, endPosition, null));
        }
    }

    private void addMoveIfAvailable(ChessBoard board, ChessPosition startPosition,
                                    Collection<ChessMove> moves, int row, int column) {
        if (!isOnBoard(row, column)) {
            return;
        }

        ChessPosition endPosition = new ChessPosition(row, column);
        ChessPiece destinationPiece = board.getPiece(endPosition);
        if (destinationPiece == null || destinationPiece.getTeamColor() != pieceColor) {
            moves.add(new ChessMove(startPosition, endPosition, null));
        }
    }

    private boolean isOnBoard(int row, int column) {
        return row >= 1 && row <= 8 && column >= 1 && column <= 8;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ChessPiece other)) {
            return false;
        }
        return pieceColor == other.pieceColor && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pieceColor, type);
    }

    @Override
    public String toString() {
        return "ChessPiece{pieceColor=%s, type=%s}".formatted(pieceColor, type);
    }
}
