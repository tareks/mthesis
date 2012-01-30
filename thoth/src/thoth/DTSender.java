/* 
 * DTSender
 * Description: 
 *   Program that simulates a sending/publishing opportunistic node in
 *   a delay tolerant environment/network.
 *
 *   Currently, we only handle one CCNx URI at a time. Eventually, this should
 *   allow satisfying content from a local database with all the files we have?
 */

package thoth;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.impl.support.Log;

import thoth.CCNComms;
import thoth.CCNComms.NodeNetworkMode;

public class DTSender {

    // Constructors
    protected DTSender(String contentName) throws MalformedContentNameStringException {
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
	
	DTSender sender;
	try {
	    sender = new DTSender(args[0]);
	    
	    // Turn on delay tolerant node
	    // TODO: run in daemon mode or handle signals?
	    sender.PowerOn(args); 
	    
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
	System.err.println("Listen for requests under a ccn URI and satisfy when valid. Data sent is versioned. (contains file meta data)");
	System.err.println("Usage: DTSender <ccn URI> <file>");
    }
    
    // Private Methods
    private void PowerOn(String[] args) throws ConfigurationException, IOException, MalformedContentNameStringException, InterruptedException {

	inFile = new File(args[1]);
	if (!inFile.exists()) {
	    throw new IOException(); // TODO: is this okay instead of returning?
	}
	
	_connection.createConnection();
	
	Log.warning("Our public key is: " + _connection.getMyPublicKeyString());
	Log.warning("Our public key fingerprint is: " + _connection.getMyPublicKeyShortString());

	/* 
	 * All we do is listen for Interest packets on our face(s) and send
	 * the data out if we can satisfy it.
	 *
	 */
	if (_connection.getNetworkMode() == NodeNetworkMode.NODE_USES_CCNX_STREAMS) {
	    Log.warning("Network Mode set to use ++ CCNX STREAMS ++");
	    listenWithStreams(inFile);
	}
	else {
	    Log.warning("Network Mode set to ++ MANUAL ++");
	    listen(inFile);
	}
	
	_connection.closeConnection();
    }


    /* 
     * Encapsulates sendData() and handles file manipulations 
     * blocking send?
     * sleep and resend?
     * BufferedReader = openfile(filename)
     * while !EOF, use _channel to publish the data over CCN
     * close file
     */
    private void listenWithStreams(File file) throws IOException, InterruptedException {
	boolean stopListening = false;

	long bytesSent=0;

	Log.warning("We can serve: " + _connection.getContentName()
		    + " based on " + file.getPath());

	while (! stopListening) {
	    numRetries++;

	    // reopen stream every time otherwise 0 bytes get transferred
	    inStream = new FileInputStream(file);

	    Log.warning("Listening for requests.. | Attempt #: " + numRetries); 

	    bytesSent = _connection.writeFileToNetwork(inStream);
	    if (bytesSent < 0) {
		Log.warning("Timed out - No requests received.");
	    } else {
		Log.warning("File (" + file.getPath() + ") sent successfully! "
			    + bytesSent + " bytes sent.");
		break;
	    }
	    
	    inStream.close();
	    
	    // We retry here because CCNx stack will timeout and we can't 
	    // overwrite that without implementing our own FlowController?
	    // Otherwise, we just listen indefintely until the Interest
	    // we are waiting for is received.
	    if (numRetries == MAX_SEND_RETRIES) {
		Log.warning("Exhausted attempts to send, giving up.");
		break;
	    }
	    
	    Log.warning("Retrying in "  + (retryTimeout / MSEC_PER_SEC) 
			+ " seconds..");

	    // Wait before retrying
	    Thread.sleep(retryTimeout);	    
	}
	
    }

    /* 
     * 
     * Manual listening for Interests and handling.
     *
     */
    private void listen(File file) throws IOException, InterruptedException {
	boolean stopListening = false;

	long bytesSent = -1;

	Log.warning("We can serve: " + _connection.getContentName()
		    + " based on " + file.getPath());

	inStream = new FileInputStream(file);

	while (! stopListening) {
	    numRetries++;

	    Log.warning("Listening for requests.. | Attempt #: " + numRetries); 

	    // Should this be blocking?
	    _connection.handleInterests();
	    //bytesSent = getRequestedData();
	    
	    if (bytesSent < 0) {
		Log.warning("Timed out - No requests received.");
	    } else {
		Log.warning("File (" + file.getPath() + ") sent successfully! "
			    + bytesSent + " bytes sent.");
		break;
	    }
	    

	    // We retry here because CCNx stack will timeout and we can't 
	    // overwrite that without implementing our own FlowController?
	    // Otherwise, we just listen indefintely until the Interest
	    // we are waiting for is received.
	    if (numRetries >= MAX_SEND_RETRIES) {
		Log.warning("Exhausted attempts to send, giving up.");
		break;
	    }
	    
	    Log.warning("Retrying in "  + (retryTimeout / MSEC_PER_SEC) 
			+ " seconds..");

	    // Wait before retrying
	    Thread.sleep(retryTimeout);	    
	}

	inStream.close();
    }

    // Internal Variables
    private final CCNComms _connection;
    private int numRetries = 0;
    private final short retryTimeout = 5000; // 5 seconds (in msec)?
    private File inFile;
    private InputStream inStream;

	    
    private static final short MSEC_PER_SEC = 1000;
    private static final short MAX_SEND_RETRIES = 5;
}