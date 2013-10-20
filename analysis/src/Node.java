package analyser;

import analyser.Message;

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

    
}
