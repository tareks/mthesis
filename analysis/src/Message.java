package analyser;

import analyser.util.Logger;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;

class Message {

    public class MessageComparator implements Comparator<Message> {
	@Override
        public int compare(Message m1, Message m2) {
	    return Long.valueOf(m1.timestamp).compareTo(Long.valueOf(m2.timestamp));
	}
    }
  

    /*
     * Interest: interest_from or interest to
     * Response: content_from or content_to
     * Relay is if node is neither host nor an Initiator
     * Other: every other message we dont care about
     */
    
    protected enum MessageType { Interest, Response, Other };
    
    protected long timestamp;
    protected int face;
    protected String message;
    protected MessageType type;
    
    Message() {
    }
    
    Message(long _timestamp, MessageType _t, int _face, String _msg) {
	timestamp = _timestamp;
	type = _t;
	face = _face;
	message = _msg;
    }
    
    /**
     * Classify messages as we create them:
     *
     * interest_to : events in which interests are pushed on network. There is one or more interets_to events for every interest_from.
     * interest_from: events in which interests are received by ccnd (from app or network face). 
     * content_to: content being pushed around network
     * content_from: content being received on a face (normally would be pushed out). There is one or more content_to events for every content_from.
     * 
     * For purposes of our logging, we only care about interest_to and 
     * corresponding content_from events
     */
    
    Message(String ts, String t, String f, String m) {

	//	if (t.matches(".*interest_[to|from].*"))
	if (t.matches(".*interest_to.*"))
	    type = MessageType.Interest;
	//	else if (t.matches(".*content_[to|from].*"))
 	else if (t.matches(".*content_from.*")) 
	    type = MessageType.Response;
	else 
	    type = MessageType.Other;
	
	if (type == MessageType.Other) {
	    // Use defaults - can't guarantee rest of string
	    // We don't care about these anyway
	    timestamp = Long.parseLong(ts.replace(".",""));
	    face = -1;
	    message = m;

	} else {
	    timestamp = Long.parseLong(ts.replace(".",""));
	    face = Integer.parseInt(f);
	    message = m;
	    
	    //  Logger.msg ("Message: " + timestamp + " " + face + " " + message);
	}

    }
    
    public String getMessage() {
	return message;
    }

    public MessageType getMessageType() {
	return type;	
    }
    
    public long getTimeStamp() {
	return timestamp;
    }

    public void setTimeStamp(long ts) {
	timestamp = ts;
    }
    
    public int getFace() {
	return face;
    }

    public void setFace(int f) {
	face = f;
    }
    
    /**
     * Returns a ResponseMessage based on message text and face id
     */
    public ResponseMessage findResponse(ArrayList<ResponseMessage> msgs) {
	
	ResponseMessage r = null;
	
	for (ResponseMessage m: msgs) {
	    if ((m.getFace() == face) && (message.matches(m.getMessage()))) {
		r = m;
		Logger.msg("findResponse(): Found Match: " + m.getMessage());
		break; // we only expect one response
	    }
	}

	return r;
    }

    /**
     * Returns a GameMessage based on message text and being a response
     */
    public GameMessage findResponse(ArrayList<GameMessage> msgs) {
	
	GameMessage r = null;
	
	Logger.msg ("Matching a response for: " + message);
	
	for (GameMessage m: msgs) {
	    
	    if ((m.getMessageType() == MessageType.Response)) {// && (message.matches(".*" + m.getMessage() + ".*"))) {
		//		Logger.msg("Testing : " + m.getMessage());
		if (m.getMessage().matches(message + ".*")) {
		    r = m;
		    Logger.msg("findResponse(): Found Match: " + m.getMessage());
		    break; // we only expect one response
		}
	    }
	}

	return r;
    }

    /**
     * Returns a Message based on message text and being a response and face
     */
    public Message findResponse(ArrayList<Message> msgs) {
	
	Message r = null;
	
	Logger.msg ("Matching a response for: " + message);
	
	for (Message m: msgs) {
	    
	    if ((m.getMessageType() == MessageType.Response)) {// && (message.matches(".*" + m.getMessage() + ".*"))) {
		//		Logger.msg("Testing : " + m.getMessage());
		if ((m.getFace() == face) && (m.getMessage().matches(message + ".*"))) {
		    r = m;
		    Logger.msg("findResponse(): Found Match: " + m.getMessage());
		    break; // we only expect one response
		}
	    }
	}

	return r;
    }


    /**
     * Returns the number of matches found based on message, face
     */ 
    public int countInterestMatches(ArrayList<InterestMessage> msgs) {
	
	int count = 0;
	
	for (InterestMessage m: msgs) {
	    if ((m.getFace() == face) && (m.getMessage().matches(message))) {
		count++;
		Logger.msg("countInterestMatches(): Found match: " + m.getMessage());
	    }
	}
	
	return count;
    }
    
}
