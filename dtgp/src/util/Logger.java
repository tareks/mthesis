
package dtgp.util;

import java.util.logging.Level;
import org.ccnx.ccn.impl.support.Log;

public final class Logger {

	private Logger() {
		_debug = true;
	}

	public static void msg(String s) {
		if (!_debug) return;
	
		Log.info(s);
		System.out.println(s);
	}	

	public static void setLevel() {

	if (_debug == true)
	    Log.setLevel(Log.FAC_ALL, Level.INFO);
	else 
	    Log.setLevel(Log.FAC_ALL, Level.OFF);

	}

	public static void enable() {
		_debug = true;
	}

	public static void disable() {
		_debug = false;
	}

	private static boolean _debug;
}
