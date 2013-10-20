package analyser;


import analyser.*;
import analyser.Message.*;

import analyser.util.Logger;

import java.io.*;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.Comparator;

// Filesystem manipulation
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;

// String manipulation
import org.apache.commons.lang3.StringUtils;


class CCNdAnalyser {
    
    // Constants for filenames and paths
    private String workDirStr = "."; // assume processing from current dir
    private static final String expDirRegExp = "experiment[0-9]+-[0-9]+";
    private static final String ItrDirRegExp = "[0-9]+";
    private static final String nodeDirRegExp = "node-[0-9]+";
    private static final String execLogFileRegExp = "execution\\..*\\.log";

    // Regular Expressions used for matching data from ccnd.log
    private static final String dtgpNewGamePrefixRegEx = ".*interest_from.*ccnx:/uu/core/games/ttt/new.*";  // Finds the initial Interests from origin and new games
    private static final String dtgpEndGamePrefixRegEx = ".*content_to.*ccnx:/uu/core/games/ttt/fin/.*";
    private static final String dtgpGamePrefixRegEx = ".*ccnx:/uu/core/games/ttt/.*";
    
    private static final String ccndLogFile = "ccnd.log";
    private static final String appLogFile = "app.config";

    ArrayList<Experiment> experiments;
    
    CCNdAnalyser() {
	
	experiments = new ArrayList<Experiment>();
    }
    
    private boolean verifyAppFilesExist(Experiment e) {
	File f;
	String path;
	
	Logger.msg("Checking app.config exists..");
	path = new String(e.getPath() + "/" + appLogFile);
	f = new File(path);
	if (! f.exists())
	    return false;
	
	Logger.msg("Checking ccnd.log exists on all nodes..");
	for (Iteration i: e.getIterations()) {
	    for (Node n: i.getNodes()) {
		path = new String(n.getPath() + "/" + ccndLogFile);
		
		f = new File(path);
		if (! f.exists() ) {
		    Logger.msg("File not found: " + path);
		    return false;
		}
	    }
	}
	return true;
    }

