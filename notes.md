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
