
package dtgp.app;

import dtgp.util.*;
import dtgp.app.DTApp;
import dtgp.app.tictactoe.TicTacToe;
import dtgp.app.tictactoe.IllegalMoveException;
import dtgp.app.tictactoe.Player;
import dtgp.app.tictactoe.Move;
import dtgp.app.tictactoe.ConsoleUI;
import dtgp.net.Network;
import dtgp.net.CCNxNetwork;

import java.util.*;
import java.lang.Math;
import java.util.Scanner;

public class Game implements DTApp {

    public Game() {
	playerList = new ArrayList<Player>();
	gameID = -1;
	myTurn = false;
	inGame = false;
    }

    public void init() {
	Logger.msg("Initializing game...");
	
	isGameOver = false;
	inGame = false;
	
	// generate a game id and create a new game instance 
	gameID = Math.abs(new Random().nextInt());

	try {
	    network = new CCNxNetwork();
	    network.openConnection();
	    
	    tttState = new TicTacToe(gameID);
		
	    myProfile = new Player(network.getMyNodeId()); // me
	    Logger.msg("My player id: " + myProfile.getId());
	    opponent = new Player(); // other player
	    Logger.msg("Opponent's id: " + opponent.getId());	   
	}
	
	catch (Exception e) {
	    Logger.msg("TTTMain init failed: ");
	    e.printStackTrace();
	    System.exit(1);
	}

	Logger.msg("Created new game with ID: " + gameID);
	
    }

   /**    
    *	Start and keep track of a game 
    */
    public void run() {
	Move move = null;

	Logger.msg("Running game...");

	// advertise a new game if we get requests for one
	Logger.msg("Advertising game: " + gameID);
	network.putGame(tttState); 
	
	// in the meantime, ask for a new game with a certain ID
	tttState  = network.getGame(gameID);
	if (gameID == tttState.gameId()) {
	    // we got this object off the network
	    myTurn = false;
	} else {
	    // this is our object with gameID updated to match Interest
	    // we play first as its our game object, update our local ID copy
	    myTurn = true;
	    gameID = tttState.gameId();
	}

	inGame = true;
	Logger.msg("Found a game: " + gameID);
	
	moveCount=1;
	while (!tttState.isGameOver()) {
	    if (myTurn) {
		move = selectMove(moveCount);
		network.putMove(gameID, moveCount, move);
		
		// dont ask for next move until we've sent ours?

		myTurn = false;
	    }
	    else {
		move = network.getMove(gameID, moveCount);
		    
		myTurn = true;
	    }
	    
	    try {
		tttState.applyMove(tttState.getNextPlayer(),move);
	    }
	    catch (IllegalMoveException e) {
		Logger.msg("Illegal move! - Cheating?");
		moveCount--; myTurn=!myTurn;
	    }

	    moveCount++;
	}

	// Game ended, wait until other player knows they lost
	if (!myTurn) {
	    try {
		// FIXME: What if latency is more than 5s?
		Thread.sleep(5000);
	    }
	    catch (Exception e) {
	    }
	}

	Logger.msg("Game ended! - Winner is: " + tttState.getWinner());

	isGameOver = true;
    }
    
    public boolean isRunning() {
	if (!isGameOver)
	    return true;
	else
	    return false;
    }
    
    // Called when we reach a final state
    public void end() {
	Logger.msg("Ending TTTMain...");
	
	Logger.msg("Shutting down...");	
	// Kill connection with ccnd
	network.closeConnection();
    }
    
    private void matchPlayer() {
	
    }
    
    /** 
     * This is where we choose our next move.
     * 
     * Can be done either through UI or AI.
     */
    private Move selectMove(int moveNum) {
	Scanner stdin = new Scanner(System.in);
	ConsoleUI ui=  new ConsoleUI();
	int x,y;
	
	ui.printBoard(tttState);
	
	boolean validMove = false;
	do {
	    System.out.println("Enter X and Y coordinates (1-3). Ex: 1 3, or 2 2.");
	    x = stdin.nextInt() - 1;
	    y = stdin.nextInt() - 1;

	    if (! tttState.validMove(x,y)) {
		System.out.println("Invalid move: " + x + "," +y);
		System.out.println("Try again.");
	    }
	    else validMove=true;
	    
	} while (!validMove);
	
	ui.printBoard(tttState);

	return new Move(x,y);
    }
    
    // search for player in our known player list
    private void lookupPlayer() {
	
    }
    
    // load the current gamestate from storage, resuming app
    public void loadState() {
	
    }
    
    // save current gamestate to storage in case the app exists
    public void saveState() {
	
    }
    
    private Network network;
    private TicTacToe tttState; // Current game state
    private int gameID; 
    private boolean myTurn;
    private int moveCount;
    private Player myProfile;
    private Player opponent;
    private boolean inGame;

    /** Timeout between attempts when looking for players. */
    private final int playerSearchTimeout = 5000;
    
    private ArrayList<Player> playerList; // List of players that we know about 
    private boolean isGameOver; // Keeps track of our main execution thread
}
