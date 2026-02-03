import java.net.*;
import java.io.*;

public class TCPClient {
	public static void main(String[] args) {
		
		Socket socket = null;
		PrintWriter out = null;
		BufferedReader in = null;

		InetAddress address = null;
		int port = 0;
		String command = "";


		for(int i=0; i<args.length;) {
			switch(args[i]) {
				case "-address":
					try {
						address = InetAddress.getByName(args[i+1]);
					} catch (UnknownHostException e) {
						System.err.println("Unknown host: " + args[i+1]);
					}
					i += 2;
					break;
				case "-port":
					port = Integer.parseInt(args[i+1]);
					i +=2;
					break;
				case "-command":
					i++;
					command = args[i++];
					String parameter = "";
					String name = "";
					int value = 0;

					switch(command) {
						case "GET":
							parameter=args[i++];
							command += " " + parameter;
							if(parameter.equals("NAMES")) {
							} else if(parameter.equals("VALUE")) {
								name = args[i++];
								command += " " + name;
							} else {
								System.err.println("Unknown: " + parameter);
							}
							break;
							case "SET":
								name = args[i++];
								value = Integer.parseInt(args[i++]);
								command += " " + name + " " + value;
								break;
						case "QUIT":
							break;
						default:
							System.err.println("Unknown: " + command);
					}
					break;
				default:
					System.err.println("Unknown parameter: " + args[i]);
					i++;
			}
		}
		
		if(address == null || port == 0 || command.equals("")) {
			System.err.println("Incorrect execution syntax");
                        System.exit(1);
                }

		try {
			System.out.println("Creating a client socket");
			socket = new Socket(address, port);
			System.out.println("Socket created");
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			System.out.println("Sending " + command + " as a request");
			out.println(command);
		
			if(! command.equals("QUIT")) {	
				String response = "";
			
				System.out.println("Waiting for a response");
				response = in.readLine();
			
				System.out.println(response);
			}
		
			out.close();
			in.close();
			socket.close();
		} catch(IOException e) {
			System.err.println("Error at work: " + e);
			System.exit(1);
		}
	}
}
