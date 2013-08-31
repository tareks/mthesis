
package dtgp.net;

import dtgp.app.tictactoe.Move;
import dtgp.app.tictactoe.Player;
import dtgp.app.tictactoe.TicTacToe;

public interface Network {
    /** Retrieve move `moveNum' from the network. Blocks until it is available.  */
    
    /** Initiate a connection */
    public void openConnection();
    
    /** Close the current connection */
    public void closeConnection();
    
    /** Send a request for the next move by other player */
    public Move getMove(int gameID, int moveNum);
    
    /** A function that makes a Move available to the other player */
    public void putMove(int gameID, int moveNum, Move move);

    /** Publish a player's profile for a game. */
    public void putProfile(int gameID, Player p);

    /** Request a player's profile for a new game */
    public Player getProfile(int gameID);
    
    /** Ask for a new game instance. */
    public TicTacToe getGame(int gameID);

    /** Send a new game instance. */
    public TicTacToe putGame(TicTacToe game);

    /** Retrieve ID for a certain node. */
    public String getMyNodeId();

    /** Retrieve ID for a certain node. */
    public String getRemoteNodeId();

    /** Request that the game be terminated. */
    public TicTacToe getEndGame(int gameID);

    /** Publish final game state object for termination */
    public void putEndGame(int gameID);

}

