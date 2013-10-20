package analyser;

import analyser.Node;
import analyser.Game;

import java.util.ArrayList;

class Iteration {
    
    private ArrayList<Node> nodes;
    private ArrayList<Game> games;
    private int number;
    private String path;
    private int numNodes;

    private long startTime;
    private long endTime;

    private String execLogFileName;
    private ArrayList<GameData> gameData;


    Iteration() {
	
    }
    
    Iteration(String s) {

	numNodes = 0;
	number = Integer.parseInt(s);
	nodes = new ArrayList<Node>();
	games = new ArrayList<Game>();
	gameData = new ArrayList<GameData>();

	startTime = endTime = 0;
	execLogFileName = new String("");
    }
    
    public int getNum() {
	return number;
    }

    public void setNumNodes(int n) {
	numNodes = n;
    }


    public void setPath(String p) {
	path = p;
    }

    public String getPath() {
	return path;
    }
    
    public void addGame(int id) {
	games.add(new Game(id));
    }

    public Game getGame(int id) {
	return games.get(id);
    }

    public ArrayList<Game> getGames() {
	return games;
    }
    
    public boolean gameIdExists(int id) {

	for (Game g: games) {
	    if (g.getId() == id) 
		return true;
	}
	
	return false;
    }
    
    public ArrayList<Node> getNodes() {
	return nodes;
    }
    
    public Node getNode(int i) {
	return nodes.get(i);
    }
    
    public void addNode(String s) {
	nodes.add(new Node(s));
    }

    public int getNumNodes() {
	return numNodes;
    }

    public void setStartTime(long t) {
	startTime = t;
    }
    
    public long getStartTime() {
	return startTime;
    }

    public void setEndTime(long t) {
	endTime = t;
    }

    public long getEndTime() {
	return endTime;
    }

    public void setExecLogFileName(String s) {
	execLogFileName = s;
    }

    public String getExecLogFileName() {
	return execLogFileName;
    }
    
    public int getNumGames() {
	return games.size();
    }

    public void storeGameData(GameData g) {
	gameData.add(g);
    }

    public ArrayList<GameData> getGameData() {
	return gameData;
    }
}
