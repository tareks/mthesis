// XML stuff
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

// File access
import java.io.*;

// ArrayList...:
import java.util.*;

// Sync broadcast
import java.net.*;

public class scenariorunner_small
{
	static boolean threadState = true; // controlThread exit flag

	private static class ControlThread extends Thread 
	{
	    private volatile boolean runFlag = true; // default is that we resume execution
	    private DatagramSocket socket;
	    
		public void run() {
		    System.out.println("ControlThread starting..");
		    this.startListening();
		}	
	    
	    public boolean checkFlag() { return runFlag; }
	    public void flipFlag() { runFlag = (!runFlag); }
	    
	    
	    public void cleanup () { threadState=false; }

	    public void startListening() {
		int LISTEN_PORT = 1894;
		String RESUME_MSG = "engage";
		String PAUSE_MSG = "pause";
		
		// get a datagram socket
		byte[] msgbuf = new byte[8];

		DatagramPacket message = new DatagramPacket(msgbuf, msgbuf.length);
		try {
		    socket = new DatagramSocket(LISTEN_PORT);
		    socket.setSoTimeout(60000); // wait for a long time, then check if we want to exit
		}
		catch (Exception e) { System.out.println("Fatal Exception while creating socket: " + e); System.exit(1);}
		
		System.out.println("Listening for control messages..");
		
		Vector<String> nodeList = new Vector<String>();
		while (threadState) {
		    System.out.println("Entering blocking receive with thread state = "+ threadState + " and runFlag = " +runFlag);
		    try {
			socket.receive(message);
			String received = new String(message.getData(), 0, message.getLength());
			
			if (received.equals(PAUSE_MSG)) {
			    String addr = message.getAddress().getHostAddress();
			    System.out.println("Node at addr = " + addr + " requested a PAUSE.");
			    // LOCK 
			    runFlag = false;
			} else if (received.equals(RESUME_MSG)) {
			    String addr = message.getAddress().getHostAddress();
			    System.out.println("Node at addr = " + addr + " requested a RESUME.");
			    // UNLOCK 
			    runFlag = true;
			} else System.out.println("Got unknown message.");
		    }
		    catch (Exception e) { /*System.out.println("ControlThread got no network messages: "+ e);*/ }
		}
		System.out.println("ControlThread exiting..");
		socket.close();
	    }
	}
    
    
	private static class action 
	{
	    public long timestamp;
	    public String cmd;
	    public boolean awaitSignal;
	    public boolean linkEnable;

	    public action(long timestamp, String cmd, boolean signal, boolean linkStatus)
		{
			this.timestamp = timestamp;
			this.cmd = cmd;
			this.awaitSignal = signal;
			linkEnable = linkStatus;
		}
	}
	
	private static class actionComparator implements Comparator<action>
	{
		public final int compare ( action a, action b )
		{
			return (int) (a.timestamp - b.timestamp);
		}
	}
	
	private static class linkTuple
	{
		public int firstNode;
		public int secondNode;
		public int startTime;
		public int stopTime;
	        public boolean awaitSignal;
		
	    public linkTuple(int _firstNode, int _secondNode, int _startTime, int _stopTime)
		{
			firstNode = _firstNode;
			secondNode = _secondNode;
			startTime = _startTime;
			stopTime = _stopTime;
			awaitSignal = false;
		}

	    public linkTuple(int _firstNode, int _secondNode, int _startTime, int _stopTime, boolean flag)
	    {
		firstNode = _firstNode;
		secondNode = _secondNode;
		startTime = _startTime;
		stopTime = _stopTime;
		awaitSignal = flag;
	    }

	}
	
        static linkTuple[] parseLinkTuples(String[] trace)
	{
		linkTuple[]	tmp, retval;
		int			i, j;
		
		tmp = new linkTuple[trace.length];
		j = 0;
		for(i = 0; i < trace.length; i++) {
			String[] brokenDown = trace[i].split("\\s+");
			if(brokenDown.length >= 4) {
				tmp[j] = new linkTuple(Integer.parseInt(brokenDown[0]), Integer.parseInt(brokenDown[1]), Integer.parseInt(brokenDown[2]), Integer.parseInt(brokenDown[3]));
				if ( (brokenDown.length == 5) && (brokenDown[4].equals("wait")) ) { // we have a signal field, store it
				    System.out.println("Parsed wait, saving.");
				    tmp[j].awaitSignal = true;
				}
				j++;
			}
			
		}
		
		if(j != trace.length) {
			retval = new linkTuple[j];
			for(i = 0; i < j; i++)
				retval[i] = tmp[i];
		}else {
			retval = tmp;
		}
		return retval;
	}
	
