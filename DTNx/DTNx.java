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

public class DTNx implements CCNInterestHandler, CCNContentHandler {
	public static final int INTEREST_TIMEOUT = 1000 * 60;
	public static final int PERIODIC_RETRANSMISSION_INTERVAL = 1000 * 10;
	
	protected CCNHandle handle;
	protected List<Interest> interests = new ArrayList<Interest>();
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
		
		if (! interests.contains(i)) {
		    System.out.printf("Received new interest %s\n", i.toString());
		    interests.add(i);
		    interestsRecvd.add(System.currentTimeMillis());
		}
		else {
		    System.out.printf("Interest already cached, extending lifetime.\n");
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
		
		System.out.printf("%d interests in retransmission list.\n", n);
		
		for (i=0;i<n;i++) {
			Interest in = interests.get(i);
			long recvd = interestsRecvd.get(i);
					
			if (System.currentTimeMillis() - INTEREST_TIMEOUT > recvd) {
				/* The interest has timed out. */
				System.out.printf("Dropping interest %s.\n", in.toString());
				interests.remove(i);
				interestsRecvd.remove(i);
				n--;
				i--;
			} else {
				/* Retransmit. */
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
		interests.remove(i);
		interestsRecvd.remove(i);
		
		return null;
	}
	
	public static void main(String[] argv) throws Exception {
		DTNx d = new DTNx(CCNHandle.getHandle());
		d.init();
	}
}

