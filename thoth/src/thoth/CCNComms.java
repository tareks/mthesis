/*
 * DTComms
 * Description:
 *     This file provides the CCN communication support for opportunistic 
 *     nodes in a delay tolerant environment/network.
 *
 * 
 *
 *
 *
 */

package thoth;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.logging.Level;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.impl.CCNNetworkManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
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

    // Constructors 
    public CCNComms(String resourceURI) throws MalformedContentNameStringException {
	//_rsURI = uri; // unnecessary?
	_contentName = ContentName.fromURI(resourceURI);
    }
    
    // Public Methods
    // TODO: can probably overload this to specify URI
    public void createConnection() throws ConfigurationException, IOException {
	Log.warning("Connecting to CCN network..."); 

	_connection = CCNHandle.open();
	// get CCNNetworkManager reference
	_networkManager = _connection.getNetworkManager();
	
	Log.warning("Connected to " + getCCNDPublicKey() + " over " + getIPProto() + "."); 
	
    }
    
    public void closeConnection() { // throws something?
	Log.warning("Killing network connection ..."); 
	try {
	    _networkManager.shutdown();
	    _connection.close();
	}
	
	catch (Exception e) {
	    // ignore errors here 
	}
	Log.warning("Disconnected."); 
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
	    Log.warning("IOException: "+ e.getMessage());
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
	    Log.warning("IOException: "+ e.getMessage());
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

    public String getMyPublicKey() {
	PublisherPublicKeyDigest keyDigest = _connection.getDefaultPublisher();
	
	if ( keyDigest.validate() ) 
	    return keyDigest.toString();
	else 
	    return ""; // FIXME
    }

    public String getMyPublicKeyShort() {
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
    //private String _rsURI;
    private final ContentName _contentName;
    private CCNHandle _connection;
    private CCNNetworkManager _networkManager;
    
    // Contants
    private static int BLOCK_SIZE = 512;
    private static int NETWORK_TIMEOUT = 10000; //ms
}