    /*
     *  Look through the directories for the files we need and store
     *  paths as well as names.
     */
    public boolean init() {
	
	File workDir;
	
	// Find the experiment directories
	workDir = new File(workDirStr);
	String[] expDirLst = workDir.list(new RegexFileFilter(expDirRegExp));
	Logger.msg("Number of experiment directories found: " + expDirLst.length);

	for (int i=0; i < expDirLst.length; i++) {
	    experiments.add(new Experiment(expDirLst[i]));
	    experiments.get(i).setPath(expDirLst[i]);
	}
	
	// Then find the iterations within each    
	for (Experiment e: experiments) {
	    Logger.msg("Scanning through: " + e.getName());
	    workDir = new File(workDirStr+"/"+e.getName());

	    String[] ItrDirLst = workDir.list(new RegexFileFilter(ItrDirRegExp));
	    Logger.msg("Found Iterations: " + ItrDirLst.length);
	    e.setNumIterations(ItrDirLst.length);

	    // Store iteration number in case they're not consecutive
	    for (int i=0; i < ItrDirLst.length; i++) {
		e.addIteration(ItrDirLst[i]);
		e.getIteration(i).setPath(ItrDirLst[i]);

		// Then count the nodes within each iteration
		workDir = new File(workDirStr+"/"+e.getName()+"/"+e.getIteration(i).getNum()+"/");
		String[] nodeLst = workDir.list(new RegexFileFilter(nodeDirRegExp));
		Logger.msg("Found Nodes: " + nodeLst.length);
		e.getIteration(i).setNumNodes(nodeLst.length);
	        
		// Store the Nodes counting from 1
		for (int j=0; j < nodeLst.length; j++) {
		    e.getIteration(i).addNode("node-"+(j+1));
		    e.getIteration(i).getNode(j).setPath(workDir.toString()+"/node-"+(j+1));
		}

		// Also store the name of the execution log file
		String[] execLogFileLst = workDir.list(new RegexFileFilter(execLogFileRegExp));
		for (int j=0; j < execLogFileLst.length; j++) 
		    e.getIteration(i).setExecLogFileName(execLogFileLst[j]);
		Logger.msg ("Execution Log File: " + e.getIteration(i).getExecLogFileName());
		
		
	    }
	}

	// Now do some basic checks to verify the files we need are there
	for (Experiment e: experiments) {
	    Logger.msg("Checking app config and log files exist for: " + e.getName());
	    if (! verifyAppFilesExist(e)) {
		Logger.msg("Some important files were missing for " + e.getName()  + ". Cannot continue, exiting.");
		return false;
	    }
	}
	
	// Mark nodes based on their roles based on app.config
	for (Experiment e: experiments) {
	    String path = new String(e.getPath() + "/" + appLogFile);
	    File f = new File(path);
	    Logger.msg("Collecting node type information..");
	    for (Iteration i: e.getIterations()) {
		
		ArrayList<String> inodeLst = matchLines(f,1,";","-I");
		ArrayList<String> hnodeLst = matchLines(f,1,";","-H");
		
		for (Node n: i.getNodes()) {
		    if (hnodeLst.contains(n.getName())) {
			// Host node
			n.setType(Node.Type.Host);
			Logger.msg("Host Node: " + n.getName());
		    } else if ( inodeLst.contains(n.getName())) {
			// Initiator node
			n.setType(Node.Type.Initiator);
			Logger.msg("Initiator Node: " + n.getName());
		    }
		    else {
			// Relay node (not mentioned in file)
			n.setType(Node.Type.Relay);
			Logger.msg("Relay Node: " + n.getName());
		    }
		}

		
		// Now get when the start and end times of the iteration
		path = new String(e.getPath() + "/" + i.getPath() + "/" + i.getExecLogFileName());
		f = new File(path);
		
		ArrayList<String> startTime = matchLines(f,2,":","start time");
		i.setStartTime(Long.parseLong(startTime.get(0)));
		Logger.msg("StartTime: " + i.getStartTime());
		
		ArrayList<String> endTime = matchLines(f,2,":","end time");
		i.setEndTime(Long.parseLong(endTime.get(0)));
		Logger.msg("EndTime: " + i.getEndTime());
		
	    }
	}

	return true;
    }
    
