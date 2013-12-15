package analyser;

import analyser.Message;
import analyser.Message.MessageType;

import java.util.ArrayList;

class Node {
    
    private int id;
    private String name;
    public enum Type { Initiator, Host, Relay };
    private String path;
    private Type type;
    ArrayList<Message> messages;
    
    Node() { 

    }

    Node(String s) {
	name =s;
	type = Type.Relay;

	messages = new ArrayList<Message>();
    }

    public void setPath(String p) {
	path = p;
    }

    public String getPath() {
	return path;
    }

    public void setType(Type t) {
	type = t;
    }

    public String getName() {
	return name;
    }

    public Type getType() {
	return type;
    }


    public void addMessage(Message m) {
	messages.add(m);
    }

    public ArrayList<Message> getMessages() {
	return messages;
    }
    
    public ArrayList<Message> getMessagesForGame(int gameID) {
	
	ArrayList<Message> gameMessages = new ArrayList<Message>();
	
	for (Message m: messages) {
	    if (m.getMessage().matches(".*" + gameID +".*"))
		gameMessages.add(m);
	}

	return gameMessages;
    }

    public ArrayList<Message> getInterestsForGame(int gameID) {
	ArrayList<Message> gameMessages = new ArrayList<Message>();
	
	for (Message m: messages) {
	    if ( (m.getMessageType() == MessageType.Interest) && (m.getMessage().matches(".*" + gameID +".*")))
		gameMessages.add(m);
	}
	
	return gameMessages;
    }
    
}
