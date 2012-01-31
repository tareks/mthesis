/* 
 * Contains consolidated node code to handle sending and receiving.
 *
 *
 */

package thoth;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Level;


import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.profiles.SegmentationProfile;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.SignedInfo;
import org.ccnx.ccn.impl.support.Log;

import thoth.CCNComms;
import thoth.CCNComms.NodeNetworkMode;

public class DTNode {
    
    protected DTNode() {
	_debug = false;
	_dataObjects = new ArrayList<ContentObject>();
    }
    
    /*  
     * Send interest requests, wait for response and put file together.
     * 
     * If a request is satisfied, callbacks will be triggered in CCNComms.
     */
    protected void sendRequests(int numRequests, int interval) {
	ContentObject co = null;
	
	try {
	    for (int i=0; i < numRequests; i++) {
		co = _connection.sendRequest();
		if (co != null)
		    break;
		Thread.sleep(interval);
	    }
	}
	
	catch (InterruptedException e) {
	}

	if (co == null) {
	    Log.warning("No data received, giving up.");
	}
	else {
	    Log.warning("Got CO: " + co.toString());
	    Log.warning("Writing ContentObject to file...");
	    
	    try {
		// write co to file
		outStream = new FileOutputStream(_file);
		outStream.write(co.content());
		
		outStream.close();
		Log.warning("Write complete to " + _filename);
	    }

	    catch (IOException e) {
		Log.warning("Error writing data to file.");
	    }
	}
    }

    protected void handleRequests() {
	Interest i = null;
	while (true) {
	    i = _connection.handleInterests();
	    
	if (i != null) {
	    Log.warning("Checking if we have a CO to match this interest..");
	    
	    for (ContentObject co : _dataObjects) {
		if (i.matches(co)) {
		    Log.warning("Found match! Writing CO to network..");
		    _connection.sendObject(co);
		} else
		    Log.warning("Found no match. Ignoring interest.");
	    }
	}
	}
    }

    private void createContentObjects() {
	ContentName name, baseName; 
	ContentObject co;
	
	try {
	    baseName = ContentName.fromURI(_ccnURI);// + (new CCNTime()).toShortString());
	    name = SegmentationProfile.segmentName(VersioningProfile.addVersion(baseName, new CCNTime()), 0);
	    Log.warning("Creating CO with name: " + name);

	    long length = _file.length();

	    if (length > Integer.MAX_VALUE) {
		Log.warning("File is too big, skipping..");
		return;
	    }
	    
	    inStream = new FileInputStream(_file);
	    byte[] data = new byte[(int) length];
	    inStream.read(data);
	    inStream.close();

	    co = ContentObject.buildContentObject(name, data);
	    //, new SignedInfo(_connection.getMyPublicKey(), new KeyLocator(name)), data, (int) _file.length());
	    
	    _dataObjects.add(co);

	    Log.warning("Created CO: " + co.toString());
	}

	catch (IOException e) {
	    Log.warning("IOException: " + e.getMessage());
	}

	catch (MalformedContentNameStringException e) {
	    Log.warning("MalformedContentNameStringException: " + e.getMessage());
	}

    }

    private void PowerOn() throws ConfigurationException, IOException, MalformedContentNameStringException, InterruptedException {
	
	_file = new File(_filename);
	
	if ( (! _file.exists()) && (_mode == NodeMode.Sender) ) {
	    throw new IOException("File not found.");
	}
	
	_connection = new CCNComms(_ccnURI);
	_connection.createConnection();
	
	Log.warning("Our public key is: " + _connection.getMyPublicKeyString());
	Log.warning("Our public key fingerprint is: " + _connection.getMyPublicKeyShortString());

	switch (_mode) {
	case Sender:
	    Log.warning("NODE is in SENDER MODE");
	    createContentObjects();
	    handleRequests();
	    break;
	case Receiver:
	    Log.warning("NODE is in RECEIVER MODE");
	    sendRequests(1, 5000);
	    break;
	default:
	    // should never happen
	    break;
	}
	    
	_connection.closeConnection();
	
    }
    
    public boolean parseArgs(String[] args) {
	
	if (args.length != 3) return false;
	
	boolean foundMode = false;
	boolean flagError = false;
	for (String s: args) {
	    if (s.equals("-S")) {
		if (foundMode) { flagError = true; break; }
		foundMode = true;
		_mode = NodeMode.Sender;
	    }
	    
	    if (s.equals("-R")) {
		if (foundMode) { flagError = true; break; }
		foundMode = true;
		_mode = NodeMode.Receiver;
	    }
	    
	}
	
	if (flagError || !foundMode)
	    return false;
	
	_ccnURI = args[1];
	_filename = args[2];

	return true;
    }


    public static void usage() {
	System.err.println("Start a delay tolerant node.\n");
	System.err.println("Usage: DTNode < -S | -R > < ccnx URI > < filename >");
	System.err.println("\t -S: Enables sender mode."); 
	System.err.println("\t -R: Enables receiver mode.");
	//	System.err.println("\t -d: Enables debugging messages.\n");
    }
    
    public static void main(String[] args) {
	DTNode node = new DTNode();
	
	if (! node.parseArgs(args) ) {
	    usage();
	    System.exit(-1);
	}
	
	// TODO - add -debug flag and switch to Level.info or define new?
	Log.setLevel(Log.FAC_ALL, Level.WARNING);
	
	try {
	    node.PowerOn(); 
	    
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

    // Variables
    private CCNComms _connection;
    private int numRetries = 0;
    private final short retryTimeout = 5000; // 5 seconds (in msec)?
    private File _file;
    private InputStream inStream;
    private OutputStream outStream;
    
    private String _filename;
    private String _ccnURI;
    private NodeMode _mode;
    private boolean _debug;
    
    private ArrayList<ContentObject> _dataObjects = null;

    // Constants
    private static final short MSEC_PER_SEC = 1000;
    private static final short MAX_SEND_RETRIES = 5;


    private enum NodeMode { Sender, Receiver; }

}