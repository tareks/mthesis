package analyser;

import analyser.util.Logger;

import analyser.Node;

import java.util.ArrayList;

class Game {

    private int id;
    private boolean hasCompleted;
    private int timeStarted;
    private int timeEnded;
    
    Game() {
	
    }

    Game(int n) {
	id = n;
	hasCompleted = false;
    }
    
    public int getId() {
	return id;
    }

    public void markEnded() {
	hasCompleted = true;
    }

    public boolean hasEnded() {
	return hasCompleted;
    }
    
}    

