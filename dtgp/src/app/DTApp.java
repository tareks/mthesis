
package dtgp.app;

/* Interface for a Delay Tolerant application */
public interface DTApp {

    // initializes the application
    void init();

    // runs the application
    void run();
    
    // returns whether the application is currently running or not
    boolean isRunning();
    
    // local application state from local store
    void loadState(); 

    // save application state locally
    void saveState(); 

    // terminates the application
    void end();

}
