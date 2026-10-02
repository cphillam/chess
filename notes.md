CS 240 Chess Notes

Phase 0:
Had to set up the board and get all the piece moves working.
Remember that chess coordinates are 1-8 but 2d arrays in java are 0-7 so subtract 1 for array indices.
Added equals and hashcode to ChessPosition, ChessMove, ChessPiece, and ChessBoard so the tests compare objects properly.

Piece move notes:
- Knight moves in L shapes (8 possible jumps).
- Rook goes straight in 4 directions until it hits the edge or another piece.
- Bishop goes diagonal until blocked.
- Queen is rook and bishop combined.
- King is just one step in any direction.
- Pawn was the trickiest one: moves forward 1, can move 2 from starting rank if both squares are clear, only captures diagonally, and promotes to queen, rook, bishop, or knight when hitting the opposite side.

All 64 tests pass.

Phase 1:
Finished implementing ChessGame for overall gameplay rules.
Game starts with white's turn and a standard board setup.
Had to write a deep copy for ChessBoard so simulating moves doesn't mess up the actual board.

Game logic notes:
- isInCheck: loops over the board, finds the king for the color, and checks if any opponent piece has a move targeting that square.
- validMoves: gets all moves from pieceMoves, then simulates each one on a board clone to make sure your king isn't in check after moving.
- makeMove: checks if it's the right team's turn, makes sure the move is in validMoves, moves the piece (handles pawn promotion if there is one), and swaps whose turn it is. Throws InvalidMoveException if anything is illegal.
- isInCheckmate: returns true if the team is in check and has 0 valid moves anywhere on the board.
- isInStalemate: returns true if the team is NOT in check but still has 0 valid moves anywhere on the board.

All 107 tests pass (full game, game status, make move, valid moves, and all phase 0 tests).
