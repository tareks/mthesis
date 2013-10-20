package analyser;

import analyser.util.Logger;

import analyser.Message;

class InterestMessage extends Message {


    private boolean hasResponse;
    
    private long responseTime; // Time taken to find a response
    

    InterestMessage() {
    }

    InterestMessage(Message m) {
	
	super(m.getTimeStamp(), m.getMessageType(), m.getFace(), m.getMessage());

	hasResponse = false;
	responseTime = -1;
    }
    
    public void setHasResponse() {
	hasResponse = true;
    }
    
    public boolean hasResponse() {
	return hasResponse;
    }

    public void setResponseTime(long t) {

	responseTime = t;
    }

    public long getResponseTime() {
	return responseTime;
    }
    
}
