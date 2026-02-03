import java.io.*;
import java.net.*;
import java.util.*;

public class UDPServer {
	public static void main(String args[]) {
		
		DatagramSocket serverSocket = null;

		int portNumber = 0;
		String keyName = null;
		int keyValue = 0;
		
		for(int i=0; i<args.length;) {
			switch(args[i]) {
				case "-port" :
					portNumber = Integer.parseInt(args[i+1]);
					i += 2;
					break;
				case "-key" :
					keyName = args[i+1];
					System.out.println(keyName);
					i += 2;
					break;
				case "-value" :
					keyValue = Integer.parseInt(args[i+1]);
					i += 2;
					break;
				default:
					System.err.println("Unknown parameter: " + args[i]);
					i++;
			}
		}
		if(portNumber == 0 || keyName == null) {
			System.err.println("Incorrect execution syntax");
			System.exit(1);
		}

		try {
			System.out.println("Creating the main server socket at port " + portNumber);
			serverSocket = new DatagramSocket(portNumber);
			System.out.println("Socket created");
		}
		catch (IOException e) {
			System.err.println("Counldn't create a server socket: " + e);
			System.exit(1);
		}   
		
		while(true) try {
	                byte[] buffer = new byte[256];
                	DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        System.out.println("Waiting for a request");
			serverSocket.receive(packet);
			InetAddress clientAddress = packet.getAddress();
			int clientPort = packet.getPort();
			System.out.println("A request received from " + clientAddress.toString() + ":" + clientPort);

			Scanner in = new Scanner(new ByteArrayInputStream(packet.getData()));
	    	
			String command = "";
			String parameter = "";
			String name = "";
			int value = 0;

			String input = "";
			String output = "";

			command=in.next();
			input = command;
			switch(command) {
				case "GET": 
					parameter=in.next();
					input += " " + parameter;
					switch(parameter) {
						case "NAMES":
							output = "OK 1 " + keyName;
							break;
						case "VALUE":
							name = in.next();
							input += " " + name;
							if(name.equals(keyName)) {
								output = "OK " + keyValue;
							} else {
								output = "NA";
							}
							break;
						default:
							output = "NA";
					}
					break;
				case "SET":
					name = in.next();
					input += " " + name;
					if(name.equals(keyName)) {
						value = in.nextInt();
						input += " " + value;
						keyValue = value;
						output  = "OK";
					} else {
						output  = "NA";
					}
					break;
				case "QUIT":
					System.out.println("Terminating");
					System.exit(0);
				default:
					output = "NA";
			}
			System.out.println("Parsed command: " + input);
			System.out.println("Response: " + output);

			packet.setData(output.getBytes());
			serverSocket.send(packet);
			
		    	in.close();
		}
		catch (IOException e) {
			System.err.println("Error at work");
			System.err.println(e);
		}
	}    
}
