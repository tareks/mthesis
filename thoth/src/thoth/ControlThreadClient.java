package thoth;

// File access
import java.io.*;

// Sync broadcast
import java.net.*;

class ControlThreadClient {

    private void sendPacket(boolean resume) {
	String message;

	try {
	    socket = new DatagramSocket();
	    //	    socket.setSoTimeout(500); 
	    if (resume) {
		message = new String(RESUME_MSG);
	    } else message  = new String(PAUSE_MSG);
	    
	    serverAddr= InetAddress.getByName(SERVER_IP);

	    msg = new DatagramPacket(message.getBytes(), message.length(), serverAddr, SERVER_PORT);
	    
	    socket.send(msg);
	    Thread.sleep(100); // give server some time to process this msg
	    socket.close();
	}
	catch (Exception e) { System.out.println("sendPacket error: " + e); }

    }

    public void resume() {
	sendPacket(true);

	System.out.println("Asked server to resume.");
    }

    public void pause() {
	sendPacket(false);
	System.out.println("Asked server to pause.");
    }
    
    ControlThreadClient() {

    }

    private DatagramSocket socket;
    private DatagramPacket msg;

    private InetAddress serverAddr;
    private int SERVER_PORT = 1894;
    private String SERVER_IP = "192.168.122.254";
    private String RESUME_MSG = "engage";
    private String PAUSE_MSG = "pause";

}