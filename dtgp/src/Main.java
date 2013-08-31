
package dtgp;

import dtgp.app.Game;
import dtgp.util.*;

class Main {
    
    public static void usage() {
	System.err.println("Start a delay tolerant version of TicTacToe.");
	System.err.println("\t -d: Enable debugging messages.");
	System.err.println("\t -s: Enable Haggle testbed Synchronization signals.");
	System.err.println("\t -u [URI]: Specify ccnx URI prefix to use in ccnx:// format.");
    }


    public static void main(String[] args) {

	boolean isInitiatorNode=false; // describes if this node starts games

	// parse args
	for (int i=0; i < args.length; i++) {
	    if (args[i].equals("-d"))
		Logger.enable();
	    
	    if (args[i].equals("-I"))
		isInitiatorNode = true;
	    
	    if (args[i].equals("-h")) {
		usage();
		System.exit(1);
	    }
	}

	// configure logging based on option
	Logger.setLevel();
	
	Game ttt = new Game();
	
	ttt.init(); 
	// Wait for testbed sync signal before we do anything? - no longer needed as the game should be able to pickup from whatever state it is in on load

	ttt.setInitiator(isInitiatorNode);
	ttt.run();
	
	ttt.end();
	
	Logger.msg("Exiting..");
	System.exit(0);
    }
}
