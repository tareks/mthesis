
package dtgp.net;

import dtgp.net.Network;
import dtgp.util.*;
import dtgp.app.tictactoe.Move;
import dtgp.app.tictactoe.Player;
import dtgp.app.tictactoe.TicTacToe;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNContentHandler;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.impl.CCNNetworkManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherID;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

import java.io.IOException;
//import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;

public class CCNxNetwork implements Network, CCNInterestHandler, CCNContentHandler {
    
    private String ccnURI;
    private CCNHandle ccnHandle;
    private CCNNetworkManager networkManager;
    private ContentName contentName;
    private ContentObject contentData;
    private boolean interestSatisfied = false;
    private PublisherPublicKeyDigest myPublicKeyDigest, remotePublicKeyDigest;
    
    private final String tttCCNxPrefix = "/uu/core/games/ttt";

    /** time to fulfill Interest in milliseconds */
    private long requestFulfilledTime; 
    /** time to fulfill from first Interest */
    private long totalTimeToFulfillRequest; 
    /** the request that was fulfilled */
    private int numRequestFulfilled; 
    
    // Constants
    private static int NETWORK_TIMEOUT = 5000; //ms 

    public CCNxNetwork() {
	//	ccnURI = tttCCNxPrefix;
	ccnURI = ""; // Listen for nothing by default
    }

    public void openConnection() {
	
	Logger.msg("Connecting to CCN network..."); 	
	
	ccnHandle = CCNHandle.getHandle();
	networkManager = ccnHandle.getNetworkManager();

	try {
	    myPublicKeyDigest = networkManager.getCCNDId();
	    
	    Logger.msg("Connected to " + getMyNodeId() + " over " + getIPProto() + "."); 
	    Logger.msg("Our public key digest is: " + myPublicKeyDigest.toString());
	    
	    contentName = ContentName.fromURI(ccnURI);
	    /* Register prefix so we'll get interests. */
	    Logger.msg("Listening for prefix: " + ccnURI);
	    ccnHandle.registerFilter(contentName, this);
	    
	}
	catch (Exception e) {
	    Logger.msg("Error opening connection: " + e.getMessage());
	}
    }
    
    public void closeConnection() {

	try {
	    Logger.msg("Unregistering prefix: "+ccnURI);
	    // Unregister prefix to cleanup
	    ccnHandle.unregisterFilter(contentName,this);
	    
	    Logger.msg("Shutting down...");	
	    // Kill connection with ccnd
	    ccnHandle.close();
	}

	catch (Exception e) {
	    Logger.msg("Error closing connection: " + e.getMessage());
	}


	if (numRequestFulfilled > 0) {
	    Logger.msg("Number of Interests to fulfill request: " + numRequestFulfilled);
	    Logger.msg("Time to fulfill request for successful Interest: " + requestFulfilledTime + " milliseconds.");
	    Logger.msg("Time to fulfill request from first Interest: " + totalTimeToFulfillRequest + " milliseconds.");
	}
	
    }
    
    /** 
     * Send an interest asking for the next move
     */
    public Move getMove(int gameID, int moveNum) { 
	String uri = tttCCNxPrefix + "/" + gameID + "/move/" + moveNum;
	ContentObject co;
	
	co = sendRequest(uri);
	
	Logger.msg("Asking for move #.." + moveNum);
	
	return ContentObjectToMove(co);
    }

    /**
     * Create a Content Object and make it available
     */
    public void putMove(int gameID, int moveNum, Move move) {
	String uri = tttCCNxPrefix + "/" + gameID + "/move/" + moveNum;
	Logger.msg("Sending move..");

	try {
	    contentName = ContentName.fromURI(uri);
	    /* Register prefix so we'll get interests. */
	    Logger.msg("Listening for prefix: " + uri);	
	    ccnHandle.registerFilter(contentName, this);
	}
	
	catch (Exception e) {
	    Logger.msg("Failed publishing game state.");
	    e.printStackTrace();
	}
	
	ContentObject co = createContentObject(contentName, (Object) move);
	
	contentData = co;
    } 
    

    public boolean handleInterest(Interest i) {
	
	Logger.msg("Got an Interest from: ");// + i.publisherID().hashCode());// TODO: can't figure out where the Interest is coming from?
	
	if (i.matches(contentData)) {
	    Logger.msg("MATCH, send the CO back.");
	    // Only send it back once, receiver has to re-request if lost
	    sendObject(contentData);
	    
	    //	    remotePublicKeyDigest = i.publisherID();
	    interestSatisfied = true; //FIXME: could cause problems

	    Logger.msg("Unregistering prefix: "+ccnURI);
	    // Unregister prefix to cleanup
	    ccnHandle.unregisterFilter(i.getContentName(),this);
	}
	
	return true;
    }
    
    /** No need to use this if we use a blocking get() in sendRequest() */
    public Interest handleContent(ContentObject co, Interest in) {
	
	Logger.msg("Got a Content Object.");

	return null;
    }

    public ContentObject sendRequest() {
	
	if (contentName != null)
	    return sendRequest(contentName);
	else return null;
    }
    
    public ContentObject sendRequest(String cURI) {
	// TODO: sanity checks here on URI
	ContentName cName = new ContentName("");

	try {
	    cName = ContentName.fromURI(cURI);
	    
	}
	catch (MalformedContentNameStringException e) {
	    System.err.println("Invalid CCN URI: " + cURI + ": " + e.getMessage());
	}
	
	return sendRequest(cName);
    }
    
