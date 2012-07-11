 /*
 * DTComms
 * Description:
 *     This file provides the CCN communication support for opportunistic 
 *     nodes in a delay tolerant environment/network.
 *
 */

package thoth;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.ccnx.ccn.CCNFilterListener;
import org.ccnx.ccn.CCNInterestListener;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.impl.CCNNetworkManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Exclude;
import org.ccnx.ccn.protocol.ExcludeComponent;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ccnx.ccn.io.CCNFileOutputStream;
import org.ccnx.ccn.io.CCNFileInputStream;
import org.ccnx.ccn.io.CCNOutputStream;
import org.ccnx.ccn.io.CCNInputStream;

/*
 * HOWTO:
 *
 * DTComms("ccnx:/thoth/data")
 * createConnection(); // uses uri passed to constructor
 * ...
 * getMyPublicKey();
 * getMyPublicKeyShort();
 * getContentName();
 * ...
 * closeConnection();
 */
public final class CCNComms {

    /* 
     * This class provides the callback for the receiver waiting for 
     * responses to Interest packets.
     */
    class ReceiverListener implements CCNInterestListener {

	public Interest handleContent(ContentObject co,
				      Interest interest) {
	    Log.info("Received data response for Interest: " + interest.toString());
	    _receiverGotData = true;
	    contentData = co;
	    filterSema.release();
	    
	    return null;
	}
    }

    /* 
     * This class provides the callback for the sender waiting for 
     * Interest packets.
     */
    class SenderListener implements CCNFilterListener {
	
	public boolean handleInterest(Interest interest) {
	    
	    Log.info("Received an interest: " + interest.toString());
	    
	    _senderGotInterest = true;
	    filterSema.release();

	    return true;
	}
	
    }
    
    // Constructors 
    public CCNComms(String resourceURI) throws MalformedContentNameStringException {
	_ccnURI = resourceURI;
	_contentName = ContentName.fromURI(resourceURI);
	
	// Don't use CCN*streams by default
	nodeNetMode = NodeNetworkMode.NODE_USES_MANUAL_REQUESTS;
	//nodeNetMode = NodeNetworkMode.NODE_USES_CCNX_STREAMS;

	_rcvrListener = new ReceiverListener();
	_sndrListener = new SenderListener();

	filterSema = new Semaphore(0);

    }
    
    public void createConnection() throws ConfigurationException, IOException {
	Log.info("Connecting to CCN network..."); 
	
	_connection = CCNHandle.open();
	_networkManager = _connection.getNetworkManager();
	
	Log.info("Connected to " + getCCNDPublicKey() + " over " + getIPProto() + "."); 
	
    }
    
    public void closeConnection() { 
	Log.info("Killing network connection ..."); 
	try {
	    _networkManager.shutdown();
	    _connection.close();
	}
	
	catch (Exception e) {
	    // ignore errors here 
	}
	Log.info("Disconnected."); 
    }

    /* 
     * Creates and sends one interest packet on behalf of a receiver node.
     */
    public ContentObject sendRequest() {
	Interest i = new Interest(_contentName);

	_receiverGotData = false;
	try {
	    if (! i.validate()) {
		Log.info("Error in Interest creation!");
		throw new Exception();
	    }
	     Log.info("Interest text: " + i.toString());

	     _connection.expressInterest(i, _rcvrListener);
	     filterSema.tryAcquire(SEMA_TIMEOUT, TimeUnit.MILLISECONDS);

	     // cancel before 2000ms so that we  control re-expression
	     _connection.cancelInterest(i, _rcvrListener);
	     if (_receiverGotData) {
		 return contentData;
	     } 
	}
	
	catch (Exception e) {
	    // Something went wrong
	    Log.info("Exception: "+ e.getMessage());
	}
	
	return null;
    }

    public void setInterestFilter() {
        try {
	    _networkManager.setInterestFilter(this, _contentName, _sndrListener);
        } catch (IOException ioe) {
	    Log.info("Exception: "+ ioe.getMessage());
        }
    }
    
    public void cancelInterestFilter() {
	_networkManager.cancelInterestFilter(this, _contentName, _sndrListener);
    }
    
    /* 
     * Register a listener for interests and handle them if we get any.
     */
    public Interest handleInterests() {
	
	_senderGotInterest = false;
	try {
	    // Keep waiting for an interest 
	    while (! _senderGotInterest) {
		Log.info("Listening for interests..");
		filterSema.tryAcquire(SEMA_TIMEOUT, TimeUnit.MILLISECONDS);
	    }
	    
	    /* Got an Interest we need to process */

	    // Build a reference Interest to match CO received against
	    Interest i = new Interest(_contentName); 

	    return i;
	}
	
	catch (Exception e) {
	    // Something went wrong
	    Log.info("Exception: "+ e.getMessage());
	}

	return null;
    }
    
    public void sendObject(ContentObject co) {
	
	try {
	    _networkManager.put(co);
	}

	catch (Exception e) {
	    Log.info("Error sending ContentObject.");
	}
    }

    public NodeNetworkMode getNetworkMode() {
	return nodeNetMode;
    }
    
    public void setNetworkMode(NodeNetworkMode mode) {
	nodeNetMode = mode;
    }

