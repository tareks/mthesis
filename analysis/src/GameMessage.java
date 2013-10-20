package analyser;

import analyser.*;

import java.util.ArrayList;

import analyser.util.Logger;

class GameMessage extends Message {
	
    GameMessage() {
    }

    GameMessage(long timestamp, MessageType t, int face, String msg) {
	super(timestamp, t, face, msg);
	
	hasResponse = false;
	numOccurrences = numInodeOccurrences = 0;
	firstSeen = Long.MAX_VALUE;
	nodesTraversed = new ArrayList<Node>();
    }

    private boolean hasResponse;
    private int numOccurrences; // # found on network
    private int numInodeOccurrences; // # sent by Inode (only 1 inode per game)
    private ArrayList<Node> nodesTraversed; // nodes it was seen on

    private long firstSeen; // timestamp of first occurrence 

    public void incNumOccurrences() {
	numOccurrences++;
    }
    
    public void incNumInodeOccurrences() {
	numInodeOccurrences++;
    }

    public int getNumOccurrences() {
	return numOccurrences;
    }

    public int getNumInodeOccurrences() {
	return numInodeOccurrences;
    }

    public long getFirstSeen() {
	return firstSeen;
    }

    public void addNodeTraversed(Node n) {
	nodesTraversed.add(n);
    }
	
    /**
     * Checks if the messages match based on string content and type
     */ 
    public boolean msgMatches(Message m) {
	
	if (m.getMessage().equals(message) && 
	    (m.getMessageType() == type)) {
	    
	    if (m.getTimeStamp() < firstSeen) 
		firstSeen = m.getTimeStamp();
	    
	    return true;
	}
	else
	    return false;
    }

}
