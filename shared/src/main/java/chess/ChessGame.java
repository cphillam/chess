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

    // Move tracking for castling and en passant
    private boolean whiteKingMoved;
    private boolean whiteLeftRookMoved;
    private boolean whiteRightRookMoved;
    private boolean blackKingMoved;
    private boolean blackLeftRookMoved;
    private boolean blackRightRookMoved;

    private ChessMove lastMove;
    private ChessPiece lastMovedPiece;

    public ChessGame() {
        this.currentTurn = TeamColor.WHITE;
        this.board = new ChessBoard();
        this.board.resetBoard();
        resetSpecialMoveState();
    }

    private void resetSpecialMoveState() {
        this.whiteKingMoved = false;
        this.whiteLeftRookMoved = false;
        this.whiteRightRookMoved = false;
        this.blackKingMoved = false;
        this.blackLeftRookMoved = false;
        this.blackRightRookMoved = false;
        this.lastMove = null;
        this.lastMovedPiece = null;
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

        Collection<ChessMove> baseMoves = piece.pieceMoves(board, startPosition);
        Collection<ChessMove> potentialMoves = (baseMoves != null)
                ? new ArrayList<>(baseMoves)
                : new ArrayList<>();

        if (piece.getPieceType() == ChessPiece.PieceType.KING) {
            addCastlingMoves(potentialMoves, startPosition, piece);
        } else if (piece.getPieceType() == ChessPiece.PieceType.PAWN) {
            addEnPassantMoves(potentialMoves, startPosition, piece);
        }

        if (potentialMoves.isEmpty()) {
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
     * Adds candidate castling moves for a king if conditions are met
     */
    private void addCastlingMoves(Collection<ChessMove> potentialMoves, ChessPosition startPosition, ChessPiece king) {
        TeamColor color = king.getTeamColor();
        int row = (color == TeamColor.WHITE) ? 1 : 8;

        if (startPosition.getRow() != row || startPosition.getColumn() != 5) {
            return;
        }

        boolean kingMoved = (color == TeamColor.WHITE) ? whiteKingMoved : blackKingMoved;
        if (kingMoved) {
            return;
        }

        if (isBoardInCheck(board, color)) {
            return;
        }

        boolean leftRookMoved = (color == TeamColor.WHITE) ? whiteLeftRookMoved : blackLeftRookMoved;
        boolean rightRookMoved = (color == TeamColor.WHITE) ? whiteRightRookMoved : blackRightRookMoved;

        // Queenside Castle (King moves to col 3, rook from col 1 to col 4)
        if (!leftRookMoved) {
            ChessPiece rook = board.getPiece(new ChessPosition(row, 1));
            if (rook != null && rook.getPieceType() == ChessPiece.PieceType.ROOK && rook.getTeamColor() == color) {
                if (board.getPiece(new ChessPosition(row, 2)) == null
                        && board.getPiece(new ChessPosition(row, 3)) == null
                        && board.getPiece(new ChessPosition(row, 4)) == null) {
                    ChessBoard stepBoard = simulateMove(board, new ChessMove(startPosition, new ChessPosition(row, 4), null));
                    if (!isBoardInCheck(stepBoard, color)) {
                        potentialMoves.add(new ChessMove(startPosition, new ChessPosition(row, 3), null));
                    }
                }
            }
        }

        // Kingside Castle (King moves to col 7, rook from col 8 to col 6)
        if (!rightRookMoved) {
            ChessPiece rook = board.getPiece(new ChessPosition(row, 8));
            if (rook != null && rook.getPieceType() == ChessPiece.PieceType.ROOK && rook.getTeamColor() == color) {
                if (board.getPiece(new ChessPosition(row, 6)) == null
                        && board.getPiece(new ChessPosition(row, 7)) == null) {
                    ChessBoard stepBoard = simulateMove(board, new ChessMove(startPosition, new ChessPosition(row, 6), null));
                    if (!isBoardInCheck(stepBoard, color)) {
                        potentialMoves.add(new ChessMove(startPosition, new ChessPosition(row, 7), null));
                    }
                }
            }
        }
    }

    /**
     * Adds candidate en passant moves for a pawn if the opponent just double-moved adjacent
     */
    private void addEnPassantMoves(Collection<ChessMove> potentialMoves, ChessPosition startPosition, ChessPiece pawn) {
        if (lastMove == null || lastMovedPiece == null) {
            return;
        }
        if (lastMovedPiece.getPieceType() != ChessPiece.PieceType.PAWN) {
            return;
        }
        if (lastMovedPiece.getTeamColor() == pawn.getTeamColor()) {
            return;
        }

        int startRow = lastMove.getStartPosition().getRow();
        int endRow = lastMove.getEndPosition().getRow();
        int endCol = lastMove.getEndPosition().getColumn();

        if (Math.abs(endRow - startRow) != 2) {
            return;
        }

        if (startPosition.getRow() != endRow) {
            return;
        }

        if (Math.abs(startPosition.getColumn() - endCol) != 1) {
            return;
        }

        int captureRow = (pawn.getTeamColor() == TeamColor.WHITE) ? endRow + 1 : endRow - 1;
        potentialMoves.add(new ChessMove(startPosition, new ChessPosition(captureRow, endCol), null));
    }

    private boolean isCastleMove(ChessPiece piece, ChessMove move) {
        if (piece == null || piece.getPieceType() != ChessPiece.PieceType.KING) {
            return false;
        }
        return Math.abs(move.getStartPosition().getColumn() - move.getEndPosition().getColumn()) == 2;
    }

    private boolean isEnPassantMoveOnBoard(ChessBoard currentBoard, ChessPiece piece, ChessMove move) {
        if (piece == null || piece.getPieceType() != ChessPiece.PieceType.PAWN) {
            return false;
        }
        if (move.getStartPosition().getColumn() != move.getEndPosition().getColumn()) {
            return currentBoard.getPiece(move.getEndPosition()) == null;
        }
        return false;
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
        boolean isEnPassant = isEnPassantMoveOnBoard(currentBoard, movingPiece, move);

        ChessPiece placedPiece = (move.getPromotionPiece() != null)
                ? new ChessPiece(movingPiece.getTeamColor(), move.getPromotionPiece())
                : movingPiece;

        newBoard.addPiece(move.getEndPosition(), placedPiece);
        newBoard.addPiece(move.getStartPosition(), null);

        // Handle castling rook movement
        if (isCastleMove(movingPiece, move)) {
            int row = move.getStartPosition().getRow();
            if (move.getEndPosition().getColumn() == 3) {
                ChessPiece rook = newBoard.getPiece(new ChessPosition(row, 1));
                newBoard.addPiece(new ChessPosition(row, 4), rook);
                newBoard.addPiece(new ChessPosition(row, 1), null);
            } else if (move.getEndPosition().getColumn() == 7) {
                ChessPiece rook = newBoard.getPiece(new ChessPosition(row, 8));
                newBoard.addPiece(new ChessPosition(row, 6), rook);
                newBoard.addPiece(new ChessPosition(row, 8), null);
            }
        }

        // Handle en passant captured pawn removal
        if (isEnPassant) {
            newBoard.addPiece(new ChessPosition(move.getStartPosition().getRow(), move.getEndPosition().getColumn()), null);
        }

        return newBoard;
    }

    /**
     * Makes a move in the chess game
     *
     * @param move chess move to perform
     * @throws InvalidMoveException if move is invalid
     */
    public void makeMove(ChessMove move) throws InvalidMoveException {
        if (move == null || move.getStartPosition() == null || move.getEndPosition() == null) {
            throw new InvalidMoveException("Invalid move parameters");
        }

        if (board == null) {
            throw new InvalidMoveException("No active board");
        }

        ChessPiece piece = board.getPiece(move.getStartPosition());
        if (piece == null) {
            throw new InvalidMoveException("No piece at start position: " + move.getStartPosition());
        }

        if (piece.getTeamColor() != currentTurn) {
            throw new InvalidMoveException("Piece color does not match current turn");
        }

        Collection<ChessMove> legalMoves = validMoves(move.getStartPosition());
        if (legalMoves == null || !legalMoves.contains(move)) {
            throw new InvalidMoveException("Move is not legal: " + move);
        }

        boolean isEnPassant = isEnPassantMoveOnBoard(board, piece, move);

        ChessPiece placedPiece = (move.getPromotionPiece() != null)
                ? new ChessPiece(piece.getTeamColor(), move.getPromotionPiece())
                : piece;

        board.addPiece(move.getEndPosition(), placedPiece);
        board.addPiece(move.getStartPosition(), null);

        // Handle castling rook movement
        if (isCastleMove(piece, move)) {
            int row = move.getStartPosition().getRow();
            if (move.getEndPosition().getColumn() == 3) {
                ChessPiece rook = board.getPiece(new ChessPosition(row, 1));
                board.addPiece(new ChessPosition(row, 4), rook);
                board.addPiece(new ChessPosition(row, 1), null);
            } else if (move.getEndPosition().getColumn() == 7) {
                ChessPiece rook = board.getPiece(new ChessPosition(row, 8));
                board.addPiece(new ChessPosition(row, 6), rook);
                board.addPiece(new ChessPosition(row, 8), null);
            }
        }

        // Handle en passant captured pawn removal
        if (isEnPassant) {
            board.addPiece(new ChessPosition(move.getStartPosition().getRow(), move.getEndPosition().getColumn()), null);
        }

        // Update moved piece flags
        updateMovedFlags(piece, move);

        // Record last move for en passant
        this.lastMove = move;
        this.lastMovedPiece = piece;

        currentTurn = (currentTurn == TeamColor.WHITE) ? TeamColor.BLACK : TeamColor.WHITE;
    }

    private void updateMovedFlags(ChessPiece piece, ChessMove move) {
        if (piece.getPieceType() == ChessPiece.PieceType.KING) {
            if (piece.getTeamColor() == TeamColor.WHITE) {
                whiteKingMoved = true;
            } else {
                blackKingMoved = true;
            }
        }
        if (move.getStartPosition().equals(new ChessPosition(1, 1)) || move.getEndPosition().equals(new ChessPosition(1, 1))) {
            whiteLeftRookMoved = true;
        }
        if (move.getStartPosition().equals(new ChessPosition(1, 8)) || move.getEndPosition().equals(new ChessPosition(1, 8))) {
            whiteRightRookMoved = true;
        }
        if (move.getStartPosition().equals(new ChessPosition(8, 1)) || move.getEndPosition().equals(new ChessPosition(8, 1))) {
            blackLeftRookMoved = true;
        }
        if (move.getStartPosition().equals(new ChessPosition(8, 8)) || move.getEndPosition().equals(new ChessPosition(8, 8))) {
            blackRightRookMoved = true;
        }
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
        if (!isInCheck(teamColor)) {
            return false;
        }
        return !hasAnyValidMoves(teamColor);
    }

    /**
     * Checks whether the specified team has any legal moves available
     *
     * @param teamColor the team to check
     * @return True if at least one piece of teamColor has a valid move
     */
    private boolean hasAnyValidMoves(TeamColor teamColor) {
        if (board == null) {
            return false;
        }
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition pos = new ChessPosition(row, col);
                ChessPiece piece = board.getPiece(pos);
                if (piece != null && piece.getTeamColor() == teamColor) {
                    Collection<ChessMove> moves = validMoves(pos);
                    if (moves != null && !moves.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines if the given team is in stalemate, which here is defined as having
     * no valid moves while not in check.
     *
     * @param teamColor which team to check for stalemate
     * @return True if the specified team is in stalemate, otherwise false
     */
    public boolean isInStalemate(TeamColor teamColor) {
        if (isInCheck(teamColor)) {
            return false;
        }
        return !hasAnyValidMoves(teamColor);
    }

    /**
     * Sets this game's chessboard to a given board
     *
     * @param board the new board to use
     */
    public void setBoard(ChessBoard board) {
        this.board = board;
        resetSpecialMoveState();
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
