import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    private int SEMA_TIMEOUT = 3000; /* must be less than 4s or ccnd retransmits*/

    protected CCNHandle handle;
    private Semaphore sema;
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

	sema = new Semaphore(0);
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
		Log.warning("Interest already cached, ignoring.\n");
/*		Log.warning("Interest already cached, extending lifetime.\n");
		interestsRecvd.set(interests.indexOf(i), 
				   interestsRecvd.get(interests.indexOf(i.getContentName())) + INTEREST_TIMEOUT); */
	    }
	    /* We never satisfy interests ourselves, we just store
	     * them and retransmit them periodically. */
	    return false;
	}
	
/* 
 * When retransmitting, we:
 * 1) Create a new Interest
 * 2) cancelInterest() to remove it from the PIT when its satisfied or expires
 *
 * otherwise, they all get marked as dupes and nothing gets sent over the wire.
 *
 */
    protected synchronized void retransmitInterests() throws IOException {
	int i;
	int n = interests.size();
		
	Log.warning(n + " interests in retransmission list.");
		
	for (i=0;i<n;i++) {
	    ContentName cn = interests.get(i);
	    long recvd = interestsRecvd.get(i);
					
	    if (System.currentTimeMillis() - INTEREST_TIMEOUT > recvd) {
		/* The interest has timed out. */
		Log.warning("Timeout: Dropping interest " + cn.toString());
		Interest in = new Interest(cn);
		handle.cancelInterest(in, this);
		interests.remove(i);
		interestsRecvd.remove(i);
		n--;
		i--;
	    } else {
		/* Retransmit. */
		Interest in = new Interest(interests.get(i));
		Log.warning("Retransmitting Interest: " + cn.toString());
		/* express then wait for 3s and cancel so we control transmission */
		try {
		    handle.expressInterest(in, this);
		    sema.tryAcquire(SEMA_TIMEOUT, TimeUnit.MILLISECONDS);
		    handle.cancelInterest(in,this);
		}
		catch (Exception e) {
		    Log.warning("Failed to express and cancel interest!");
		}
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
	    ContentName cn = in.getContentName();
	    int i = interests.indexOf(cn);
	    if (i >= 0) {
		Log.warning("Got Matching CO: Dropping interest " + in.getContentName().toString());
		//handle.cancelInterest(in, this);
		sema.release();
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

