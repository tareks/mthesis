
package dtgp.app.tictactoe;

import java.io.Serializable;

public class TicTacToe implements Serializable {

    static final long serialVersionUID = 8681269155476863974L;

    /** Tiles are either empty (_), O, or X. */
    public enum Tile { _, O, X };
	
    /** The current board state. */
    protected Tile[][] board = new Tile[3][3];
	
    /** The player who is next to make a move. */
    protected Tile nextPlayer = Tile.X;
	
    /** Unique identifier for this game object. */
    protected int gameID;

    /** Keep track of which move we're on since game started. */
    private int moveCount;
    
    public TicTacToe() {
	for (int i=0;i<3;i++) {
	    for (int j=0;j<3;j++) {
		board[i][j] = Tile._;
	    }
	}
    }

    public TicTacToe(int id) {
	for (int i=0;i<3;i++) {
	    for (int j=0;j<3;j++) {
		board[i][j] = Tile._;
	    }
	}
	gameID = id;
	moveCount = 0;
    }
	
    public boolean validMove(int x, int y) {
	
	return true;
    }

    public void applyMove(Tile player, Move m) throws IllegalMoveException {
	
	applyMove(player, m.getX(), m.getY());
    }
    
    public void applyMove(Tile player, int x, int y) throws IllegalMoveException {
	if (player != nextPlayer) {
	    throw new IllegalMoveException("Next player is " + nextPlayer + ", but got move from " + player + ".");
	}
	if (x < 0 || x > 2 || y < 0 || y > 2) {
	    throw new IllegalMoveException("Out of bounds move.");
	}
	if (board[x][y] != Tile._) {
	    throw new IllegalMoveException("Tile not empty");
	}
		
	board[x][y] = player;
	nextPlayer = player == Tile.X ? Tile.O : Tile.X;
    }
	
    /** Determine the winner of a game.Will return 
     * 	 * @return X or O, or _ if the game is a draw or not over.
     * 	 	 */
    public Tile getWinner() {
	int i = 0;
		
	/* Check rows and columns. */
	for (i=0;i<3;i++) {
	    if (board[i][0] != Tile._ && board[i][0] == board[i][1] && board[i][0] == board[i][2]) {
		return board[i][0];
	    }
	    if (board[0][i] != Tile._ && board[0][i] == board[1][i] && board[0][i] == board[2][i]) {
		return board[0][i];
	    }
	}
		
	/* Check diagonals. */
	if (board[0][0] != Tile._ && board[0][0] == board[1][1] && board[0][0] == board[2][2]) {
	    return board[0][0];
	}
	if (board[2][0] != Tile._ && board[2][0] == board[1][1] && board[2][0] == board[0][2]) {
	    return board[0][0];
	}
		
	return Tile._;
    }
	
    /** Returns if a game is over or not.
     * 	 * @todo Early draw detection.
     * 	 	 */
    public boolean isGameOver() {
	if (getWinner() != Tile._) {
	    return true;
	}
		
	for (int i=0;i<3;i++) {
	    for (int j=0;j<3;j++) {
		if (board[i][j] == Tile._) {
		    return false;
		}
	    }
	}
	
	return true;
    }
	
    public Tile getNextPlayer() {
	return nextPlayer;
    }
	
    public Tile[][] getBoard() {
	return board;
    }

    public int getMoveNum() {
	return moveCount;
    }

    public int gameId() {
	return gameID;
    }
}

