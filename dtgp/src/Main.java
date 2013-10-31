
package dtgp;

import dtgp.app.Game;
import dtgp.util.*;

class Main {
    
    public static void usage() {
	System.err.println("Start a delay tolerant version of TicTacToe.");
	System.err.println("\t -d: Enable debugging messages.");
	System.err.println("\t -I: Act as an Initiator Node (sends Interets looking to start a game.");
	System.err.println("\t -H: Act as a Host Node (listens for Interets and hosts a game.");
	System.err.println("\t -s <milliseconds>: Specify the amount of sleep time before the application communicates on the network.");
	System.err.println("\t -h: Prints out this message.");
    }


    public static void main(String[] args) {

	boolean isInitiatorNode=false; // describes if this node sends interests for games
	boolean isHostNode=false; // describes if this node hosts games (accepts interests)
	boolean delayedStart=false; // specifies whether there is a delay of sleepTime before starting
	int sleepTime=0; // specifies the amount of time to sleep before we start

	// parse args
	for (int i=0; i < args.length; i++) {
	    if (args[i].equals("-d"))
		Logger.enable();
	    
	    if (args[i].equals("-I"))
		isInitiatorNode = true;
	    
	    if (args[i].equals("-H"))
		isHostNode = true;
	    
	    if (args[i].equals("-s")) {
		delayedStart = true;
		sleepTime = Integer.parseInt(args[i+1]);
	    }
	    
	    if (args[i].equals("-h")) {
		usage();
		System.exit(1);
	    }
	}

	// configure logging based on option
	Logger.setLevel();
	
	if (delayedStart) {
	    try {
		Logger.msg("Sleeping for : " + sleepTime);
		Thread.sleep(sleepTime);
	    }

	    catch (Exception e) {
		System.err.println("Failed to delay application start. Exiting. ");
	    }
	}

	Game ttt = new Game();
	
	ttt.init(); 
	// Wait for testbed sync signal before we do anything? - no longer needed as the game should be able to pickup from whatever state it is in on load

	ttt.setInitiator(isInitiatorNode);
	ttt.setHost(isHostNode);
	ttt.run();
	
	ttt.end();
	
	Logger.msg("Exiting..");
	System.exit(0);
    }
}