	static linkTuple[] preprocess(linkTuple[] tuple)
	{
		if(tuple.length == 0) {
			return tuple;
		}
		int	i,j,a,b, minNode, maxNode;
		int	minTime, maxTime, startTime, stopTime;
		int[] connectivityMap;
		boolean execFlag = false;
		ArrayList<linkTuple> retval = new ArrayList<linkTuple>();
		
		minTime = tuple[0].startTime;
		maxTime = tuple[0].startTime;
		minNode = tuple[0].firstNode;
		maxNode = tuple[0].firstNode;
		
		for(i = 0; i < tuple.length; i++)
		{
			if(minTime > tuple[i].startTime)
				minTime = tuple[i].startTime;
			if(minTime > tuple[i].stopTime)
				minTime = tuple[i].stopTime;
			if(maxTime < tuple[i].startTime)
				maxTime = tuple[i].startTime;
			if(maxTime < tuple[i].stopTime)
				maxTime = tuple[i].stopTime;
			
			if(minNode > tuple[i].firstNode)
				minNode = tuple[i].firstNode;
			if(minNode > tuple[i].secondNode)
				minNode = tuple[i].secondNode;
			if(maxNode < tuple[i].firstNode)
				maxNode = tuple[i].firstNode;
			if(maxNode < tuple[i].secondNode)
				maxNode = tuple[i].secondNode;
		}

		connectivityMap = new int[maxTime - minTime + 1];
		System.out.println("creating connectiving map array of size: " + (maxTime - minTime + 1));

		for(a = minNode; a <= maxNode; a++) {
			for(b = a+1; b <= maxNode; b++)	{
				for(i = 0; i < connectivityMap.length; i++) {
					connectivityMap[i] = 0;
				}
				for(j = 0; j < tuple.length; j++) {
					if(	tuple[j].firstNode == a && tuple[j].secondNode == b) {
						for(i = tuple[j].startTime; i < tuple[j].stopTime; i++) {
							connectivityMap[i - minTime] = connectivityMap[i - minTime] | 1;
						}
					}
					if(	tuple[j].firstNode == b && tuple[j].secondNode == a) {
						for(i = tuple[j].startTime; i < tuple[j].stopTime; i++) {
							connectivityMap[i - minTime] = connectivityMap[i - minTime] | 2;
						}
					}
				}
				
				// FIXME: call other function to do different things here...
				
				// j is the value of the previous element in the connectivity 
				// map.
				j = 0;
				startTime = 0;
				stopTime = 0;
				for(i = 0; i < connectivityMap.length; i++) {
					if(j == 0 && connectivityMap[i] != 0) {
						startTime = i + minTime;
						j = connectivityMap[i];
					}
					if(j != 0 && connectivityMap[i] == 0) {
						stopTime = i + minTime;
						j = connectivityMap[i];

						for(int x = 0; x < tuple.length; x++) {
						    execFlag = false;
						    if(tuple[x].firstNode == a && tuple[x].secondNode == b && tuple[x].startTime == startTime && tuple[x].stopTime == stopTime && tuple[x].awaitSignal) {
							execFlag = tuple[x].awaitSignal;
							break;
						    }
						}
						//System.out.println(a + ", " + b + ", " + startTime + ", " + stopTime + ", " + execFlag);
						retval.add(new linkTuple(a,b,startTime,stopTime,execFlag));
					}
				}
				if(j != 0) {
					stopTime = connectivityMap.length + minTime;
					System.out.println("2. moving signal to new struct");


					retval.add(new linkTuple(a,b,startTime,stopTime,execFlag));
				}
			}
		}
		return retval.toArray(new linkTuple[0]);
	}
	
	public static Node parseXML(String xml)
	{
		try {
			InputSource iSource = new InputSource(new StringReader(xml));
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(iSource);
			doc.getDocumentElement().normalize();
			return (Node) doc.getDocumentElement();
		}catch(Exception e) {
		}
		return (Node) null;
	}
	