    public void gather() {
	
	String path;
	File f;
	
	try {
	    for (Experiment e: experiments) {
		for (Iteration i: e.getIterations()) {
		    
		    // Find Game object(s) for every iteration - this has to be done separately
		    // because we don't guarantee node search order
		    for (Node n: i.getNodes()) {
			// Check Initiator nodes ccnd.log for initial Interests
			if (n.getType() == Node.Type.Initiator) { 
			    path = new String(n.getPath() + "/" + ccndLogFile);
			    f = new File(path);
			    
			    Logger.msg ("Looking at " + path);
			    if (!f.exists()) 
				throw new Exception("File not found: " + f.toString());
			    
			    ArrayList<String> intLst = matchLinesEx(f,6," ",dtgpNewGamePrefixRegEx);
			    for (String s: intLst) {
				int id = Integer.parseInt(s.substring(s.lastIndexOf("/")+1));
				if (! i.gameIdExists(id)) {
				    i.addGame(id);
				    Logger.msg("Making note of Game: " + id);
				}
			    }
			}
		    }
		    
		    for (Node n: i.getNodes()) {
			// Check Host Nodes for games that ended
			if (n.getType() == Node.Type.Host) {
			    path = new String(n.getPath() + "/" + ccndLogFile);
			    f = new File(path);
			    
			    Logger.msg ("Looking at " + path);
			    if (!f.exists()) 
				throw new Exception("File not found: " + f.toString());
			    
			    // Iterate over games 
			    for (Game g: i.getGames()) {
				// Check if each game ended and mark it as such			    
				ArrayList<String> intLst = matchLinesEx(f,6," ",dtgpEndGamePrefixRegEx + g.getId()  +".*");
				if (! intLst.isEmpty()) {
				    g.markEnded();
				    Logger.msg("Marking Game Ended: " + g.getId());
				}
			    }
			}
			
		    }

		    // Now go through each game and record message information for this game
		    for (Game g: i.getGames()) {

			for (Node n: i.getNodes() ) {
			    path = new String(n.getPath() + "/" + ccndLogFile);
			    f = new File(path);
			    
			    Logger.msg ("Looking at " + path);
			    if (!f.exists()) 
				throw new Exception("File not found: " + f.toString());
			    
			    int[] fieldArray =  {0,3,4,5}; // Start from 0
			    HashMap<String, ArrayList<String>> messageLst = 
				matchLinesMultiEx(f, fieldArray, 0, " ",
						  dtgpGamePrefixRegEx+g.getId()+".*");
			    
			    // Create a new Message for each entry in that hashmap
			    Set<String> keys = messageLst.keySet();
			    
			    for (String k: keys) {
				ArrayList<String> values = messageLst.get(k);
				
				Message m = new Message(
							values.get(0), //timestamp
							values.get(1), //type
							values.get(2), //face
							values.get(3) //string
							);
				// Associate message with this node
				n.addMessage(m);
			    }
			    
		    

			    
			}
		    }
		    
		}
		
		// Start looking for data we need and save it to Game object
	    }
	    
	}
	catch (Exception e) {
	    Logger.msg("Some problem occurred..");
	    e.printStackTrace();
	    return;
	}
    }
    
    public void analyse() {
	
	GameData data; 
	
	for (Experiment e: experiments) {
	    for (Iteration i: e.getIterations()) {
		for (Game g: i.getGames()) {
		    
		    // Initialize with data we already have
		    data = new GameData(g.getId(), g.hasEnded());
		    
		    Logger.msg("Creating list of unique game messages..");
		    for (Node n: i.getNodes()) {
			data.filterMessages(n.getMessages());
		    } // Node
		    Logger.msg ("Number of unique game messages found: " + data.getNumGameMessages());

		    // Sort by timestamp
		    Collections.sort(data.getGameMessages(), new Comparator<Message>() {
			    public int compare(Message m1, Message m2) {
				return Long.valueOf(m1.getTimeStamp()).compareTo(Long.valueOf(m2.getTimeStamp()));
			    }			
			});
		    
		    // Iterate through sorted unique messages
		    for (GameMessage gm: data.getGameMessages()) {
			Logger.msg("Unique: " + gm.getMessageType().name() + " " + gm.getMessage() + " " + gm.getTimeStamp() + " face: " + gm.getFace());

			// Count occurences of each game message 
			for (Node n: i.getNodes()) {
			    for (Message nm: n.getMessages()) {
				if (gm.msgMatches(nm)) {
				    // record all occurences on network
				    gm.incNumOccurrences();
				    // record all nodes that saw message
				    gm.addNodeTraversed(n);
				    if (n.getType() == Node.Type.Initiator) {
					// Also record Inode occurences
					gm.incNumInodeOccurrences();
				    } 
				}
			    }
			}
			

			// Classify the unique messages
			switch (gm.getMessageType()) {
			case Interest:
			    data.incNumInterests();
			    break;
			case Response:
			    data.incNumResponses();
			    break;
			default:
			    // Should have none, but ignore anyway
			    break;
			}
		    } // Unique Game Messages

		    // Create inidividual objects so we can store info
		    for (Node n: i.getNodes()) {
			for (Message m: n.getMessages()) {
			    switch (m.getMessageType()) {
			    case Interest:
				data.addInterest(new InterestMessage(m));
				break;
			    case Response:
				data.addResponse(new ResponseMessage(m));
				break;
			    default:
				break;
			    }
			}
			Logger.msg(n.getName() + ": Created Interest and Response messages..");
			
			// Loop over all Interests now 
			// - Count totals for each type
			// - Check if Interests were satisified and when
			// - Count the number of interests per response
			//FIXME: problem is that if we have one node sending more than one interest for the same request, we won't calculate the responeTime properly. To solve this, we need to sort the messages by timestamp, then go through the list one by one and check for an 'interest_expiry' on the originating face for that message based on the original 'interest_from'
			for (InterestMessage interest: data.getInterests()) {
			    data.incNumTotalInterests();
			    ResponseMessage r = interest.findResponse(data.getResponses());
			    if (r != null) {
				// found a response
				interest.setHasResponse();
				interest.setResponseTime(r.getTimeStamp() - interest.getTimeStamp());
			    }
			}
			
			for (ResponseMessage response: data.getResponses()) {
			    data.incNumTotalResponses();
			    int count = response.countInterestMatches(data.getInterests());
			    response.setInterestCount(count);
			}
			

		    } 

		    
		    
		    // For each node, go through every interest_to we have seen and see if there's a matching content_to on the same face. 
		    // Then count the number of matched Interests
		    
		    
		    // TODO Check if every Interest has a matching response - might not be needed - if game ends, then we have at least 1 match and everything else is a retransmission
			
		    // Loop over Initiator Nodes and check the first "interest_to" instance of every unique game message
		    // then find the first (should be only one) corresponding "content_to" instance on the Initiator node
		    // get the timestamps for each and calculate the time difference
		    
		    // Can do the same for every node as well, but no guarantees that their Interests were satisfied
		    
		    
		    i.storeGameData(data);
		} // Game
	    } // Iteration
	} // Experiment	
	
    }
    
