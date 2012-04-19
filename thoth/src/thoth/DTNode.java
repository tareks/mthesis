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

import java.net.*;
import java.util.*;


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

    private class BeaconGenerator implements Runnable {
	
	boolean keepSending=true;
	
	public void stopSending() {
	    keepSending = false;
	}
	
	public void run() {
	    int SERVER_PORT = 1893;
	    String SERVER_IP = "192.168.122.254";
	    DatagramSocket socket = null;
	    Log.info("Starting beacon thread..");

	    try {
		socket = new DatagramSocket();
		InetAddress serverAddr= InetAddress.getByName(SERVER_IP);
		DatagramPacket msg = new DatagramPacket(new String("ready").getBytes(), 5, serverAddr, SERVER_PORT);
		while (keepSending) {
		    // send beacon to server 
		    socket.send(msg);
		    Log.info("Sent ready signal to server.");
		    
		    //delay for two seconds before next beacon
		    Thread.currentThread().sleep(2000);
		}
		
		socket.close();
		
	    } 
	    catch (InterruptedException e) {
		// interrupt early to avoid sleeping extra
		socket.close();
	    }
	    catch (Exception e) {
		System.err.println("Problem sending beacon to server. Exception:" +e );
	    }
	}
    }
    
    
    protected DTNode() {
	_dataObjects = new ArrayList<ContentObject>();
    }
    
    /*  
     * Send interest requests, wait for response and put file together.
     * 
     * If a request is satisfied, callbacks will be triggered in CCNComms.
     */
    protected void sendRequests(int numRequests, int interval) {
	ContentObject co = null;

	// reset for every set of requests
	requestFulfilledTime = -1;
	numRequestFulfilled = -1;
	totalTimeToFulfillRequest = -1;
	
	try {
	    long totalRequestStartTime = System.currentTimeMillis();

	    for (int i=1; i <= numRequests; i++) {
		// start requestFulfillmentTimer
		long requestStartTime = System.currentTimeMillis();
		co = _connection.sendRequest();
		if (co != null) {
		    // got data
		    requestFulfilledTime = System.currentTimeMillis() - requestStartTime;
		    totalTimeToFulfillRequest = System.currentTimeMillis() - totalRequestStartTime;
		    numRequestFulfilled = i;

		    break;
		}
		Thread.sleep(interval);
	    }
	}
	
	catch (InterruptedException e) {
	}

	if (co == null) {
	    Log.info("No data received, giving up.");
	}
	else {
	    Log.info("Got CO: " + co.toString());
	    Log.info("Writing ContentObject to file...");
	    
	    try {
		// write co to file
		outStream = new FileOutputStream(_file);
		outStream.write(co.content());
		
		outStream.close();
		Log.info("Write complete to " + _filename);
	    }

	    catch (IOException e) {
		Log.info("Error writing data to file.");
	    }
	}
    }

    protected void handleRequests() {
	Interest i = null;
	while (true) {
	    i = _connection.handleInterests();
	    
	if (i != null) {
	    Log.info("Checking if we have a CO to match this interest..");
	    
	    for (ContentObject co : _dataObjects) {
		if (i.matches(co)) {
		    Log.info("Found match! Writing CO to network..");
		    _connection.sendObject(co);
		} else
		    Log.info("Found no match. Ignoring interest.");
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
	    Log.info("Creating CO with name: " + name);

	    long length = _file.length();

	    if (length > Integer.MAX_VALUE) {
		Log.info("File is too big, skipping..");
		return;
	    }
	    
	    inStream = new FileInputStream(_file);
	    byte[] data = new byte[(int) length];
	    inStream.read(data);
	    inStream.close();

	    co = ContentObject.buildContentObject(name, data);
	    //, new SignedInfo(_connection.getMyPublicKey(), new KeyLocator(name)), data, (int) _file.length());
	    
	    _dataObjects.add(co);

	    Log.info("Created CO: " + co.toString());
	}

	catch (IOException e) {
	    Log.info("IOException: " + e.getMessage());
	}

	catch (MalformedContentNameStringException e) {
	    Log.info("MalformedContentNameStringException: " + e.getMessage());
	}

    }

    private void waitForSync() {
	int BCAST_PORT = 1892;
	String SERVER_IP = "192.168.122.254";
	String SYNC_MSG = "gogogo";
	
	// Create and start new thread to send alive beacons once ready
	BeaconGenerator generator = new BeaconGenerator();
	Thread beaconThread = new Thread(generator);
	beaconThread.start();
	
	// main thread blocks and listens for sync signal
        
        byte[] beacon = new byte[16];
	try {
	    DatagramSocket socket = new DatagramSocket(BCAST_PORT);
	    DatagramPacket packet = new DatagramPacket(beacon, beacon.length);
	    Log.info("Waiting for synchronization signal..");

	    while (true) {
		socket.receive(packet);
		String received = new String(packet.getData(), 0, packet.getLength());
		if (received.equals(SYNC_MSG)) break;
	    }
	
	    generator.stopSending();
//	    beaconThread.interrupt();
	
	    Log.info("Got synchronization signal, good to go!");
	    Thread.currentThread().sleep(1000);
	    
	    socket.close();
	}
	catch (Exception e) {
	    System.err.println("Error receiving synchronization signal.");
	}
    }

    private void PowerOn() throws ConfigurationException, IOException, MalformedContentNameStringException, InterruptedException {
	
	_file = new File(_filename);
	
	if ( (! _file.exists()) && (_mode == NodeMode.Sender) ) {
	    throw new IOException("File not found.");
	}
	
	_connection = new CCNComms(_ccnURI);
	_connection.createConnection();
	
	Log.info("Our public key is: " + _connection.getMyPublicKeyString());
	Log.info("Our public key fingerprint is: " + _connection.getMyPublicKeyShortString());

	if (_mode == NodeMode.Sender)
	    createContentObjects();
	
	// Now wait for Sync signal before sending/receiving requests
	waitForSync();
	
	switch (_mode) {
	case Sender:
	    Log.info("NODE is in SENDER MODE");
	    handleRequests();
	    break;
	case Receiver:
	    Log.info("NODE is in RECEIVER MODE");
	    sendRequests(numRetries, retryTimeout);
	    break;
	default:
	    // should never happen
	    break;
	}
	    
	_connection.closeConnection();

	if (numRequestFulfilled > 0) {
	    Log.info("Number of Interests to fulfill request: " + numRequestFulfilled);
	    Log.info("Time to fulfill request for successful Interest: " + requestFulfilledTime + " milliseconds.");
	    Log.info("Time to fulfill request from first Interest: " + totalTimeToFulfillRequest + " milliseconds.");
	}
	
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

	// TODO - add -debug flag and switch to Level.info or define new?
	boolean _debug = true;

	if (_debug == true)
	    Log.setLevel(Log.FAC_ALL, Level.INFO);
	else 
	    Log.setLevel(Log.FAC_ALL, Level.OFF);

	DTNode node = new DTNode();
	
	if (! node.parseArgs(args) ) {
	    usage();
	    System.exit(-1);
	}
	


	
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
	
	Log.info("Exiting..");
	System.exit(1);
    }

    // Variables
    private CCNComms _connection;
    private int numRetries = 3;
    private final short retryTimeout = 5000; // 5 seconds (in msec)?
    private File _file;
    private InputStream inStream;
    private OutputStream outStream;
    private long requestFulfilledTime; // time to fulfill Interest in milliseconds
    private long totalTimeToFulfillRequest; // time to fulfill from first Interest
    private int numRequestFulfilled; // which request got fulfilled
    
    private String _filename;
    private String _ccnURI;
    private NodeMode _mode;
    
    ArrayList<ContentObject> _dataObjects = null;

    // Constants
    private static final short MSEC_PER_SEC = 1000;
    private static final short MAX_SEND_RETRIES = 5;


    private enum NodeMode { Sender, Receiver; }

}
