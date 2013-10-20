package analyser;

import analyser.Iteration;
import analyser.Node;

import java.util.ArrayList;

class Experiment {

    private String dirName;
    private String path; // path for the experiment
    private ArrayList<Iteration> iterations;

    private int numIterations;

    Experiment() {
	dirName = "";
    }
    
    Experiment(String name) {
	
	numIterations = 0;
	dirName = name;
	iterations = new ArrayList<Iteration>();

    }

    public String getName() {
	return dirName;
    }

    public void setNumIterations(int n) {
	numIterations = n;
    }

    public void addIteration(String s) {
	iterations.add(new Iteration(s));
    }
    
    public Iteration getIteration(int n) {
	
	return iterations.get(n);
    }

    protected ArrayList<Iteration> getIterations() {
	return iterations;
    }

    public int getNumIterations() {
	return numIterations;
    }
      
    
    public void setPath(String p) {
	path = p;
    }

    public String getPath() {
	return path;
    }

}