    /* 
     * Attempts to read requested data from the CCN network and write
     * that data to a local file. Interest packets are issued continously
     * until the request is satisfied. Randomize the generation to avoid
     * deadlocks with senders.
     *
     * For now, follows behavior similar to writeFileToNetwork().
     * TODO: allow unversioned (no meta) reads from network
     */
    public long readFileFromNetwork(OutputStream outStream) throws IOException {
	long bytesRead = 0;
	//	CCNInputStream inStreamRaw;
	CCNFileInputStream inStream;
	int blksz = BLOCK_SIZE;
	int readLen = 0;
	byte [] buffer = new byte[BLOCK_SIZE];
	
	try {
	    // Versioned write to outStream (includes metadata)
	    inStream = new CCNFileInputStream(_contentName, _connection);
	    inStream.setTimeout(NETWORK_TIMEOUT);

	    // Send an interest and write any results back to file
	    while ((readLen = inStream.read(buffer)) != -1){
		bytesRead +=readLen;
		outStream.write(buffer, 0, readLen);
		outStream.flush();
	    }
	    
	    inStream.close();
	}

	catch (IOException e) {
	    // Timeout or error 
	    Log.info("IOException: "+ e.getMessage());
	    bytesRead = -1;
	}
	
	return bytesRead;
    }
    
    /* 
     * This can:
     * 1) Do a RAW write an InputStream directly to a CCNOutputStream
     * 2) Do a 'versioned'(?) write to a stream?
     * 3) Use CCNFlowControl to manage listening and sending simultaneously 
     * 4) Manually control everything?
     * TODO: allow unversioned raw (no file metadata) writes to the network
     */
    public long writeFileToNetwork(InputStream inStream) throws IOException {
	long bytesWritten=0;
	//	CCNOutputStream outStreamRaw;
	CCNFileOutputStream outStream;
	int readLen=0;
	int blksz = BLOCK_SIZE;
	byte[] buffer = new byte[BLOCK_SIZE];
	
	try {
	    // Versioned write to outStream (includes metadata)
	    outStream = new CCNFileOutputStream(_contentName, _connection);
	    outStream.setTimeout(NETWORK_TIMEOUT);

	    while ((readLen = inStream.read(buffer, 0, blksz)) != -1) {
		outStream.write(buffer, 0, readLen);
		bytesWritten+=readLen;
	    }

	    outStream.close();
	}
	
	catch (IOException e) {
	    // assume we timed out or error occurred while writing
	    Log.info("IOException: "+ e.getMessage());
	    bytesWritten = -1;
	}
	
	return bytesWritten;
    }

    /* getHandle() doesn't really return existing handle - exception should take care of this? 
    public boolean isConnected() {
	if (_connection == _connection.getHandle()) {
	    return true;
	} else 
	    return false;
    }
    */

    /*
     * Returns the name of the content in CCN format without prefix (ie. /data)
     */
    public String getContentName() {
	return _contentName.toString();
    }

    public PublisherPublicKeyDigest getMyPublicKey() {
	PublisherPublicKeyDigest keyDigest = _connection.getDefaultPublisher();
	
	return keyDigest;
    }

    public String getMyPublicKeyString() {
	PublisherPublicKeyDigest keyDigest = _connection.getDefaultPublisher();
	
	if ( keyDigest.validate() ) 
	    return keyDigest.toString();
	else 
	    return ""; // FIXME
    }

    public String getMyPublicKeyShortString() {
	PublisherPublicKeyDigest keyDigest = _connection.getDefaultPublisher();
	
	if ( keyDigest.validate() ) 
	    return keyDigest.shortFingerprint();
	else 
	    return ""; // FIXME
    }

    // Internal Methods
    private String getIPProto() {
	String proto;

	switch (_networkManager.getProtocol()) {
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

    private String getCCNDPublicKey() throws IOException {
	return _networkManager.getCCNDId().toString();
    }

    private String getCCNDPublicKeyShort() throws IOException {
	return _networkManager.getCCNDId().shortFingerprint();
    }



    // Variables
    private String _ccnURI;
    private final ContentName _contentName;
    private ContentObject contentData;
    private CCNHandle _connection;
    private CCNNetworkManager _networkManager;
    private NodeNetworkMode nodeNetMode;
    	    
    private boolean _senderGotInterest = false;
    private boolean _receiverGotData = false;

    private SenderListener _sndrListener;
    private ReceiverListener _rcvrListener;

    private Semaphore filterSema;

    // Contants
    private static int BLOCK_SIZE = 512;
    private static int NETWORK_TIMEOUT = 10000; //ms OR SystemConfiguration.getDefaultTimeout(); ?
    private static int LISTEN_TIMEOUT = 5000; //ms OR SystemConfiguration.getDefaultTimeout(); ?
    protected static final int SEMA_TIMEOUT = 2000; // if this is too big, ccnd will resend interests before they expire - expressInterest() will re-express Interests every 2000ms until cancelled.
    
    public enum NodeNetworkMode { 
	NODE_USES_CCNX_STREAMS, // Use CCN*Streams
	NODE_USES_MANUAL_REQUESTS; // Manual Interest and Content handling
    }

}