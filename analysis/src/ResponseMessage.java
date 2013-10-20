package analyser;

import analyser.util.Logger;

import analyser.Message;

class ResponseMessage extends Message {

    private int numInterests; // Number of associated interests

    ResponseMessage() {
    }

    ResponseMessage(Message m) {
	
	super(m.getTimeStamp(), m.getMessageType(), m.getFace(), m.getMessage());

	numInterests = 0;
    }
    
    public void setInterestCount(int n) {
	numInterests = n;
    }

    public int getInterestCount() {
	return numInterests;
    }
    
}
