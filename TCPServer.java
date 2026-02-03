import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {
	public static void main(String args[]) {
		
		ServerSocket serverSocket = null;
		Socket clientSocket = null;

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
			serverSocket = new ServerSocket(portNumber);
			System.out.println("Socket created");
		}
		catch (IOException e) {
			System.err.println("Counldn't create a server socket: " + e);
			System.exit(1);
		}   
		
		while(true) try {
			System.out.println("Waiting for a client");
			clientSocket = serverSocket.accept();
			System.out.println("A client connected from " + clientSocket.getInetAddress().toString() + ":" + clientSocket.getPort());

			Scanner in = new Scanner(clientSocket.getInputStream());
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
	    	
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

			out.println(output);
			
		    	in.close();
	    		out.close();
	    		clientSocket.close();
		}
		catch (IOException e) {
			System.err.println("Error at work: " + e);
		}
	}    
}
