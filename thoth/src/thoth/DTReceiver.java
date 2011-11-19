/* 
 * DTReceiver
 * Description: 
 *   Program that simulates a receiving opportunistic node in
 *   a delay tolerant environment/network.
 *
 *
 */

package thoth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.impl.support.Log;

import thoth.CCNComms;

public class DTReceiver {

    // Constructors
    protected DTReceiver(String contentName) throws MalformedContentNameStringException {
	// CCNComms will validate namespace syntax
	_connection = new CCNComms(contentName);
    }
    
    // Public Methods
    
    public static void main(String[] args) {
	if (args.length != 2) {
	    usage();
	    System.exit(-1);
	}

	// TODO - add -debug flag and switch to Level.info or define new?
	Log.setLevel(Log.FAC_ALL, Level.WARNING);
	
	DTReceiver receiver;
	try {
	    receiver = new DTReceiver(args[0]);
	    
	    // Turn on delay tolerant node
	    // TODO: run in daemon mode or handle signals?
	    receiver.PowerOn(args); 
	    
	} catch (MalformedContentNameStringException e) {
	    System.err.println("Invalid CCN URI: " + args[0] + ": " + e.getMessage());
	    e.printStackTrace();
	} catch (ConfigurationException e) {
	    System.err.println("Configuration exception: " + e.getMessage());
	    e.printStackTrace();
	} catch (IOException e) {
	    System.err.println("IOException: " + e.getMessage());
	    e.printStackTrace();
	} catch (InterruptedException e) {
	    System.err.println("InterruptedException: " + e.getMessage());
	    e.printStackTrace();
	}
	
	Log.warning("Exiting..");
	System.exit(1);
    }

    public static void usage() {
	System.err.println("Get a filename through its CCN URI. Data received is versioned. (contains file meta data)");
	System.err.println("Usage: DTReceiver <ccn URI> <file>");
    }
    
    // Private Methods
    private void PowerOn(String[] args) throws ConfigurationException, IOException, MalformedContentNameStringException, InterruptedException {

	outFile = new File(args[1]);
	
	_connection.createConnection();
	
	Log.warning("Our public key is: " + _connection.getMyPublicKey());
	Log.warning("Our public key fingerprint is: " + _connection.getMyPublicKeyShort());
	Log.warning("Requesting: " + _connection.getContentName());

	/* 
	 * Request file from the network until we get it.
	 */
	requestFile(outFile);
	
	_connection.closeConnection();
    }


    /* 
     * Continously triggers Interest requests to get a specific file.
     *
     *
     */
    private void requestFile(File file) throws IOException, InterruptedException {
	boolean receivedFile = false;
	long bytesReceived = 0;



	while (! receivedFile) {
	    numRetries++;

	    // reopen stream every time otherwise 0 bytes get transferred
	    outStream = new FileOutputStream(file);

	    Log.warning("Requesting " + _connection.getContentName()  
			+ " | Attempt #: " + numRetries); 

	    bytesReceived = _connection.readFileFromNetwork(outStream);
	    if (bytesReceived < 0) {
		Log.warning("File was NOT received.");
	    } else {
		Log.warning("File (" + file.getPath() 
			    + ") received successfully! " + bytesReceived 
			    + " bytes received.");
		break;
	    }

	    outStream.close();
	    
	    if (numRetries == MAX_RECEIVE_RETRIES) {
		Log.warning("Exhausted attempts to get file, giving up.");
		break;
	    }
	    
	    Log.warning("Retrying in "  + (retryInterval / MSEC_PER_SEC) 
			+ " seconds..");

	    // Wait before retrying
	    Thread.sleep(retryInterval);	    
	}
	

    }

    // Internal Variables
    private final CCNComms _connection;
    private int numRetries = 0;
    private final short retryInterval = 5000; // 5 seconds (in msec)?
    private File outFile;
    private OutputStream outStream;

	    
    private static final short MSEC_PER_SEC = 1000;
    private static final short MAX_RECEIVE_RETRIES = 5;
}