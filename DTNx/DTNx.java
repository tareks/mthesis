import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ccnx.ccn.CCNContentHandler;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

import java.util.logging.Level;
import org.ccnx.ccn.impl.support.Log;

public class DTNx implements CCNInterestHandler, CCNContentHandler {
    private int INTEREST_TIMEOUT = 1000 * 60;
    private int PERIODIC_RETRANSMISSION_INTERVAL = 500; /*ms*/

    protected CCNHandle handle;
    protected List<ContentName> interests = new ArrayList<ContentName>();
    protected List<Long> interestsRecvd = new ArrayList<Long>();
    /*
      TODO: Use tuple instead?
    */
    
    protected class PeriodicTransmitter implements Runnable {
	public void run() {
	    while (true) {
		try {
		    retransmitInterests();
		} catch (IOException ioe) {
		    System.err.println(ioe.toString());
		    ioe.printStackTrace();
		}
				
		try {
		    Thread.sleep(PERIODIC_RETRANSMISSION_INTERVAL);
		} catch (InterruptedException ioe) {
		    /* Ignore this. */
		}
	    }
	}
    }
	
    public DTNx(CCNHandle h) {
	handle = h;
    }

    public DTNx(CCNHandle h, int retransmit, int lifetime) {
	this(h);
	        
	INTEREST_TIMEOUT = lifetime;
	PERIODIC_RETRANSMISSION_INTERVAL = retransmit;
		

    }
	
    public void init() throws IOException {
	try {
	    handle.registerFilter(ContentName.fromURI("ccnx:/"), this);
	} catch (MalformedContentNameStringException mcnse) {
	    /* Should never happen. */
	}
		
	new Thread(new PeriodicTransmitter()).start();
    }
	
    @Override
	public synchronized boolean handleInterest(Interest i) {
		
	    if (! interests.contains(i.getContentName())) {
		Log.warning("Received new interest: " + i.getContentName().toString());
		interests.add(i.getContentName());
		interestsRecvd.add(System.currentTimeMillis());
	    }
	    else {
		Log.warning("Interest already cached, extending lifetime.\n");
		interestsRecvd.set(interests.indexOf(i), 
				   interestsRecvd.get(interests.indexOf(i)) + INTEREST_TIMEOUT);
	    }
	    /* We never satisfy interests ourselves, we just store
	     * them and retransmit them periodically. */
	    return false;
	}
	
    protected synchronized void retransmitInterests() throws IOException {
	int i;
	int n = interests.size();
		
	Log.warning(n + " interests in retransmission list.");
		
	for (i=0;i<n;i++) {
	    ContentName cn = interests.get(i);
	    long recvd = interestsRecvd.get(i);
					
	    if (System.currentTimeMillis() - INTEREST_TIMEOUT > recvd) {
		/* The interest has timed out. */
		Log.warning("Dropping interest " + cn.toString());
		interests.remove(i);
		interestsRecvd.remove(i);
		n--;
		i--;
	    } else {
		/* Retransmit. */
		Interest in = new Interest(interests.get(i));
		handle.expressInterest(in, this);
	    }
	}
    }

    @Override
	public synchronized Interest handleContent(ContentObject co, Interest in) {
	    /* We don't actually care about the content object. An interested node
	     * will re-express in interest in time, and then we can answer that
	     * interest from the cache. Thus, all we do here is remove the 
	     * now satisfied interests from our retransmission list. 
	     */
	    int i = interests.indexOf(in);
	    if (i >= 0) {
		interests.remove(i);
		interestsRecvd.remove(i);
	    }
		
	    return null;
	}
	
    public static void main(String[] argv) throws Exception {
	DTNx d;
	/* 
	 * INTEREST TIMEOUT = lifetime of the Interest for which we keep retransmitting in ms
	 * PERIODIC_RETRANSMISSION_INTERVAL = retransmit each interest every X ms
	 */
	if (argv.length > 0) {
	    d = new DTNx(CCNHandle.getHandle(), Integer.parseInt(argv[0]), Integer.parseInt(argv[1]));
	}
	else
	    d = new DTNx(CCNHandle.getHandle());

	Log.setLevel(Log.FAC_ALL, Level.WARNING);
	    
	    
	d.init();
    }
}

