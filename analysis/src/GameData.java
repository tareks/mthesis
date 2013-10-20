package analyser;

import analyser.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import analyser.util.Logger;

/**
 * This class holds data mined from ccnd.log files for each game in an iteration 
 * of an experiment. This data is across all nodes (ie. network data)
 */
class GameData {

    private ArrayList<GameMessage> gameMessages;
    private int gameId;
    private boolean hasEnded;
    private int numInterests; // unique interests
    private int numResponses; // unique responses
    private int numTotalInterests; // all interests
    private int numTotalResponses; // all responses
    private ArrayList<Node> inodes;
    private ArrayList<Node> hnodes;
    private ArrayList<Node> rnodes;
    private ArrayList<InterestMessage> interests;
    private ArrayList<ResponseMessage> responses;
    
    GameData() {
	gameId = 0;
	hasEnded = false;
    }
    
    GameData(int _id, boolean _hasEnded) {

	gameId = _id;
	hasEnded = _hasEnded;
	
	gameMessages = new ArrayList<GameMessage>();
	interests = new ArrayList<InterestMessage>();
	responses = new ArrayList<ResponseMessage>();

	numInterests = numResponses = numTotalInterests = numTotalResponses= 0;
	
	inodes = hnodes = rnodes = new ArrayList<Node>();
    }
    
    public int getId() {
	return gameId;
    }
    
    public ArrayList<GameMessage> getGameMessages() {
	return gameMessages;
    }

    public ArrayList<InterestMessage> getInterests() {
	return interests;
    }

    public ArrayList<ResponseMessage> getResponses() {
	return responses;
    }

    public void addInterest(InterestMessage i) {
	interests.add(i);
    }

    public void addResponse(ResponseMessage r) {
	responses.add(r);
    }
    
    // Filter out unique game messages from the message list
    // ignoring timestamp and face fields
    // Focus on text and type
    // Normally we would expect New, Fin, 5x moves (x2) - request + response
    public void filterMessages(ArrayList<Message> messages) {
	
	boolean foundMatch = false;
	
	for (Message m: messages) {

	    if (m.getMessageType() == Message.MessageType.Other) 
		continue;

	    if (gameMessages.isEmpty()) { 
		gameMessages.add(new GameMessage(m.getTimeStamp(), m.getMessageType(), m.getFace(), m.getMessage()));
		//	Logger.msg("Unique: " + m.getMessageType().name() + " " + m.getMessage() + " " + m.getTimeStamp());
		continue;
	    }
 
	    foundMatch = false;
	    for (GameMessage g: gameMessages) {
		if ((m.getMessageType() == g.getMessageType())
		    && (m.getMessage().equals(g.getMessage()))
		    //		    && (m.getFace() == g.getFace() ) 
		    )
		    {
			foundMatch = true;
			
			// check the timestamp so we always have the earliest copy and replace data if needed
			if (m.getTimeStamp() < g.getTimeStamp()) {

			    g.setTimeStamp(m.getTimeStamp());
			    g.setFace(m.getFace());
			}
			
			break;
		    }
	    }
	    
	    if (! foundMatch) {
		gameMessages.add(new GameMessage(m.getTimeStamp(), m.getMessageType(), m.getFace(), m.getMessage()));
		//Logger.msg("Unique: " + m.getMessageType().name() + " " + m.getMessage() + " " + m.getTimeStamp());
	    }
	}
	
    }
    
    public boolean hasEnded() {
	return hasEnded;
    }

    public void setNumInterests(int n) {
	numInterests = n;
    }

    public void setNumResponses(int n) {
	numResponses = n;
    }

    public int getNumInterests() {
	return numInterests;
    }
    
    public int getNumResponses() {
	return numResponses;
    }
	
    public int getNumGameMessages() {
	return gameMessages.size();
    }
	
    public void incNumInterests() {
	numInterests++;
    }

    public void incNumResponses() {
	numResponses++;
    }

    public void incNumTotalInterests() {
	numTotalInterests++;
    }

    public void incNumTotalResponses() {
	numTotalResponses++;
    }

    
}

/* if needed
// Sort all messages by timestamp
Collections.sort(data.getMessages(), new Comparator<Message>() {
public int compare(Message m1, Message m2) {
return Long.valueOf(m1.getTimeStamp()).compareTo(Long.valueOf(m2.getTimeStamp()));
}			
});
*/		    
