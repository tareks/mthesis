
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
    
    private CCNHandle ccnHandle;
    private CCNNetworkManager networkManager;
    private ContentName contentNameDefault = null;
    private ContentObject contentData = null;
    private ContentName contentName = null;
    private boolean gotMatchingContent = false;
    private boolean sentMatchingContent = false;
    private PublisherPublicKeyDigest myPublicKeyDigest, remotePublicKeyDigest;

    private final String tttCCNxPrefix = "/uu/core/games/ttt";
    private final String tttCCNxNewGamePrefix = "/uu/core/games/ttt/new";
    private final String tttCCNxEndGamePrefix = "/uu/core/games/ttt/fin";
    private TicTacToe gameObject;
    private boolean isInitiatorNode;

    // Constants
    private static int NETWORK_TIMEOUT = 5000; //ms 
    private static int MAX_NETWORK_TIMEOUT = 60000; //ms 

    public CCNxNetwork() {
	String defaultURI = "";
	
	isInitiatorNode = false;

	try {
	    contentNameDefault = ContentName.fromURI(defaultURI);
	}
	catch (Exception e) {
	    
	}
    }

    public void openConnection() {
	
	Logger.msg("Connecting to CCN network..."); 	
	
	ccnHandle = CCNHandle.getHandle();
	networkManager = ccnHandle.getNetworkManager();

	try {
	    myPublicKeyDigest = networkManager.getCCNDId();
	    
	    Logger.msg("Connected to " + getMyNodeId() + " over " + getIPProto() + "."); 
	    Logger.msg("Our public key digest is: " + myPublicKeyDigest.toString());
	    
	    /* Register prefix so we get all interests. */
	    Logger.msg("Listening for prefix: " + contentNameDefault.toString());
	    ccnHandle.registerFilter(contentNameDefault, this);
	}
	catch (Exception e) {
	    Logger.msg("Error opening connection: " + e.getMessage());
	}
    }
    
    public void closeConnection() {

	try {
	    Logger.msg("Unregistering prefix: " + contentNameDefault.toString());
	    // Unregister prefix to cleanup
	    ccnHandle.unregisterFilter(contentNameDefault,this);
	    
	    Logger.msg("Shutting down...");	
	    // Kill connection with ccnd
	    ccnHandle.close();
	}

	catch (Exception e) {
	    Logger.msg("Error closing connection: " + e.getMessage());
	}

    }
    
    /**
     * 
     */
    private void unsetURIListener() {
	
	if (contentName ==  null) return;

	try {
	    Logger.msg("Unregistering prefix: " + contentName.toString());
	    // Unregister prefix to cleanup
	    ccnHandle.unregisterFilter(contentName,this);
	    // reset contentName here
	    contentName = ContentName.fromURI("");
	}
	catch (Exception e) { 
	    Logger.msg("Failed to unregister: " + contentName.toString());
	    e.printStackTrace();
	}
    }

    
    /** 
     * Adds the URI to our filter list.
     */
    private void setURIListener(String uri) {
	try {
	    contentName = ContentName.fromURI(uri);
	    /* Register prefix so we'll get interests. */
	    Logger.msg("Listening for prefix: " + contentName.toString());	
	    ccnHandle.registerFilter(contentName, this);
	}
	
	catch (Exception e) {
	    Logger.msg("Failed to register: " + uri);
	    e.printStackTrace();
	}
    }

    public boolean handleInterest(Interest i) {
	
	Logger.msg("Got an Interest: " + i.getContentName().toString()); // TODO: We can't figure out where the Interest is coming from..
		
	/* 
	 * For new game Interests, we construct the CO with the gameID on the fly
	 * and send it back.
	*/
	String uri = i.getContentName().toString();
	if (isInitiatorNode && gameObject.isNew() && uri.startsWith(tttCCNxNewGamePrefix)) {
	  // Catch new game requests and satisfy them
	    String uriEnd = uri.substring(uri.lastIndexOf("/") + 1);
	    
	    Logger.msg("New game Interest: GameID = " + uriEnd);
	    gameObject.setGameId(Integer.parseInt(uriEnd));
	    gameObject.markStarted();
	    
	    contentData = createContentObject(i.getContentName(), (Object) gameObject);
	}
	
	if (contentData == null) {
	    Logger.msg("We are waiting for nothing. Ignoring this request.");
	    return true;
	} else {
	    // FIXME: If we are waiting for move/1 for too long, we need to timeout and return null

	    Logger.msg("We are waiting for: " + contentData.getContentName().toString());
	}

	if (i.matches(contentData)) {
	    Logger.msg("MATCH, sending the CO back for: " + contentData.getContentName().toString());
	    
	    sendObject(contentData);
	    contentData = null;
 	    //	    unsetURIListener();
	    sentMatchingContent = true; 
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
	ContentName cName = null;

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
	ContentObject co = null;
	Interest i = new Interest(cName);
	
	Logger.msg("Sending request for:" + cName.toString());
	
	try {
	    if (! i.validate()) {
		Logger.msg("Error in Interest creation!");
		throw new Exception();
	    }
	    
	    gotMatchingContent = false;
	    while (true) {
		Logger.msg("GET LOOP: Sending: " + i.name());
		co = ccnHandle.get(i, NETWORK_TIMEOUT);
		
		if ( co != null) {
		    Logger.msg("Exiting loop, Got CO:" + co.getContentName().toString());
		    gotMatchingContent = true;
		    break;
		}
		if (sentMatchingContent) {
		    Logger.msg("Exiting loop, We sent back a  CO!");
		    // this should only happen for game COs
		    // move COs should not be affected or we get nulls
		    break;
		}
	    } 

	    //ccnHandle.expressInterest(i, this);
	    //ccnHandle.cancelInterest(i, this);
	}
	
	catch (Exception e) {
	    // Something went wrong
	    Logger.msg("Problem sending Interest: "+ e.getMessage());
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
	ObjectOutputStream os = null;	ByteArrayOutputStream bos = null;
	Logger.msg ("Creating CO: " + name.toString());
	
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
	// 
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

	Logger.msg("Disassembling CO :" + co.getContentName().toString());

	byte[] data = co.content();

	try {
	    bis = new ByteArrayInputStream(data);
	    is = new ObjectInputStream(bis);

	    game = (TicTacToe) is.readObject();
	}
	
	catch (Exception e) {
	    e.printStackTrace();
	}

	Logger.msg("Disassembled Game object with ID: " + game.gameId());

	return game;
    }

    public void putProfile(int gameID, Player p) {
	String uri = tttCCNxPrefix + "/" + gameID +"/player";
	ContentName cname = null;

	setURIListener(uri);

	contentData = createContentObject(contentName, (Object) p);
    }

    public Player getProfile(int gameID) {
	String uri = tttCCNxPrefix + "/" +gameID + "/player";
	
	setURIListener(uri);

	ContentObject co = sendRequest(uri);
	
	return ContentObjectToProfile(co);
    }

    public TicTacToe putGame(TicTacToe game) {
	String uri = tttCCNxPrefix + "/new";
	ContentName cname = null;

	sentMatchingContent = false;

	gameObject = game;
	setURIListener(uri);

	while (true) {
	    try {
		Thread.sleep(100);
	    }
	    catch (InterruptedException e) { }
	    if (sentMatchingContent) break;
	}

	unsetURIListener();
	Logger.msg("We've sent our game object.");
	
	return gameObject;
    }

    	
    public TicTacToe getGame(int gameID) {
	String uri = tttCCNxPrefix + "/new/" + gameID;

	// initialize object
	gameObject = new TicTacToe(gameID);

	ContentObject co = sendRequest(uri);
	// TODO: check that co really matches our URI
	// we received a CO, extract game from CO
	gameObject = ContentObjectToGame(co);
	
	Logger.msg("Got new game object for ID: " + gameObject.gameId() + " - stop listening.");

	return gameObject;
    }

    /**
     * Create a Content Object and make it available
     */
    public void putMove(int gameID, int moveNum, Move move) {
	String uri = tttCCNxPrefix + "/" + gameID + "/move/" + moveNum;

	Logger.msg("Sending move..");

	sentMatchingContent = false;

	try {
	    contentName = ContentName.fromURI(uri);
	}
	catch (Exception e) {
	}

	contentData = createContentObject(contentName, (Object) move);

	setURIListener(uri);

	// Wait till we actually send a CO back on the network
	while (true) {
	    try {
		Thread.sleep(10);
	    }
	    catch (InterruptedException e) { }
	    if (sentMatchingContent) break;
	}
	
	unsetURIListener();	
    } 

    /** 
     * Send an interest asking for the next move
     */
    public Move getMove(int gameID, int moveNum) { 
	String uri = tttCCNxPrefix + "/" + gameID + "/move/" + moveNum;
	ContentObject co;

	Logger.msg("Asking for move #.." + moveNum);

	do {
	    co = sendRequest(uri);
	} while (co == null);
	//FIXME we should never get a null CO Here
       

	return ContentObjectToMove(co);
    }

    public TicTacToe getEndGame(int gameID) {
	String uri = tttCCNxPrefix + "/fin/" + gameID;
	ContentObject co;
	
	Logger.msg("Requesting game " + gameID + " to be terminated: " + uri);

	do {
	    co = sendRequest(uri);
	} while (co == null);
	
	return ContentObjectToGame(co);
	
    }

    public void putEndGame(int gameID) {
	String uri = tttCCNxPrefix + "/fin/" + gameID;
	ContentObject co;

	sentMatchingContent = false;

	Logger.msg("Preparing final game state object for: " + gameID);
	setURIListener(uri);
	contentData = createContentObject(contentName, (Object) gameObject);
	
	while (true) {
	    try {
		Thread.sleep(10);
	    }
	    catch (InterruptedException e) { }
	    if (sentMatchingContent) break;
	}
	
	unsetURIListener();	
    }

    public void setInitiator() {
	isInitiatorNode = true;
    }
}