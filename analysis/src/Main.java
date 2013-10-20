package analyser;

import analyser.*;
import analyser.util.Logger;

class Main {
    
    
    public static void usage() {
	System.err.println("Run analysis on ccnd log files.");
	System.err.println("\t -d: Enable debugging messages.");
    }
    
    public static void main(String[] args) {
	
	for (int i=0; i < args.length; i++) {
	    if (args[i].equals("-d"))
		Logger.enable();
	    else
		Logger.disable();

	    if (args[i].equals("-h")) {
		usage();
		System.exit(0);
	    }
	    
	}
	
	CCNdAnalyser a = new CCNdAnalyser();

	if ( a.init() ) {
	    a.gather();
	    a.analyse();
	    a.display();
	}
	
	Logger.msg("Exiting..");
	System.exit(0);
    }
}