	public static String getTagContent(Node node)
	{
		if(node != null) {
			NodeList fstNm = ((Element) node).getChildNodes();
			if(fstNm != null) {
				if(fstNm.getLength() > 0) {
					return ((Node) fstNm.item(0)).getNodeValue();
				}
			}
		}
		return null;
	}
	
	public static String getTagContent(Node node, String element_name, int num)
	{
		if(node != null) {
			if(node.getNodeType() == Node.ELEMENT_NODE) {
				NodeList nodes = getSubTags(node, element_name);
				if(nodes != null) {
					Element subElmnt = (Element) nodes.item(num);
					if(subElmnt != null) {
						NodeList fstNm = subElmnt.getChildNodes();
						if(fstNm != null) {
							if(fstNm.getLength() > 0) {
								return ((Node) fstNm.item(0)).getNodeValue();
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	public static String getTagContent(Node node, String element_name)
	{
		return getTagContent(node, element_name, 0);
	}
	
	public static Node getSubTag(Node node, String element_name, int num)
	{
		if(node.getNodeType() == Node.ELEMENT_NODE)	{
			// FIXME: figure out a faster way to create an empty node:
			Node tmp = node.cloneNode(false);
			while(tmp.hasChildNodes()) {
				tmp.removeChild(tmp.getFirstChild());
			}
			NodeList children = node.getChildNodes();
			int i, j;
				
			j = 0;
			for(i = 0; i < children.getLength(); i++) {
				if(children.item(i).getNodeName().equals(element_name)) {
					if(j == num) {
						return children.item(i);
					}
					j++;
				}
			}
		}
		return null;
	}
	
	public static Node getSubTag(Node node, String element_name)
	{
		return getSubTag(node, element_name, 0);
	}
	
	public static NodeList getSubTags(Node node, String element_name)
	{
		if(node.getNodeType() == Node.ELEMENT_NODE) {
			// FIXME: figure out a faster way to create an empty node:
			Node tmp = node.cloneNode(false);
			while(tmp.hasChildNodes()) {
				tmp.removeChild(tmp.getFirstChild());
			}
			NodeList children = node.getChildNodes();
			int i;
			
			for(i = 0; i < children.getLength(); i++) {
				if(children.item(i).getNodeName().equals(element_name)) {
					tmp.appendChild(children.item(i));
				}
			}
			return tmp.getChildNodes();
		}
		return null;
	}
	
	static public String getContents(String filename) 
	{
		File aFile = new File(filename);
		StringBuilder contents = new StringBuilder();
		
		try {
			BufferedReader input = new BufferedReader(new FileReader(aFile));
			try {
				String line = null;
				while((line = input.readLine()) != null) {
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}finally {
				input.close();
			}
		}catch (IOException ex) {
			ex.printStackTrace();
		}	
		return contents.toString();
	}
	
	static public String[] getLinesOfFile(String filename)
	{
		File aFile = new File(filename);
		ArrayList<String> lines = new ArrayList<String>();

		try {
			BufferedReader input = new BufferedReader(new FileReader(aFile));
			try {
				String line = null;
				while((line = input.readLine()) != null) {
					String lineWithoutComment = line.split("#")[0];
					if(!lineWithoutComment.equals("")) {
						lines.add(lineWithoutComment);
					}
				}
			}finally {
				input.close();
			}
			return lines.toArray(new String[0]);
		}catch (IOException ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	static public int system(String cmd)
	{
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedWriter stdIn;
			stdIn = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
			try {
				stdIn.close();
				p.waitFor();
				return p.exitValue();
			}catch (Exception e) {
			}
			p.waitFor();
			return p.exitValue();
		}catch(Exception e) {
		}
		return -1;
	}
	
	static public Process popen(String cmd)
	{
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			return p;
		}catch(Exception e) {
		}
		return null;
	}
	
	public static String pathFromFileName(String filename)
	{
		int i;
		String path = "";
		String[] path_element = filename.split("/");
		if(path_element != null) {
			for(i = 0; i < path_element.length-1; i++) {
				if(!path_element[i].equals("")) {
//					path += "/" + path_element[i];
					path += path_element[i] + "/";
				}
			}
		}
//		path += "/";
		return path;
	}
	
	public static String getFileName(String filename)
	{
		String[] split = filename.split("/");
		if(split == null)
			return "";
		if(split.length < 1)
			return "";
		return split[split.length-1];
	}

	public static void main(String[] args)
	{
		int i;
		Date now = new Date();
		
		// input argument check
		if(args == null)
			return;
		if(args.length < 1) {
			System.out.println("Not enough input arguments.");
			return;
		}
		
		// Scenario file name:
		String scenario_file_name = args[0];
		if(scenario_file_name == null) {
			System.out.println("No scenario file specified.");
			return;
		}

		// read and parse scenario:
		Node scenario_file = parseXML(getContents(scenario_file_name));
		if(scenario_file == null) {
			System.out.println("Unable to load scenario file " + scenario_file_name + ".");
			return;
		}
		
		// Check "magic" string:
		// (See "man file" for why it's called "magic".
		String magic = getTagContent(scenario_file, "Magic");
		if(magic == null) {
			System.out.println("No magic tag!");
			return;
		}
		if(!magic.equals("haggle"))	{
			System.out.println("Magic tag wrong!");
			return;
		}

		// Get number of iterations. If not specified the default is one iteration.
		int iterations = 1;
		String tmp = getTagContent(scenario_file, "Iterations");
		if(tmp != null) {
			iterations = Integer.parseInt(tmp);
			System.out.println("Running for " + iterations + " iterations.");
		}

		// Figure out the base path for all files:
		String scenario_path = pathFromFileName(scenario_file_name);
		
		// FIXME: check!
		
		System.out.println("scenario_path: " + scenario_path);
		
		ArrayList<action> actions = new ArrayList<action>();
		int nodeCount = 0;

		// Get the trace file name:
		String traceFile = getTagContent(scenario_file, "Tracefile");
		System.out.println("trace: " + traceFile);
		if(traceFile == null) {
			System.out.println("No tracefile specified.");
			return;
		}else {
			traceFile = scenario_path + traceFile;
			
			// Go through the trace and create actions for the events in it:
			String[] trace = getLinesOfFile(traceFile);
			
			if(trace != null) {
				linkTuple[]	tuple = parseLinkTuples(trace);
				
				int minNode = tuple[0].firstNode;	
				int maxNode = tuple[0].firstNode;
				for(i = 0; i < tuple.length; i++) {
					if(tuple[i].firstNode < minNode)
						minNode = tuple[i].firstNode;
					if(tuple[i].secondNode < minNode)
						minNode = tuple[i].firstNode;
					if(tuple[i].firstNode > maxNode)
						maxNode = tuple[i].firstNode;
					if(tuple[i].secondNode > maxNode)
						maxNode = tuple[i].firstNode;
				}
				nodeCount = (maxNode - minNode) + 1;
				//nodeCount = maxNode + 1;
				//minNode = 1;	
				System.out.println("nodes: " + nodeCount);

				// FIXME: preprocess:
				tuple = preprocess(tuple);
				
				for(i = 0; i < tuple.length; i++) {
				    if(tuple[i] != null) {
					if( tuple[i].firstNode <= maxNode && tuple[i].secondNode <= maxNode) {
					    if(tuple[i].firstNode < tuple[i].secondNode) {
						// In the trace, the nodes are called 1, 2, 3, 
						// but in the testbed, the nodes are called 
						// 0, 1, 2:
						if (tuple[i].awaitSignal) {
						    System.out.println("Using halflink scripts for event " + i + "...");
						    // HACK - sender node must come first to keep node-2 to node-1 traffic going.
						    String cmdUp = "./linkuphalf.sh node-" + (tuple[i].secondNode) + " node-" + (tuple[i].firstNode);
						    String cmdDown = "./linkdownhalf.sh node-" + (tuple[i].secondNode) + " node-" + (tuple[i].firstNode);
						    // Tarek - waiting is current disabled
						    actions.add(new action(tuple[i].startTime, cmdUp, false, true));
						    actions.add(new action(tuple[i].stopTime, cmdDown, false, false));
						} else {
						    System.out.println("Using fulllink scripts for event " + i + "...");
						    String cmdUp = "./linkup.sh node-" + (tuple[i].firstNode) + " node-" + (tuple[i].secondNode);
						    String cmdDown = "./linkdown.sh node-" + (tuple[i].firstNode) + " node-" + (tuple[i].secondNode);

						    actions.add(new action(tuple[i].startTime, cmdUp, false, true));
						    actions.add(new action(tuple[i].stopTime, cmdDown, false, false));
						}
					    }
					}
				    }
				}
			}else {
			    System.out.println("Unable to load trace file " + traceFile + ".");
			    return;
			}
		}

		// get other scenario information for future iterations
		String nodeDataFileName = getTagContent(scenario_file, "NodeData"); 
		nodeDataFileName = scenario_path + nodeDataFileName;
		String networkArch = getTagContent(scenario_file, "Architecture");
		String appNameFile = getTagContent(scenario_file, "Application");
		appNameFile = scenario_path + appNameFile;
		String warmupTimeString = getTagContent(scenario_file, "Warmup");


		// Tarek - start ControlThread
		ControlThread execControl = new ControlThread();
		execControl.start();

		//While loop start
		int run = 0;
		while(iterations > 0) {
			run++;
		System.out.println("Starting iteration #: " + run);
/*
                scenario_file = parseXML(getContents(scenario_file_name));
                if(scenario_file == null) {
                        System.out.println("Unable to load scenario file " + scenario_file_name + ".");
                        return;
                }
*/
			// Create execution log file.
			File oFile = new File("execution." +now.getTime() + ".log");
			PrintStream output = null;
			try {
				output = new PrintStream(new FileOutputStream(oFile));
			}catch (IOException ex) {
				ex.printStackTrace();
			}

			// Make sure all nodes are started!
			System.out.println("Waiting for nodes to startup..");
			if(system("./check_nodes.sh " + nodeCount) != 0) {
				System.out.println("Check nodes failed!");
				return;
			}

                        // Wait until nodes/nfs/sshd have initialized.
                        try     {
                                Thread.sleep(60000);
                        }catch(Exception e) {}

			// Remove logs from node.
            		System.out.println("Cleaning nodes");
			if(system("./clean_nodes.sh " + nodeCount) != 0) {
				System.out.println("Clean nodes failed!");
				return;
			}

			// Flush filters. All links are up for arch to start
			System.out.println("Flushing iptables - all links up.");
			if(system("./flush_filter.sh") != 0) {
				System.out.println("Flush filters failed!");
				return;
			}
		
			// Upload configuration file.
			/*String cfgFileName = getTagContent(scenario_file, "Configuration");
			if (cfgFileName != null) {
				System.out.println("config: " + cfgFileName);
				system("./upload_file.sh " + scenario_path + cfgFileName + " config.xml " + nodeCount);
			}*/
			
			// Upload unique configuration file per node.
/*			String cfgFileName = getTagContent(scenario_file, "Configuration");
			cfgFileName = scenario_path + cfgFileName;
			if (cfgFileName != null) {
				File aFile = new File(cfgFileName);
				try {
					BufferedReader input = new BufferedReader(new FileReader(aFile));
					try {
						String line = null;
						while((line = input.readLine()) != null) {
							String nodeName = line.split(";")[0];
							String configFile = line.split(";")[1];
							if (nodeName != null && configFile != null) {
								System.out.println(nodeName + ":Uploading config: " + configFile);
								system("./upload_file_to_node.sh " + scenario_path + configFile + " ccnd.conf " + nodeName);
							}
						}
					}finally {
						input.close();
					}
				}catch (IOException ex) {
					ex.printStackTrace();
				}
			}
*/
			// Upload all node data files to various dirs
//			String nodeDataFileName = getTagContent(scenario_file, "NodeData"); 
//			nodeDataFileName = scenario_path + nodeDataFileName;
			System.out.println("Getting node data from: " + nodeDataFileName);
			if (nodeDataFileName != null) {
                               File aFile = new File(nodeDataFileName);
                                try {
                                        BufferedReader input = new BufferedReader(new FileReader(aFile));
                                        try {
                                                String line = null;
                                                while((line = input.readLine()) != null) {
                                                        String nodeName = line.split(";")[0];
                                                        String dataFile = line.split(";")[1];
                                                        String uploadPath = line.split(";")[2];
                                                        String remoteFile = line.split(";")[3];
                                                        if (nodeName != null && dataFile != null) {
								System.out.println(nodeName + ":Uploading data: " + uploadPath + "/" + remoteFile);
								system("./upload_file_to_node.sh " + scenario_path + dataFile + " " + uploadPath + " " + remoteFile+ " " + nodeName);
                                                        }
                                                }
                                        }finally {
                                                input.close();
                                        }
                                }catch (IOException ex) {
                                        ex.printStackTrace();
                                }

			}
		
			// Start arch on each node:
//			String networkArch = getTagContent(scenario_file, "Architecture");
			
			for(i = 1; i <= nodeCount; i++) {
				System.out.println("node-"+i +" starting... " + networkArch);
				system("./start_program_on_node.sh " + "node-" + i + " arch " + networkArch);
			}
		
			// FIXME: make sure arch started!
			// Wait until arch has initialized.
			try	{
				Thread.sleep(30000);
			}catch(Exception e) {}

			System.out.println("Initializing iptables - all links down.");
                        // Initialize filters. Bring all links down
                        if(system("./initfilter.sh") != 0) {
                                System.out.println("Init filters failed!");
                                return;
                        }

			// Get the file name/path of the application to start:
/*			String appName = getTagContent(scenario_file, "Application");
			System.out.println("starting application... " + appName);
			if(appName != null) {
				// Start the application on all nodes
				for(i = 1; i <= nodeCount; i++) {
					system("./start_program_on_node.sh" + " node-" + i + " " + appName);
				}
			}
*/			
			// Get the file name/path of the application to start:
//			String appNameFile = getTagContent(scenario_file, "Application");
//			appNameFile = scenario_path + appNameFile;
			if (appNameFile != null) {
				File aFile = new File(appNameFile);
				try {
					BufferedReader input = new BufferedReader(new FileReader(aFile));
					try {
						String line = null;
						String nodeName = null;
						while((line = input.readLine()) != null) {
							nodeName = line.split(";")[0];
							String appName = line.split(";")[1];
							if (nodeName != null && appName != null) {
								System.out.println(nodeName +" starting... " + appName);
								system("./start_program_on_node.sh " + nodeName + " app " + appName);
							}
						}
					}finally {
						input.close();
					}
				}catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		
			// FIXME: make sure the application started!	
			// Wait until application has initialized.
			int warmupTime = 0;

//			String warmupTimeString = getTagContent(scenario_file, "Warmup");
			if(warmupTimeString != null) {
				warmupTime = Integer.parseInt(warmupTimeString);
//				warmupTime = Integer.parseInt(warmupTimeString)*1000;
                System.out.println("WarmupTime is: " + warmupTime);
			}

			try	{
				Thread.sleep(warmupTime);
			}catch(Exception e) {}

			// Sort the action list on time of execution.
			Collections.sort(actions, new actionComparator());
		
			// An array is easier to access:
			action[] event = actions.toArray(new action[0]);

			// Send sync broadcast signal to nodes  (nodeCount)
			int BCAST_PORT = 1892;
			int LISTEN_PORT = 1893;
			String BCAST_ADDR = "192.168.122.255";
			String READY_MSG = "ready";
 
			// get a datagram socket
			byte[] readymsg = new byte[16];
			try  {
		        DatagramSocket socket = new DatagramSocket(LISTEN_PORT);
	
			DatagramPacket ready = new DatagramPacket(readymsg, readymsg.length);
			System.out.println("Waiting for ready messages from 2 nodes running both applications...");
			boolean allReady = false;
			int readyCount = 0;
			Vector<String> nodeList = new Vector<String>();
			while (true) {
	    			socket.receive(ready);
 	    			String received = new String(ready.getData(), 0, ready.getLength());

			    if (received.equals(READY_MSG)) {
				String addr = ready.getAddress().getHostAddress();
				System.out.println("addr = " + addr);
				if (! nodeList.contains(addr)) {
				    nodeList.add(addr);
				    readyCount++;
				}
	    		    }
	    
		    		if (readyCount == 2) break; // TODO this only works for experiments with 1 sender and 1 receiver
			}
	
		System.out.println("All nodes accounted for, sending SYNC!");
		
	    	// send synchronization signal
		socket.setBroadcast(true);
       		String beacon = new String("gogogo");
        	InetAddress address = InetAddress.getByName(BCAST_ADDR);
	        DatagramPacket packet = new DatagramPacket(beacon.getBytes(), beacon.getBytes().length, address, BCAST_PORT);

		System.out.println("Syncing nodes...");
       	 	socket.send(packet);
     
        	socket.close();
    	 	}
		catch (Exception e) {
			System.out.println("Synchronization signal failed. Exception: " +e);
			return; 
		}	

			// Run the scenario:
			if(event.length > 0) {
				System.out.println("Start scenario");
				
				long done, waitedTime;
				long start = done = new Date().getTime();
				waitedTime = 0;

				System.out.println("Real Start time:" + start + ", done:" + done + ",waitedTime:" + waitedTime);
				output.println("Real Start time:" + start);
				for(i = 0; i < event.length; i++) {
				    long time_to_sleep = (start + event[i].timestamp) - done;
				    // long time_to_sleep = (start + event[i].timestamp*1000) - done;
				    // Tarek - process signal waiting code, need to subtract time waited from done
				    //				    if (i == 1) event[i].awaitSignal = true; // HACK!!
				    System.out.println("Checking event[i] flag: i="+i+", wait?"+event[i].awaitSignal + ". This is a linkEnable?"+event[i].linkEnable + ", time_tosleep:" + time_to_sleep);
				    if ( (event[i].awaitSignal == true) && (event[i].linkEnable == false) )  {
					waitedTime = new Date().getTime();
					if (execControl.checkFlag())
					    execControl.flipFlag();
					while (true) {
					    
					    if ( execControl.checkFlag() == true) {
						System.out.println("CONTROLTHREAD STATUS: Continue processing events."); 
						break; 
					    }
					}
					waitedTime = new Date().getTime() - waitedTime;
				    } else if(time_to_sleep > 0) { //original code
					try {
					    Thread.sleep(time_to_sleep);
					}catch(Exception e) {}
				    }
				    // Tarek - HACK!
/*				    if (i==0) { // first event
					System.out.println("Allowing traffic only from node-1 to node-2...");
					system("./linkuphalf.sh node-2 node-1");
				    } else if (i ==1) { // second event
					System.out.println("Dropping our half way rules..");
					system("./linkdownhalf.sh node-2 node-1");
				    }
				    else*/ system(event[i].cmd);
				    done = new Date().getTime() - waitedTime;
				    // Should only be logged if the testbed is evaluated.
				    System.out.println(event[i].cmd + " start:" + (start+event[i].timestamp) + ",end: " + done+ ",slept=" + time_to_sleep);
				    output.println(event[i].cmd + " " + done + ", actual: " + new Date().getTime());
				    //                  Date a = new Date(done);
				    System.out.println(event[i].cmd + " " + (new Date(done)));
				    output.println(event[i].cmd + " " + (new Date(done)));
				}
			}
			output.close();
				
			try {
				Thread.sleep(15000);
			}catch(Exception e) {}

			// stop haggle
			System.out.println("stopping ccnd and application");
			for(i = 1; i <= nodeCount; i++) {
//				system("start_program_on_node.sh" + " node-" + i + " clitool shutdown");
				system("./stop_program_on_node.sh " + "node-" + i + " arch");
				system("./stop_program_on_node.sh " + "node-" + i + " app");
			}
			// The reason for not placing the two ssh commands in one is to make
			// the nodes shut down faster by allowing them to shut down 
			// simultaneously.

			// Make sure all nodes have stopped running haggle:
			for(i = 1; i <= nodeCount; i++) {
				System.out.print("  node-" + i + "... "); 
				system("./wait_for_app_to_stop.sh" + " node-" + i + " ccnd");
				system("./wait_for_app_to_stop.sh" + " node-" + i + " java");
				System.out.println("ok");
			}

			// Collect and save logs.
			System.out.println("Collect logs for " + nodeCount + " nodes for iteration " + run + " to " + scenario_path);
			if(system("./collect_logs.sh " + nodeCount + " " + run + " " + scenario_path) != 0) {
				System.out.println("Collect logs failed!");
				return;
			}

			// Remove logs from node.
            System.out.println("Clean nodes");
			if(system("./clean_nodes.sh " + nodeCount) != 0) {
				System.out.println("Clean nodes failed!");
				return;
			}
			
			// Shutdown all nodes.
			System.out.println("Shutting down all nodes.");
			if(system("./shutdown_nodes.sh " + nodeCount) != 0) {
				System.out.println("Shutdown nodes failed!");
				return;
			}
			
			// Decrease number of iterations left.
			iterations--;
		}// while(iterations > 0) end loop.
		System.out.println("done");
		threadState = false;
		execControl.interrupt();
	}
}
