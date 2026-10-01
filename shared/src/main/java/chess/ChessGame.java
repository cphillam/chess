package chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * A class that can manage a chess game, making moves on a board
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessGame {

    private TeamColor currentTurn;
    private ChessBoard board;

    public ChessGame() {
        this.currentTurn = TeamColor.WHITE;
        this.board = new ChessBoard();
        this.board.resetBoard();
    }

    /**
     * @return Which team's turn it is
     */
    public TeamColor getTeamTurn() {
        return currentTurn;
    }

    /**
     * Sets which teams turn it is
     *
     * @param team the team whose turn it is
     */
    public void setTeamTurn(TeamColor team) {
        this.currentTurn = team;
    }

    /**
     * Enum identifying the 2 possible teams in a chess game
     */
    public enum TeamColor {
        WHITE,
        BLACK
    }

    /**
     * Gets all valid moves for a piece at the given location
     *
     * @param startPosition the piece to get valid moves for
     * @return Set of valid moves for requested piece, or null if no piece at
     * startPosition
     */
    public Collection<ChessMove> validMoves(ChessPosition startPosition) {
        if (board == null) {
            return null;
        }
        ChessPiece piece = board.getPiece(startPosition);
        if (piece == null) {
            return null;
        }

        Collection<ChessMove> potentialMoves = piece.pieceMoves(board, startPosition);
        if (potentialMoves == null || potentialMoves.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<ChessMove> legalMoves = new ArrayList<>();
        for (ChessMove move : potentialMoves) {
            ChessBoard simulatedBoard = simulateMove(board, move);
            if (!isBoardInCheck(simulatedBoard, piece.getTeamColor())) {
                legalMoves.add(move);
            }
        }
        return legalMoves;
    }

    /**
     * Simulates executing a move on a copy of the given board
     *
     * @param currentBoard the board state before the move
     * @param move the move to simulate
     * @return a new board state reflecting the move
     */
    private ChessBoard simulateMove(ChessBoard currentBoard, ChessMove move) {
        ChessBoard newBoard = new ChessBoard(currentBoard);
        ChessPiece movingPiece = newBoard.getPiece(move.getStartPosition());

        ChessPiece placedPiece = (move.getPromotionPiece() != null)
                ? new ChessPiece(movingPiece.getTeamColor(), move.getPromotionPiece())
                : movingPiece;

        newBoard.addPiece(move.getEndPosition(), placedPiece);
        newBoard.addPiece(move.getStartPosition(), null);
        return newBoard;
    }

    /**
     * Makes a move in the chess game
     *
     * @param move chess move to perform
     * @throws InvalidMoveException if move is invalid
     */
    public void makeMove(ChessMove move) throws InvalidMoveException {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Determines if the given team is in check
     *
     * @param teamColor which team to check for check
     * @return True if the specified team is in check
     */
    public boolean isInCheck(TeamColor teamColor) {
        return isBoardInCheck(this.board, teamColor);
    }

    /**
     * Determines if the given team is in check on the specified board
     *
     * @param evalBoard the board state to evaluate
     * @param teamColor which team to check for check
     * @return True if teamColor's king is under attack
     */
    private boolean isBoardInCheck(ChessBoard evalBoard, TeamColor teamColor) {
        ChessPosition kingPosition = findKing(evalBoard, teamColor);
        if (kingPosition == null) {
            return false;
        }

        TeamColor opponentColor = (teamColor == TeamColor.WHITE) ? TeamColor.BLACK : TeamColor.WHITE;

        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition piecePosition = new ChessPosition(row, col);
                ChessPiece piece = evalBoard.getPiece(piecePosition);

                if (piece != null && piece.getTeamColor() == opponentColor) {
                    Collection<ChessMove> opponentMoves = piece.pieceMoves(evalBoard, piecePosition);
                    if (opponentMoves != null) {
                        for (ChessMove move : opponentMoves) {
                            if (move.getEndPosition().equals(kingPosition)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Finds the position of the king for the given team on the specified board
     *
     * @param evalBoard the board to search
     * @param teamColor the color of the king to find
     * @return ChessPosition of the king, or null if not found
     */
    private ChessPosition findKing(ChessBoard evalBoard, TeamColor teamColor) {
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition pos = new ChessPosition(row, col);
                ChessPiece piece = evalBoard.getPiece(pos);
                if (piece != null && piece.getTeamColor() == teamColor && piece.getPieceType() == ChessPiece.PieceType.KING) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Determines if the given team is in checkmate
     *
     * @param teamColor which team to check for checkmate
     * @return True if the specified team is in checkmate
     */
    public boolean isInCheckmate(TeamColor teamColor) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Determines if the given team is in stalemate, which here is defined as having
     * no valid moves while not in check.
     *
     * @param teamColor which team to check for stalemate
     * @return True if the specified team is in stalemate, otherwise false
     */
    public boolean isInStalemate(TeamColor teamColor) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Sets this game's chessboard to a given board
     *
     * @param board the new board to use
     */
    public void setBoard(ChessBoard board) {
        this.board = board;
    }

    /**
     * Gets the current chessboard
     *
     * @return the chessboard
     */
    public ChessBoard getBoard() {
        return board;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChessGame chessGame = (ChessGame) o;
        return currentTurn == chessGame.currentTurn && Objects.equals(board, chessGame.board);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTurn, board);
    }
}