    // format and print results
    public void display() {
	System.out.println("Number of Experiments: " + experiments.size());
	for (Experiment e: experiments) {
	    System.out.println("---------- ---------");
	    System.out.println("Experiment: " + e.getName());
	    
	    System.out.println("Number of Iterations: " + e.getNumIterations());
	    for (Iteration  i: e.getIterations()) {
		System.out.println(" *** Iteration #" + i.getNum() + " ***");
		System.out.println("Number of Nodes: " + i.getNumNodes());
		System.out.println("Total number of Games: " + i.getNumGames());
		
		int numGamesEnded = 0;
		for (GameData g: i.getGameData()) {
		    if (g.hasEnded()) 
			numGamesEnded++;
		    
		    System.out.println(" ---- Game " + g.getId() + " ---- ");
		    System.out.println("Number of game messages: " + g.getNumGameMessages());
		    System.out.println("Timestamp,Seen on Initiator,Seen on network,Message");
		    for (GameMessage gm: g.getGameMessages()) {
			//System.out.println(gm.getFirstSeen() + "," + gm.getNumInodeOccurrences() + "," + gm.getNumOccurrences() + "," + gm.getMessage());
			if (gm.getMessageType() == Message.MessageType.Interest)
			    System.out.println(gm.getNumInodeOccurrences() + "," + gm.getNumOccurrences());
		    }
		}
		
		System.out.println("Games completed: " + numGamesEnded);
		System.out.println("Games not completed: " + (i.getNumGames() - numGamesEnded));
		
	    }

	}
    }

    /** 
     * This function acts as a grep+awk combo and returns a list of matching
     * Strings.
     */ 
    private ArrayList<String> matchLines(File f, int fieldNum, String sep, String pattern) {

	ArrayList<String> res = new ArrayList<String>();
	String line = null;
	BufferedReader br = null;


	pattern = new String(".*" + pattern + ".*");

	Pattern p = Pattern.compile(pattern);
	Matcher m;
	
	try {
	    if (!f.exists()) 
		return null;
	    
	    br = new BufferedReader(new FileReader(f));
	    while ((line = br.readLine()) != null) {
		if (line.matches(pattern)) {
		    int sepCount = StringUtils.countMatches(line,sep);
		    int beginAt = 0;
		    int endAt = line.length();
		    int fieldCount=1; 
		    
		    while (sepCount > 0) {
			endAt = line.indexOf(sep,beginAt);

			if (fieldCount == fieldNum) 
			    break;
			
			beginAt = endAt+1;
			endAt = line.length();

			sepCount--; fieldCount++;
		    }
		    
		    String match = line.substring(beginAt,endAt);
		    res.add(match);
		}
	    }
	    
	    br.close();
	}
	
	catch (Exception e) {
	    Logger.msg("Failed to open file.");
	    return null;
	}

	return res;
    }

