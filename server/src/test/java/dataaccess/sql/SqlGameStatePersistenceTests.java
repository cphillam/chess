package dataaccess.sql;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessGame.TeamColor;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPiece.PieceType;
import chess.ChessPosition;
import chess.InvalidMoveException;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import service.GameService;
import service.JoinGameRequest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SqlGameStatePersistenceTests extends SqlTestSupport {
    private GameDAO gameDAO;

    @BeforeEach
    public void setupDAO() throws DataAccessException {
        gameDAO = new SqlGameDAO();
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void castlingRemainsAvailableAndItsResultPersists(TeamColor color) throws Exception {
        int row = homeRow(color);
        int gameID = saveGame(castlingGame(color));

        ChessGame restored = reload(gameID);

        assertTrue(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, 3)));
        assertTrue(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, 7)));
        restored.makeMove(castle(row, 7));
        updateGame(gameID, restored);
        ChessGame castled = reload(gameID);
        assertEquals(restored.getBoard(), castled.getBoard());
        assertEquals(new ChessPiece(color, PieceType.KING), castled.getBoard().getPiece(new ChessPosition(row, 7)));
        assertEquals(new ChessPiece(color, PieceType.ROOK), castled.getBoard().getPiece(new ChessPosition(row, 6)));
        assertNull(castled.getBoard().getPiece(new ChessPosition(row, 5)));
        assertNull(castled.getBoard().getPiece(new ChessPosition(row, 8)));
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void kingMovementStillPreventsCastlingAfterReload(TeamColor color) throws Exception {
        ChessGame game = castlingGame(color);
        ChessBoard originalBoard = new ChessBoard(game.getBoard());
        int row = homeRow(color);
        int opponentRow = 9 - row;
        move(game, row, 5, row, 6);
        move(game, opponentRow, 5, opponentRow, 6);
        move(game, row, 6, row, 5);
        move(game, opponentRow, 6, opponentRow, 5);

        ChessGame restored = reload(saveGame(game));

        assertEquals(originalBoard, restored.getBoard());
        assertFalse(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, 3)));
        assertFalse(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, 7)));
        assertThrows(InvalidMoveException.class, () -> restored.makeMove(castle(row, 7)));
    }

    @ParameterizedTest
    @CsvSource({"WHITE, 1", "WHITE, 8", "BLACK, 1", "BLACK, 8"})
    public void rookMovementOnlyRemovesItsOwnCastlingRightAfterReload(TeamColor color, int rookColumn) throws Exception {
        ChessGame game = castlingGame(color);
        ChessBoard originalBoard = new ChessBoard(game.getBoard());
        int row = homeRow(color);
        int nextRow = color == TeamColor.WHITE ? 2 : 7;
        int opponentRow = 9 - row;
        move(game, row, rookColumn, nextRow, rookColumn);
        move(game, opponentRow, 5, opponentRow, 6);
        move(game, nextRow, rookColumn, row, rookColumn);
        move(game, opponentRow, 6, opponentRow, 5);

        ChessGame restored = reload(saveGame(game));

        int forbiddenColumn = rookColumn == 1 ? 3 : 7;
        int allowedColumn = rookColumn == 1 ? 7 : 3;
        assertEquals(originalBoard, restored.getBoard());
        assertFalse(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, forbiddenColumn)));
        assertTrue(restored.validMoves(new ChessPosition(row, 5)).contains(castle(row, allowedColumn)));
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void enPassantRemainsAvailableAndCapturedPawnStaysRemoved(TeamColor color) throws Exception {
        int gameID = saveGame(enPassantGame(color));
        ChessMove capture = enPassantMove(color);
        ChessGame restored = reload(gameID);

        assertTrue(restored.validMoves(capture.getStartPosition()).contains(capture));
        restored.makeMove(capture);
        updateGame(gameID, restored);
        ChessGame captured = reload(gameID);

        assertEquals(restored.getBoard(), captured.getBoard());
        assertEquals(new ChessPiece(color, PieceType.PAWN), captured.getBoard().getPiece(capture.getEndPosition()));
        assertNull(captured.getBoard().getPiece(capture.getStartPosition()));
        assertNull(captured.getBoard().getPiece(new ChessPosition(capture.getStartPosition().getRow(), 4)));
        assertEquals(restored.getTeamTurn(), captured.getTeamTurn());
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    public void expiredEnPassantWindowStaysExpiredAfterReload(TeamColor color) throws Exception {
        ChessGame game = enPassantGame(color);
        int row = homeRow(color);
        move(game, row, 5, row, 6);
        move(game, 9 - row, 5, 9 - row, 6);

        ChessGame restored = reload(saveGame(game));
        ChessMove capture = enPassantMove(color);

        assertEquals(game.getBoard(), restored.getBoard());
        assertFalse(restored.validMoves(capture.getStartPosition()).contains(capture));
        assertThrows(InvalidMoveException.class, () -> restored.makeMove(capture));
    }

    @ParameterizedTest
    @MethodSource("promotionCases")
    public void promotionChoiceAndTurnPersistAfterUpdate(TeamColor color, PieceType promotion) throws Exception {
        ChessBoard board = new ChessBoard();
        place(board, TeamColor.WHITE, PieceType.KING, 1, 8);
        place(board, TeamColor.BLACK, PieceType.KING, 8, 8);
        int pawnRow = color == TeamColor.WHITE ? 7 : 2;
        int promotionRow = color == TeamColor.WHITE ? 8 : 1;
        place(board, color, PieceType.PAWN, pawnRow, 1);
        int gameID = saveGame(gameWithBoard(board, color));
        ChessGame restored = reload(gameID);

        restored.makeMove(new ChessMove(new ChessPosition(pawnRow, 1), new ChessPosition(promotionRow, 1), promotion));
        updateGame(gameID, restored);

        ChessGame promoted = reload(gameID);
        TeamColor opponent = color == TeamColor.WHITE ? TeamColor.BLACK : TeamColor.WHITE;
        assertEquals(new ChessPiece(color, promotion), promoted.getBoard().getPiece(new ChessPosition(promotionRow, 1)));
        assertNull(promoted.getBoard().getPiece(new ChessPosition(pawnRow, 1)));
        assertEquals(opponent, promoted.getTeamTurn());
        assertEquals(restored.isInCheck(opponent), promoted.isInCheck(opponent));
    }

    static Stream<Arguments> promotionCases() {
        return Stream.of(TeamColor.values()).flatMap(color ->
                Stream.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                        .map(piece -> Arguments.of(color, piece)));
    }

    @Test
    public void checkmateIsPreservedAfterReload() throws Exception {
        ChessGame game = new ChessGame();
        move(game, 2, 6, 3, 6);
        move(game, 7, 5, 5, 5);
        move(game, 2, 7, 4, 7);
        move(game, 8, 4, 4, 8);

        ChessGame restored = reload(saveGame(game));

        assertEquals(TeamColor.WHITE, restored.getTeamTurn());
        assertTrue(restored.isInCheck(TeamColor.WHITE));
        assertTrue(restored.isInCheckmate(TeamColor.WHITE));
        assertFalse(restored.isInStalemate(TeamColor.WHITE));
    }

    @Test
    public void stalemateIsPreservedAfterReload() throws Exception {
        ChessBoard board = new ChessBoard();
        place(board, TeamColor.WHITE, PieceType.KING, 1, 8);
        place(board, TeamColor.BLACK, PieceType.KING, 2, 6);
        place(board, TeamColor.BLACK, PieceType.QUEEN, 3, 7);

        ChessGame restored = reload(saveGame(gameWithBoard(board, TeamColor.WHITE)));

        assertFalse(restored.isInCheck(TeamColor.WHITE));
        assertTrue(restored.isInStalemate(TeamColor.WHITE));
        assertFalse(restored.isInCheckmate(TeamColor.WHITE));
    }

    @Test
    public void assigningAPlayerPreservesSpecialMoveHistory() throws Exception {
        int gameID = saveGame(enPassantGame(TeamColor.WHITE));
        AuthDAO authDAO = new SqlAuthDAO();
        authDAO.createAuth(new AuthData("alice-token", "alice"));
        GameService gameService = new GameService(gameDAO, authDAO);

        gameService.joinGame(new JoinGameRequest("WHITE", gameID), "alice-token");

        GameData stored = new SqlGameDAO().getGame(gameID);
        ChessMove capture = enPassantMove(TeamColor.WHITE);
        assertEquals("alice", stored.whiteUsername());
        assertNull(stored.blackUsername());
        assertTrue(stored.game().validMoves(capture.getStartPosition()).contains(capture));
    }

    private ChessGame castlingGame(TeamColor color) {
        ChessBoard board = new ChessBoard();
        int row = homeRow(color);
        TeamColor opponent = color == TeamColor.WHITE ? TeamColor.BLACK : TeamColor.WHITE;
        place(board, color, PieceType.KING, row, 5);
        place(board, color, PieceType.ROOK, row, 1);
        place(board, color, PieceType.ROOK, row, 8);
        place(board, opponent, PieceType.KING, 9 - row, 5);
        return gameWithBoard(board, color);
    }

    private ChessGame enPassantGame(TeamColor color) throws InvalidMoveException {
        ChessBoard board = new ChessBoard();
        place(board, TeamColor.WHITE, PieceType.KING, 1, 5);
        place(board, TeamColor.BLACK, PieceType.KING, 8, 5);
        TeamColor opponent = color == TeamColor.WHITE ? TeamColor.BLACK : TeamColor.WHITE;
        int capturingRow = color == TeamColor.WHITE ? 5 : 4;
        int opponentPawnRow = color == TeamColor.WHITE ? 7 : 2;
        place(board, color, PieceType.PAWN, capturingRow, 5);
        place(board, opponent, PieceType.PAWN, opponentPawnRow, 4);
        ChessGame game = gameWithBoard(board, opponent);
        move(game, opponentPawnRow, 4, capturingRow, 4);
        return game;
    }

    private ChessMove enPassantMove(TeamColor color) {
        int row = color == TeamColor.WHITE ? 5 : 4;
        int destinationRow = color == TeamColor.WHITE ? 6 : 3;
        return new ChessMove(new ChessPosition(row, 5), new ChessPosition(destinationRow, 4), null);
    }

    private ChessMove castle(int row, int column) {
        return new ChessMove(new ChessPosition(row, 5), new ChessPosition(row, column), null);
    }

    private int homeRow(TeamColor color) {
        return color == TeamColor.WHITE ? 1 : 8;
    }

    private ChessGame gameWithBoard(ChessBoard board, TeamColor turn) {
        ChessGame game = new ChessGame();
        game.setBoard(board);
        game.setTeamTurn(turn);
        return game;
    }

    private void place(ChessBoard board, TeamColor color, PieceType type, int row, int column) {
        board.addPiece(new ChessPosition(row, column), new ChessPiece(color, type));
    }

    private void move(ChessGame game, int startRow, int startColumn, int endRow, int endColumn) throws InvalidMoveException {
        game.makeMove(new ChessMove(new ChessPosition(startRow, startColumn), new ChessPosition(endRow, endColumn), null));
    }

    private int saveGame(ChessGame game) throws DataAccessException {
        return gameDAO.createGame(new GameData(0, null, null, "Persisted game", game));
    }

    private ChessGame reload(int gameID) throws DataAccessException {
        return new SqlGameDAO().getGame(gameID).game();
    }

    private void updateGame(int gameID, ChessGame game) throws DataAccessException {
        GameData current = gameDAO.getGame(gameID);
        gameDAO.updateGame(new GameData(gameID, current.whiteUsername(), current.blackUsername(), current.gameName(), game));
    }
}