    /* 
     * Sends one interest packet for a CN.
     */
    public ContentObject sendRequest(ContentName cName) {
	ContentObject co;
	Interest i = new Interest(cName);
	
	Logger.msg("Sending request for:" + cName.toString());
	
	try {
	    if (! i.validate()) {
		Logger.msg("Error in Interest creation!");
		throw new Exception();
	    }
	    
	    do {
		co = ccnHandle.get(i, NETWORK_TIMEOUT);
	    } while (co == null);
	    
	    //ccnHandle.expressInterest(i, this);
	    //ccnHandle.cancelInterest(i, this);
	}
	
	catch (Exception e) {
	    // Something went wrong
	    Logger.msg("Exception: "+ e.getMessage());
	    return null;
	}
	
	return co;
    }
    
    public void sendObject(ContentObject co) {
	
	try {
	    ccnHandle.put(co);
	}

	catch (Exception e) {
	    Logger.msg("Error sending ContentObject.");
	}
    }
    
    private String getIPProto() {
	String proto;

	switch (networkManager.getProtocol()) {
	case TCP:
	    proto =  new String("TCP");
	    break;
	case UDP:
	    proto =  new String("UDP");
	    break;
	default:
	    proto =  new String("Unknown Protocol");
	    break;
	}

	return proto;
    }

    
    /** Use short fingerprint for our network node ID */
    public String getMyNodeId() {
	String name = "";
	PublisherID p = null;
	
	//name = networkManager.getCCNDId().shortFingerprint();
	p = new PublisherID(myPublicKeyDigest);
	name = Integer.toString(p.hashCode());
	    	    
	return name;
    }

        /** Use short fingerprint for our network node ID */
    public String getRemoteNodeId() {
	String name = "";
	
	// we get this off the Interest from handleInterest()
	return remotePublicKeyDigest.toString();
    }


    private ContentObject createContentObject(ContentName name, Object o) {
	ObjectOutputStream os = null;
	ByteArrayOutputStream bos = null;
	Logger.msg ("Creating CO:");
	
	try {
	    bos = new ByteArrayOutputStream();
	    os = new ObjectOutputStream(bos);
	    os.writeObject(o);
	    os.close();
	    
	    byte[] data = bos.toByteArray();
	    
	    return ContentObject.buildContentObject(name, data);
	}
	
	catch (Exception e) {
	    e.printStackTrace();
	}

	return null;
    }

    private Player ContentObjectToProfile(ContentObject co) {
	Player p = null;
      
	ObjectInputStream is = null;
	ByteArrayInputStream bis = null;

	Logger.msg("Disassembling Profile CO:");

	byte[] data = co.content();

	try {

	    bis = new ByteArrayInputStream(data);
	    is = new ObjectInputStream(bis);

	    p = (Player) is.readObject();
	}
	
	catch (Exception e) {
	    e.printStackTrace();
	}

	return p;
    }
    
    private Move ContentObjectToMove(ContentObject co) {
	Move m = null;

	ObjectInputStream is = null;
	ByteArrayInputStream bis = null;

	Logger.msg("Disassembling Move CO:");

	byte[] data = co.content();

	try {
	    bis = new ByteArrayInputStream(data);
	    is = new ObjectInputStream(bis);

	    m = (Move) is.readObject();
	}
	
	catch (Exception e) {
	    e.printStackTrace();
	}

	return m;
    }

    private TicTacToe ContentObjectToGame(ContentObject co) {
	TicTacToe game = null;

	ObjectInputStream is = null;
	ByteArrayInputStream bis = null;

	Logger.msg("Disassembling Game CO:");

	byte[] data = co.content();

	try {
	    bis = new ByteArrayInputStream(data);
	    is = new ObjectInputStream(bis);

	    game = (TicTacToe) is.readObject();
	}
	
	catch (Exception e) {
	    e.printStackTrace();
	}

	return game;
    }

    public void putProfile(int gameID, Player p) {
	ccnURI = tttCCNxPrefix + "/" + gameID +"/player";

	try {
	    contentName = ContentName.fromURI(ccnURI);
	    /* Register prefix so we'll get interests. */
	    Logger.msg("Listening for prefix: " + ccnURI);
	}

	catch (Exception e) {
	}
	
	ContentObject co = createContentObject(contentName, (Object) p);
	contentData = co;
    }

    public Player getProfile(int gameID) {
	String uri = tttCCNxPrefix + "/" +gameID + "/player";
	
	ContentObject co = sendRequest(uri);
	
	return ContentObjectToProfile(co);
    }

    public void putGame(TicTacToe game) {
	String uri = tttCCNxPrefix + "/newgame";

	try {
	    contentName = ContentName.fromURI(uri);
	    /* Register prefix so we'll get interests. */
	    Logger.msg("Listening for prefix: " + uri);	
	    ccnHandle.registerFilter(contentName, this);
	}

	catch (Exception e) {
	    Logger.msg("Failed publishing game state.");
	    e.printStackTrace();
	}
	
	ContentObject co = createContentObject(contentName, (Object) game);
	contentData = co;
    }

    	
    public TicTacToe getGame() {
	String uri = tttCCNxPrefix + "/newgame";
	
	ContentObject co = sendRequest(uri);
	
	if (interestSatisfied) {
	    Logger.msg("We've sent our game state.");
	    return null; // we initiated, our state is valid
	}
	
	return ContentObjectToGame(co);
    }
    
}

/*

- How to link Move to TicTacToe logic? applyMove needs to take x,y?
-- Tile identifies the which player is which (X is first, O is second)

- Why have a list of Interests or ContentObjects? Isn't the current enough?
-- do we need an old move in case of disconnection for a long time
- After getMove() and get a CO, then switch turns

*/