    /** 
     * This function is the same as matchLines() but uses grep to speed up the matches. 
     */ 
    private ArrayList<String> matchLinesEx(File f, int fieldNum, String sep, String pattern) {
	
	ArrayList<String> res = new ArrayList<String>();
	String line;
	String grepCmd = "/bin/grep -E";
	
	if (!f.exists()) 
	    return null;
	
	pattern = new String(".*" + pattern + ".*");
	
	String cmd = new String(grepCmd + " " +  pattern + " " + f.toString());
	
	//	Logger.msg ("Running grep: " + cmd);
	    
	try {
	    Process p = Runtime.getRuntime().exec(cmd);

	    BufferedReader input = new BufferedReader
		(new InputStreamReader(p.getInputStream()));
	    while ((line = input.readLine()) != null) {
		Logger.msg("Match: " + line);

		int sepCount = StringUtils.countMatches(line,sep);
		int beginAt = 0;
		int endAt = line.length();
		int fieldCount=1; 
	  
		while (sepCount > 0) {
		    endAt = line.indexOf(sep,beginAt);
	  
		    if (fieldCount == fieldNum) 
			break;
	  
		    beginAt = endAt+1;
		    endAt = line.length();

		    sepCount--; fieldCount++;
		}
	  
		String match = line.substring(beginAt,endAt);
		res.add(match);
	    }
	    int exitVal = p.waitFor();
	    //	    Logger.msg("Ran command.. and got val: " + exitVal);
	    input.close();
	}
	

	catch (Exception e) {
	    Logger.msg("Failed to read from file: " + f.toString());
	    e.printStackTrace();
	    return null;
	}
	
	return res;
    }

    /**
     * Same as matchLinesEx() but returns a HashMap with all the fields in the line that we need
     */
    private HashMap<String, ArrayList<String>> matchLinesMultiEx(File f, int[] fieldids, int primaryid, String sep, String pattern) {
	
	HashMap <String,ArrayList<String>> res = new HashMap<String, ArrayList<String>>();
	String line;
	String grepCmd = "/bin/grep -E";
	
	if (!f.exists()) 
	    return null;
	
	pattern = new String(".*" + pattern + ".*");
	
	String cmd = new String(grepCmd + " " +  pattern + " " + f.toString());
	
	//	Logger.msg ("Running grep: " + cmd);
	    
	try {
	    Process p = Runtime.getRuntime().exec(cmd);
	    
	    BufferedReader input = new BufferedReader
		(new InputStreamReader(p.getInputStream()));
	    while ((line = input.readLine()) != null) {
		//		Logger.msg("Match: " + line);
		
		String[] words = line.split(sep);
		ArrayList<String> fields = new ArrayList<String>();
		String primary = new String(words[primaryid]);
		for (int i=0; i < fieldids.length; i++) {
		    fields.add(words[fieldids[i]]);
		}
		
		//		System.out.println("Primary: " + primary);
		//for (String s: fields) {
		//    System.out.println("Data: " + s);
		//}
		
		res.put(primary, fields);
	    }
	    
	    int exitVal = p.waitFor();
	    //	    Logger.msg("Ran command.. and got val: " + exitVal);
	    input.close();
	}
	

	catch (Exception e) {
	    Logger.msg("Failed to read from file: " + f.toString());
	    e.printStackTrace();
	    return null;
	}
	
	return res;
    }
    
    
}
