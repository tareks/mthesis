
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
	initiatorNode = false;
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

	Logger.msg("Initialized new game with ID: " + gameID);
	
    }

   /**    
    *	Start and keep track of a game 
    */
    public void run() {
	Move move = null;
	ConsoleUI ui=  new ConsoleUI();

	System.out.println("Running game...");

	// To avoid new game race conditions, we only start a game if we are
	// set as an initiator node.

	if (initiatorNode) {
	    // advertise a new game and wait for a request
	    System.out.println("Advertising game: " + gameID);
	    tttState = network.putGame(tttState);     
	    // get the new ID that's been updated based on received Interest
	    gameID = tttState.gameId();
	    myTurn = true; 

	} else {
	    // All other nodes, ask for a new game with a certain ID
	    tttState  = network.getGame(gameID);
	    myTurn = false;
	}

	inGame = true;
	System.out.println("In a game: " + gameID);
	
	moveCount=1;
	while (!tttState.isGameOver()) {
	    if (myTurn) {
		ui.printBoard(tttState);
		move = selectMove(moveCount);
		network.putMove(gameID, moveCount, move);
		
		// don't ask for next move until we've sent ours
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
		System.out.println("Illegal move! - Cheating?");
		// CAVEAT: If we GET an illegal move, game is over because we can't expire previous cached move CO - no longer the case, moves will increment for re-requests and continue until a move is acceptable. Cannot correlate move # to player in this case.
		//moveCount--; 
		myTurn=!myTurn; // ignore bad move, and continue without switching turns
	    }
	    
	    moveCount++;
	}
	
	ui.printBoard(tttState);
	System.out.println("Game ended! - Winner is: " + tttState.getWinner());
	
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
	
	// send a terminate request
	if (initiatorNode)
	    tttState = network.getEndGame(gameID);
	else network.putEndGame(gameID);
	
		
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
	//	ConsoleUI ui=  new ConsoleUI();
	int x,y;
	
	//	ui.printBoard(tttState);
	
	boolean validMove = false;
	do {
	    System.out.println("Enter X and Y coordinates (1-3). Ex: 2 2 (col row). You are player: " + tttState.getNextPlayer());
	    /* - CONSOLEUI CODE ONLY
	      x = stdin.nextInt() - 1;
	      y = stdin.nextInt() - 1;
	    */
	    /*	    
	    // AI Selection with brute force
	    // Generate a random int between 1-3 and either brute force or verify
	    Random r = new Random();
	    x = r.nextInt(3); 
	    y = r.nextInt(3);
	    System.out.println("AI chose: " + (x+1) + "," + (y+1));
	    */
	    
	    // Hardcoded selection - need 3 moves to complete each game regardless of who starts. Array is ordered based on moveNum: odd, even, odd, even, etc.
	    // Whoever starts will always win
	    int moves[][] = { {0,0},{0,2},{1,0},{1,2},{2,0} };
	    x = moves[moveNum-1][0];
	    y = moves[moveNum-1][1];
	    
	    System.out.println("Predef move: " + (x+1) + "," + (y+1));
	    
	    if (! tttState.validMove(x,y)) {
		System.out.println("Invalid move: " + x + "," +y);
		System.out.println("Try again.");
	    }
	    else validMove=true;
	    
	} while (!validMove);
	
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

    public void setInitiator(boolean flag) {
	initiatorNode = flag;
    }
    
    private Network network;
    private TicTacToe tttState; // Current game state
    private int gameID; 
    private boolean myTurn;
    private int moveCount;
    private Player myProfile;
    private Player opponent;
    private boolean inGame;
    private boolean initiatorNode;

    /** Timeout between attempts when looking for players. */
    private final int playerSearchTimeout = 5000;
    
    private ArrayList<Player> playerList; // List of players that we know about 
    private boolean isGameOver; // Keeps track of our main execution thread
}
